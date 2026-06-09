"""
Bill-stats Lambda for marathon-bib-expo.

Computes the platform's billing statistics **entirely outside Spring** and stores them as a single
precomputed snapshot row in MySQL (`billing_stats_snapshot`, scope GLOBAL). The Spring app only
reads that row and slices it to the requested range — it never computes a statistic. This Lambda is
invoked asynchronously (fire-and-forget, InvocationType=Event) by Spring whenever:

  - a bill is finalized          -> {"reason": "FINALIZE"}
  - a bill is marked paid/unpaid -> {"reason": "PAYMENT"}
  - an admin asks for a refresh  -> {"reason": "MANUAL"}

A failed invoke is not retried by Spring; the previous snapshot simply stays in place until the next
trigger or a manual refresh. The write is an idempotent upsert (last-writer-wins), so concurrent
runs are safe — no advisory lock needed.

What counts (auditor view):
  - **Only FINAL (issued) bills.** Drafts change repeatedly and are never a financial fact, so they
    are excluded entirely.
  - A range window is defined on the finalize date. Within a window the FINAL bills form a cohort
    that reconciles: billed = collected + outstanding (both amount and count). collected = the
    cohort's PAID bills; outstanding = the cohort's UNPAID bills (the open receivable).
  - finalized_at / paid_at can be null for bills created before this feature existed (dev stage);
    created_at is used as the finalize-date proxy and the finalize date as the collection-date proxy
    so no issued revenue is silently dropped.

Snapshot blob shape (one blob holds all three range windows so the read endpoint never recomputes):
  {
    "currency": "INR",
    "refreshedAt": "2026-06-08T10:00:00Z",
    "computedBy": "PAYMENT",
    "ranges": { "ALL": <RangeStats>, "YEAR": <RangeStats>, "MONTH": <RangeStats> }
  }
  RangeStats = {
    "billed":      {"amount", "net", "tax", "count"},
    "collected":   {"amount", "count"},
    "outstanding": {"amount", "count"},
    "collectionRate", "averageBill", "dso",
    "gst":         {"collected", "outstanding", "total"},
    "byReason":    {"AUTO": {"amount","count"}, "MANUAL": {"amount","count"}},
    "aging":       [{"bucket","amount","count"}, ...],            # 0-30 / 31-60 / 61-90 / 90+
    "trend":       {"interval":"MONTH","bucketLabels","billed","collected","count"},  # last 12 months
    "topOrganizations": [{"organizationId","organizerName","billed","collected","outstanding","billsCount"}]
  }

Config (environment variables):
  DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD   - RDS MySQL connection (same DB as the bill Lambda)
  CURRENCY             (default INR)
  STATS_TREND_MONTHS   (default 12)
  STATS_TOP_ORGS       (default 10)

Packaging: pymysql must be bundled (requirements.txt) as a layer or in the deployment zip. No S3,
DynamoDB or reportlab needed.
"""
import json
import logging
import os
from datetime import datetime, timezone
from decimal import Decimal, ROUND_HALF_UP

import pymysql

log = logging.getLogger()
log.setLevel(logging.INFO)

TWO_PLACES = Decimal("0.01")
HUNDRED = Decimal("100")
RANGES = ("ALL", "YEAR", "MONTH")
VALID_REASONS = ("FINALIZE", "PAYMENT", "MANUAL")
AGING_BANDS = (("0-30", 0, 30), ("31-60", 31, 60), ("61-90", 61, 90), ("90+", 91, None))

CURRENCY = os.environ.get("CURRENCY", "INR")
TREND_MONTHS = int(os.environ.get("STATS_TREND_MONTHS", "12"))
TOP_ORGS = int(os.environ.get("STATS_TOP_ORGS", "10"))


# ---- data access -----------------------------------------------------------

def _db_connect():
    return pymysql.connect(
        host=os.environ.get("DB_HOST"),
        port=int(os.environ.get("DB_PORT", "3306")),
        user=os.environ.get("DB_USER"),
        password=os.environ.get("DB_PASSWORD"),
        database=os.environ.get("DB_NAME"),
        cursorclass=pymysql.cursors.DictCursor,
        connect_timeout=5,
    )


def _load_finals(conn):
    """Every issued (FINAL) bill — the only rows that count. organizer_name is denormalized on the
    invoice, so no join is needed."""
    sql = """
        SELECT bill_id, organization_id, organizer_name, reason, payment_status,
               subtotal, tax_amount, total_amount, finalized_at, paid_at, created_at
          FROM invoices
         WHERE status = 'FINAL'
    """
    with conn.cursor() as cur:
        cur.execute(sql)
        return cur.fetchall()


def _upsert_snapshot(conn, blob, computed_by, now):
    sql = """
        INSERT INTO billing_stats_snapshot (scope, scope_key, snapshot_data, computed_by, refreshed_at, created_at)
        VALUES ('GLOBAL', 0, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
            snapshot_data = VALUES(snapshot_data),
            computed_by   = VALUES(computed_by),
            refreshed_at  = VALUES(refreshed_at)
    """
    with conn.cursor() as cur:
        cur.execute(sql, (json.dumps(blob), computed_by, now, now))


# ---- helpers ---------------------------------------------------------------

def _dec(value):
    return Decimal(str(value)) if value is not None else Decimal("0")


def _money(value):
    """Round a Decimal to 2 places and emit a float Jackson reads straight into BigDecimal."""
    return float(_dec(value).quantize(TWO_PLACES, ROUND_HALF_UP))


def _eff_finalized(inv):
    """Finalize date, falling back to creation date for pre-feature finals (dev stage)."""
    return inv["finalized_at"] or inv["created_at"]


def _eff_paid(inv):
    """Collection date, falling back to the finalize date when a PAID bill has no paid_at."""
    return inv["paid_at"] or _eff_finalized(inv)


def _is_paid(inv):
    return inv["payment_status"] == "PAID"


def _month_key(when):
    return f"{when.year:04d}-{when.month:02d}"


# ---- computation -----------------------------------------------------------

def _range_floor(range_name, now):
    """Lower bound (on the finalize date) for a range window; None means no floor (ALL)."""
    if range_name == "ALL":
        return None
    if range_name == "YEAR":
        return now.replace(year=now.year - 1)
    if range_name == "MONTH":
        return now - _delta_days(30)
    return None


def _delta_days(days):
    from datetime import timedelta
    return timedelta(days=days)


def _money_stat(invoices):
    amount = sum((_dec(i["total_amount"]) for i in invoices), Decimal("0"))
    return {"amount": _money(amount), "count": len(invoices)}


def _by_reason(cohort):
    result = {}
    for reason in ("AUTO", "MANUAL"):
        matching = [i for i in cohort if (i["reason"] or "").upper() == reason]
        result[reason] = _money_stat(matching)
    return result


def _aging(unpaid, now):
    counts = {band[0]: [Decimal("0"), 0] for band in AGING_BANDS}
    for inv in unpaid:
        age = (now - _eff_finalized(inv)).days
        for label, low, high in AGING_BANDS:
            if age >= low and (high is None or age <= high):
                counts[label][0] += _dec(inv["total_amount"])
                counts[label][1] += 1
                break
    return [{"bucket": label, "amount": _money(counts[label][0]), "count": counts[label][1]}
            for label, _, _ in AGING_BANDS]


def _dso(unpaid, now):
    """Days sales outstanding — average age in days of the unpaid bills (0 when none)."""
    if not unpaid:
        return 0
    total_age = sum((now - _eff_finalized(i)).days for i in unpaid)
    return round(total_age / len(unpaid))


def _top_organizations(cohort):
    by_org = {}
    for inv in cohort:
        org_id = int(inv["organization_id"])
        acc = by_org.setdefault(org_id, {
            "organizationId": org_id,
            "organizerName": inv["organizer_name"],
            "_billed": Decimal("0"),
            "_collected": Decimal("0"),
            "_outstanding": Decimal("0"),
            "billsCount": 0,
        })
        acc["organizerName"] = inv["organizer_name"]
        total = _dec(inv["total_amount"])
        acc["_billed"] += total
        if _is_paid(inv):
            acc["_collected"] += total
        else:
            acc["_outstanding"] += total
        acc["billsCount"] += 1

    ranked = sorted(by_org.values(), key=lambda a: a["_billed"], reverse=True)[:TOP_ORGS]
    return [{
        "organizationId": a["organizationId"],
        "organizerName": a["organizerName"],
        "billed": _money(a["_billed"]),
        "collected": _money(a["_collected"]),
        "outstanding": _money(a["_outstanding"]),
        "billsCount": a["billsCount"],
    } for a in ranked]


def _trend(finals, now):
    """Billed-vs-collected over the last TREND_MONTHS months (range-independent). billed/count by
    finalize month over all finals; collected by payment month over PAID finals."""
    labels = []
    index = {}
    year, month = now.year, now.month
    keys = []
    for _ in range(TREND_MONTHS):
        keys.append((year, month))
        month -= 1
        if month == 0:
            month = 12
            year -= 1
    keys.reverse()
    for i, (y, m) in enumerate(keys):
        key = f"{y:04d}-{m:02d}"
        labels.append(key)
        index[key] = i

    billed = [Decimal("0")] * TREND_MONTHS
    collected = [Decimal("0")] * TREND_MONTHS
    count = [0] * TREND_MONTHS
    for inv in finals:
        total = _dec(inv["total_amount"])
        fkey = _month_key(_eff_finalized(inv))
        if fkey in index:
            billed[index[fkey]] += total
            count[index[fkey]] += 1
        if _is_paid(inv):
            pkey = _month_key(_eff_paid(inv))
            if pkey in index:
                collected[index[pkey]] += total

    return {
        "interval": "MONTH",
        "bucketLabels": labels,
        "billed": [_money(v) for v in billed],
        "collected": [_money(v) for v in collected],
        "count": count,
    }


def _range_stats(cohort, trend, now):
    billed_gross = sum((_dec(i["total_amount"]) for i in cohort), Decimal("0"))
    billed_net = sum((_dec(i["subtotal"]) for i in cohort), Decimal("0"))
    billed_tax = sum((_dec(i["tax_amount"]) for i in cohort), Decimal("0"))
    billed_count = len(cohort)

    paid = [i for i in cohort if _is_paid(i)]
    unpaid = [i for i in cohort if not _is_paid(i)]
    collected_gross = sum((_dec(i["total_amount"]) for i in paid), Decimal("0"))
    outstanding_gross = sum((_dec(i["total_amount"]) for i in unpaid), Decimal("0"))
    gst_collected = sum((_dec(i["tax_amount"]) for i in paid), Decimal("0"))
    gst_outstanding = sum((_dec(i["tax_amount"]) for i in unpaid), Decimal("0"))

    collection_rate = (collected_gross / billed_gross * HUNDRED).quantize(TWO_PLACES, ROUND_HALF_UP) \
        if billed_gross > 0 else Decimal("0")
    average_bill = (billed_gross / billed_count).quantize(TWO_PLACES, ROUND_HALF_UP) \
        if billed_count else Decimal("0")

    return {
        "billed": {"amount": _money(billed_gross), "net": _money(billed_net),
                   "tax": _money(billed_tax), "count": billed_count},
        "collected": {"amount": _money(collected_gross), "count": len(paid)},
        "outstanding": {"amount": _money(outstanding_gross), "count": len(unpaid)},
        "collectionRate": float(collection_rate),
        "averageBill": _money(average_bill),
        "dso": _dso(unpaid, now),
        "gst": {"collected": _money(gst_collected), "outstanding": _money(gst_outstanding),
                "total": _money(billed_tax)},
        "byReason": _by_reason(cohort),
        "aging": _aging(unpaid, now),
        "trend": trend,
        "topOrganizations": _top_organizations(cohort),
    }


def _build_blob(finals, computed_by, now):
    trend = _trend(finals, now)
    ranges = {}
    for range_name in RANGES:
        floor = _range_floor(range_name, now)
        cohort = [i for i in finals if floor is None or _eff_finalized(i) >= floor]
        ranges[range_name] = _range_stats(cohort, trend, now)
    return {
        "currency": CURRENCY,
        "refreshedAt": now.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "computedBy": computed_by,
        "ranges": ranges,
    }


# ---- handler ---------------------------------------------------------------

def handler(event, context):
    reason = (event or {}).get("reason", "MANUAL")
    computed_by = reason if reason in VALID_REASONS else "MANUAL"
    log.info("Bill-stats recompute requested (reason=%s)", computed_by)

    now = datetime.now(timezone.utc).replace(microsecond=0)
    # Naive UTC for MySQL DATETIME/TIMESTAMP columns and age math against naive DB timestamps.
    now_naive = now.replace(tzinfo=None)

    conn = _db_connect()
    try:
        finals = _load_finals(conn)
        blob = _build_blob(finals, computed_by, now_naive)
        _upsert_snapshot(conn, blob, computed_by, now_naive)
        conn.commit()
        log.info("Bill-stats snapshot written: %d final bills, computedBy=%s", len(finals), computed_by)
        return {"status": "OK", "finals": len(finals), "computedBy": computed_by,
                "refreshedAt": blob["refreshedAt"]}
    finally:
        conn.close()

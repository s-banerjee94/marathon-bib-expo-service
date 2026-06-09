"""
Bill-stats Lambda for marathon-bib-expo.

Computes the platform's billing statistics **entirely outside Spring** and stores them as a single
precomputed snapshot row in MySQL (`billing_stats_snapshot`, scope GLOBAL). The Spring app only
reads that row and slices it to the requested range — it never computes a statistic. This Lambda is
invoked asynchronously (fire-and-forget, InvocationType=Event) whenever:

  - a brand-new draft is created -> {"reason": "DRAFT"}      (fired by the billing Lambda)
  - a bill is finalized          -> {"reason": "FINALIZE"}   (fired by Spring)
  - a bill is marked paid         -> {"reason": "PAYMENT"}    (fired by Spring)
  - an admin asks for a refresh  -> {"reason": "MANUAL"}     (fired by Spring)

A failed invoke is not retried by Spring; the previous snapshot simply stays in place until the next
trigger or a manual refresh. The write is an idempotent upsert (last-writer-wins), so concurrent
runs are safe — no advisory lock needed.

What counts (auditor view):
  - **Only FINAL (issued) bills** drive every *amount* and the reconciliation. Drafts change
    repeatedly and are never a financial fact, so amounts exclude them. Drafts are counted in
    exactly two places: the headline `counts` block and the `byStatus` donut (counts only).
  - A range window is defined on the finalize date (drafts on the creation date). Within a window
    the FINAL bills form a cohort that reconciles: billed = collected + outstanding (both amount and
    count). collected = the cohort's PAID bills; outstanding = the cohort's UNPAID bills.
  - finalized_at / paid_at can be null for bills created before this feature existed (dev stage);
    created_at is used as the finalize-date proxy and the finalize date as the collection-date proxy
    so no issued revenue is silently dropped.
  - Every headline/money metric carries a `deltaPct` vs the **immediately-preceding equal-length
    period** (MONTH -> prior 30d, YEAR -> prior calendar year, ALL -> no prior period, so null).
    `comparisonLabel` captions that period for the UI.

Snapshot blob shape (one blob holds all three range windows so the read endpoint never recomputes):
  {
    "currency": "INR",
    "refreshedAt": "2026-06-08T10:00:00Z",
    "computedBy": "PAYMENT",
    "ranges": { "ALL": <RangeStats>, "YEAR": <RangeStats>, "MONTH": <RangeStats> }
  }
  RangeStats = {
    "comparisonLabel": "vs Apr 12 – May 11" | null,
    "counts":      {"total"|"draft"|"final"|"paid"|"unpaid": {"value", "deltaPct"}},   # drafts included
    "money":       {"billed"|"collected"|"outstanding"|"averageBill": {"amount","deltaPct","spark"},
                    "billedThisMonth": {"amount","count","spark"}},                     # spark ~6 pts, oldest first
    "billed":      {"amount", "net", "tax", "count"},
    "collected":   {"amount", "count"},
    "outstanding": {"amount", "count"},
    "collectionRate", "averageBill", "dso",
    "gst":         {"collected", "outstanding", "total"},
    "byReason":    {"AUTO": {"amount","count"}, "MANUAL": {"amount","count"}},
    "byStatus":    {"DRAFT": <count>, "FINAL": <count>},                                # counts, drafts included
    "payment":     {"paid": <amount>, "unpaid": <amount>},
    "aging":       [{"bucket","amount","count"}, ...],            # 0-30 / 31-60 / 61-90 / 90+
    "trend":       {"interval":"MONTH","bucketLabels","billed","collected","count"},  # last 12 months
    "topEvents":         [{"eventId","eventName","organizerName","billed"}],          # top 5 by billed DESC
    "topOrganizations":  [{"organizationId","organizerName","billed","collected","outstanding","billsCount"}]
  }

Config (environment variables):
  DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD   - RDS MySQL connection (same DB as the bill Lambda)
  CURRENCY             (default INR)
  STATS_TREND_MONTHS   (default 12)
  STATS_TOP_ORGS       (default 10)
  STATS_TOP_EVENTS     (default 5)
  STATS_SPARK_POINTS   (default 6)

Packaging: pymysql must be bundled (requirements.txt) as a layer or in the deployment zip. No S3,
DynamoDB or reportlab needed.
"""
import json
import logging
import os
from datetime import datetime, timedelta, timezone
from decimal import Decimal, ROUND_HALF_UP

import pymysql

log = logging.getLogger()
log.setLevel(logging.INFO)

TWO_PLACES = Decimal("0.01")
HUNDRED = Decimal("100")
RANGES = ("ALL", "YEAR", "MONTH")
VALID_REASONS = ("DRAFT", "FINALIZE", "PAYMENT", "MANUAL")
AGING_BANDS = (("0-30", 0, 30), ("31-60", 31, 60), ("61-90", 61, 90), ("90+", 91, None))

CURRENCY = os.environ.get("CURRENCY", "INR")
TREND_MONTHS = int(os.environ.get("STATS_TREND_MONTHS", "12"))
TOP_ORGS = int(os.environ.get("STATS_TOP_ORGS", "10"))
TOP_EVENTS = int(os.environ.get("STATS_TOP_EVENTS", "5"))
SPARK_POINTS = int(os.environ.get("STATS_SPARK_POINTS", "6"))


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
    """Every issued (FINAL) bill — the only rows that drive amounts. organizer_name / event_name are
    denormalized on the invoice, so no join is needed."""
    sql = """
        SELECT bill_id, organization_id, organizer_name, event_id, event_name, reason, payment_status,
               subtotal, tax_amount, total_amount, finalized_at, paid_at, created_at
          FROM invoices
         WHERE status = 'FINAL'
    """
    with conn.cursor() as cur:
        cur.execute(sql)
        return cur.fetchall()


def _load_drafts(conn):
    """Draft bills — counted (never summed) in the headline counts and the byStatus donut. Only the
    creation date is needed to window them."""
    sql = "SELECT created_at FROM invoices WHERE status = 'DRAFT'"
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


def _sum_total(invoices):
    return sum((_dec(i["total_amount"]) for i in invoices), Decimal("0"))


def _delta_pct(current, previous):
    """Signed % change vs the previous period; None when there is no prior period or it was zero."""
    if previous is None:
        return None
    prev = _dec(previous)
    if prev == 0:
        return None
    return float(((_dec(current) - prev) / prev * HUNDRED).quantize(TWO_PLACES, ROUND_HALF_UP))


# ---- windowing -------------------------------------------------------------

def _range_floor(range_name, now):
    """Lower bound (on the finalize date) for a range window; None means no floor (ALL)."""
    if range_name == "YEAR":
        return now.replace(year=now.year - 1)
    if range_name == "MONTH":
        return now - timedelta(days=30)
    return None


def _prev_floor(range_name, now):
    """Lower bound of the immediately-preceding equal-length window; None for ALL (no prior period)."""
    if range_name == "YEAR":
        return now.replace(year=now.year - 2)
    if range_name == "MONTH":
        return now - timedelta(days=60)
    return None


def _finals_in(finals, floor, ceiling):
    return [i for i in finals
            if (floor is None or _eff_finalized(i) >= floor)
            and (ceiling is None or _eff_finalized(i) < ceiling)]


def _drafts_in(drafts, floor, ceiling):
    return [i for i in drafts
            if (floor is None or i["created_at"] >= floor)
            and (ceiling is None or i["created_at"] < ceiling)]


def _comparison_label(prev_floor, floor):
    """Caption for the delta period [prev_floor, floor); None for ALL (no prior period)."""
    if prev_floor is None or floor is None:
        return None
    end = floor - timedelta(days=1)
    return f"vs {prev_floor.strftime('%b %d')} – {end.strftime('%b %d')}"


# ---- existing per-cohort figures -------------------------------------------

def _money_stat(invoices):
    return {"amount": _money(_sum_total(invoices)), "count": len(invoices)}


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


def _top_events(cohort):
    """Top events by gross billed, descending; tie-break on event name. FINAL-only (it's an amount)."""
    by_event = {}
    for inv in cohort:
        event_id = int(inv["event_id"])
        acc = by_event.setdefault(event_id, {
            "eventId": event_id,
            "eventName": inv["event_name"],
            "organizerName": inv["organizer_name"],
            "_billed": Decimal("0"),
        })
        acc["eventName"] = inv["event_name"]
        acc["organizerName"] = inv["organizer_name"]
        acc["_billed"] += _dec(inv["total_amount"])

    ranked = sorted(by_event.values(), key=lambda a: (-a["_billed"], a["eventName"] or ""))[:TOP_EVENTS]
    return [{
        "eventId": a["eventId"],
        "eventName": a["eventName"],
        "organizerName": a["organizerName"],
        "billed": _money(a["_billed"]),
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


# ---- new: headline counts, money cards (+ sparklines) ----------------------

def _counts(cohort, prev_cohort, draft_cur, draft_prev):
    """Headline counts incl. drafts. Amounts stay FINAL-only elsewhere; here it is counts only."""
    final_cur = len(cohort)
    paid_cur = sum(1 for i in cohort if _is_paid(i))
    unpaid_cur = final_cur - paid_cur
    draft_c = len(draft_cur)
    total_cur = final_cur + draft_c

    if prev_cohort is None:
        prev = {"total": None, "draft": None, "final": None, "paid": None, "unpaid": None}
    else:
        final_p = len(prev_cohort)
        paid_p = sum(1 for i in prev_cohort if _is_paid(i))
        draft_p = len(draft_prev)
        prev = {
            "total": final_p + draft_p,
            "draft": draft_p,
            "final": final_p,
            "paid": paid_p,
            "unpaid": final_p - paid_p,
        }

    cur = {"total": total_cur, "draft": draft_c, "final": final_cur, "paid": paid_cur, "unpaid": unpaid_cur}
    return {key: {"value": cur[key], "deltaPct": _delta_pct(cur[key], prev[key])} for key in cur}


def _spark_series(window_finals, floor, now):
    """Cumulative (billed, count, collected) Decimal/int lists over SPARK_POINTS time points spanning
    [start, now], oldest first; cosmetic running totals for the card mini-charts. For ALL (floor is
    None) the start is the earliest finalize date in the cohort."""
    if floor is None:
        dates = [_eff_finalized(i) for i in window_finals]
        start = min(dates) if dates else now
    else:
        start = floor

    if now <= start:
        billed = _sum_total(window_finals)
        collected = _sum_total([i for i in window_finals if _is_paid(i)])
        cnt = len(window_finals)
        return [billed] * SPARK_POINTS, [cnt] * SPARK_POINTS, [collected] * SPARK_POINTS

    span = now - start
    billed_cum, count_cum, collected_cum = [], [], []
    for k in range(1, SPARK_POINTS + 1):
        boundary = start + span * (k / SPARK_POINTS)
        billed = Decimal("0")
        collected = Decimal("0")
        cnt = 0
        for inv in window_finals:
            if start <= _eff_finalized(inv) <= boundary:
                billed += _dec(inv["total_amount"])
                cnt += 1
                if _is_paid(inv) and _eff_paid(inv) <= boundary:
                    collected += _dec(inv["total_amount"])
        billed_cum.append(billed)
        count_cum.append(cnt)
        collected_cum.append(collected)
    return billed_cum, count_cum, collected_cum


def _money_cards(cohort, prev_cohort, floor, billed_this_month, now):
    """The five money cards: amount + deltaPct (vs the previous period) + a short sparkline."""
    billed_gross = _sum_total(cohort)
    paid = [i for i in cohort if _is_paid(i)]
    unpaid = [i for i in cohort if not _is_paid(i)]
    collected_gross = _sum_total(paid)
    outstanding_gross = _sum_total(unpaid)
    avg = (billed_gross / len(cohort)) if cohort else Decimal("0")

    billed_cum, count_cum, collected_cum = _spark_series(cohort, floor, now)
    billed_spark = [_money(v) for v in billed_cum]
    collected_spark = [_money(v) for v in collected_cum]
    outstanding_spark = [_money(billed_cum[k] - collected_cum[k]) for k in range(len(billed_cum))]
    avg_spark = [_money(billed_cum[k] / count_cum[k]) if count_cum[k] else _money(Decimal("0"))
                 for k in range(len(billed_cum))]

    if prev_cohort is None:
        billed_prev = collected_prev = outstanding_prev = avg_prev = None
    else:
        billed_prev = _sum_total(prev_cohort)
        collected_prev = _sum_total([i for i in prev_cohort if _is_paid(i)])
        outstanding_prev = _sum_total([i for i in prev_cohort if not _is_paid(i)])
        avg_prev = (billed_prev / len(prev_cohort)) if prev_cohort else Decimal("0")

    return {
        "billed": {"amount": _money(billed_gross),
                   "deltaPct": _delta_pct(billed_gross, billed_prev), "spark": billed_spark},
        "collected": {"amount": _money(collected_gross),
                      "deltaPct": _delta_pct(collected_gross, collected_prev), "spark": collected_spark},
        "outstanding": {"amount": _money(outstanding_gross),
                        "deltaPct": _delta_pct(outstanding_gross, outstanding_prev), "spark": outstanding_spark},
        "averageBill": {"amount": _money(avg),
                        "deltaPct": _delta_pct(avg, avg_prev), "spark": avg_spark},
        "billedThisMonth": billed_this_month,
    }


def _billed_this_month(finals, now):
    """Current-calendar-month billed amount + count (range-independent), with a cumulative sparkline."""
    month_start = now.replace(day=1, hour=0, minute=0, second=0, microsecond=0)
    month = [i for i in finals if _eff_finalized(i) >= month_start]
    billed_cum, _, _ = _spark_series(month, month_start, now)
    return {
        "amount": _money(_sum_total(month)),
        "count": len(month),
        "spark": [_money(v) for v in billed_cum],
    }


# ---- assembly --------------------------------------------------------------

def _range_stats(cohort, prev_cohort, draft_cur, draft_prev, floor, prev_floor,
                 trend, billed_this_month, now):
    billed_gross = _sum_total(cohort)
    billed_net = sum((_dec(i["subtotal"]) for i in cohort), Decimal("0"))
    billed_tax = sum((_dec(i["tax_amount"]) for i in cohort), Decimal("0"))
    billed_count = len(cohort)

    paid = [i for i in cohort if _is_paid(i)]
    unpaid = [i for i in cohort if not _is_paid(i)]
    collected_gross = _sum_total(paid)
    outstanding_gross = _sum_total(unpaid)
    gst_collected = sum((_dec(i["tax_amount"]) for i in paid), Decimal("0"))
    gst_outstanding = sum((_dec(i["tax_amount"]) for i in unpaid), Decimal("0"))

    collection_rate = (collected_gross / billed_gross * HUNDRED).quantize(TWO_PLACES, ROUND_HALF_UP) \
        if billed_gross > 0 else Decimal("0")
    average_bill = (billed_gross / billed_count).quantize(TWO_PLACES, ROUND_HALF_UP) \
        if billed_count else Decimal("0")

    return {
        "comparisonLabel": _comparison_label(prev_floor, floor),
        "counts": _counts(cohort, prev_cohort, draft_cur, draft_prev),
        "money": _money_cards(cohort, prev_cohort, floor, billed_this_month, now),
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
        "byStatus": {"DRAFT": len(draft_cur), "FINAL": billed_count},
        "payment": {"paid": _money(collected_gross), "unpaid": _money(outstanding_gross)},
        "aging": _aging(unpaid, now),
        "trend": trend,
        "topEvents": _top_events(cohort),
        "topOrganizations": _top_organizations(cohort),
    }


def _build_blob(finals, drafts, computed_by, now):
    trend = _trend(finals, now)
    billed_this_month = _billed_this_month(finals, now)
    ranges = {}
    for range_name in RANGES:
        floor = _range_floor(range_name, now)
        prev_floor = _prev_floor(range_name, now)
        cohort = _finals_in(finals, floor, None)
        prev_cohort = None if floor is None else _finals_in(finals, prev_floor, floor)
        draft_cur = _drafts_in(drafts, floor, None)
        draft_prev = None if floor is None else _drafts_in(drafts, prev_floor, floor)
        ranges[range_name] = _range_stats(cohort, prev_cohort, draft_cur, draft_prev,
                                          floor, prev_floor, trend, billed_this_month, now)
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
        drafts = _load_drafts(conn)
        blob = _build_blob(finals, drafts, computed_by, now_naive)
        _upsert_snapshot(conn, blob, computed_by, now_naive)
        conn.commit()
        log.info("Bill-stats snapshot written: %d final + %d draft bills, computedBy=%s",
                 len(finals), len(drafts), computed_by)
        return {"status": "OK", "finals": len(finals), "drafts": len(drafts),
                "computedBy": computed_by, "refreshedAt": blob["refreshedAt"]}
    finally:
        conn.close()

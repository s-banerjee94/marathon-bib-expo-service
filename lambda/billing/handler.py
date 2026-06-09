"""
Billing Lambda for marathon-bib-expo.

Builds the *structured* bill for an event directly in MySQL: the invoice header (rollup
totals only) plus a system-generated PARTICIPANT line item carrying the participant fee.
Draft PDFs are rendered on the frontend; on **FINAL** this Lambda also renders the GST
tax-invoice PDF, stores it in S3, and writes the S3 key back to the bill (`pdf_key`) — all
inline, so the finalize call returns with a ready download URL. (Spring never touches PDF
or S3 work.) Pricing inputs (UNIT_PRICE, GST_RATE_PERCENT, CURRENCY) are env config and
must stay in sync with the Spring side's BillingRates. Invoked two ways:

  - AUTO   : an EventBridge one-time schedule ~24h after the event turned terminal.
             Payload: {"eventId": "...", "reason": "AUTO", "scheduleName": "..."}.
             AUTO **always produces a DRAFT** and notifies ROOT/admins to review it.
  - MANUAL : the Spring app, on a ROOT/ADMIN/organizer request.
             Payload: {"eventId": "...", "reason": "MANUAL", "mode": "DRAFT"|"FINAL"}.
             DRAFT creates/refreshes the draft. For FINAL, Spring has already flipped the draft
             to status=FINAL (no number yet) before invoking; this Lambda only *processes* that
             marked bill, and refuses (NOT_MARKED_FINAL) if none is marked.

Lifecycle (see BILLING_GAPS.md):
  - DRAFT  : replaceable proforma, no invoice number, not payable. At most one per event;
             a refresh updates the participant charge and keeps any manual line items.
  - FINAL  : the GST tax invoice. Processes the bill Spring marked FINAL: assigns a gap-free
             per-financial-year serial (INV/2026-27/0001), recomputes including the draft's
             manual line items, and locks the event (event_billing_state.final_locked = 1).
             The Lambda never creates a bill on finalize. One per event.

Concurrency: the whole decision runs under the per-event advisory lock GET_LOCK('bill-<id>')
— the same lock the Spring edit endpoints take — so generate/edit/finalize serialize across
both processes.

Billing rules:
  - amount  = participant count x unit price (+ manual line items) + GST.
  - COMPLETED -> always billable.
  - CANCELLED -> billable only if distribution had started.
  - DRAFT count comes from the fast event-stats TOTAL counter; FINAL count comes from an
    authoritative DynamoDB COUNT of participant rows (logged if the two disagree).

Config (environment variables):
  DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD   - RDS MySQL connection
  STATS_TABLE          (default marathon-event-stats)
  PARTICIPANTS_TABLE   (default marathon-participants)
  UNIT_PRICE           (default 5)        - per participant
  CURRENCY             (default INR)
  GST_RATE_PERCENT     (default 18)
  BILL_LOCK_TIMEOUT    (default 10)       - seconds to wait for the per-event lock
  MEDIA_BUCKET         (default marathon-bib-expo-media)  - S3 bucket for invoice PDFs;
                                            must match the Spring S3 bucket so it can presign
  ISSUER_NAME, ISSUER_ADDRESS, ISSUER_GSTIN           - the platform's billing identity (PDF)
  STATS_LAMBDA_ARN     - the bill-stats Lambda, invoked async when a brand-new draft is created so
                         the snapshot's draft/total counts stay fresh (the execution role needs
                         lambda:InvokeFunction on it). Unset -> the recompute is skipped (logged).

Bill-stats: a new draft changes the platform's draft/total bill counts, which the stats snapshot
includes, so creating one fires the bill-stats Lambda (reason DRAFT) — covering both the manual and
AUTO draft paths, which Spring cannot trigger for the timer. Finalize and payment recomputes are
fired by Spring; a draft *refresh* changes no count and is FINAL-only-irrelevant, so it skips it.

Packaging: boto3 ships in the Lambda runtime; pymysql and reportlab must be bundled
(requirements.txt) as a layer or in the deployment zip.
"""
import json
import logging
import os
import uuid
from datetime import datetime, timezone
from decimal import Decimal, ROUND_HALF_UP
from io import BytesIO

import boto3
import pymysql
from boto3.dynamodb.conditions import Key
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import mm
from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle

log = logging.getLogger()
log.setLevel(logging.INFO)

TERMINAL_STATUSES = ("COMPLETED", "CANCELLED")
TWO_PLACES = Decimal("0.01")
HUNDRED = Decimal("100")

dynamodb = boto3.resource("dynamodb")
scheduler = boto3.client("scheduler")
s3 = boto3.client("s3")
lambda_client = boto3.client("lambda")


class DiscountBelowZero(Exception):
    """A final's discounts would drive its subtotal below zero. A final tax invoice must never be
    negative, so the finalize is refused and Spring keeps the bill as an editable draft."""


# ---- config ----------------------------------------------------------------

def _env(name, default=None):
    return os.environ.get(name, default)


STATS_TABLE = _env("STATS_TABLE", "marathon-event-stats")
PARTICIPANTS_TABLE = _env("PARTICIPANTS_TABLE", "marathon-participants")
UNIT_PRICE = Decimal(_env("UNIT_PRICE", "5"))
CURRENCY = _env("CURRENCY", "INR")
GST_RATE = Decimal(_env("GST_RATE_PERCENT", "18"))
LOCK_TIMEOUT_SECONDS = int(_env("BILL_LOCK_TIMEOUT", "10"))
MEDIA_BUCKET = _env("MEDIA_BUCKET", "marathon-bib-expo-media")
ISSUER_NAME = _env("ISSUER_NAME", "Acme Timing Pvt Ltd")
ISSUER_ADDRESS = _env("ISSUER_ADDRESS", "221B Placeholder Road, Mumbai, MH 400001")
ISSUER_GSTIN = _env("ISSUER_GSTIN", "27AAAAA0000A1Z5")
# ARN of the dedicated bill-stats Lambda, invoked async when a brand-new draft is created so the
# snapshot's draft/total counts stay fresh. Unset -> the recompute is skipped (logged).
STATS_LAMBDA_ARN = _env("STATS_LAMBDA_ARN")


# ---- data access -----------------------------------------------------------

def _db_connect():
    return pymysql.connect(
        host=_env("DB_HOST"),
        port=int(_env("DB_PORT", "3306")),
        user=_env("DB_USER"),
        password=_env("DB_PASSWORD"),
        database=_env("DB_NAME"),
        cursorclass=pymysql.cursors.DictCursor,
        connect_timeout=5,
    )


def _scalar(row):
    """First value of a one-column DictCursor row, or None."""
    if not row:
        return None
    return next(iter(row.values()))


def _load_event(conn, event_id):
    """Event + its organizer name (the bill-to party), or None if the event is gone."""
    sql = """
        SELECT e.id, e.event_name, e.event_start_date, e.status,
               e.distribution_started, e.organization_id, o.organizer_name
          FROM events e
          JOIN organizations o ON o.id = e.organization_id
         WHERE e.id = %s
    """
    with conn.cursor() as cur:
        cur.execute(sql, (event_id,))
        return cur.fetchone()


def _existing_final(conn, event_id):
    """A fully issued final — one that already has an invoice number. A bill Spring marked FINAL
    but the Lambda has not processed yet has a null invoice_number and is not counted here."""
    with conn.cursor() as cur:
        cur.execute("SELECT bill_id, invoice_number FROM invoices "
                    "WHERE event_id = %s AND status = 'FINAL' AND invoice_number IS NOT NULL "
                    "LIMIT 1", (event_id,))
        return cur.fetchone()


def _existing_draft(conn, event_id):
    with conn.cursor() as cur:
        cur.execute("SELECT bill_id FROM invoices "
                    "WHERE event_id = %s AND status = 'DRAFT' LIMIT 1", (event_id,))
        return cur.fetchone()


def _marked_final(conn, event_id):
    """The bill Spring flipped to FINAL ahead of finalizing — status FINAL, invoice_number still
    null, awaiting this Lambda to issue the number and PDF."""
    with conn.cursor() as cur:
        cur.execute("SELECT bill_id FROM invoices "
                    "WHERE event_id = %s AND status = 'FINAL' AND invoice_number IS NULL "
                    "LIMIT 1", (event_id,))
        return cur.fetchone()


def _draft_participant_count(conn, bill_id):
    """Billed quantity currently on the draft (the PARTICIPANT line's quantity), or None."""
    with conn.cursor() as cur:
        cur.execute("SELECT quantity FROM invoice_line_item "
                    "WHERE invoice_id = %s AND kind = 'PARTICIPANT' LIMIT 1", (bill_id,))
        row = cur.fetchone()
    return int(row["quantity"]) if row and row["quantity"] is not None else None


def _participant_count_fast(event_id):
    """Total uploaded participants from the event-stats TOTAL counter (cheap, for drafts)."""
    table = dynamodb.Table(STATS_TABLE)
    item = table.get_item(Key={"eventId": str(event_id), "statKey": "TOTAL"}).get("Item")
    return int(item["count"]) if item and item.get("count") is not None else 0


def _participant_count_authoritative(event_id):
    """Real count of participant rows (for finals); logs if it drifts from the fast counter."""
    table = dynamodb.Table(PARTICIPANTS_TABLE)
    total = 0
    kwargs = {"KeyConditionExpression": Key("eventId").eq(str(event_id)), "Select": "COUNT"}
    while True:
        resp = table.query(**kwargs)
        total += resp.get("Count", 0)
        start_key = resp.get("LastEvaluatedKey")
        if not start_key:
            break
        kwargs["ExclusiveStartKey"] = start_key
    fast = _participant_count_fast(event_id)
    if fast != total:
        log.warning("Participant count mismatch for event %s: authoritative=%s counter=%s",
                    event_id, total, fast)
    return total


# ---- locking ---------------------------------------------------------------

def _lock_key(event_id):
    return f"bill-{event_id}"


def _acquire_lock(conn, event_id):
    with conn.cursor() as cur:
        cur.execute("SELECT GET_LOCK(%s, %s)", (_lock_key(event_id), LOCK_TIMEOUT_SECONDS))
        return _scalar(cur.fetchone()) == 1


def _release_lock(conn, event_id):
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT RELEASE_LOCK(%s)", (_lock_key(event_id),))
            cur.fetchone()
    except Exception as e:  # best-effort
        log.warning("Failed to release billing lock for event %s: %s", event_id, e)


# ---- rules + math ----------------------------------------------------------

def _is_billable(event):
    status = event["status"]
    if status not in TERMINAL_STATUSES:
        log.info("Event %s is %s (not terminal) — skipping", event["id"], status)
        return False
    if status == "CANCELLED" and not event["distribution_started"]:
        log.info("Event %s cancelled with no distribution — no bill", event["id"])
        return False
    return True


def _financial_year(when):
    """Indian financial year (Apr–Mar) for a date, e.g. April 2026 -> '2026-27'."""
    start = when.year if when.month >= 4 else when.year - 1
    return f"{start}-{(start + 1) % 100:02d}"


def _next_invoice_number(conn):
    """Allocate the next gap-free per-FY serial via a LAST_INSERT_ID upsert."""
    fy = _financial_year(datetime.now(timezone.utc))
    with conn.cursor() as cur:
        cur.execute(
            "INSERT INTO invoice_number_sequence (financial_year, last_number) "
            "VALUES (%s, LAST_INSERT_ID(1)) "
            "ON DUPLICATE KEY UPDATE last_number = LAST_INSERT_ID(last_number + 1)",
            (fy,))
        cur.execute("SELECT LAST_INSERT_ID()")
        n = _scalar(cur.fetchone())
    return f"INV/{fy}/{int(n):04d}"


# ---- persistence -----------------------------------------------------------

def _insert_invoice_header(conn, ev, *, bill_id, status, invoice_number,
                           subtotal, tax, total, reason, created_at, event_date):
    sql = """
        INSERT INTO invoices
            (bill_id, invoice_number, status, event_id, organization_id, event_name,
             organizer_name, event_date, subtotal, tax_amount, total_amount,
             reason, payment_status, created_at, updated_at)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
    """
    with conn.cursor() as cur:
        cur.execute(sql, (
            bill_id, invoice_number, status, int(ev["id"]), int(ev["organization_id"]),
            ev["event_name"], ev["organizer_name"], event_date, subtotal, tax, total,
            reason, "UNPAID", created_at, created_at,
        ))


def _set_participant_line(conn, bill_id, count, now):
    """Create or refresh the bill's single system-generated PARTICIPANT line (the participant fee).

    A manual unit-price override is preserved: once the line exists, a refresh updates only the
    quantity (count) and recomputes amount = count × existing unit price; the price is never
    reset to the default."""
    with conn.cursor() as cur:
        cur.execute("SELECT id, unit_price FROM invoice_line_item "
                    "WHERE invoice_id = %s AND kind = 'PARTICIPANT' LIMIT 1", (bill_id,))
        row = cur.fetchone()
        if row is None:
            amount = (UNIT_PRICE * count).quantize(TWO_PLACES, ROUND_HALF_UP)
            cur.execute(
                "INSERT INTO invoice_line_item "
                "(invoice_id, kind, description, quantity, unit_price, amount, "
                " system_generated, created_at, updated_at) "
                "VALUES (%s, 'PARTICIPANT', %s, %s, %s, %s, 1, %s, %s)",
                (bill_id, "Participant registrations", count, UNIT_PRICE, amount, now, now))
        else:
            unit_price = Decimal(str(row["unit_price"]))
            amount = (unit_price * count).quantize(TWO_PLACES, ROUND_HALF_UP)
            cur.execute(
                "UPDATE invoice_line_item SET quantity = %s, amount = %s, updated_at = %s "
                "WHERE id = %s",
                (count, amount, now, row["id"]))


def _recompute_totals(conn, bill_id):
    """Recompute the bill's totals from all its line items, re-deriving percentage-discount
    amounts against the current pre-tax charge base (and persisting them). Returns
    (subtotal, tax, total)."""
    with conn.cursor() as cur:
        cur.execute("SELECT id, kind, amount, discount_percent "
                    "FROM invoice_line_item WHERE invoice_id = %s", (bill_id,))
        lines = cur.fetchall()

    # Pre-tax charge base: every non-discount line (participant fee + extras).
    base = Decimal("0")
    for ln in lines:
        if ln["kind"] != "DISCOUNT":
            base += Decimal(str(ln["amount"]))
    base = base.quantize(TWO_PLACES, ROUND_HALF_UP)

    now = datetime.now(timezone.utc)
    discount_sum = Decimal("0")
    updates = []
    for ln in lines:
        if ln["kind"] != "DISCOUNT":
            continue
        if ln["discount_percent"] is not None:
            derived = -(Decimal(str(ln["discount_percent"])) / HUNDRED * base) \
                .quantize(TWO_PLACES, ROUND_HALF_UP)
            updates.append((derived, now, ln["id"]))
            discount_sum += derived
        else:
            discount_sum += Decimal(str(ln["amount"]))
    if updates:
        with conn.cursor() as cur:
            cur.executemany(
                "UPDATE invoice_line_item SET amount = %s, updated_at = %s WHERE id = %s", updates)

    subtotal = (base + discount_sum).quantize(TWO_PLACES, ROUND_HALF_UP)
    tax = (subtotal * GST_RATE / HUNDRED).quantize(TWO_PLACES, ROUND_HALF_UP)
    total = (subtotal + tax).quantize(TWO_PLACES, ROUND_HALF_UP)
    return subtotal, tax, total


def _refresh_header(conn, bill_id, subtotal, tax, total, updated_at):
    with conn.cursor() as cur:
        cur.execute("UPDATE invoices SET subtotal = %s, tax_amount = %s, total_amount = %s, "
                    "updated_at = %s WHERE bill_id = %s",
                    (subtotal, tax, total, updated_at, bill_id))


def _finalize_header(conn, bill_id, invoice_number, subtotal, tax, total, updated_at):
    # finalized_at marks when the bill became an issued receivable — it drives the bill-stats
    # range windows, receivables aging and DSO. Stamped once, here, on finalize.
    with conn.cursor() as cur:
        cur.execute("UPDATE invoices SET status = 'FINAL', invoice_number = %s, subtotal = %s, "
                    "tax_amount = %s, total_amount = %s, reason = 'MANUAL', updated_at = %s, "
                    "finalized_at = %s "
                    "WHERE bill_id = %s",
                    (invoice_number, subtotal, tax, total, updated_at, updated_at, bill_id))


def _lock_billing_state(conn, event_id):
    """Mark the event finalized so no further bill can be requested (atomic with the final)."""
    with conn.cursor() as cur:
        cur.execute("INSERT IGNORE INTO event_billing_state "
                    "(event_id, org_admin_attempts, admin_attempts, final_locked) "
                    "VALUES (%s, 0, 0, 0)", (event_id,))
        cur.execute("UPDATE event_billing_state SET final_locked = 1 WHERE event_id = %s", (event_id,))


def _insert_notifications(conn, ev):
    """One notification row per ROOT, ADMIN, and the event's organization's ORGANIZER_ADMIN(s)."""
    with conn.cursor() as cur:
        cur.execute(
            "SELECT id FROM users WHERE enabled = 1 AND "
            "(role IN ('ROOT', 'ADMIN') OR (role = 'ORGANIZER_ADMIN' AND organization_id = %s))",
            (int(ev["organization_id"]),))
        recipients = [_scalar(r) if isinstance(r, dict) else r[0] for r in cur.fetchall()]
    if not recipients:
        return
    title = "Draft bill ready for review"
    message = f"A draft bill has been generated for '{ev['event_name']}'. Review and finalize it."
    now = datetime.now(timezone.utc)
    rows = [(uid, title, message, 0, int(ev["id"]), now) for uid in recipients]
    with conn.cursor() as cur:
        cur.executemany(
            "INSERT INTO notifications (user_id, title, message, is_read, event_id, created_at) "
            "VALUES (%s, %s, %s, %s, %s, %s)", rows)
    log.info("Queued %d draft-bill notifications for event %s", len(rows), ev["id"])


# ---- bill builders ---------------------------------------------------------

def _create_or_refresh_draft(conn, ev, count, reason):
    """Returns (status, bill_id, created_new): CREATED_DRAFT with created_new True only when a
    brand-new draft row was inserted (a refresh of an existing draft is created_new False), or
    SKIPPED_DUPLICATE if nothing changed."""
    event_id = int(ev["id"])
    now = datetime.now(timezone.utc)
    existing = _existing_draft(conn, event_id)
    created_new = existing is None
    if existing is not None:
        bill_id = existing["bill_id"]
        if _draft_participant_count(conn, bill_id) == count:
            return "SKIPPED_DUPLICATE", bill_id, False
    else:
        bill_id = str(uuid.uuid4())
        _insert_invoice_header(conn, ev, bill_id=bill_id, status="DRAFT", invoice_number=None,
                               subtotal=Decimal("0"), tax=Decimal("0"), total=Decimal("0"),
                               reason=reason, created_at=now, event_date=ev.get("event_start_date"))

    _set_participant_line(conn, bill_id, count, now)
    subtotal, tax, total = _recompute_totals(conn, bill_id)
    _refresh_header(conn, bill_id, subtotal, tax, total, now)
    return "CREATED_DRAFT", bill_id, created_new


def _process_marked_final(conn, ev, count):
    """Process the bill Spring already marked FINAL: set the participant count, recompute totals,
    issue the invoice number, and lock the event. The Lambda never creates the bill on finalize —
    Spring flips an existing draft to FINAL first. Returns (bill_id, number), or None when no bill
    is marked final (so the caller can refuse and let Spring roll its mark back)."""
    event_id = int(ev["id"])
    marked = _marked_final(conn, event_id)
    if marked is None:
        return None
    bill_id = marked["bill_id"]
    now = datetime.now(timezone.utc)
    _set_participant_line(conn, bill_id, count, now)
    subtotal, tax, total = _recompute_totals(conn, bill_id)
    if subtotal < 0:
        # Refuse a negative final before spending an invoice number on it (the per-FY sequence
        # is gap-free, so a rejected finalize must not consume a serial).
        raise DiscountBelowZero()
    number = _next_invoice_number(conn)
    _finalize_header(conn, bill_id, number, subtotal, tax, total, now)
    _lock_billing_state(conn, event_id)
    return bill_id, number


# ---- PDF (FINAL only) ------------------------------------------------------

def _pdf_invoice(conn, bill_id):
    """The finalized bill header + its organization (the bill-to party)."""
    sql = """
        SELECT i.bill_id, i.invoice_number, i.event_id, i.organization_id,
               i.event_name, i.organizer_name, i.event_date, i.subtotal, i.tax_amount,
               i.total_amount, i.created_at,
               o.billing_email, o.email AS org_email, o.address_line1, o.address_line2,
               o.city, o.state_province, o.postal_code, o.country, o.tax_id
          FROM invoices i
          JOIN organizations o ON o.id = i.organization_id
         WHERE i.bill_id = %s
    """
    with conn.cursor() as cur:
        cur.execute(sql, (bill_id,))
        return cur.fetchone()


def _pdf_line_items(conn, bill_id):
    with conn.cursor() as cur:
        cur.execute("SELECT kind, description, quantity, unit_price, amount, discount_percent "
                    "FROM invoice_line_item WHERE invoice_id = %s ORDER BY id ASC", (bill_id,))
        return cur.fetchall()


def _money(amount):
    # Currency code prefix (not the ₹ glyph, which standard PDF fonts lack).
    return f"{CURRENCY} {Decimal(str(amount)):,.2f}"


def _address_lines(inv):
    parts = [inv.get("address_line1"), inv.get("address_line2"), inv.get("city"),
             inv.get("state_province"), inv.get("postal_code"), inv.get("country")]
    return ", ".join(p for p in parts if p)


def _event_date_label(inv):
    value = inv.get("event_date")
    return value.strftime("%d %b %Y") if value else "—"


def _line_label(line):
    desc = line.get("description") or line["kind"]
    if line["kind"] == "DISCOUNT" and line.get("discount_percent") is not None:
        return f"{desc} ({Decimal(str(line['discount_percent'])):g}%)"
    return desc


def _render_pdf(inv, line_items):
    buf = BytesIO()
    number = inv.get("invoice_number") or inv["bill_id"]
    doc = SimpleDocTemplate(buf, pagesize=A4, title=f"Invoice {number}",
                            topMargin=20 * mm, bottomMargin=20 * mm)
    styles = getSampleStyleSheet()
    small = ParagraphStyle("small", parent=styles["Normal"], fontSize=9, leading=12)
    right = ParagraphStyle("right", parent=small, alignment=2)
    h1 = ParagraphStyle("h1", parent=styles["Title"], fontSize=18)

    elements = [Paragraph("TAX INVOICE", h1), Spacer(1, 6 * mm)]

    issuer = Paragraph(
        f"<b>{ISSUER_NAME}</b><br/>{ISSUER_ADDRESS}<br/>GSTIN: {ISSUER_GSTIN}", small)
    meta = Paragraph(
        f"<b>Invoice No:</b> {number}<br/>"
        f"<b>Date:</b> {inv['created_at']:%d %b %Y %H:%M UTC}", right)
    elements.append(Table([[issuer, meta]], colWidths=[100 * mm, 70 * mm]))
    elements.append(Spacer(1, 6 * mm))

    bill_to = Paragraph(
        f"<b>Bill To</b><br/>{inv['organizer_name']}<br/>"
        f"{_address_lines(inv) or '—'}<br/>"
        f"GSTIN: {inv.get('tax_id') or '—'}<br/>"
        f"{inv.get('billing_email') or inv.get('org_email') or ''}", small)
    event_block = Paragraph(
        f"<b>Event</b><br/>{inv['event_name']}<br/>"
        f"Date: {_event_date_label(inv)}", small)
    elements.append(Table([[bill_to, event_block]], colWidths=[100 * mm, 70 * mm]))
    elements.append(Spacer(1, 8 * mm))

    rows = [["Description", "Qty", "Unit Price", "Amount"]]
    for line in line_items:
        qty = "" if line.get("quantity") is None else str(line["quantity"])
        unit = "" if line.get("unit_price") is None else _money(line["unit_price"])
        rows.append([_line_label(line), qty, unit, _money(line["amount"])])
    rows.append(["", "", "Subtotal", _money(inv["subtotal"])])
    rows.append(["", "", f"GST ({GST_RATE:g}%)", _money(inv["tax_amount"])])
    rows.append(["", "", "Total", _money(inv["total_amount"])])

    last = len(rows) - 1
    table = Table(rows, colWidths=[95 * mm, 20 * mm, 30 * mm, 30 * mm])
    table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#222222")),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("FONTSIZE", (0, 0), (-1, -1), 9),
        ("ALIGN", (1, 0), (-1, -1), "RIGHT"),
        ("LINEBELOW", (0, 0), (-1, 0), 0.5, colors.grey),
        ("LINEABOVE", (2, last), (-1, last), 0.5, colors.black),
        ("FONTNAME", (2, last), (-1, last), "Helvetica-Bold"),
        ("TOPPADDING", (0, 0), (-1, -1), 6),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
    ]))
    elements.append(table)
    elements.append(Spacer(1, 10 * mm))
    elements.append(Paragraph(
        "This is a system-generated invoice. Amount billed on total registered "
        "participants and is not reduced by uncollected entries.", small))

    doc.build(elements)
    return buf.getvalue()


def _render_and_store_pdf(conn, bill_id):
    """Render the FINAL's tax-invoice PDF, store it in S3, and write back pdf_key. Best-effort:
    a failure does not undo the already-committed FINAL — the bill just has no download URL yet."""
    try:
        inv = _pdf_invoice(conn, bill_id)
        if inv is None:
            return
        pdf_bytes = _render_pdf(inv, _pdf_line_items(conn, bill_id))
        key = f"invoices/org/{inv['organization_id']}/event/{inv['event_id']}/{bill_id}.pdf"
        s3.put_object(Bucket=MEDIA_BUCKET, Key=key, Body=pdf_bytes, ContentType="application/pdf")
        with conn.cursor() as cur:
            cur.execute("UPDATE invoices SET pdf_key = %s, updated_at = %s WHERE bill_id = %s",
                        (key, datetime.now(timezone.utc), bill_id))
        conn.commit()
        log.info("PDF stored for bill %s at %s", bill_id, key)
    except Exception as e:  # best-effort — the FINAL is already committed and locked
        log.warning("Could not generate PDF for bill %s: %s", bill_id, e)


# ---- handler ---------------------------------------------------------------

def _trigger_stats_recompute(reason):
    """Fire-and-forget the bill-stats Lambda so the snapshot's draft/total counts stay fresh. Best-
    effort: a missing ARN or a failed invoke only logs — the snapshot self-heals on the next trigger
    or a manual refresh, and the bill itself is already committed."""
    if not STATS_LAMBDA_ARN:
        log.warning("[bill-stats] STATS_LAMBDA_ARN not set — skipping %s recompute", reason)
        return
    try:
        lambda_client.invoke(
            FunctionName=STATS_LAMBDA_ARN,
            InvocationType="Event",
            Payload=json.dumps({"reason": reason}).encode("utf-8"),
        )
        log.info("[bill-stats] triggered %s recompute", reason)
    except Exception as e:  # best-effort
        log.warning("[bill-stats] could not trigger %s recompute: %s", reason, e)


def _delete_own_schedule(event):
    """Self-clean the one-time AUTO schedule. The auto-bill is a single-shot nudge, so the
    schedule is deleted whether the run succeeded or failed — there is no re-firing. A failed
    auto-draft is recovered by a manual generate, not by re-running the timer."""
    if event.get("reason") != "AUTO":
        return
    name = event.get("scheduleName")
    if not name:
        return
    try:
        scheduler.delete_schedule(Name=name)
        log.info("Deleted one-time schedule %s", name)
    except Exception as e:  # best-effort cleanup
        log.warning("Could not delete schedule %s: %s", name, e)


def handler(event, context):
    # The AUTO schedule fires once; delete it on every outcome (success or failure) so a failed
    # run never re-fires. The exception still propagates — MANUAL callers (Spring) need to see it.
    try:
        return _process(event, context)
    finally:
        _delete_own_schedule(event)


def _process(event, context):
    event_id = str(event["eventId"])
    reason = event.get("reason", "AUTO")
    # AUTO is always a draft; MANUAL honors the requested mode (default draft).
    mode = "DRAFT" if reason == "AUTO" else (event.get("mode") or "DRAFT").upper()
    log.info("Billing requested for event %s (reason=%s mode=%s)", event_id, reason, mode)

    conn = _db_connect()
    try:
        if not _acquire_lock(conn, event_id):
            # Let it surface as a function error so EventBridge/Spring can retry.
            raise RuntimeError(f"Could not acquire billing lock for event {event_id}")
        try:
            ev = _load_event(conn, event_id)
            if ev is None:
                log.warning("Event %s not found", event_id)
                return {"status": "NOT_FOUND", "eventId": event_id}

            if not _is_billable(ev):
                return {"status": "SKIPPED_NOT_BILLABLE", "eventId": event_id, "eventStatus": ev["status"]}

            if _existing_final(conn, int(ev["id"])) is not None:
                log.info("Event %s already has a final bill — skipping", event_id)
                return {"status": "ALREADY_FINAL", "eventId": event_id}

            if mode == "FINAL":
                count = _participant_count_authoritative(event_id)
                try:
                    result = _process_marked_final(conn, ev, count)
                except DiscountBelowZero:
                    conn.rollback()
                    log.warning("Event %s final blocked: discounts exceed the charges", event_id)
                    return {"status": "DISCOUNT_BELOW_ZERO", "eventId": event_id}
                if result is None:
                    # Spring did not mark a bill FINAL — nothing to finalize; refuse so Spring
                    # rolls its mark back and tells the user the bill could not be generated.
                    log.warning("Event %s FINAL requested but no bill is marked final", event_id)
                    return {"status": "NOT_MARKED_FINAL", "eventId": event_id}
                bill_id, number = result
                conn.commit()
                # Synchronous, inline: render + store the PDF and write back pdf_key before
                # returning, so the finalize response can carry a ready download URL.
                _render_and_store_pdf(conn, bill_id)
                log.info("Final bill %s (%s) issued for event %s", number, bill_id, event_id)
                return {"status": "CREATED_FINAL", "eventId": event_id,
                        "billId": bill_id, "invoiceNumber": number}

            count = _participant_count_fast(event_id)
            status, bill_id, created_new = _create_or_refresh_draft(conn, ev, count, reason)
            if status == "SKIPPED_DUPLICATE":
                conn.rollback()
                log.info("Event %s draft already at count %s — skipping", event_id, count)
                return {"status": "SKIPPED_DUPLICATE", "eventId": event_id, "billId": bill_id}

            # Persist the draft first so a (secondary) notification failure can never lose it.
            conn.commit()
            # Only the AUTO timer notifies ROOT/admins; manual drafts don't. Best-effort: the
            # nudge is secondary, so a failure here must not undo the already-committed draft.
            if reason == "AUTO":
                try:
                    _insert_notifications(conn, ev)
                    conn.commit()
                except Exception as e:
                    conn.rollback()
                    log.warning("Could not queue draft-bill notifications for event %s: %s",
                                event_id, e)
            # A brand-new draft changes the platform's draft/total bill counts, which the stats
            # snapshot includes — recompute it. A refresh leaves counts (and all FINAL-only amounts)
            # unchanged, so it needs no recompute. Covers both the manual and AUTO draft paths.
            if created_new:
                _trigger_stats_recompute("DRAFT")
            log.info("Draft bill %s created/refreshed for event %s", bill_id, event_id)
            return {"status": "CREATED_DRAFT", "eventId": event_id, "billId": bill_id}
        finally:
            _release_lock(conn, event_id)
    finally:
        conn.close()

"""
Billing Lambda for marathon-bib-expo.

Generates a GST invoice PDF for an event, stores it in S3, and records it as a row
in the MySQL ``invoices`` table (the Spring app owns that table's schema via
Hibernate; this Lambda only inserts). Invoked two ways, both carrying
``{"eventId": "...", "reason": "AUTO" | "MANUAL"}``:

  - AUTO   : an EventBridge Scheduler one-time schedule, ~5h after the event
             became COMPLETED/CANCELLED.
  - MANUAL : the Spring app, on an org user's on-demand request.

Billing rules (EVENT_LIFECYCLE_BILLING_PLAN.md, Rule 4):
  - amount  = total uploaded participants x unit price, + GST.
  - COMPLETED -> always billed.
  - CANCELLED -> billed only if distribution had started.

Dedup-by-count: a new bill is written only when the participant count (or unit
price) differs from the event's most recent bill. So an AUTO timer and a MANUAL
request for the same completion converge on a single bill, while a
reopen -> add participants -> re-complete produces a fresh one.

Config (environment variables):
  DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD   - RDS MySQL connection
  STATS_TABLE       (default marathon-event-stats)
  MEDIA_BUCKET      (default marathon-bib-expo-media)
  UNIT_PRICE        (default 5)        - per participant
  CURRENCY          (default INR)
  GST_RATE_PERCENT  (default 18)
  ISSUER_NAME, ISSUER_ADDRESS, ISSUER_GSTIN         - the platform's billing identity

Packaging: boto3 ships in the Lambda runtime; pymysql and reportlab must be
bundled (see requirements.txt) as a layer or in the deployment zip.
"""
import logging
import os
import uuid
from datetime import datetime, timezone
from decimal import Decimal, ROUND_HALF_UP
from io import BytesIO

import boto3
import pymysql
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
s3 = boto3.client("s3")
scheduler = boto3.client("scheduler")


# ---- config ----------------------------------------------------------------

def _env(name, default=None):
    return os.environ.get(name, default)


STATS_TABLE = _env("STATS_TABLE", "marathon-event-stats")
MEDIA_BUCKET = _env("MEDIA_BUCKET", "marathon-bib-expo-media")
UNIT_PRICE = Decimal(_env("UNIT_PRICE", "5"))
CURRENCY = _env("CURRENCY", "INR")
GST_RATE = Decimal(_env("GST_RATE_PERCENT", "18"))
ISSUER_NAME = _env("ISSUER_NAME", "Acme Timing Pvt Ltd")
ISSUER_ADDRESS = _env("ISSUER_ADDRESS", "221B Placeholder Road, Mumbai, MH 400001")
ISSUER_GSTIN = _env("ISSUER_GSTIN", "27AAAAA0000A1Z5")


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


def _load_event(conn, event_id):
    """Event + its organization (the bill-to party), or None if the event is gone."""
    sql = """
        SELECT e.id, e.event_name, e.event_start_date, e.status,
               e.distribution_started, e.organization_id,
               o.organizer_name, o.billing_email, o.email AS org_email,
               o.address_line1, o.address_line2, o.city, o.state_province,
               o.postal_code, o.country, o.tax_id
          FROM events e
          JOIN organizations o ON o.id = e.organization_id
         WHERE e.id = %s
    """
    with conn.cursor() as cur:
        cur.execute(sql, (event_id,))
        return cur.fetchone()


def _participant_count(event_id):
    """Total uploaded participants from the event-stats TOTAL counter."""
    table = dynamodb.Table(STATS_TABLE)
    item = table.get_item(Key={"eventId": str(event_id), "statKey": "TOTAL"}).get("Item")
    return int(item["count"]) if item and item.get("count") is not None else 0


def _latest_invoice(conn, event_id):
    """Most recent bill for the event, or None."""
    sql = """
        SELECT bill_id, participant_count, unit_price
          FROM invoices
         WHERE event_id = %s
         ORDER BY created_at DESC
         LIMIT 1
    """
    with conn.cursor() as cur:
        cur.execute(sql, (event_id,))
        return cur.fetchone()


# ---- rules -----------------------------------------------------------------

def _is_billable(event):
    status = event["status"]
    if status not in TERMINAL_STATUSES:
        log.info("Event %s is %s (not terminal) — skipping", event["id"], status)
        return False
    if status == "CANCELLED" and not event["distribution_started"]:
        log.info("Event %s cancelled with no distribution — no bill", event["id"])
        return False
    return True


def _already_billed(latest, count):
    """Dedup-by-count: the current count + price already produced the latest bill."""
    if latest is None:
        return False
    same_count = int(latest["participant_count"]) == count
    same_price = Decimal(str(latest["unit_price"])) == UNIT_PRICE
    return same_count and same_price


def _compute(count):
    subtotal = (UNIT_PRICE * count).quantize(TWO_PLACES, ROUND_HALF_UP)
    tax = (subtotal * GST_RATE / HUNDRED).quantize(TWO_PLACES, ROUND_HALF_UP)
    total = (subtotal + tax).quantize(TWO_PLACES, ROUND_HALF_UP)
    return subtotal, tax, total


# ---- PDF -------------------------------------------------------------------

def _money(amount):
    # Currency code prefix (not the ₹ glyph, which standard PDF fonts lack).
    return f"{CURRENCY} {amount:,.2f}"


def _address_lines(event):
    parts = [event.get("address_line1"), event.get("address_line2"),
             event.get("city"), event.get("state_province"),
             event.get("postal_code"), event.get("country")]
    return ", ".join(p for p in parts if p)


def _event_date_label(event):
    value = event.get("event_start_date")
    return value.strftime("%d %b %Y") if value else "—"


def _render_pdf(event, bill_id, created_at, count, subtotal, tax, total):
    buf = BytesIO()
    doc = SimpleDocTemplate(buf, pagesize=A4, title=f"Invoice {bill_id}",
                            topMargin=20 * mm, bottomMargin=20 * mm)
    styles = getSampleStyleSheet()
    small = ParagraphStyle("small", parent=styles["Normal"], fontSize=9, leading=12)
    right = ParagraphStyle("right", parent=small, alignment=2)
    h1 = ParagraphStyle("h1", parent=styles["Title"], fontSize=18)

    elements = [Paragraph("TAX INVOICE", h1), Spacer(1, 6 * mm)]

    issuer = Paragraph(
        f"<b>{ISSUER_NAME}</b><br/>{ISSUER_ADDRESS}<br/>GSTIN: {ISSUER_GSTIN}", small)
    meta = Paragraph(
        f"<b>Invoice No:</b> {bill_id}<br/>"
        f"<b>Date:</b> {created_at:%d %b %Y %H:%M UTC}", right)
    elements.append(Table([[issuer, meta]], colWidths=[100 * mm, 70 * mm]))
    elements.append(Spacer(1, 6 * mm))

    bill_to = Paragraph(
        f"<b>Bill To</b><br/>{event['organizer_name']}<br/>"
        f"{_address_lines(event) or '—'}<br/>"
        f"GSTIN: {event.get('tax_id') or '—'}<br/>"
        f"{event.get('billing_email') or event.get('org_email') or ''}", small)
    event_block = Paragraph(
        f"<b>Event</b><br/>{event['event_name']}<br/>"
        f"Date: {_event_date_label(event)}<br/>"
        f"Status: {event['status']}", small)
    elements.append(Table([[bill_to, event_block]], colWidths=[100 * mm, 70 * mm]))
    elements.append(Spacer(1, 8 * mm))

    rows = [
        ["Description", "Qty", "Unit Price", "Amount"],
        [f"Participant registrations — {event['event_name']}",
         str(count), _money(UNIT_PRICE), _money(subtotal)],
        ["", "", "Subtotal", _money(subtotal)],
        ["", "", f"GST ({GST_RATE:g}%)", _money(tax)],
        ["", "", "Total", _money(total)],
    ]
    table = Table(rows, colWidths=[95 * mm, 20 * mm, 30 * mm, 30 * mm])
    table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#222222")),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("FONTSIZE", (0, 0), (-1, -1), 9),
        ("ALIGN", (1, 0), (-1, -1), "RIGHT"),
        ("LINEBELOW", (0, 0), (-1, 1), 0.5, colors.grey),
        ("LINEABOVE", (2, -1), (-1, -1), 0.5, colors.black),
        ("FONTNAME", (2, -1), (-1, -1), "Helvetica-Bold"),
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


# ---- persistence -----------------------------------------------------------

def _upload_pdf(org_id, event_id, bill_id, pdf_bytes):
    key = f"invoices/org/{org_id}/event/{event_id}/{bill_id}.pdf"
    s3.put_object(Bucket=MEDIA_BUCKET, Key=key, Body=pdf_bytes,
                  ContentType="application/pdf")
    return key


def _save_invoice(conn, event, bill_id, created_at, event_date,
                  count, subtotal, tax, total, pdf_key, reason):
    """Insert the bill row. Column names must match the Hibernate-managed schema."""
    sql = """
        INSERT INTO invoices
            (bill_id, event_id, organization_id, event_name, organizer_name,
             event_date, participant_count, unit_price, subtotal, tax_rate,
             tax_amount, total_amount, currency, pdf_key, reason,
             payment_status, created_at)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
    """
    with conn.cursor() as cur:
        cur.execute(sql, (
            bill_id, int(event["id"]), int(event["organization_id"]),
            event["event_name"], event["organizer_name"],
            event_date, count, UNIT_PRICE, subtotal, GST_RATE,
            tax, total, CURRENCY, pdf_key, reason,
            "UNPAID", created_at,
        ))
    conn.commit()


# ---- handler ---------------------------------------------------------------

def _delete_own_schedule(event):
    """Self-clean the one-time AUTO schedule once we reach a no-retry outcome.

    EventBridge fires AUTO bills via a one-time schedule named in the payload; it does
    not delete itself on this SDK level, so the Lambda removes it. Only called after a
    terminal decision — on an exception we skip this so EventBridge can retry.
    """
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
    result = _process(event, context)
    _delete_own_schedule(event)
    return result


def _process(event, context):
    event_id = str(event["eventId"])
    reason = event.get("reason", "AUTO")
    log.info("Billing requested for event %s (%s)", event_id, reason)

    conn = _db_connect()
    try:
        ev = _load_event(conn, event_id)
        if ev is None:
            log.warning("Event %s not found", event_id)
            return {"status": "NOT_FOUND", "eventId": event_id}

        if not _is_billable(ev):
            return {"status": "SKIPPED_NOT_BILLABLE", "eventId": event_id, "eventStatus": ev["status"]}

        count = _participant_count(event_id)
        latest = _latest_invoice(conn, event_id)
        if _already_billed(latest, count):
            log.info("Event %s already billed for count %s — skipping", event_id, count)
            return {"status": "SKIPPED_DUPLICATE", "eventId": event_id, "billId": latest["bill_id"]}

        subtotal, tax, total = _compute(count)
        bill_id = str(uuid.uuid4())
        created_at = datetime.now(timezone.utc)
        event_date = ev.get("event_start_date")

        pdf_bytes = _render_pdf(ev, bill_id, created_at, count, subtotal, tax, total)
        pdf_key = _upload_pdf(ev["organization_id"], event_id, bill_id, pdf_bytes)
        _save_invoice(conn, ev, bill_id, created_at, event_date,
                      count, subtotal, tax, total, pdf_key, reason)
    finally:
        conn.close()

    log.info("Bill %s created for event %s: %s", bill_id, event_id, _money(total))
    return {"status": "CREATED", "eventId": event_id, "billId": bill_id,
            "pdfKey": pdf_key, "total": str(total)}

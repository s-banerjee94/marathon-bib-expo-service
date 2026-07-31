# Deployment notes

Schema-affecting changes, newest first. `DDL_AUTO=update` adds new nullable columns on its own; it
never relaxes an existing `NOT NULL`, so anything under **Manual** has to be run by hand on every
environment before the new build starts.

---

## Messaging: template stamps, sender rules, provider generality

### Manual — run before deploying

```sql
-- sms_template_id is no longer mandatory: it is required only when the sender's request
-- mapping reads {{TEMPLATE_ID}} (the Indian DLT gateways). Providers that take the message
-- text directly need none. Hibernate cannot drop the NOT NULL itself.
ALTER TABLE sms_templates MODIFY sms_template_id VARCHAR(100) NULL;
```

Nothing else is manual. Skipping it makes every SMS template create fail with
`Database constraint violation` when no template id is supplied.

### Automatic — added by `DDL_AUTO=update`

| Table | Column | Type |
|---|---|---|
| `sms_templates` | `render_mode` | `VARCHAR(20)` null |
| `sms_templates` | `provider_source` | `VARCHAR(20)` null |
| `whatsapp_templates` | `render_mode` | `VARCHAR(20)` null |
| `whatsapp_templates` | `provider_source` | `VARCHAR(20)` null |
| `messaging_providers` | `default_country_code` | `VARCHAR(6)` null |
| `messaging_providers` | `success_contains` | `VARCHAR(200)` null |

Existing rows keep `NULL` in all of them, which is handled:

- **Templates** — a null `render_mode` is read from the template's own content, and a null
  `provider_source` leaves the sender check switched off for that row. Both are stamped on the next
  edit. No backfill needed.
- **Providers** — a null `default_country_code` **is** backfilled, see below.

### Runs once on first start

`MessagingProviderMappingVerifier` (a `CommandLineRunner`) does two things at boot:

1. **Backfills `default_country_code` to `91`** on provider rows that have none, and logs
   `WARN Set the country code to 91 on N messaging provider(s) that predate the setting`. The code
   used to be hardcoded as `+91` in the sender, so this preserves exactly how those rows already
   behaved. **Check the value on each row afterwards if you send outside India.**
2. **Logs `ERROR` for any provider row whose declared `templateMode` contradicts its own request
   mapping** — client-rendered without `{{MESSAGE}}`, or provider-rendered without `{{VAR:n}}` /
   `{{VARIABLES_JSON}}`. It does **not** stop startup: these are rows an organizer created, so one bad
   row must not keep the platform down. Messages sent through such a row lose their content, so treat
   the log line as an alarm, not noise.

### No schema impact

- `AuditAction.SEND` and `AuditEntityType.PARTICIPANT_MESSAGE` — the audit log is DynamoDB, values are
  written as strings.
- `HttpMethodType` gained `PUT`/`PATCH`, `MessageContentType` gained `XML`/`TEXT` — both fit the
  existing `VARCHAR(8)` columns.

### Rollback

The new columns are additive and nullable, so an older build ignores them. The one-way step is the
`sms_template_id` relaxation: if templates without a template id have been created, restoring the
`NOT NULL` fails until those rows are given a value or removed.

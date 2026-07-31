# Frontend notes — messaging, campaigns and provider configuration

Written for the Angular app at `marathon-bib-expo-frontend`. Everything below is a backend change that
the UI must react to, plus guidance on how. **The guidance is a starting point, not a spec** — layout,
component structure and UX polish are yours. What is *not* negotiable is the contract: field names,
status codes, and which field the server will now reject.

Backend Swagger: `http://localhost:8080/swagger-ui.html`. Group ids changed — see §0.

### Swagger groups relevant to this work

Pick the group from the dropdown, or deep-link it. Raw JSON is the contract of record — generate types
from it rather than transcribing by hand.

| Group | What it covers here | Raw JSON | Deep link |
|---|---|---|---|
| **Targeted Participant Messaging** | §1 — the new endpoint, its request/response schemas | `/v3/api-docs/10-participant-messaging` | `/swagger-ui.html?urls.primaryName=10-participant-messaging` |
| **SMS Campaigns & Templates** | §2 §3 — `renderMode`, template validation, campaign arming | `/v3/api-docs/08-sms` | `?urls.primaryName=08-sms` |
| **WhatsApp Campaigns & Templates** | §2 §3 — same, WhatsApp side | `/v3/api-docs/09-whatsapp` | `?urls.primaryName=09-whatsapp` |
| **Campaign Providers (SMS/WhatsApp senders)** | §4 §6 — the sender form, new fields, test send, `campaign-provider-style` | `/v3/api-docs/11-campaign-providers` | `?urls.primaryName=11-campaign-providers` |
| **System Messaging (Root)** | §4 — the **same** provider request/response shape, for transactional senders | `/v3/api-docs/15-system-messaging` | `?urls.primaryName=15-system-messaging` |
| **Notifications** | §3 — the campaign-failure notification the reason now rides on | `/v3/api-docs/14-notifications` | `?urls.primaryName=14-notifications` |
| **Audit Logs** | §1 — `action=SEND`, `entityType=PARTICIPANT_MESSAGE` filters | `/v3/api-docs/13-audit-logs` | `?urls.primaryName=13-audit-logs` |
| **All APIs** | everything, when you want one document | `/v3/api-docs/00-all` | `?urls.primaryName=00-all` |

Note the provider connection shape lives in **two** groups (`11-campaign-providers` and
`15-system-messaging`) because campaign senders and system senders share
`SaveMessagingProviderRequest` / `MessagingProviderResponse` — which is why `system-messaging.model.ts`
is the right place for the new `defaultCountryCode` and `successContains` fields.

---

## 0. Breaking bits, read first

| Change | Impact |
|---|---|
| Swagger group ids shifted | `campaign-providers` `10-`→`11-`, `dashboard` `11-`→`12-`, `audit-logs` `12-`→`13-`, `notifications` `13-`→`14-`, `system-messaging` `14-`→`15-`, `dev-operations` `15-`→`16-`, `landing-demo` `16-`→`17-`. New group `10-participant-messaging`. Any `?urls.primaryName=` deep link needs updating. |
| Template create/update now rejects the wrong content field | A `CLIENT_RENDERED` template may no longer carry `bodyVariables`; a `PROVIDER_RENDERED` SMS template may no longer carry `template` text. Previously both were accepted. |
| Arming a campaign can now fail with `400` | If the template cannot satisfy the sender's request mapping. Saving as DRAFT is unaffected. |
| Saving a campaign sender can now fail with `400` | If the declared `templateMode` disagrees with the tokens in the request mapping, or no recipient token is present. |
| Malformed request bodies now return `400`, not `500` | Bad enum values, wrong types, malformed JSON, missing body. |
| `MessagingProviderResponse` / `SaveMessagingProviderRequest` gained two fields | `defaultCountryCode`, `successContains`. |
| **`smsTemplateId` is no longer always required** | The DLT id is required only when the sender's mapping reads `{{TEMPLATE_ID}}`. Providers that take the message text directly need none. **Needs a schema change**: `ALTER TABLE sms_templates MODIFY sms_template_id VARCHAR(100) NULL;` — Hibernate's auto-update does not drop NOT NULL. |
| Country code is no longer defaulted server-side | Blank was silently `91`. Now it is required only when the mapping builds an international number, and rejected at save if missing. Pre-fill `91` in the form instead. |
| `SmsTemplateResponse` / `WhatsAppTemplateResponse` gained two fields | `renderMode`, `providerSource`. |
| Disabling or deleting a campaign sender can now fail with `409` | While campaigns are armed. The platform sender accepts `?force=true`; an organization's own sender does not. |
| `HttpMethodType` and `MessageContentType` gained values | `PUT`, `PATCH`; `XML`, `TEXT`. Dropdowns in the sender form must offer them. |

---

## 1. New feature — targeted participant messaging

**The problem it solves.** Today every send is event-wide. A participant walks up to the counter and
says "I never got the confirmation SMS." There is no way to send to just them.

**Endpoint:** `POST /api/events/{eventId}/participant-messages`
**Swagger group:** `10-participant-messaging` — *Targeted Participant Messaging*

### Request

```ts
interface SendParticipantMessagesRequest {
  channel: 'SMS' | 'WHATSAPP';   // EMAIL is rejected by validation until email templates exist
  templateId?: number;           // omit to reuse the active bib-collection campaign's template
  bibNumbers: string[];          // 1..25, duplicates ignored, whitespace trimmed
}
```

`templateId` omitted → the server takes the template from the event's **active `AUTO_BIB_COLLECTED`
campaign** on that channel. That is the one-click "send it again" path and should be the default action.
If no such campaign exists the response is `400` — *"Choose a template to send — this event has no
active bib collection SMS campaign to take one from."* — so the UI needs a template picker as fallback.

### Response — always `200` once the request itself is valid

```ts
interface ParticipantMessagesResponse {
  channel: 'SMS' | 'WHATSAPP';
  templateId: number;
  templateName: string;
  sentCount: number;
  failedCount: number;
  results: {
    bibNumber: string;
    status: 'SENT' | 'FAILED';
    reason?: string | null;     // present only when FAILED, written for end users
  }[];
}
```

**Partial success is the normal case.** A `200` does not mean everything sent. Render the per-bib list.
`reason` strings are already phrased for the operator (`"This participant does not have a phone number
on record."`, `"The participant you requested does not exist."`) — show them verbatim, do not map them.

### Errors that abort the whole call

| Status | Cause | UI |
|---|---|---|
| `400` | validation, no resolvable template, template incompatible with the sender, event state disallows sending | single error, no result list |
| `404` | event or template not found | single error |
| `502` | no sender configured for the channel | single error, link to Campaign Senders |

### UI guidance

- **Entry points:** a *Send message* action on the participant row / detail drawer (pre-selects that
  bib), and a bulk action on the participant grid multi-select. **Cap the selection at 25** — 26 is a
  `400`. Show the cap in the UI rather than letting the request fail.
- **Synchronous.** 25 messages take a few seconds. Keep the button in a pending state; there is no job
  id and nothing to poll.
- **Roles:** `ROOT`, `ADMIN`, `ORGANIZER_ADMIN`, `ORGANIZER_USER`. **Not `DISTRIBUTOR`** — hide the
  action for them entirely, the API returns 403.
- **Cost awareness.** Every message is billed. A confirmation step showing *"Send SMS to 12
  participants?"* with the template name is worth having.
- **Result presentation.** Suggest a summary line (`10 sent, 2 failed`) with the failures expandable.
  Failed rows are actionable — a missing phone number is something staff can fix, then retry.

### New files you will likely add

```
src/app/core/models/participant-message.model.ts
src/app/core/services/participant-message.service.ts
src/app/features/events/event-details/participant-message-dialog/…
```

---

## 2. Templates now carry `renderMode` — and the edit form must use it

Swagger: `/v3/api-docs/08-sms` (SMS) · `/v3/api-docs/09-whatsapp` (WhatsApp)

### The contract

```ts
// add to SmsTemplate, CreateSmsTemplateRequest is unchanged (server derives it)
renderMode: 'CLIENT_RENDERED' | 'PROVIDER_RENDERED';
```

Present on `SmsTemplateResponse` and `WhatsAppTemplateResponse`. Never null — templates saved before
this change get a value derived from their content. The server sets it at **create** from the
organization's resolved sender, and it never changes afterwards.

### The bug this fixes, in your code

`sms-template-form.ts` currently resolves the shape from the provider on **both** create and edit:

```ts
// sms-template-form.ts ~line 169
this.styleService.getStyle(this.eventId, 'SMS').subscribe({
  next: (style) => { this.providerMode.set(style.templateMode ?? null); … }
});
// ~line 81
usesVariables = computed(() => this.providerMode() === 'PROVIDER_RENDERED');
// ~line 237
? { ...base, bodyVariables: this.bodyVariables() }
: { ...base, template: this.formData.template };
```

If the organization switches sender after a template was authored, opening that template for edit
reshapes the form and submits the field the template was not written for. The server now rejects that
with a `400`.

**Change:** on **edit**, drive `usesVariables` from `template.renderMode`. Keep the style endpoint for
**create** only (a new template follows the current sender). One-line change in the computed signal,
plus not calling the style service when `isEditMode()`.

### What the server enforces now

| `renderMode` | Accepts | Rejects with `400` |
|---|---|---|
| `CLIENT_RENDERED` | `template` (message text) | `bodyVariables` → *"Remove the template variables — your SMS provider expects the full message text instead."* |
| `PROVIDER_RENDERED` | `bodyVariables`, may be empty | `template` → *"Remove the message text — your SMS provider holds the registered template and expects only the variables."* |

Also: `CLIENT_RENDERED` **requires** non-blank message text at create — *"Add the message text — your
SMS provider sends the message body itself."*

**A `PROVIDER_RENDERED` template with zero variables is valid.** A registered vendor template can be
fully static — *"Dear participant, the bib expo runs 5–7 Jan"* needs no variables at all. Do not force
the user to add one.

WhatsApp templates always keep their `body` field (it is the local readable copy of the vendor-approved
text, never sent on the wire) and are stamped `PROVIDER_RENDERED`.

### `providerSource` — which sender the template belongs to

```ts
providerSource?: 'ORGANIZATION' | 'DEFAULT' | null;   // null on templates written before this existed
```

Stamped at create, same as `renderMode`. A registered template id belongs to **one vendor account**: an
organization's DLT id means nothing in the platform's account. So if the sender in force at send time is
the other source, sending is refused rather than quietly delivered under the wrong registration and
billed to the wrong party.

| Template built for | Sender in force | Result |
|---|---|---|
| ORGANIZATION | ORGANIZATION | sends |
| ORGANIZATION | DEFAULT — own sender was switched off | `400` *"This template was built for your own SMS sender, which is no longer in use — switch it back on, or rebuild the template for the platform sender."* |
| DEFAULT | ORGANIZATION — org just added its own | `400` *"This template was built for the platform SMS sender, but your own sender is now in use — rebuild the template for it."* |
| DEFAULT | DEFAULT | sends |

Checked at the same three moments as §3 (arm, send, targeted). Show it beside `renderMode` on the
template detail — *"Uses: your own sender"* / *"Uses: platform sender"* — and badge templates whose
source no longer matches the active sender, so the operator sees it before arming.

### UI guidance

- Show the mode as a read-only badge on the template detail and in the edit header — *"Message text"* vs
  *"Template variables"* reads better than the enum.
- If a template's `renderMode` no longer matches the current sender, badge it as needing attention. The
  compatibility check (§3) will block it at arm time anyway; better to surface it in the list.

---

## 3. Compatibility is enforced before money is spent

Swagger: `/v3/api-docs/08-sms`, `/v3/api-docs/09-whatsapp` (arming) · `/v3/api-docs/10-participant-messaging`
(targeted) · `/v3/api-docs/14-notifications` (failure notification)

A template must be able to fill what the sender's request mapping actually asks for. The check reads
the provider's `{{TOKEN}}`s, so it is exact about counts.

| Mapping reads | Template must supply |
|---|---|
| `{{MESSAGE}}` | non-blank message text (zero placeholders inside it is fine) |
| `{{VAR:k}}` — highest index k | at least **k+1** variables |
| only `{{VARIABLES_JSON}}` | nothing; any count including zero |
| `{{TEMPLATE_ID}}` | the DLT template id / Content SID |

### Where the UI meets it

| Moment | Server behaviour | UI |
|---|---|---|
| **Arm a campaign** (create/update with `triggerType`) | `400` with the reason | inline error on the arm action. **DRAFT still saves** — let users park incomplete work and fix it later. |
| **Scheduled / auto send** | campaign → `FAILED`, in-app notification **carrying the reason** | show the reason on the campaign row, not just "failed" |
| **Targeted send** (§1) | `400` before any message is billed | single error |
| **Auto-trigger on bib collection** | skipped, logged server-side, no notification | nothing — deliberately silent to avoid one notification per participant |

Example message, shown as-is: *"Your SMS sender fills 2 variable(s), but this template supplies 0. Add
the missing variables before sending."*

**Notification audience** is unchanged: organization staff (`ORGANIZER_ADMIN`, `ORGANIZER_USER`) plus
platform admins (`ROOT`, `ADMIN`). Distributors never receive campaign notifications.

---

## 4. Campaign Senders screen — the big one

Swagger: `/v3/api-docs/11-campaign-providers` — and `/v3/api-docs/15-system-messaging` for the identical
provider shape used by transactional senders

This is the screen where the outage came from. The current form let **Template mode** say
`Client-rendered (we build the text)` while the **Body template** below read
`"var1":"{{VAR:0}}","var2":"{{VAR:1}}"`. Two fields on one form contradicting each other. Messages
rendered correctly, then had their text dropped, and the vendor billed for empty sends.

### The server now rejects contradictions on save

| Situation | `400` message |
|---|---|
| `CLIENT_RENDERED` but no `{{MESSAGE}}` in the mapping | *This provider is set to send the finished message text, but its request never uses {{MESSAGE}}. Add it, or set the provider to render the message itself.* |
| `PROVIDER_RENDERED` but no `{{VAR:n}}` / `{{VARIABLES_JSON}}` | *This provider is set to render the message from its own registered template, but its request never uses {{VAR:n}} or {{VARIABLES_JSON}}. Add one, or set the provider to send the finished message text.* |
| SMS/WhatsApp with no `{{RECIPIENT}}` or `{{RECIPIENT_E164}}` | *This provider's request never uses {{RECIPIENT}} or {{RECIPIENT_E164}}, so it has no number to send to. Add one.* |
| EMAIL with no `{{RECIPIENT_EMAIL}}` | *This provider sends email but its request never uses {{RECIPIENT_EMAIL}}, so it has no address to send to. Add it.* |

Existing rows that violate these are logged at boot but do **not** stop the app.

### New fields on the form

```ts
// add to SaveMessagingProviderRequest / MessagingProviderResponse in system-messaging.model.ts
defaultCountryCode?: string;   // digits, optional '+', max 6. Defaults to '91'
successContains?: string;      // max 200
```

**`defaultCountryCode`** — what `{{RECIPIENT_E164}}` prefixes onto a local number. Participants are
stored as 10-digit local numbers, so this is what makes the platform work outside India. Label it
plainly: *"Country code — added to local numbers when the sender needs international format."*

**`successContains`** — text the provider's response must contain for the send to count. Many gateways
answer HTTP 200 with `{"status":"error"}`; without this the send is recorded as delivered and billed.
Label it something like *"Success marker (optional) — text the provider's reply must contain. Leave
blank to trust the HTTP status."* Worth a short explainer, because it is the difference between a
detected failure and a silent one.

### Extended dropdowns

```ts
export type ProviderHttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH';
export type MessageContentType = 'JSON' | 'FORM' | 'XML' | 'TEXT';
```

Body-format selection is only meaningful for the body-carrying verbs (`POST`, `PUT`, `PATCH`) — hide or
disable it for `GET`, as the current form already does for POST/GET. Escaping is picked per format
server-side (JSON escaping, form-urlencoding, XML entity escaping, raw for TEXT).

### Suggested UI work, in value order

1. **Live warning under Body template.** The rule is trivial in the browser: `CLIENT_RENDERED` needs the
   substring `{{MESSAGE}}`; `PROVIDER_RENDERED` needs `{{VAR:` or `{{VARIABLES_JSON}}`; any channel
   needs a recipient token. Warn before Save. The server stays the backstop, but the mistake is made
   here, so catch it here.
2. **Make the Available Tokens panel mode-aware.** Dim or badge tokens that do not apply to the selected
   mode and channel. That panel is where the author's eye goes.
3. **Document the indexing asymmetry in that panel.** `{{VAR:n}}` counts from **0**;
   `{{VARIABLES_JSON}}` emits keys from **1**. `{{VAR:0}}` and key `"1"` are the same value. This is not
   changing — existing rows depend on it — so it has to be visible where people type tokens.
4. **Warn when Template mode changes on a saved sender.** Existing templates keep their old
   `renderMode` and will start failing the compatibility check: *"Templates created for the previous
   mode will need their content re-authored."*
5. **`Send test` is now a real diagnostic.** The backend logs the fully assembled request with secrets
   masked, and honours `successContains`. Consider surfacing the outcome more prominently — this is the
   cheapest way for an admin to prove a new vendor works before arming a campaign.

### Fields are required by the mapping, not by country

Nothing about India is hardcoded any more. What a template must supply is decided by the tokens the
sender's request actually reads:

| Sender's mapping contains | Template must have | Otherwise |
|---|---|---|
| `{{TEMPLATE_ID}}` | the provider template id (DLT id in India) | `400` *"Your SMS sender needs a registered template id, which this template does not have."* |
| `{{SENDER_ID}}` | a sender id | `400` *"Your SMS sender puts a sender id on every message, which this template does not have."* |
| `{{RECIPIENT_E164}}` or `{{RECIPIENT_CC}}` | — (this one is on the **sender**: a country code must be set) | `400` at sender save |

So an Indian DLT setup is still fully protected, while a European gateway needs neither field. **The UI
should stop marking DLT id as mandatory** and instead take its cue from the sender: fetch the
campaign-provider style, and require the field only when the sender uses that token. Simplest version:
keep both fields optional in the form and let the `400` explain — the messages name the missing field.

### Switching a sender off is now guarded

A sender that armed campaigns depend on cannot be switched off or deleted out from under them.

| Who | Action | Campaigns armed | Result |
|---|---|---|---|
| Organization | disable / delete own sender | yes | `409` *"This sender cannot be switched off while 3 of your campaign(s) are still armed — disarm them first."* |
| Organization | disable / delete own sender | no | allowed |
| Root | disable / delete platform sender | yes | `409` …*"— disarm them, or repeat the request with force to override."* |
| Root | same request with `?force=true` | yes | allowed, logged as an override |
| Root | **edit** platform sender (rotate key, repoint vendor) | any | always allowed |

`ACTIVE` and `SENDING` count; `DRAFT` does not. The organization endpoint has **no** force parameter —
it owns both sides, so disarming first is a fair ask. The platform sender keeps the escape hatch because
it is shared by every organization, and a hard block would make an emergency impossible to act on.

**UI:** on the org sender, catch the `409` and offer a link to the campaigns that need disarming (the
count is in the message). On the platform sender, show a confirm dialog that states the blast radius —
*"N campaigns across the platform will stop sending"* — before re-sending with `force=true`. Never send
`force=true` silently.

Note for the emergency case: **repointing beats disabling.** Editing the row (new endpoint, new key, new
vendor) keeps every campaign running and needs no force. Disable is the blunt instrument.

A disabled sender is not mapping-checked, so a broken sender can always be switched off; it simply
cannot be switched back on until its mapping and mode agree.

### Full token reference (what the mapping can read)

| Token | Value |
|---|---|
| `{{RECIPIENT}}` | phone as stored (local, 10 digits) |
| `{{RECIPIENT_E164}}` | `+<defaultCountryCode><phone>`, or the number as-is if it already starts with `+` |
| `{{RECIPIENT_CC}}` | same, without the leading `+` — for gateways that reject the plus form |
| `{{RECIPIENT_EMAIL}}` | email address (email channel) |
| `{{SUBJECT}}` | subject line (email channel) |
| `{{MESSAGE}}` | finished message text (client-rendered) |
| `{{VAR:0}}`, `{{VAR:1}}`, … | positional variables, **zero-based** (provider-rendered) |
| `{{VARIABLES_JSON}}` | `{"1":"…","2":"…"}` map, **one-based keys** (e.g. Twilio ContentVariables) |
| `{{TEMPLATE_ID}}` | DLT template id / Content SID |
| `{{SENDER_ID}}` | registered sender / header id |
| `{{API_KEY}}` | stored API token |
| `{{USERNAME}}` / `{{PASSWORD}}` | stored credentials |
| `{{BASIC_AUTH}}` | `base64(user:pass)` for an `Authorization: Basic …` header |

Tokens work in the URL, in any header/query param value, and in the body template. That is the whole
integration surface — a new vendor is a form fill, not a backend release.

---

## 5. Error payloads are now consistent for bad bodies

`400` with the standard `ErrorResponse` shape (was `500`):

| Problem | `message` |
|---|---|
| bad enum value | `Invalid value 'NONE' for 'authType'. Allowed values: TOKEN, USERNAME_PASSWORD.` |
| wrong type | `Invalid value 'abc' for 'templateId'.` |
| malformed JSON | `The request body is not valid JSON.` |
| wrong shape | `The 'bibNumbers' value is not in the expected format.` |
| no body | `A request body is required.` |

Nested fields report dotted paths (`parent.child`). Generic error handling can display `message`
directly. Field-level validation failures still come back as `error: "Validation Failed"` with the
`validationErrors` array, unchanged.

---

## 6. Email — what exists and what does not

Swagger: `/v3/api-docs/11-campaign-providers` (an EMAIL sender is configurable today)

The frontend has `email-template-section` and `email-campaign-section` rendering *"Coming soon."* That
is still accurate, and deliberately so.

**What the backend now has:** the provider layer is email-capable. An EMAIL sender can be configured,
validated and test-sent — `{{RECIPIENT_EMAIL}}` and `{{SUBJECT}}` tokens exist, `OutboundMessage`
carries both, and the row-consistency check knows an email sender needs an address token.

**What is missing, and it is a product decision, not an oversight:** email templates and campaigns have
no storage. An email template is not shaped like an SMS one — it needs at least a subject, and probably
an HTML body plus a plain-text fallback, and the editor is a different component entirely. Until that
is specified there is nothing for the UI to bind to.

`POST /api/events/{eventId}/participant-messages` therefore rejects `channel: "EMAIL"` at validation.
When email templates land, three small backend changes light it up (drop `EMAIL` from the channel
exclusion, add an `emailPlan`, add subject/body to the compatibility check) — the `switch` already has
the `EMAIL` arm stubbed.

**Suggestion:** keep the "Coming soon" panels, but if you want to move first, the useful step is
designing the email template editor — subject + rich body + the same `#{placeholder}` palette the SMS
editor already uses (`sms-template-placeholders.constant.ts`).

---

## 7. Known data problems in the current dev database

Not UI bugs. Both rows now warn at boot and cannot be re-saved until corrected:

| Row | Declares | Mapping reads | Consequence |
|---|---|---|---|
| `CAMPAIGN / SMS / platform default` | `CLIENT_RENDERED` | `{{VAR:0}}`, `{{VAR:1}}` | client-rendered templates send empty variables |
| `CAMPAIGN / SMS / organization 2` | `PROVIDER_RENDERED` | `{{MESSAGE}}` | that organization's campaigns send blank messages |

Until the first is relabelled `PROVIDER_RENDERED`, **no SMS template for that organization can be armed
or sent** — the compatibility check refuses it, on purpose. Previously those sends went out empty and
were billed. If you see that `400` while testing, the sender configuration is what needs fixing, not
the template.

Separately, the default WhatsApp sender has no `From` configured (`From=whatsapp:` with nothing after
it). Harmless against the local stub, fatal against real Twilio.

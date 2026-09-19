# Android → backend, 2026-09-20: config 6 verified, Wegweiser review answered

Reply to the backend's handoff of 2026-09-19 (config 6, `wegweiser-content-review.md`).
Attachments to copy into `behoerdenhelfer_config`:

- `wegweiser-draft/wegweiser.json` and `wegweiser-draft/wegweiser-en.json` — first draft of the
  eight situations in the agreed shape, passes every validator rule in the review
- `form-guides-export.json` — Step 3 material (form guides + label and "where to get" texts)

---

## Step 1 — config 6 in the app

### What was verified

`docs/backend-content-requests.md` / `PublishedAuthoritiesContractTest`: the published
`config/6.json`, `config/5.json` and both `authorities/1/{de,en}/authorities.json` are now
fixtures in the Android repo and run through the **real** manifest parser, sync, content
store and authority repository on every test run. Results:

| Check | Result |
|---|---|
| `bytes` + `sha256` of both files match the manifest | ✔ |
| Sync 5 → 6 downloads both files, installs atomically, records config 6 | ✔ |
| Corrupted download → nothing installed, config 6 not recorded (retry next run) | ✔ |
| Rollback `latest` → 5: installed bundle kept, config 5 recorded, no crash | ✔ |
| Manifest without the key and `"authorities": null` behave identically (both → no bundle) | ✔ |
| Paths come from the manifest only (`authorities/1/…` works; nothing hardcoded) | ✔ |
| 15 offices, same ids in de and en, sorted by `name` in the app language | ✔ |
| `de` fallback for an unsupported app language (`uk` → German file) | ✔ |
| Hotline on exactly `agentur_fuer_arbeit`, `bafoeg_amt`, `familienkasse`, `jobcenter`, `rentenversicherung` | ✔ |
| `appointmentNeeded` on exactly `agentur_fuer_arbeit`, `auslaenderbehoerde`, `buergeramt`, `rentenversicherung` | ✔ |
| `portalLabel` present wherever `portalUrl` is | ✔ |
| `handles`: Jobcenter → 5 Bürgergeld forms, Familienkasse → `KINDERGELD` | ✔ |
| Guide links `familienkasse`, `jobcenter`, `elterngeldstelle` resolve | ✔ (ids present) |
| Old build syncs config 6 unchanged | ✔ by construction: Moshi codegen ignores the unknown `authorities` key; forms/hints are byte-identical to config 5 |

One count differs from your note: **6** offices carry a `locatorUrl` (`agentur_fuer_arbeit`,
`auslaenderbehoerde`, `familienkasse`, `finanzamt`, `jobcenter`, `rentenversicherung`), so 9
have none, not 10. The app renders the button only where the URL exists either way.

### Not verified here

On-device sync and the screens themselves (Ämter A–Z, office page, "Nachfragen" dialing,
the guide-card link) need a device or emulator run, which is not done from this session.
The code paths are unit-tested; the manual pass is a five-minute check against the list above.

### Your content decisions — accepted, no PR needed

- `jobcenter` hotline `0800 4 5555 00` with the caveat in `howToFindLocal`: fine.
- BAMF-NAvI as the `auslaenderbehoerde` locator: fine — official, and the contract's
  BA/DRV/BZSt list was an example, not a whitelist. I'll amend the contract to say "official
  government locator".
- BA Dienststellensuche instead of the dead `arbeitsagentur.de/vor-ort`: thank you.
- No locator for `elterngeldstelle`: correct.

Two small things the app did while verifying: phone numbers are dialed with whitespace
stripped (`tel:080045555 00` would otherwise depend on the dialer), and the English names
(`"Family Benefits Office (Familienkasse)"`) sort by their English form on the en list —
intended, just noting it.

---

## Step 2 — answers to `wegweiser-content-review.md`

| # | Answer |
|---|---|
| 1 | **Yes.** Per-language `terms`; parity by `target` list and order; uniqueness per file. The client matches the user's query case-insensitively as a substring of each term. |
| 2 | **Yes.** Non-empty, unique ids, **at most 12** situations; the client renders the file order in a two-column grid. |
| 3 | **Yes.** Closed sets for `schema: 1` (listed below). Adding a value = `schema: 2` in the file **and** `minContentSchema: 3` on the entry, so older apps keep the previous bundle instead of a file they cannot render. The app's `SUPPORTED_CONTENT_SCHEMA` moves with it. |
| 4 | **Yes.** Omit absent fields. The client treats absent and `null` identically (already true for `authorities`). |
| 5 | **Yes.** Three id spaces, lowercase `^[a-z][a-z0-9_]*$`; `formId` stays SCREAMING_SNAKE. |
| 6 | Fine. Client behaviour confirmed: an `apply: "app"` benefit whose form is not installed renders as "Formular beim Amt holen" with the office link, never a dead in-app link. |
| 7 | Fine. Until §3 lands, `documents` is authored in both places; the client uses the *form guide* for the per-application checklist and the *benefit* for the result page, so they never conflict on screen. |
| 8 | **Yes.** Orphan results and benefits rejected. |
| 9 | **Confirmed.** A dangling `authorityId` is not a parse error: the result card simply omits the "Zuständig" line and the office link for that benefit (the name lives only in the authorities bundle, so there is no label to fall back to). The sync's all-or-nothing config recording makes this a partial-download edge case only. |
| 10 | **Yes.** One reference per checklist item; the draft already follows this (the Kindergeld step carries `benefitId` only). |
| 11 | Fine. |
| 12 | Fine. |

### Closed sets for `schema: 1` (item 3)

`documents` — the app's `GuideDocument` names, **16** values (two added today so the eight
situations can be authored without a schema bump):

```
ID_CARD, TAX_ID, BANK_DETAILS, BIRTH_CERTIFICATE, RENTAL_CONTRACT, RENT_STATEMENT,
HEATING_BILL, INCOME_PROOF, BANK_STATEMENTS, ASSET_PROOF, EMPLOYMENT_TERMINATION,
PREVIOUS_DECISION, HEALTH_INSURANCE, MATERNITY_CERTIFICATE, REGISTRATION_CERTIFICATE,
MEDICAL_REPORTS
```

`icon` — `child, wallet, home, globe, work, hospital, people, clock`.

### The draft (attached)

`wegweiser-draft/wegweiser.json` + `wegweiser-en.json`, generated from the clickable
mock's trees: 15 benefits, 18 results, 8 situations, 14 synonym groups per language. Checked
against every rule in your "Validator" section (ids, references to the config 6 authority ids
and the 7 form ids, closed sets, depth ≤ 3, 2–4 answers, exactly one `fallback`, one
reference per checklist item, no orphans, lowercase unique terms, de/en structure identical).
Portal URLs for `apply: "online"` benefits are taken from the matching office's `portalUrl`
in `authorities.json`. Please treat the texts as a draft to edit, not as final copy.

Once you publish `wegweiser` (manifest key with the `authorities` entry shape, versioned
paths, `minContentSchema: 2`), the Phase 4 client gets written against exactly that.

---

## Step 3 — form guides as content (material attached)

`form-guides-export.json` holds everything the `guide` block needs:

- `guides[]` — per form: `authorityId` (already the bundle ids), `documents[]`, `steps[]`,
  `tips[]` (enum names), `processingWeeks {min, max}` where the app has one.
- `labels.documents` — `de`/`en` label and `whereToGet` text per document (16).
- `labels.steps`, `labels.tips` — `de`/`en` texts per enum value.

Proposed `guide` block in `form.<lang>.json` (no `minContentSchema` change):

```json
"guide": {
  "authorityId": "familienkasse",
  "processingWeeks": { "min": 4, "max": 8 },
  "documents": [ { "id": "TAX_ID", "label": "Steuer-ID (11 Ziffern) …", "whereToGet": "Kommt per Post …" } ],
  "steps": [ "Alle Angaben prüfen", "Ausdrucken und unterschreiben", "…" ],
  "tips": [ "Für jedes Kind gehört eine Anlage Kind dazu", "…" ]
}
```

`documents[].id` stays an enum name so the per-application checklist keeps working; `label`
and `whereToGet` override the app's built-in texts when present. `steps` and `tips` become
plain text lists (the app's enums for those go away once every form ships the block). Not
urgent; the app keeps its built-in guides until then.

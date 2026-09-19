# Android content contract

*Saved verbatim from the Android handoff of 2026-09-19 (`feat/wegweiser_phase1`). The shapes in §1 are
implemented in this repo; §2 is a proposal under review (see `wegweiser-content-review.md`). Where this
repo deviates from a literal reading — published paths are versioned (`authorities/<version>/de/…`) and
`version` is derived from file hashes, never bumped by hand — the manifest remains the only interface.*

Written 2026-09-19 for the backend maintainer. The app (branch `feat/wegweiser_phase1`) has
shipped the client side of the Wegweiser plan up to and including Zuständigkeit light
(`wegweiser-plan.md`, Phases 0–3). Two things are now blocked on content:

1. **Now:** the `authorities` bundle — the client is built, tested and waits for the file.
   Without it the app shows the plain office label, an "Ämter A–Z" page that says *"Die
   Seiten zu den Ämtern kommen mit der nächsten Aktualisierung"*, and no "Nachfragen" hotline.
2. **Before Phase 4 starts:** the situation / benefit / result / synonym files. The client for
   Phase 4 is *not* built yet, on purpose — content first, then code.

Nothing here bumps `minContentSchema`; the app still accepts schema level 2. Every new file is
optional: a manifest without it must keep working exactly as today.

---

## 1. `authorities` bundle (needed now)

### 1.1 Manifest entry

One optional, singular entry next to `forms` and `hints` in the config manifest:

```json
{
  "schemaVersion": 1,
  "config": 152,
  "forms": [ … ],
  "hints": [ … ],
  "authorities": {
    "version": 1,
    "minContentSchema": 2,
    "jsonDe": { "path": "authorities/de/authorities.json", "sha256": "…", "bytes": 12345 },
    "jsonEn": { "path": "authorities/en/authorities.json", "sha256": "…", "bytes": 12000 }
  }
}
```

- `version`: bump on every content change; the app re-downloads only when it differs from
  the installed version.
- `minContentSchema`: `2`. The app skips the bundle (keeps the installed one) if this is
  higher than what it supports.
- `jsonDe` / `jsonEn`: same `path` / `sha256` / `bytes` triple as form and hint files; the
  app verifies both before installing, and installs both or neither.
- Any other language the app runs in falls back to `de`.

### 1.2 File format `authorities.json`

```json
{
  "schema": 1,
  "authorities": [
    {
      "id": "familienkasse",
      "name": "Familienkasse",
      "does": "Zahlt Kindergeld und Kinderzuschlag. Gehört zur Bundesagentur für Arbeit.",
      "doesNot": "Kein Elterngeld – das ist die Elterngeldstelle. Kein Bürgergeld – das ist das Jobcenter.",
      "handles": ["KINDERGELD"],
      "hotline": "0800 4 5555 30",
      "portalUrl": "https://www.arbeitsagentur.de/familie-und-kinder",
      "portalLabel": "familienkasse.de",
      "appointmentNeeded": false,
      "bring": [
        "Ausweis oder Pass, ggf. Aufenthaltstitel",
        "Meldebescheinigung",
        "Den letzten Brief der Familienkasse"
      ],
      "rights": [
        "Eine Begleitperson oder einen Dolmetscher mitbringen",
        "Unterlagen nachreichen – den Antrag trotzdem sofort abgeben",
        "Eine Eingangsbestätigung verlangen"
      ],
      "howToFindLocal": "Die Familienkasse ist nach Ihrem Wohnort zuständig. Die Bundesagentur für Arbeit hat eine offizielle Suche mit Postleitzahl.",
      "locatorUrl": "https://www.arbeitsagentur.de/vor-ort"
    }
  ]
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | string | yes | stable, lowercase ASCII, no spaces; identical across languages |
| `name` | string | yes | shown as page title and in "Zuständig: …" lines |
| `does` | string | yes | two sentences max, what this office handles |
| `doesNot` | string | no | the common wrong door ("Kein Kindergeld – das ist die Familienkasse") |
| `handles` | string[] | no | form ids from the manifest (`KINDERGELD`, `BUERGERGELD_HA`, …); used for cross-links |
| `hotline` | string | no | nationwide number only, formatted for reading; the app dials it as typed |
| `portalUrl` | string | no | https URL of the nationwide online portal |
| `portalLabel` | string | no | short label for the portal button; falls back to "Online-Portal" |
| `appointmentNeeded` | bool | no (false) | drives one sentence on the page |
| `bring` | string[] | no | one document per entry, plain language |
| `rights` | string[] | no | one right per entry, plain language, imperative |
| `howToFindLocal` | string | no | how to find the local branch *without* the app resolving it |
| `locatorUrl` | string | no | official locator (BA, DRV, BZSt); never a third-party map |

Client behaviour to design against: unknown fields are ignored; an entry without `id` or
`name` is dropped; duplicate ids keep the first; the list is sorted by `name` at runtime.
A file that fails to parse is treated as absent (no crash, no partial display).

### 1.3 Ids the app already references

The form guides (still shipped inside the app, `LocalFormGuideDataSource`) point at these
ids. They **must** exist in the bundle or the links from Anträge cards and the form's
guide card land on the "not available" state:

| id | Used by |
|---|---|
| `familienkasse` | Kindergeld |
| `jobcenter` | Bürgergeld HA / KDU / WBA / EK / VM |
| `elterngeldstelle` | Elterngeld |

Recommended complete set for the first version (order = suggested `name` sort is automatic):
`agentur_fuer_arbeit`, `auslaenderbehoerde`, `bafoeg_amt`, `buergeramt`, `elterngeldstelle`,
`familienkasse`, `finanzamt`, `jobcenter`, `jugendamt`, `krankenkasse`, `pflegekasse`,
`rentenversicherung`, `sozialamt`, `versorgungsamt`, `wohngeldstelle`.

### 1.4 Validator rules (backend CI)

- `schema == 1`; `authorities` non-empty; ids unique, matching `^[a-z][a-z0-9_]*$`.
- Every `handles` entry is a known form id from the same manifest.
- `hotline` matches `^[0-9 +/()-]+$`; `portalUrl` and `locatorUrl` start with `https://`.
- `de` and `en` files contain the same set of ids.
- Text fields have no HTML.

---

## 2. Wegweiser content (needed before Phase 4 client work)

Phase 4 turns the home screen into situation → questions → result. The engine exists in a
git stash on the Android side; it only needs these files. Please treat the shapes below as
the proposal to agree on, not as final — the client will be written against whatever we
settle. All files are per language (`de`, `en`), optional in the manifest, and would share
one manifest entry `wegweiser` with the same `version` / `minContentSchema` / `jsonDe` /
`jsonEn` pattern as `authorities`, pointing at one file each that contains all four sections.

### 2.1 `wegweiser.json`

```json
{
  "schema": 1,
  "benefits": [
    {
      "id": "kindergeld",
      "name": "Kindergeld",
      "what": "Für jedes Kind, unabhängig vom Einkommen, bis mindestens 18.",
      "authorityId": "familienkasse",
      "apply": "app",
      "formId": "KINDERGELD",
      "portalUrl": null,
      "deadline": "Rückwirkend nur 6 Monate – bald beantragen.",
      "documents": ["TAX_ID", "BIRTH_CERTIFICATE", "BANK_DETAILS"]
    }
  ],
  "results": [
    {
      "id": "kind_beides",
      "benefits": ["kindergeld", "elterngeld"],
      "steps": [
        "Kindergeld in der App ausfüllen und an die Familienkasse schicken",
        "Elterngeld online beim Land beantragen – innerhalb von 3 Monaten"
      ],
      "extraAuthorityId": null
    }
  ],
  "situations": [
    {
      "id": "kind",
      "title": "Ich bekomme ein Kind oder habe ein Kind",
      "icon": "child",
      "root": {
        "question": "Möchten Sie nach der Geburt weniger oder gar nicht arbeiten?",
        "answers": [
          { "text": "Ja", "next": { "result": "kind_beides" } },
          { "text": "Nein", "next": { "result": "kind_nur" } },
          { "text": "Weiß ich nicht", "next": { "result": "kind_beides" }, "fallback": true }
        ]
      }
    },
    {
      "id": "neu",
      "title": "Ich bin neu in Deutschland",
      "icon": "globe",
      "root": {
        "checklist": [
          { "text": "Wohnung anmelden (innerhalb von 14 Tagen)", "authorityId": "buergeramt" },
          { "text": "Kindergeld beantragen, wenn Sie Kinder haben", "authorityId": "familienkasse", "benefitId": "kindergeld" }
        ]
      }
    }
  ],
  "synonyms": [
    { "terms": ["miete", "wohngeld", "rent"], "target": { "benefit": "wohngeld" } },
    { "terms": ["pass", "ausweis", "anmeldung"], "target": { "authority": "buergeramt" } },
    { "terms": ["arbeitslos", "job verloren"], "target": { "situation": "arbeit" } }
  ]
}
```

Node shapes (one of): `{ "question", "answers": [{ "text", "next": <node>, "fallback"? }] }`,
`{ "result": "<resultId>" }`, `{ "checklist": [{ "text", "authorityId"?, "benefitId"? }] }`.

`apply` is one of `app` (needs `formId`), `online` (needs `portalUrl`), `paper`.
`documents` use the `GuideDocument` enum names the app already has (`ID_CARD`, `TAX_ID`,
`BANK_DETAILS`, `BIRTH_CERTIFICATE`, `RENTAL_CONTRACT`, `RENT_STATEMENT`, `HEATING_BILL`,
`INCOME_PROOF`, `BANK_STATEMENTS`, `ASSET_PROOF`, `EMPLOYMENT_TERMINATION`,
`PREVIOUS_DECISION`, `HEALTH_INSURANCE`, `MATERNITY_CERTIFICATE`). `icon` is one of
`child`, `wallet`, `home`, `globe`, `work`, `hospital`, `people`, `clock`.

### 2.2 Validator rules

- Every referenced `authorityId`, `benefitId`, `resultId`, `formId`, document name and
  `icon` exists.
- Exactly eight situations; every question path ends in a `result` within depth 3; every
  question has 2–4 answers, of which exactly one has `fallback: true`.
- No `apply: "app"` benefit without an installed form in the same manifest.
- Synonym terms are lowercase and unique across the file.
- `de` and `en` files share the same ids and structure; only text differs.

### 2.3 The eight situations to author (from `wegweiser-concept.md`)

Kind · Kein/zu wenig Geld · Wohnen und Miete · Neu in Deutschland · Arbeit verloren ·
Krankheit und Pflege · Familie und Trennung · Alter und Rente. The question trees and result
texts of the clickable mock are a usable first draft (its content model mirrors this file).

---

## 3. Later, not blocking

- **Form guides as content.** Documents, steps, tips, `processingWeeks` and the authority id
  per form still live in the app (`LocalFormGuideDataSource`). Moving them into the form
  bundle (`guide` block in `form.<lang>.json`) would let you change them without an app
  release. Shape: the fields of `FormGuide` as they are, with `authority` as the id string.
- **"Wo bekomme ich das?" per document.** Currently app strings (`doc_where_*`). Could move
  into the same guide block as `documents: [{ "id": "TAX_ID", "whereToGet": "…" }]`.
- **Per-Land locator table** (16 rows: Land → Zuständigkeitsfinder URL) to enrich
  `howToFindLocal` later — only if the light version proves insufficient
  (`zustaendigkeit-research.md`).

## 4. What the app does meanwhile

| Content missing | App behaviour |
|---|---|
| `authorities` bundle | office name shown as plain label; "Ämter A–Z" and office pages show an explanatory empty state; "Nachfragen" opens the (empty) office page |
| `wegweiser` file | home screen stays the current form catalogue with the "Weitermachen" card |

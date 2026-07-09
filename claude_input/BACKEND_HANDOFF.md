# Backend Config Update — Form JSON Changes (2026-07-10)

The Android app was updated. All form JSONs in `claude-input/forms/` in the app repo are the
**canonical updated versions** — they can be copied 1:1 into the backend config repo. This
document describes what changed and what the backend must know going forward.

## 1. Files to replace (copy verbatim from app repo `claude-input/forms/`)

| Backend file | Changes |
|---|---|
| `forms/kindergeld/3/de/form.json` | removed "TEST IGNORE", removed 13× "(TT.MM.JJJJ)", 2× new `input_row` group, `input_type` on 10 fields |
| `forms/kindergeld/3/en/form.json` | removed 13× "(DD.MM.YYYY)", 2× new `input_row` group, `input_type` on 10 fields |
| `forms/buergergeld_ha/2/de/form.json` | removed 2× "(TT.MM.JJJJ)" |
| `forms/buergergeld_ha/2/en/form.json` | removed 2× date-format suffix |
| `forms/buergergeld_kdu/2/de/form.json` | removed 1× "(TT.MM.JJJJ)" |
| `forms/buergergeld_kdu/2/en/form.json` | removed 1× date-format suffix |
| `forms/buergergeld_wba/2/de/form.json` | removed 18× "(TT.MM.JJJJ)" |
| `forms/buergergeld_wba/2/en/form.json` | removed 18× date-format suffix |
| `forms/buergergeld_ek/2/de/form.json` | removed 2× "(TT.MM.JJJJ)" |
| `forms/buergergeld_ek/2/en/form.json` | removed 2× date-format suffix |
| `forms/buergergeld_vm/2/de/form.json` | removed 1× "(TT.MM.JJJJ)" |
| `forms/buergergeld_vm/2/en/form.json` | removed 1× date-format suffix |

**Important:** bump the version number of every changed form in the backend's `versions.json`
manifest, otherwise already-synced clients will not re-download the content.

## 2. New OPTIONAL schema features the app now supports

Both are fully backward compatible — forms without them render exactly as before.

### 2.1 `input_row` element type (segmented input group)

Renders several short input fields side by side under one shared title. Used for values that
are split across multiple PDF fields (e.g. the 11-digit Steuer-ID split into 4 PDF blocks).

```json
{
  "name": "group_steuer_id_1",
  "type": "input_row",
  "title": "4–7 – Steuer-ID (zwingend, soweit vergeben)",
  "children": [
    { "name": "<exact PDF field name>", "type": "input", "title": "Teil 1", "input_type": "number" },
    { "name": "<exact PDF field name>", "type": "input", "title": "Teil 2", "input_type": "number" },
    { "name": "<exact PDF field name>", "type": "input", "title": "Teil 3", "input_type": "number" },
    { "name": "<exact PDF field name>", "type": "input", "title": "Teil 4", "input_type": "number" }
  ]
}
```

Rules:
- The group `name` is a synthetic UI id (prefix `group_`), it is never written to the PDF.
- Each child keeps the **exact PDF field name** — PDF filling and drafts use the child names.
- Child `title` is a short label shown inside the small field ("Teil 1" / "Part 1").
- Keep child count ≤ 4-5 (screen width).
- A title like `"4–7 – …"` (number range, en-dash between numbers, no space) is displayed
  as-is; a single-number prefix `"8 – "` is parsed by the app and shown as a field-number badge.

### 2.2 `input_type` attribute (keyboard hint)

Optional on any `input` element (top-level or child): `"input_type": "number" | "phone"`.
Omitted or unknown values fall back to the normal text keyboard.

Use `number` for digit-only values (Steuer-ID parts, counts, PLZ, amounts), `phone` for phone
numbers. Do **not** use `number` for mixed alphanumeric values (IBAN, Kindergeld-Nr.).

## 3. Authoring rules going forward (app behavior changed)

- **No format suffixes on date fields.** The app shows a date picker and always writes
  `TT.MM.JJJJ` into the PDF. Never add "(TT.MM.JJJJ)" / "(DD.MM.YYYY)" to titles of
  `"type": "date"` fields.
- **Field-number prefixes stay.** Titles like `"8 – Familienname"` are wanted: the app parses
  the leading `<number> – ` and renders it as a small badge with a clean label. Keep the
  convention `<number><space>–<space><title>` (en-dash or hyphen both work).
- **Numbered section headers** (`"1. Antragstellende Person"`) are NOT parsed — dot notation
  is kept verbatim. This is intentional; don't convert them to dash notation.
- **No placeholder/test text** in any title ("TEST IGNORE" etc.).
- **Repeating blocks** (e.g. "Kind 1"…"Kind 5"): keep the section-header titles as
  `"<Unit> <n>"` (e.g. `"Kind 1"`). The app derives the "Kind hinzufügen" button label from
  the unit word before the trailing number.

## 4. Ready-to-paste prompt for Claude Code in the backend repo

```
The Android app repo updated the form JSONs and the app now supports two new optional
schema features. Do the following:

1. Replace these form files with the versions I provide (copied from the app repo,
   claude-input/forms/): kindergeld 3 (de+en), buergergeld_ha 2 (de+en),
   buergergeld_kdu 2 (de+en), buergergeld_wba 2 (de+en), buergergeld_ek 2 (de+en),
   buergergeld_vm 2 (de+en).
2. Bump the version of every changed form in versions.json so clients re-sync.
3. Update the form-JSON authoring docs/validation to allow two new OPTIONAL fields:
   - element type "input_row": has children of type "input"; group name is synthetic
     (never a PDF field), children carry the real PDF field names.
   - attribute "input_type" on input elements: "number" or "phone" (keyboard hint only).
4. New authoring rules: no date-format suffixes like "(TT.MM.JJJJ)" in titles of date
   fields; no test/placeholder text in titles; keep the "<number> – <title>" prefix
   convention for PDF-numbered fields and "<n>. <title>" for section headers.
```

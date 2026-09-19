# Behördenhelfer config backend

Form content for the [Behördenhelfer Android app](https://github.com/henrypra/behoerdenhelfer_android):
form definitions (JSON, de + en), fillable PDFs, hints catalogs, the office-type
pages ("Ämter A–Z") and the Wegweiser (situation → questions → result), published as a
versioned static file tree. No server — the app syncs from plain static hosting.

## Layout

```
content/            # source of truth — edit this
├── forms/<form>/   #   form_<form>.json, form_<form>-en.json, <pdf>
├── hints/          #   hints_<id>.json, hints_<id>-en.json
├── authorities/    #   authorities.json, authorities-en.json (one optional bundle)
└── wegweiser/      #   wegweiser.json, wegweiser-en.json (one optional bundle)
docs/               # contracts with the Android app
src/                # Kotlin CLI: validator + generator (JDK 17)
dist/               # generated, deployed by CI (gitignored)
```

## Commands

| Command              |                              |
|----------------------|------------------------------|
| `./gradlew validate` | run all content checks       |
| `./gradlew generate` | validate, then write `dist/` |
| `./gradlew test`     | unit tests                   |

## How publishing works

`generate` turns `content/` into an immutable file tree plus one tiny mutable
pointer that the app fetches:

```
latest.json               { "config": 151, "configPath": "config/151.json" }
config/151.json           the manifest: every bundle with version, sha256, minContentSchema
forms/kindergeld/3/       de/form.json · en/form.json · kg1_antrag_kindergeld.pdf
hints/buergergeld/2/      de/hints.json · en/hints.json
authorities/1/            de/authorities.json · en/authorities.json
wegweiser/1/              de/wegweiser.json · en/wegweiser.json
```

Every number is derived — there is nothing to bump by hand:

- a bundle's bytes changed → its version +1, global `config` +1
- nothing changed → byte-identical output, redeploys are no-ops
- published files are never rewritten; a release only adds files and repoints
  `latest.json` (rollback = repoint it back)
- forms that need app capabilities an old client lacks carry a higher
  `minContentSchema`; old clients skip those entries and keep their last copy —
  new content can never break an old app

## Editing content

- **Change a form:** edit the files in `content/forms/<form>/`, open a PR. Done.
- **Add a form:** create `content/forms/<id>/` with both JSONs and the fillable PDF.
- **PDFs are published byte-identical.** Never re-save, flatten or "repair" one —
  some are intentionally encrypted or XFA hybrids.
- **Change an office page:** edit `content/authorities/authorities.json` and the `-en`
  twin. The bundle is optional and singular — delete the folder and the manifest
  simply omits the `authorities` key again.
- **Change the Wegweiser:** edit `content/wegweiser/wegweiser.json` and the `-en` twin;
  same optional/singular rules as `authorities`.

Form JSON authoring rules (the app relies on these):

- **No format suffixes on date field titles** — never "(TT.MM.JJJJ)" / "(DD.MM.YYYY)";
  the app shows a date picker and always writes `TT.MM.JJJJ` into the PDF.
- **Field-number prefixes only where the PDF prints them.** `"8 – Familienname"`
  (`<number> – <title>`, en-dash or hyphen) is rendered by the app as a badge — use it
  for forms whose fields are numbered on paper (the Bürgergeld Anlagen). Never invent
  numbers: the KG1 has none, only boxed section numbers. Numbered *section headers*
  (`"1. Angaben zur antragstellenden Person"`) keep their dot notation verbatim.
- **`input_row`** groups several short `input` children side by side under one title.
  The group name is a synthetic UI id (prefix `group_`, never a PDF field); each child
  carries the exact PDF field name and a short label ("Teil 1"). Keep it to at most
  4–5 children.
- **`segment_lengths`** (optional, on `input_row`) marks the group as *one* value that
  the PDF merely splits into blocks — e.g. the 11-digit Steuer-ID as `[2, 3, 3, 3]`,
  one entry per child. Clients that know the key render a single input of
  `sum(segment_lengths)` characters and distribute the value over the children in
  order; older clients ignore it and show the segmented row, so it does not raise
  `minContentSchema`. Put the keyboard `input_type` on the group as well as the children.
- **`input_type`** (`"number"` | `"phone"`) is an optional keyboard hint on `input`
  fields. Use `number` only for digit-only values — not for mixed alphanumeric ones
  like IBAN or Kindergeld-Nr.
- **Repeating blocks** keep their `"<Unit> <n>"` section titles (e.g. `"Kind 1"`) —
  the app derives the "add" button label from the unit word.
- **No placeholder/test text** in any title.
- **`guide`** (optional, top level) moves the form guide out of the app: `authorityId`
  (an authorities-bundle id), `processingWeeks {min, max}`, `documents [{id, label,
  whereToGet}]` with `id` from the closed document set, `steps[]`, `tips[]`. Only
  `label`, `whereToGet`, `steps` and `tips` may differ between de and en. Older clients
  ignore the block, so it does not raise `minContentSchema`.

Authorities JSON authoring rules (`docs/android-content-contract.md` §1):

- One entry per office *type* (`familienkasse`, `jobcenter`, …), never a concrete local
  branch. Ids are lowercase `^[a-z][a-z0-9_]*$` and identical in de and en; the app links
  to `familienkasse`, `jobcenter` and `elterngeldstelle` from its form guides, so those
  must stay.
- `handles` lists manifest form ids (`KINDERGELD`, `BUERGERGELD_HA`, …) for cross-links.
- `hotline` only for nationwide numbers (the app dials it as typed); `locatorUrl` only
  official locators (BA, DRV, BZSt, BAMF), never a third-party map; `howToFindLocal`
  tells the user in plain text how to find their own branch.
- Only text fields (`name`, `does`, `doesNot`, `portalLabel`, `bring`, `rights`,
  `howToFindLocal`) may differ between de and en; everything else must be identical.
- Plain text only — no HTML, no Markdown.

Wegweiser JSON authoring rules (`docs/android-content-contract.md` §2 as amended by
`docs/wegweiser-content-review.md` and `docs/backend-reply-2026-09-20.md`):

- Three id spaces (`benefits`, `results`, `situations`), lowercase `^[a-z][a-z0-9_]*$`;
  `formId` values are manifest form ids, `authorityId` values are authorities-bundle ids.
- `apply` is `app` (needs `formId`), `online` (needs https `portalUrl`) or `paper`.
- `documents` and `icon` are **closed sets for `schema: 1`** (16 document names, 8 icons,
  listed in `WegweiserValidator`). Adding a value means `schema: 2` in the file *and*
  `ContentSchema.WEGWEISER = 3`, so older apps keep the previous bundle.
- Situations: 1–12; a node is exactly one of `question` (2–4 answers, exactly one
  `fallback: true`, at most 3 questions deep), `result` or `checklist` (items carry
  `authorityId` *or* `benefitId`, never both). Every result must be reachable, every
  benefit referenced somewhere.
- Synonym `terms` are per language, lowercase, trimmed, unique per file; the `target`
  lists must match in order between de and en.
- Omit optional fields, never write `null`. Only `name`, `what`, `deadline`, `steps`,
  `title`, `question`, `text` and `terms` may differ between de and en.
- Plain text only — no HTML, no Markdown.

`validate` blocks anything broken: strict JSON schema, de/en structural parity,
every referenced field must exist in the PDF's AcroForm tree, hint references must
resolve, the authorities and Wegweiser rules above, published paths stay immutable.

## CI

PRs run lint, tests and `validate`. Merging to `main` generates `dist/` and publishes
it to GitHub Pages by committing it additively onto the `gh-pages` branch — files are
only ever added, so published versions survive forever, and the single commit flips
`latest.json` atomically. Before generating, CI restores the previously published
manifest from `gh-pages` as `published-config.json` — the baseline all versions are
derived against.

Live at <https://henrypra.github.io/behoerdenhelfer_config/latest.json>.

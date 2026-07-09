# Behördenhelfer config backend

Form content for the [Behördenhelfer Android app](https://github.com/henrypra/behoerdenhelfer_android):
form definitions (JSON, de + en), fillable PDFs and hints catalogs, published as a
versioned static file tree. No server — the app syncs from plain static hosting.

## Layout

```
content/            # source of truth — edit this
├── forms/<form>/   #   form_<form>.json, form_<form>-en.json, <pdf>
└── hints/          #   hints_<id>.json, hints_<id>-en.json
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

Form JSON authoring rules (the app relies on these):

- **No format suffixes on date field titles** — never "(TT.MM.JJJJ)" / "(DD.MM.YYYY)";
  the app shows a date picker and always writes `TT.MM.JJJJ` into the PDF.
- **Keep field-number prefixes** like `"8 – Familienname"` (`<number> – <title>`,
  en-dash or hyphen) — the app renders the number as a badge. Numbered *section
  headers* (`"1. Antragstellende Person"`) keep their dot notation verbatim.
- **`input_row`** groups several short `input` children side by side under one title
  (e.g. a Steuer-ID split across PDF blocks). The group name is a synthetic UI id
  (prefix `group_`, never a PDF field); each child carries the exact PDF field name
  and a short label ("Teil 1"). Keep it to at most 4–5 children.
- **`input_type`** (`"number"` | `"phone"`) is an optional keyboard hint on `input`
  fields. Use `number` only for digit-only values — not for mixed alphanumeric ones
  like IBAN or Kindergeld-Nr.
- **Repeating blocks** keep their `"<Unit> <n>"` section titles (e.g. `"Kind 1"`) —
  the app derives the "add" button label from the unit word.
- **No placeholder/test text** in any title.

`validate` blocks anything broken: strict JSON schema, de/en structural parity,
every referenced field must exist in the PDF's AcroForm tree, hint references must
resolve, published paths stay immutable.

## CI

PRs run lint, tests and `validate`. Merging to `main` generates `dist/` and publishes
it to GitHub Pages by committing it additively onto the `gh-pages` branch — files are
only ever added, so published versions survive forever, and the single commit flips
`latest.json` atomically. Before generating, CI restores the previously published
manifest from `gh-pages` as `published-config.json` — the baseline all versions are
derived against.

Live at <https://henrypra.github.io/behoerdenhelfer_config/latest.json>.

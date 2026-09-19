# Wegweiser content contract — backend review

Review of `docs/android-content-contract.md` §2 (`wegweiser.json`), 2026-09-19, against how
this repo structures, validates and publishes content. Numbered so the Android side can
answer per item. Nothing below is authored yet; the eight situations follow once the shape
is agreed.

## What is fine as proposed

- **One file per language, one manifest entry `wegweiser`** with the `version` /
  `minContentSchema` / `jsonDe` / `jsonEn` pattern. This is exactly the shape the pipeline
  has for `authorities` now; the entry will be optional, derived and versioned
  (`wegweiser/<version>/de/wegweiser.json`) with no code beyond a second bundle type.
  Keeping benefits, results, situations and synonyms in *one* file is right: they reference
  each other, and a bundle is installed atomically — four files would need four version
  numbers and a cross-bundle consistency rule the client cannot enforce.
- **Inline question trees** (`next: <node>`), depth ≤ 3, 2–4 answers, one `fallback`. All
  checkable here.
- **Referencing `authorityId` and `formId`** across bundles. The validator here already
  knows both sets (forms from `content/forms/`, authorities from `content/authorities/`)
  and will reject dangling ids at publish time.
- `minContentSchema: 2` for the entry, same reasoning as for `authorities`.

## Change requests

1. **Synonym terms are per language, not mixed.** The proposal has
   `["miete", "wohngeld", "rent"]` in one list. Since the file is per language, the de file
   carries German terms and the en file English ones. Parity rule: same list of `target`s
   in the same order in both files; `terms` may differ freely. Uniqueness of terms is
   checked per file. Without this the "only text differs" parity check cannot be written.

2. **Drop "exactly eight situations" from the validator.** A fixed count is content
   masquerading as schema; the next edit that adds or merges a situation would need a
   contract change. The validator checks *non-empty, ids unique*; if the home-screen grid
   needs a bound, say "at most 12" and the client renders whatever count it gets.

3. **Freeze the two enums in the contract and version them by schema.** `documents`
   (`GuideDocument` names) and `icon` values are app-owned enums the backend can only
   mirror. Request: the contract lists them as the closed set for `schema: 1`; adding a
   value later means `schema: 2` in the file and a higher `minContentSchema` on the
   entry — never a silent extension. The backend keeps the list in the validator and
   rejects anything else.

4. **Omit optional fields instead of `null`.** `"portalUrl": null` and
   `"extraAuthorityId": null` in the example. The strict parser here accepts both, but
   authored content omits absent fields (matches `authorities.json`, forms and hints).
   Client rule "unknown/absent → default" is already the case.

5. **Id conventions.** Wegweiser-internal ids (benefit, result, situation) are lowercase
   `^[a-z][a-z0-9_]*$`, the same rule as authority ids. `formId` values stay the manifest's
   SCREAMING_SNAKE ids (`KINDERGELD`) since they *are* manifest ids. Benefits, results and
   situations are three separate id spaces (a synonym target names the space), so
   `kindergeld` may be both a benefit id and — if ever needed — a situation id.

6. **`apply: "app"` must point at a form the same manifest ships.** Proposed already;
   confirming it is enforced here via `formId ∈ discovered forms`. Consequence for the
   client: a benefit whose form is gated out by `minContentSchema` on an old app still
   parses — the client shows it as "apply at the office" rather than a dead in-app link.

7. **`documents` on `apply: "app"` benefits duplicate the form guide.** Once §3 moves the
   guide into the form bundle, a Kindergeld benefit would list documents in two places.
   Proposal: for `apply: "app"` benefits `documents` is *optional* and, when present, the
   validator requires it to equal the form guide's list (once that exists). Until §3
   lands, it is simply authored in both places.

8. **Every `result` must be reachable and every `benefit` used.** Add to the validator:
   no orphan results (unreferenced by any tree), no orphan benefits (unreferenced by any
   result, checklist or synonym). Cheap here, and it catches the classic "renamed the id in
   one place" mistake that the app would only notice as a missing card.

9. **Dangling `authorityId` at runtime.** The backend guarantees the id exists in the
   *same* manifest, but the client may hold `authorities` v1 and `wegweiser` v3 if one
   bundle download failed. The sync algorithm already retries until all applicable
   bundles are installed before recording the config; please confirm the Phase 4 client
   also tolerates a missing authority (falls back to the plain label, as today) rather
   than treating it as a parse error.

10. **`checklist` items: one reference each.** An item may carry `authorityId` *or*
    `benefitId`, not both — a benefit already knows its authority. Validator enforces it;
    the client renders one link per item.

11. **Text rules as for authorities.** No HTML, no Markdown, no blank entries; `steps` and
    checklist `text` are one sentence each in the imperative. The validator rejects tags
    and entities exactly like for `authorities.json`.

12. **What the pipeline cannot do.** Nothing computed at runtime (no per-Land branching,
    no locale-dependent content beyond the de/en pair) and nothing the app must fetch
    outside the manifest (no external JSON, no images). Icons therefore stay an enum the
    app ships, not files.

## Agreed shape, if the above is accepted

Identical to §2.1 with: per-language `terms`; optional fields omitted; `documents` and
`icon` closed sets listed in the contract; no fixed situation count. Source lives at
`content/wegweiser/wegweiser.json` + `wegweiser-en.json`, published as
`wegweiser/<version>/{de,en}/wegweiser.json` under a manifest key `wegweiser` with the
`authorities` entry shape.

## Validator (to be implemented with the content)

- `schema == 1`; ids unique per section and matching `^[a-z][a-z0-9_]*$`.
- Every `authorityId` ∈ authorities bundle, `formId` ∈ forms, `benefitId`/`resultId`
  within the file, `documents` ⊆ closed set, `icon` ∈ closed set.
- `apply` ∈ {`app`, `online`, `paper`}; `app` ⇒ `formId`, `online` ⇒ `portalUrl`
  (https), `paper` ⇒ neither required.
- Trees: every path ends in a `result` within depth 3; 2–4 answers per question; exactly
  one `fallback: true` per question; checklist items have text and at most one reference.
- No orphan results or benefits (item 8).
- Synonym terms lowercase, trimmed, unique per file, non-empty.
- de/en: identical structure with text fields (`name`, `what`, `deadline`, `steps[]`,
  `title`, `question`, `text`, `terms[]`) stripped.

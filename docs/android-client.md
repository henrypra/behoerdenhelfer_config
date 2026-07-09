# Consuming the config backend from the Android app

Instructions for Claude Code working on the Behördenhelfer Android client
(`behoerdenhelfer_android`). This document is the contract for syncing form
content from the config backend. Follow it exactly — the backend's guarantees
only hold if the client plays its part.

## What the backend is

A versioned static file tree on GitHub Pages. **There is no server, no API, no
auth** — only plain HTTPS GETs of static files.

```
Base URL: https://henrypra.github.io/behoerdenhelfer_config/
```

Two kinds of files:

| File | Mutability | Caching |
|---|---|---|
| `latest.json` | **mutable** — the only file that ever changes | never cache; bypass HTTP caches |
| everything else (`config/<n>.json`, `forms/…`, `hints/…`) | **immutable** — published once, never rewritten | cache forever; safe to retry |

### `latest.json` — the pointer

```json
{ "config": 151, "configPath": "config/151.json" }
```

### `config/<n>.json` — the manifest (immutable snapshot)

```json
{
  "schemaVersion": 1,
  "config": 151,
  "generatedAt": "2026-07-02T23:04:55Z",
  "forms": [
    {
      "formId": "KINDERGELD",
      "version": 3,
      "minContentSchema": 1,
      "jsonDe": { "path": "forms/kindergeld/3/de/form.json", "sha256": "…", "bytes": 29476 },
      "jsonEn": { "path": "forms/kindergeld/3/en/form.json", "sha256": "…", "bytes": 29339 },
      "pdf":    { "path": "forms/kindergeld/3/kg1_antrag_kindergeld.pdf", "sha256": "…", "bytes": 1508259 }
    }
  ],
  "hints": [
    {
      "hintsId": "BUERGERGELD",
      "version": 2,
      "minContentSchema": 1,
      "jsonDe": { "path": "hints/buergergeld/2/de/hints.json", "sha256": "…", "bytes": 21583 },
      "jsonEn": { "path": "hints/buergergeld/2/en/hints.json", "sha256": "…", "bytes": 19552 }
    }
  ]
}
```

All `path` values are relative to the base URL. **Always build URLs from
manifest paths — never hardcode or construct content paths yourself.** The
directory layout (`forms/<id>/<version>/…`) is an implementation detail of the
backend and may change; the manifest is the only interface.

## The content schema handshake

The app must declare a single constant, e.g.:

```kotlin
/** Highest content schema level this app's parser understands. */
const val SUPPORTED_CONTENT_SCHEMA = 1
```

- Every manifest entry carries `minContentSchema`. If it is **greater than**
  `SUPPORTED_CONTENT_SCHEMA`, the app cannot render that bundle: **skip the
  entry and keep the last locally installed version of that bundle.** This is
  the mechanism that lets the backend publish new content without ever breaking
  old app versions — it only works if the client honors it.
- Bump `SUPPORTED_CONTENT_SCHEMA` only when the parser actually gains the new
  capability (a new field type, a new behavioral key). Never bump it "to make
  new content show up."
- The manifest's top-level `schemaVersion` versions the *manifest format*
  itself. If it is greater than what the sync code was written against, abort
  the sync and keep current content (don't crash, don't half-parse).

## The sync algorithm

Run opportunistically (app start, background job) — **never block the UI on
network**. The app must be fully usable offline with whatever content it last
installed.

1. **Fetch `latest.json`** with caching bypassed (`Cache-Control: no-cache`;
   GitHub Pages serves it with `max-age=600`, so without the bypass you can be
   up to 10 minutes stale — acceptable for background sync, but don't add your
   own caching on top).
2. **Compare** `latest.config` to the locally stored applied config number.
   Equal → done. **Different → sync.** Compare with `!=`, not `>`: a rollback
   repoints `latest.json` to an *older* config number, and the client must
   follow it down too.
3. **Fetch the manifest** at `configPath`. It is immutable — cache it, retry
   freely.
4. **For each form and hints entry:**
   - `minContentSchema > SUPPORTED_CONTENT_SCHEMA` → skip, keep local copy.
   - Local installed version for this `formId`/`hintsId` equals the entry's
     `version` → nothing to do (versions identify bytes exactly; same version
     is always the same content).
   - Otherwise download every file of the bundle (see next section).
5. **Verify every downloaded file**: byte length must equal `bytes` and the
   SHA-256 of the bytes must equal `sha256`. **Any mismatch → discard the whole
   bundle and keep the previous local version.** Never install a partially
   verified bundle.
6. **Install atomically per bundle**: write to a temp/staging directory, then
   move into place in one step. A bundle is all-or-nothing (both JSONs + PDF
   for forms; both JSONs for hints).
7. **Record state**: per-bundle installed `version`, and — only after all
   applicable entries have been processed — the applied `config` number.
   Failures of individual bundles are fine to leave for the next sync (files
   are immutable; retrying later fetches identical bytes); just don't record
   the new config number until everything applicable succeeded, so the next
   run retries.

## Rules that are easy to get wrong

- **Download both languages.** A bundle is de + en as a unit. The user can
  switch language (or the system locale can change) while offline; never sync
  only the current locale.
- **PDFs are opaque bytes.** Store them byte-identical and hand them to the
  PDF/AcroForm layer as-is. Never re-save, flatten, "repair", or otherwise
  transform them — some are intentionally encrypted or XFA hybrids, and the
  sha256 check exists precisely to guarantee byte fidelity end to end.
- **No deletes on the backend, but entries can disappear from the manifest.**
  If a previously installed `formId` is absent from the new manifest, treat
  that as the backend withdrawing it (hide it from users); don't crash on
  unknown local leftovers.
- **Unknown JSON keys must be ignored, unknown enum values must not crash the
  parser.** Configure the JSON parser leniently for forward compatibility
  (e.g. kotlinx `Json { ignoreUnknownKeys = true }`). The `minContentSchema`
  gate means you should never *see* content you can't render — lenient parsing
  is the backstop, not the mechanism.
- **HTTPS only, no auth headers, no query parameters.** Anything beyond a plain
  GET of the manifest-given path is wrong.
- **Don't invent version comparisons.** `version` and `config` are opaque
  monotonic counters managed entirely by the backend. The client only ever
  asks "is this different from what I have installed?"

## Quick checklist for any change touching sync

- [ ] URLs come from `latest.json` → manifest `path`s, nothing hardcoded
- [ ] `latest.json` fetched cache-bypassed; immutable files cached/retried freely
- [ ] Sync triggers on `config !=` stored value (rollback-safe)
- [ ] `minContentSchema` gate enforced; skipped bundles keep their local copy
- [ ] sha256 + bytes verified before install; bundle install is atomic
- [ ] Both languages downloaded; PDFs untouched byte-for-byte
- [ ] App fully functional offline; sync never blocks UI

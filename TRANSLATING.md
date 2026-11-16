# Translating

Texts live in **`spec/strings.json`** (U1 Spool Spec v1), one entry per key with `pt` and `en`. The same catalog is used by the U1 Factory Display firmware (Portuguese only) and by this app (Portuguese and English), so both show the same wording.

- Placeholders such as `{id}`, `{ch}`, `{uid}`, `{spool}` must appear in both languages. Never translate protocol values, Spoolman data, API keys or network addresses.
- `build.ps1`/`build.sh` copy the catalog to `assets/strings.json` (native code, `L10n.t(key, ...)`) and generate `assets/i18n-data.js` (web UI, `t(key, {...})`).
- `tests/L10nTest.java` and `tests/ui-test.cjs` check placeholders, pt/en completeness and that every key the UI uses exists.

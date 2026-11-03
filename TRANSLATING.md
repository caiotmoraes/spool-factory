# Translations

Edit `assets/translations.json`: `ui` maps source-defined interface text; `messages` maps canonical Portuguese system messages to English. Message placeholders such as `{id}`, `{label}` and `{uid}` remain opaque. Only `{error}` is translated recursively. Never translate protocol values, Spoolman data, API keys or network addresses.

`build.ps1` regenerates `assets/i18n-data.js` from that catalog. Keep the generated file in source so browser UI tests can run directly. Native dialogs/notifications load the same catalog through `L10n`; the UI loads the bundled JavaScript version without network requests.

Language preference: `auto`, `pt` or `en`; existing installations default to `auto`. Portuguese phone locales use Portuguese, and other phone locales fall back to English. Choosing a language in Settings previews it; saving persists it, closing cancels the preview. A language-only change preserves the printer connection and armed session. Canonical history messages are kept unchanged and translated only when displayed; original stored date strings remain as recorded.

Run desktop Java regressions with `test.ps1`, Portuguese UI regressions with `tests/ui-test.cjs`, and English/selection regressions with `tests/i18n-ui-test.cjs`. UI tests use Playwright from `SPOOL_TOOLS_DIR/ui-test/node_modules/playwright-core`.

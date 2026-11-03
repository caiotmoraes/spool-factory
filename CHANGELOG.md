# Changelog

## 1.4.0 — Spool Factory / external-reader ownership fix

- Renamed the app and repository to Spool Factory; Android package/signing identity retained for updates.
- Require PAXX12 Filament Detection = External before metadata writes and physical controls.
- Removed automatic background repairs and clears; internal OpenRFID “no tag” exports must not compete with phone-based association.
- Discard historical identity references from earlier builds, and show physically empty feeders as empty.
- Added native Firmware Config shortcut and regression tests for reader-mode enforcement.

## 1.3.0 — public preparation

- Blank connection setup on first launch; configurable U1 and Spoolman addresses and optional API keys.
- Bare IP/hostname support, preserved explicit HTTPS, read-only connection test and settings available from the gear icon.
- No embedded developer printer addresses, accounts, signing secrets or real inventory fixtures.
- Public source, MIT license, privacy notice, setup/build/test instructions and downloadable signed APK.
- Corrected native Portuguese text encoding.

Includes the original 1.2 identity handling: no fabricated white spool when firmware returns empty metadata, UID/catalog lookup, continuous-insertion identity with explicit uncertainty after reconnect, multicolor support and manual association/load/unload with native confirmation and printer-state guards.

Physical load/unload still needs wider device and hardware validation. This beta does not support stock firmware or iOS.

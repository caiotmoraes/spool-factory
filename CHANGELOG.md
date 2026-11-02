# Changelog

## 1.3.0 — first public beta

- Blank connection setup on first launch; configurable U1 and Spoolman addresses and optional API keys.
- Bare IP/hostname support, preserved explicit HTTPS, read-only connection test and settings available from the gear icon.
- No embedded developer printer addresses, accounts, signing secrets or real inventory fixtures.
- Public source, MIT license, privacy notice, setup/build/test instructions and downloadable signed APK.
- Corrected native Portuguese text encoding.

Includes the original 1.2 identity handling: no fabricated white spool when firmware returns empty metadata, UID/catalog lookup, continuous-insertion identity with explicit uncertainty after reconnect, multicolor support and manual association/load/unload with native confirmation and printer-state guards.

Physical load/unload still needs wider device and hardware validation. This beta does not support stock firmware or iOS.

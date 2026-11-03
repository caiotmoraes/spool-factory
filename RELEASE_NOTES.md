# Spool Factory 1.8.0 — English + Português

Android NFC filament management with Spoolman and Snapmaker U1 + paxx12 Extended Firmware / SpoolLink. UI available in English and Brazilian Portuguese.

## New in 1.8
- English and Brazilian Portuguese across screens, native confirmations, notifications and known app status/error messages.
- Automatic phone-language selection, with saved English/Portuguese/Automatic override in the Control Room. Select the language and tap Save; closing without saving cancels the preview.
- Spoolman names, notes, colors/IDs/UIDs and protocol values remain unchanged. Only presentation text and live number/date formatting are localized.
- Language-only settings saves preserve the connection and pending assignment. Arbitrary firmware/server messages and original stored history dates retain their original text.
- Shared offline translation catalog and regression tests for both languages and 320/390/768 px layouts.

## Included from 1.7
- Warehouse with pixel-art shelves/workers, real inventory colors, material/brand/location filters, weight sorting, low-stock view, compact layout and spool details.
- NFC-assisted spool registration: live lookup shows an existing spool (including archived ones), otherwise a successful lookup unlocks registration. Select an existing filament or create manufacturer/material/color definitions, including multicolor.
- Add another NFC tag, edit weight/location/notes, and delete an exact spool after number confirmation. Deletion preserves manufacturer/filament definitions and is blocked for a spool indicated in a U1 feeder or pending association in the latest received state.
- Themed Control Room for printer/Spoolman addresses, independent read-only tests, optional API keys and insertion timeout.
- Searchable local history office. Warehouse registration/management can work with Spoolman alone, without a connected printer.

## Installation and printer setup
Download **Spool-Factory.apk**, install on an NFC-capable Android 8+ phone and enter your own service addresses. Spoolman often uses port 7912; printer and inventory can be on different hosts. Enable your own Tailscale/VPN if using its addresses. No personal server address, account or API key is included.

For U1 feeder assignment and physical controls, require compatible paxx12/SpoolLink and **Firmware Config → Filament Detection → External**. Change this firmware setting only while idle: it restarts Klipper. Physical load/unload remains blocked during printing/paused states and requires confirmation. This is not stock-firmware, universal Klipper or iOS support.

The app reads NFC UID; it does not rewrite tags or automatically decode the material/color of any commercial tag. It saves tag bindings in Spoolman's `extra.card_uids`. No automatic transfers between spools are made.

The package and signing certificate are retained, so earlier signed versions can be updated without uninstalling. Connection settings stay local. Verify the APK using **SHA256SUMS.txt**.

## Validation and limits
Automated tests cover NFC lookup/duplicates/archived tags, creation, multicolor/weights, metadata preservation, failure recovery without automatic write retries, exact-ID deletion, settings, feeder/manual-operation guards, and responsive UI at 320/390/768 px. The owner reviewed the UI on a Galaxy Z Fold7 and verified service connectivity. Additional phones, server versions and physical load/unload workflows need wider community validation.

Legacy Spoolman writes are not one atomic transaction across manufacturer/filament/spool. After a network error, refresh and inspect before repeating. UID preflight checks do not lock concurrent edits from other clients. Deletion uses the last received U1 state and cannot detect another printer's associations.

MIT licensed, independent community project. No ads, app account or developer telemetry.

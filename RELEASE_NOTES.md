# Spool Factory 1.4.0 — public beta

Android NFC spool identification and feeder assignment for the Snapmaker U1 with PAXX12 Extended Firmware and Spoolman. Includes a pixel-art factory interface, inventory, manual association and native load/unload controls.

## Important integration correction

**Set Firmware Config → Filament Detection → External before using NFC assignment or manual controls.** Changing that firmware setting stops internal reader services and restarts Klipper. Do it only while the printer is idle and no operation is in progress. The application checks the current setting and refuses writes when it cannot verify External mode.

This prevents OpenRFID's “no tag detected” updates from clearing a spool associated by the phone during toolhead loading. The app no longer performs automatic background repairs or metadata clearing to compete with those updates. Existing historical identities from earlier versions are discarded on first use of this version. An empty physical feeder is shown as empty even if a configured spool ID remains in the printer.

## Install

Download **Spool-Factory.apk**. Android 8.0+ with NFC, compatible PAXX12/SpoolLink and a reachable Spoolman deployment are required. Configure your own printer/Spoolman IPs and optional API keys using the gear icon; first launch opens setup and no addresses are supplied by default. LAN or your own VPN/Tailscale can be used.

The Android package and release signing certificate are unchanged from the earlier Spool Manager builds, so existing users can update without uninstalling. Settings/history remain local. The application label is now **Spool Factory**.

The APK checksum is in **SHA256SUMS.txt**. Source, setup instructions, privacy, license and test coverage are available in the repository. Public illustrations use sample data. Private signing keys, developer IPs and real diagnostics are not included.

## Validation status

Desktop regression tests cover external-mode enforcement, feeder selection, physical presence, UID handling, manual-action guards and the interface. The External setting has been applied and read back on the development printer. NFC assignment has been used on a Galaxy Z Fold7. Full physical load/unload and additional phone/firmware combinations still require validation. This is an early community beta, not stock-firmware or iOS support.

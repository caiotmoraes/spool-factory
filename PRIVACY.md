# Privacy

Spool Factory stores connection addresses, optional API keys, the spool inventory cache and recent association history inside the Android application's private storage. Android backup is disabled for this application.

The app connects directly to the printer and Spoolman addresses you configure. It has no analytics SDK, advertising SDK, app account, remote relay or developer telemetry endpoint. The interface, illustrations and font are bundled in the APK and are not downloaded while you use it.

NFC scanning reads the tag identifier. It does not write tags. A selected spool can be associated to a feeder through the printer's supported interfaces. Manual selection does not transfer tag identifiers between Spoolman records.

API keys are stored in app-private preferences, not an Android Keystore-backed encrypted vault. Protect access to your device. HTTP may be necessary for local Moonraker/Spoolman deployments; use a trusted LAN or your own protected VPN. Tailscale is optional and is configured separately by each user. The application contains no default printer IP, VPN identity, account or access credential.

**Share diagnostic** opens Android's share sheet only when you request it. A diagnostic includes device model/Android version, printer state, spool names/IDs/UIDs and recent app history. It excludes the configured API keys. Review that information before posting it publicly. Do not include your API keys, private IP addresses, account details or unsanitized diagnostics in GitHub issues.

Uninstalling the app deletes its local settings and cache. It does not delete spools from Spoolman or reset the printer.

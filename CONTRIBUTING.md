# Contributing

Open an issue with the Android version, phone model, U1 firmware version, app version, expected behavior and steps to reproduce. Remove API keys, private addresses, tag UIDs and private spool data before attaching logs. Screenshots with sample data are welcome.

Pull requests should include a focused change and a regression test when fixing behavior. The desktop test suite uses synthetic inventory and a local HTTP server. It must never send physical commands to a real printer.

See [BUILDING.md](BUILDING.md) to build and test. Preserve the distinction between live U1 identity, a confirmed continuous insertion, and an uncertain historical association. Never replace missing metadata with a fabricated material, color or balance. NFC/manual association must not reassign a physical tag's ownership in Spoolman.

Physical actions must use the supported native feeder commands, validate current printer/feeder state again immediately before execution, and require a user confirmation. Keep them disabled during printing, pause or another operation. Do not add an automatic motor retry after a network timeout.

## 2.0.0 — unificação com o U1 Factory Display (U1 Spool Spec v1)

- **Visual igual ao display**: tema claro (spec/tokens.json), Montserrat + ícones Font Awesome, 5 abas — Início, Controle, Armazém, NFC, Ajustes. Removida a fábrica em pixel art.
- **Nova aba Controle**: pausar/retomar/cancelar, temperaturas dos bicos T1–T4 e da mesa (só com a U1 ociosa), ventiladores (peça, exaustão, circulação, câmara via `M106`/`SET_FAN_SPEED`), parada de emergência, carregar/retirar e "Ativar no alimentador".
- **Conexão automática**: descobre a U1 na rede (/24, `:7125/printer/info`), lembra o IP e procura de novo se o DHCP mudar. Spoolman pelo proxy da U1 (`/server/spoolman/proxy`, `use_v2_response`); Spoolman direto vira opcional.
- **card_uids canônico**: grava sempre o texto JSON `"A,B"` em maiúsculas; lê os formatos antigos (lista, espaços, minúsculas, texto com lista) e migra ao gravar; comparação exata; tags inválidas aparecem na ficha; até 8 tags por rolo. `CARD_UID` aceito como lista ou texto decimal.
- **Mesmos fluxos e textos do display** (spec/strings.json, pt+en): exclusão com uma confirmação (digitar o número), associar/ativar permitido durante a impressão, NFC→inserir e carregar/retirar bloqueados durante a impressão, confirmação com 4 leituras em 20 s, prazo de inserção 1–10 min, janela de 240 s para carregar/retirar.
- Carregar/retirar não exige mais o modo External (igual ao display); gravar tag (`filament_detect/set`) e `spoollink/set` continuam exigindo.
- Tag desconhecida: "Cadastrar rolo" ou "Associar esta tag" direto da leitura. Cadastro de rolo com tag opcional.
- `build.sh` para Linux/macOS (equivalente a setup.ps1 + build.ps1); testes com os fixtures da Spec v1 e teste de UI no Chromium.

## 1.8.0 — public beta

- Added English and Brazilian Portuguese, automatic phone-language selection and a saved Control Room language selector.
- Localized interface, native confirmations, notifications, status/error messages and canonical local history display.
- Preserved all Spoolman names, notes, identifiers and protocol values; localized only presentation text and number/date formatting for live values.
- Language-only settings saves preserve the printer connection and pending assignment.
- Added native and responsive UI regressions for automatic selection, preview cancellation, saved override and opaque catalog labels.

## 1.7.0 — public beta

- Replaced native settings dialog with themed Control Room, separate service test results, optional masked API keys and insertion timeout controls.
- Added exact-spool deletion from Warehouse with fresh preview, typed ID confirmation, metadata-change check and guard against deleting a spool indicated in a feeder.
- Preserved manufacturer and filament catalog entries; cleared deleted spool from local inventory.
- Improved HTTP error guidance for missing endpoints versus authorization failures.
- Settings and deletion tests passed; no real inventory deleted during validation.

## 1.6.0 — included in 1.7 public beta

- Added NFC-assisted spool creation, catalog selection and new manufacturer/filament creation.
- Added fresh tag lookup with existing spool details, archived/conflicting tag guards and shortcut to the existing record.
- Added NFC append and spool weight/location/comment edits, preserving unrelated metadata.
- Warehouse can operate with Spoolman alone; retained U1-specific feeder controls.
- Added integration and UI tests for registration, network failure, duplicate prevention and catalog partial-save recovery.

## 1.5.0 — included in 1.7 public beta

- Added pixel warehouse and office rooms with workers and motion controls.
- Added inventory filters, sorting, low-stock view, compact layout and spool details.
- Added local history search and event filters; preserved physical-operation safeguards.
- Signed APK and responsive UI/API tests verified. Awaiting owner device review.

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


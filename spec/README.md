# U1 Spool Spec v1

Especificação comum ao **U1 Factory Display** (firmware ≥ 0.7.0) e ao **Spool Factory** (Android ≥ 2.0.0).
Os arquivos desta pasta são idênticos nos dois repositórios; altere nos dois ao mesmo tempo.

| Arquivo | Uso |
|---|---|
| `tokens.json` | cores, raios, tipografia, ícones (FontAwesome 5 Solid), regras numéricas (prazos, limites) |
| `strings.json` | catálogo de textos por chave, `pt` + `en`. Placeholders `{nome}`. Firmware gera `src/strings_pt.h` (`tools/gen_strings.py`); app gera `assets/i18n-data.js` no build |
| `fixtures/uids.json` | casos de teste de `card_uids` (leitura/gravação) e `CARD_UID`, usados pelos testes dos dois lados |

## `extra.card_uids`
- **Gravação (sempre canônica):** texto JSON com string `"A,B"`: hex maiúsculo, sem separadores internos, sem duplicatas, ordem de inclusão. Ex.: `extra.card_uids = "\"04A1B2C3D4E5F6,53B992B4\""`.
  Listas JSON e outros formatos legados são **migrados** ao gravar. UIDs inválidos existentes vão para o fim, sem normalizar. `tags[].uid` **não** é copiado.
- **Leitura (tolerante):** string → JSON até 2 níveis; lista → itens; texto → separar por `[,;\s]+`; somar `tags[].uid`; normalizar (só hex, maiúsculas). Válido = 8, 14 ou 20 dígitos; os demais vão para `invalid` e aparecem na ficha.
- **Comparação:** pertença exata ao conjunto (nunca substring). Máximo de 8 UIDs por rolo.
- **Duplicidade:** UID em dois rolos (arquivados incluídos) bloqueia leitura e associação.

## `CARD_UID` (U1 → `filament_detect.info[ch].CARD_UID`)
Aceita lista de inteiros 0–255 **ou** string de inteiros decimais separados por espaço/vírgula. 4, 7 ou 10 bytes; todo zero = sem tag. Resultado em hex maiúsculo.

## Rotas
| Uso | Rota |
|---|---|
| Descoberta | `GET http://<ip>:7125/printer/info` (varredura da /24; IP lembrado) |
| Status | `GET :7125/printer/objects/query?…` |
| Modo External | `GET http://<ip>:80/firmware-config/api/settings` → item `rfid.current == "external"` |
| Spoolman | padrão `POST :7125/server/spoolman/proxy {"request_method","path":"/v1/…","body"?,"use_v2_response":true}` → `result.response`; opcional no app: Spoolman direto (`/api/v1/…`, `X-Api-Key`) |
| Ativar no alimentador | `POST /printer/spoollink/set {channel, info:{…, CARD_UID:[]}, message}` e depois o mesmo vínculo SET_SPOOL_ID abaixo |
| Injetar UID | `POST /printer/filament_detect/set {channel, info:{CARD_UID:[bytes], CARD_TYPE:"M1"|"NTAG"}}` |
| Vincular extrusor → rolo (SpoolLink) | `POST /printer/gcode/script?script=SET_SPOOL_ID%20CHANNEL%3D<0..3>%20SPOOL_ID%3D<id>` (canal 0-based, corpo vazio); confirmar `print_task_config.filament_spool_id[ch]`; depois `PATCH /v1/spool/<id> {"location":"Snapmaker U1 - Extrusor N"}` |
| Rolo anterior do extrusor | depois do vínculo: `GET /v1/spool?location=Snapmaker%20U1%20-%20Extrusor%20N` (busca parcial), filtrar `location` **exato** e `id ≠ novo`; cada um recebe `PATCH {"location":"Armazém"}`. Falha aqui é aviso, não desfaz o vínculo |
| Resposta perdida do SET_SPOOL_ID | timeout não repete o comando: confere `filament_spool_id[ch]` (4 leituras, 20 s) |
| Carregar/retirar | `AUTO_FEEDING EXTRUDER=n LOAD=1/UNLOAD=1`, timeout HTTP **240 s**, sem repetir; confirmar por `channel_state` |
| Ventiladores | `SET_FAN_SPEED FAN=exhaust_fan|circulation_fan|cavity_fan SPEED=0..1`; peça `M106 S0..255` / `M107` |
| Impressão | `PAUSE`, `RESUME`, `CANCEL_PRINT` (via `/printer/print/pause|resume|cancel`); emergência `POST /printer/emergency_stop` |
| Temperatura | `SET_HEATER_TEMPERATURE HEATER=extruder|extruder1..3|heater_bed TARGET=n` |

## Regras iguais nos dois
- Escrita na U1 só em modo External.
- **Associar tag** (Spoolman) e **Ativar no alimentador** (spoollink): permitidos durante a impressão (só metadado).
- **Carregar/retirar** e o fluxo **ler tag → inserir**: bloqueados durante impressão/pausa, com erro de alimentador ou operação em andamento.
- Detector de borda: 1,2 s estável; 2 alimentadores = ambíguo; prazo de inserção 60–600 s, padrão 180 s.
- Confirmação: 4 leituras seguidas do status em até 20 s.
- Excluir: **uma** confirmação (resumo + digitar o número do rolo + "Excluir definitivamente"); o rolo é relido e a identidade conferida antes de apagar.
- Textos: frase normal (sem CAIXA ALTA), reticências "…", separador "·", "T1–T4" para alimentadores.

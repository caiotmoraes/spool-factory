'use strict';
window.STRINGS={
 "nav.home": {
  "pt": "Início",
  "en": "Home"
 },
 "nav.control": {
  "pt": "Controle",
  "en": "Control"
 },
 "nav.stock": {
  "pt": "Armazém",
  "en": "Warehouse"
 },
 "nav.nfc": {
  "pt": "NFC",
  "en": "NFC"
 },
 "nav.settings": {
  "pt": "Ajustes",
  "en": "Settings"
 },
 "nav.log": {
  "pt": "Diário",
  "en": "Log"
 },
 "common.cancel": {
  "pt": "Cancelar",
  "en": "Cancel"
 },
 "common.confirm": {
  "pt": "Confirmar",
  "en": "Confirm"
 },
 "common.save": {
  "pt": "Salvar",
  "en": "Save"
 },
 "common.back": {
  "pt": "Voltar",
  "en": "Back"
 },
 "common.close": {
  "pt": "Fechar",
  "en": "Close"
 },
 "common.refresh": {
  "pt": "Atualizar",
  "en": "Refresh"
 },
 "common.edit": {
  "pt": "Editar",
  "en": "Edit"
 },
 "common.search": {
  "pt": "Buscar",
  "en": "Search"
 },
 "common.all": {
  "pt": "Todos",
  "en": "All"
 },
 "common.none": {
  "pt": "—",
  "en": "—"
 },
 "common.saving": {
  "pt": "Salvando…",
  "en": "Saving…"
 },
 "common.loading": {
  "pt": "Carregando…",
  "en": "Loading…"
 },
 "common.sent": {
  "pt": "Comando enviado.",
  "en": "Command sent."
 },
 "common.updated_at": {
  "pt": "atualizado às {time}",
  "en": "updated at {time}"
 },
 "common.version": {
  "pt": "Versão {v}",
  "en": "Version {v}"
 },
 "conn.searching": {
  "pt": "Procurando a U1…",
  "en": "Searching for the U1…"
 },
 "conn.found": {
  "pt": "U1 encontrada em {ip}",
  "en": "U1 found at {ip}"
 },
 "conn.u1_not_found": {
  "pt": "U1 não encontrada.",
  "en": "U1 not found."
 },
 "conn.offline": {
  "pt": "Sem conexão com a U1",
  "en": "No connection to the U1"
 },
 "conn.online": {
  "pt": "U1 conectada",
  "en": "U1 connected"
 },
 "conn.spoolman_down": {
  "pt": "Spoolman indisponível",
  "en": "Spoolman unavailable"
 },
 "conn.not_ready": {
  "pt": "A impressora não está pronta.",
  "en": "The printer is not ready."
 },
 "conn.external_required": {
  "pt": "Selecione Filament Detection = External no Firmware Config antes de associar ou carregar rolos. Leitores internos podem apagar os dados.",
  "en": "Select Filament Detection = External in Firmware Config before linking or loading spools. Internal readers may erase the data."
 },
 "conn.external_unknown": {
  "pt": "Não foi possível verificar Filament Detection. Confirme o modo External no Firmware Config.",
  "en": "Could not check Filament Detection. Confirm External mode in Firmware Config."
 },
 "conn.external_ok": {
  "pt": "Modo External ativo",
  "en": "External mode active"
 },
 "conn.test": {
  "pt": "Testar conexão",
  "en": "Test connection"
 },
 "conn.rescan": {
  "pt": "Procurar impressora",
  "en": "Find printer"
 },
 "conn.test_u1_ok": {
  "pt": "U1 · OK em {ip}",
  "en": "U1 · OK at {ip}"
 },
 "conn.test_spoolman_ok": {
  "pt": "Spoolman · OK · {n} rolos",
  "en": "Spoolman · OK · {n} spools"
 },
 "conn.test_fail": {
  "pt": "{what} · Falha: {error}",
  "en": "{what} · Failed: {error}"
 },
 "conn.via_proxy": {
  "pt": "Spoolman pelo proxy da U1",
  "en": "Spoolman via the U1 proxy"
 },
 "conn.via_direct": {
  "pt": "Spoolman direto em {url}",
  "en": "Spoolman directly at {url}"
 },
 "conn.offline_writes": {
  "pt": "Sem conexão: leitura do estoque em cache; alterações desativadas.",
  "en": "Offline: cached warehouse is read-only; changes are disabled."
 },
 "uid.empty": {
  "pt": "UID NFC vazio.",
  "en": "Empty NFC UID."
 },
 "uid.invalid": {
  "pt": "UID NFC inválido.",
  "en": "Invalid NFC UID."
 },
 "uid.length": {
  "pt": "UID NFC deve ter 4, 7 ou 10 bytes.",
  "en": "NFC UID must have 4, 7 or 10 bytes."
 },
 "uid.duplicate": {
  "pt": "UID duplicado no Spoolman. Corrija os cadastros antes de associar.",
  "en": "Duplicate UID in Spoolman. Fix the records before linking."
 },
 "uid.taken": {
  "pt": "Tag já vinculada ao rolo #{id}. Nenhuma tag foi transferida.",
  "en": "Tag already linked to spool #{id}. No tag was moved."
 },
 "uid.taken_archived": {
  "pt": "Tag já vinculada ao rolo #{id} (arquivado). Nenhuma tag foi transferida.",
  "en": "Tag already linked to spool #{id} (archived). No tag was moved."
 },
 "uid.too_many": {
  "pt": "Muitas tags neste rolo (máximo {n}).",
  "en": "Too many tags on this spool (max {n})."
 },
 "uid.invalid_stored": {
  "pt": "Tag inválida no cadastro: {uid}",
  "en": "Invalid tag in record: {uid}"
 },
 "uid.field_type": {
  "pt": "O campo card_uids precisa ser do tipo texto no Spoolman.",
  "en": "The card_uids field must be of type text in Spoolman."
 },
 "uid.unsupported": {
  "pt": "Tag sem UID NFC compatível.",
  "en": "Tag without a compatible NFC UID."
 },
 "nfc.title": {
  "pt": "Leitor NFC",
  "en": "NFC reader"
 },
 "nfc.tap": {
  "pt": "Aproxime a tag do rolo",
  "en": "Tap the spool tag"
 },
 "nfc.read": {
  "pt": "Ler tag",
  "en": "Read tag"
 },
 "nfc.reading": {
  "pt": "Identificando tag {uid}…",
  "en": "Identifying tag {uid}…"
 },
 "nfc.unknown": {
  "pt": "Tag não cadastrada: {uid}",
  "en": "Tag not registered: {uid}"
 },
 "nfc.register": {
  "pt": "Cadastrar rolo",
  "en": "Register spool"
 },
 "nfc.archived": {
  "pt": "Tag já cadastrada: {spool}. Este rolo está arquivado.",
  "en": "Tag already registered: {spool}. This spool is archived."
 },
 "nfc.identified": {
  "pt": "Rolo #{id} identificado",
  "en": "Spool #{id} identified"
 },
 "nfc.insert": {
  "pt": "Insira o filamento no alimentador desejado",
  "en": "Insert the filament into the desired feeder"
 },
 "nfc.insert_one": {
  "pt": "Insira o filamento em apenas um alimentador livre.",
  "en": "Insert the filament into only one free feeder."
 },
 "nfc.remaining": {
  "pt": "{s} s restantes",
  "en": "{s} s left"
 },
 "nfc.feeding": {
  "pt": "T{ch} detectado. Aguardando terminar a alimentação…",
  "en": "T{ch} detected. Waiting for feeding to finish…"
 },
 "nfc.confirming": {
  "pt": "Confirmando…",
  "en": "Confirming…"
 },
 "nfc.linked": {
  "pt": "Associado ao T{ch}: {spool}",
  "en": "Assigned to T{ch}: {spool}"
 },
 "nfc.cancelled": {
  "pt": "Associação cancelada.",
  "en": "Assignment cancelled."
 },
 "nfc.timeout": {
  "pt": "Prazo de inserção esgotado. Leia a tag novamente.",
  "en": "Insertion time is over. Read the tag again."
 },
 "nfc.two_feeders": {
  "pt": "Mais de um alimentador mudou. Leia a tag novamente.",
  "en": "More than one feeder changed. Read the tag again."
 },
 "nfc.removed": {
  "pt": "Filamento retirado antes de confirmar. Leia a tag novamente.",
  "en": "Filament removed before confirming. Read the tag again."
 },
 "nfc.state_changed": {
  "pt": "Estado da U1 mudou. Leia a tag novamente.",
  "en": "The U1 state changed. Read the tag again."
 },
 "nfc.not_confirmed": {
  "pt": "Associação não confirmada pela U1. Confira o T{ch} e leia a tag novamente.",
  "en": "The U1 did not confirm the assignment. Check T{ch} and read the tag again."
 },
 "nfc.already_in": {
  "pt": "Este rolo já está no T{ch}.",
  "en": "This spool is already in T{ch}."
 },
 "nfc.all_full": {
  "pt": "Todos os alimentadores têm filamento. Retire um e leia a tag novamente.",
  "en": "All feeders have filament. Remove one and read the tag again."
 },
 "nfc.printing": {
  "pt": "Impressão em andamento: insira o filamento depois que ela terminar.",
  "en": "Printing: insert the filament after the print finishes."
 },
 "nfc.feeder_error": {
  "pt": "Um alimentador está indisponível ou com erro.",
  "en": "A feeder is unavailable or reports an error."
 },
 "nfc.off": {
  "pt": "NFC desligado. Ative o NFC do aparelho.",
  "en": "NFC is off. Turn on the device NFC."
 },
 "nfc.deadline": {
  "pt": "Prazo para inserir após a leitura",
  "en": "Time to insert after reading"
 },
 "nfc.minutes": {
  "pt": "{n} min",
  "en": "{n} min"
 },
 "bind.title": {
  "pt": "Associar tag",
  "en": "Link tag"
 },
 "bind.tap": {
  "pt": "Aproxime a tag que será associada ao rolo #{id}",
  "en": "Tap the tag to link to spool #{id}"
 },
 "bind.confirm": {
  "pt": "Associar a tag {uid} ao rolo #{id}?",
  "en": "Link tag {uid} to spool #{id}?"
 },
 "bind.done": {
  "pt": "Tag {uid} associada ao rolo #{id}",
  "en": "Tag {uid} linked to spool #{id}"
 },
 "bind.already": {
  "pt": "Tag {uid} já estava associada ao rolo #{id}.",
  "en": "Tag {uid} was already linked to spool #{id}."
 },
 "bind.archived": {
  "pt": "Desarquive o rolo antes de associar uma tag.",
  "en": "Unarchive the spool before linking a tag."
 },
 "bind.not_confirmed": {
  "pt": "Associação recebida sem a tag confirmada. Atualize o estoque.",
  "en": "Link saved without a confirmed tag. Refresh the warehouse."
 },
 "create.title": {
  "pt": "Novo rolo",
  "en": "New spool"
 },
 "create.tag_optional": {
  "pt": "Tag NFC (opcional)",
  "en": "NFC tag (optional)"
 },
 "create.catalog": {
  "pt": "Filamento do catálogo",
  "en": "Catalog filament"
 },
 "create.new_filament": {
  "pt": "Novo fabricante/filamento",
  "en": "New vendor/filament"
 },
 "create.done": {
  "pt": "Rolo #{id} cadastrado",
  "en": "Spool #{id} registered"
 },
 "create.done_tag": {
  "pt": "Rolo #{id} cadastrado (tag {uid})",
  "en": "Spool #{id} registered (tag {uid})"
 },
 "create.not_confirmed": {
  "pt": "Cadastro recebido sem a tag confirmada. Atualize o estoque antes de repetir.",
  "en": "Record saved without a confirmed tag. Refresh the warehouse before retrying."
 },
 "create.weight": {
  "pt": "O peso restante não pode superar o peso inicial.",
  "en": "Remaining weight cannot exceed the initial weight."
 },
 "create.partial": {
  "pt": "Se houve falha de rede, atualize e confira o estoque antes de repetir; fabricante ou filamento podem já ter sido criados.",
  "en": "If the network failed, refresh and check the warehouse before retrying; the vendor or filament may already exist."
 },
 "save.confirm": {
  "pt": "Salvar no Spoolman? Isso não movimenta a impressora.",
  "en": "Save to Spoolman? This does not move the printer."
 },
 "update.done": {
  "pt": "Rolo #{id} atualizado no Spoolman",
  "en": "Spool #{id} updated in Spoolman"
 },
 "field.vendor": {
  "pt": "Fabricante",
  "en": "Vendor"
 },
 "field.name": {
  "pt": "Nome",
  "en": "Name"
 },
 "field.material": {
  "pt": "Material",
  "en": "Material"
 },
 "field.colors": {
  "pt": "Cores HEX (até 5)",
  "en": "HEX colors (up to 5)"
 },
 "field.direction": {
  "pt": "Distribuição das cores",
  "en": "Color layout"
 },
 "field.coaxial": {
  "pt": "Lado a lado",
  "en": "Side by side"
 },
 "field.longitudinal": {
  "pt": "Ao longo do filamento",
  "en": "Along the filament"
 },
 "field.density": {
  "pt": "Densidade (g/cm³)",
  "en": "Density (g/cm³)"
 },
 "field.diameter": {
  "pt": "Diâmetro (mm)",
  "en": "Diameter (mm)"
 },
 "field.initial": {
  "pt": "Peso inicial (g)",
  "en": "Initial weight (g)"
 },
 "field.remaining": {
  "pt": "Peso restante (g)",
  "en": "Remaining weight (g)"
 },
 "field.location": {
  "pt": "Local",
  "en": "Location"
 },
 "field.comment": {
  "pt": "Observações",
  "en": "Notes"
 },
 "field.tags": {
  "pt": "Tags NFC",
  "en": "NFC tags"
 },
 "field.lot": {
  "pt": "Lote",
  "en": "Lot"
 },
 "assign.title": {
  "pt": "Ativar no alimentador",
  "en": "Assign to feeder"
 },
 "assign.pick": {
  "pt": "Escolha T1–T4",
  "en": "Choose T1–T4"
 },
 "assign.confirm": {
  "pt": "Confirme que este é o rolo no T{ch}. A associação é enviada à U1.",
  "en": "Confirm this is the spool in T{ch}. The assignment is sent to the U1."
 },
 "assign.done": {
  "pt": "Rolo #{id} ativado no T{ch}",
  "en": "Spool #{id} assigned to T{ch}"
 },
 "assign.insert_first": {
  "pt": "Insira o filamento no T{ch} antes de ativar o rolo.",
  "en": "Insert the filament into T{ch} before assigning the spool."
 },
 "assign.in_other": {
  "pt": "Este rolo já está ativo no T{ch}.",
  "en": "This spool is already assigned to T{ch}."
 },
 "assign.no_material": {
  "pt": "Rolo sem material ou cor cadastrados.",
  "en": "Spool without material or color."
 },
 "feeder.name": {
  "pt": "T{ch}",
  "en": "T{ch}"
 },
 "feeder.invalid": {
  "pt": "Alimentador inválido.",
  "en": "Invalid feeder."
 },
 "feeder.unavailable": {
  "pt": "T{ch} indisponível.",
  "en": "T{ch} unavailable."
 },
 "feeder.error": {
  "pt": "Resolva o erro do T{ch} na U1 antes da troca.",
  "en": "Fix the T{ch} error on the U1 before changing."
 },
 "feeder.busy": {
  "pt": "Aguarde a operação atual do T{ch} terminar.",
  "en": "Wait for the current T{ch} operation to finish."
 },
 "feeder.print_blocked": {
  "pt": "Carregar e retirar ficam bloqueados durante impressão ou pausa.",
  "en": "Load and unload are blocked while printing or paused."
 },
 "feeder.motion": {
  "pt": "Aguarde o movimento atual da U1 terminar.",
  "en": "Wait for the current U1 motion to finish."
 },
 "feeder.empty": {
  "pt": "Vazio",
  "en": "Empty"
 },
 "feeder.unidentified": {
  "pt": "Sem identificação",
  "en": "Not identified"
 },
 "feed.load": {
  "pt": "Carregar",
  "en": "Load"
 },
 "feed.unload": {
  "pt": "Retirar",
  "en": "Unload"
 },
 "feed.warning": {
  "pt": "A U1 pode aquecer o bico e movimentar o filamento. Confira a área da impressora.",
  "en": "The U1 may heat the nozzle and move the filament. Check the printer area."
 },
 "feed.loading": {
  "pt": "Carregando o T{ch}…",
  "en": "Loading T{ch}…"
 },
 "feed.unloading": {
  "pt": "Retirando do T{ch}…",
  "en": "Unloading T{ch}…"
 },
 "feed.load_done": {
  "pt": "Carga concluída no T{ch}.",
  "en": "Load finished on T{ch}."
 },
 "feed.unload_done": {
  "pt": "Retirada concluída no T{ch}.",
  "en": "Unload finished on T{ch}."
 },
 "feed.already_loaded": {
  "pt": "A U1 já informa carga concluída.",
  "en": "The U1 already reports the load finished."
 },
 "feed.already_unloaded": {
  "pt": "A U1 já informa retirada concluída.",
  "en": "The U1 already reports the unload finished."
 },
 "feed.insert_first": {
  "pt": "Insira o filamento no T{ch} antes de carregar ou retirar.",
  "en": "Insert the filament into T{ch} before loading or unloading."
 },
 "feed.no_material": {
  "pt": "Identifique o material antes de carregar.",
  "en": "Identify the material before loading."
 },
 "feed.auto_off": {
  "pt": "Alimentação automática desativada neste alimentador.",
  "en": "Automatic feeding is disabled on this feeder."
 },
 "feed.fail": {
  "pt": "A U1 reportou uma falha no T{ch}.",
  "en": "The U1 reported a failure on T{ch}."
 },
 "feed.unconfirmed": {
  "pt": "Comando enviado sem confirmação. Confira o T{ch} na U1.",
  "en": "Command sent without confirmation. Check T{ch} on the U1."
 },
 "feed.busy_nfc": {
  "pt": "Finalize ou cancele a associação antes de movimentar o filamento.",
  "en": "Finish or cancel the assignment before moving filament."
 },
 "feed.busy_op": {
  "pt": "Aguarde a operação física terminar.",
  "en": "Wait for the physical operation to finish."
 },
 "delete.title": {
  "pt": "Excluir rolo",
  "en": "Delete spool"
 },
 "delete.body": {
  "pt": "O cadastro e as tags deste rolo serão removidos do Spoolman. Fabricante e filamento continuam no catálogo. Não dá para desfazer.",
  "en": "This spool record and its tags will be removed from Spoolman. Vendor and filament stay in the catalog. This cannot be undone."
 },
 "delete.hint": {
  "pt": "Digite o número do rolo para confirmar",
  "en": "Type the spool number to confirm"
 },
 "delete.button": {
  "pt": "Excluir definitivamente",
  "en": "Delete permanently"
 },
 "delete.done": {
  "pt": "Rolo #{id} excluído do Spoolman",
  "en": "Spool #{id} deleted from Spoolman"
 },
 "delete.installed": {
  "pt": "Rolo #{id} está no T{ch}. Retire antes de excluir.",
  "en": "Spool #{id} is in T{ch}. Remove it before deleting."
 },
 "delete.changed": {
  "pt": "O cadastro mudou desde a confirmação. Confira a ficha novamente.",
  "en": "The record changed since confirmation. Check the spool again."
 },
 "delete.wrong_id": {
  "pt": "Número não confere.",
  "en": "Number does not match."
 },
 "ctl.title": {
  "pt": "Controle",
  "en": "Control"
 },
 "ctl.pause": {
  "pt": "Pausar",
  "en": "Pause"
 },
 "ctl.resume": {
  "pt": "Retomar",
  "en": "Resume"
 },
 "ctl.cancel": {
  "pt": "Cancelar impressão",
  "en": "Cancel print"
 },
 "ctl.pause_confirm": {
  "pt": "Pausar a impressão atual?",
  "en": "Pause the current print?"
 },
 "ctl.resume_confirm": {
  "pt": "Retomar a impressão atual?",
  "en": "Resume the current print?"
 },
 "ctl.cancel_confirm": {
  "pt": "Cancelar a impressão atual? Ela não poderá ser retomada.",
  "en": "Cancel the current print? It cannot be resumed."
 },
 "ctl.estop": {
  "pt": "Parada de emergência",
  "en": "Emergency stop"
 },
 "ctl.estop_confirm": {
  "pt": "Parar a U1 imediatamente? Motores e aquecedores serão desligados; depois é preciso reiniciar o firmware.",
  "en": "Stop the U1 immediately? Motors and heaters will be turned off; the firmware must be restarted afterwards."
 },
 "ctl.restart": {
  "pt": "Reiniciar firmware",
  "en": "Restart firmware"
 },
 "ctl.temps": {
  "pt": "Temperaturas",
  "en": "Temperatures"
 },
 "ctl.nozzle": {
  "pt": "Bico T{ch}",
  "en": "Nozzle T{ch}"
 },
 "ctl.bed": {
  "pt": "Mesa",
  "en": "Bed"
 },
 "ctl.chamber": {
  "pt": "Câmara",
  "en": "Chamber"
 },
 "ctl.set_temp": {
  "pt": "Definir {what} para {t} °C?",
  "en": "Set {what} to {t} °C?"
 },
 "ctl.off": {
  "pt": "Desligar",
  "en": "Off"
 },
 "ctl.fans": {
  "pt": "Ventiladores",
  "en": "Fans"
 },
 "fan.part": {
  "pt": "Peça",
  "en": "Part"
 },
 "fan.exhaust": {
  "pt": "Exaustão",
  "en": "Exhaust"
 },
 "fan.circulation": {
  "pt": "Circulação",
  "en": "Circulation"
 },
 "fan.cavity": {
  "pt": "Câmara",
  "en": "Chamber"
 },
 "ctl.blocked_print": {
  "pt": "Indisponível durante a impressão.",
  "en": "Unavailable while printing."
 },
 "ctl.needs_print": {
  "pt": "Sem impressão em andamento.",
  "en": "No print in progress."
 },
 "print.idle": {
  "pt": "Ociosa",
  "en": "Idle"
 },
 "print.printing": {
  "pt": "Imprimindo",
  "en": "Printing"
 },
 "print.paused": {
  "pt": "Pausada",
  "en": "Paused"
 },
 "print.complete": {
  "pt": "Concluída",
  "en": "Complete"
 },
 "print.cancelled": {
  "pt": "Cancelada",
  "en": "Cancelled"
 },
 "print.error": {
  "pt": "Erro",
  "en": "Error"
 },
 "print.offline": {
  "pt": "U1 offline",
  "en": "U1 offline"
 },
 "print.progress": {
  "pt": "{p}%",
  "en": "{p}%"
 },
 "print.eta": {
  "pt": "Termina em {time}",
  "en": "Ends in {time}"
 },
 "print.layer": {
  "pt": "Camada {a}/{b}",
  "en": "Layer {a}/{b}"
 },
 "stock.title": {
  "pt": "Armazém",
  "en": "Warehouse"
 },
 "stock.new": {
  "pt": "Novo rolo",
  "en": "New spool"
 },
 "stock.search": {
  "pt": "Buscar rolo, material, marca, cor ou local",
  "en": "Search spool, material, brand, color or location"
 },
 "stock.archived": {
  "pt": "Arquivados",
  "en": "Archived"
 },
 "stock.low": {
  "pt": "Até 200 g",
  "en": "Up to 200 g"
 },
 "stock.compact": {
  "pt": "Lista compacta",
  "en": "Compact list"
 },
 "stock.clear": {
  "pt": "Limpar filtros",
  "en": "Clear filters"
 },
 "stock.count": {
  "pt": "{n} rolos",
  "en": "{n} spools"
 },
 "stock.total": {
  "pt": "{g} kg no total",
  "en": "{g} kg in total"
 },
 "stock.low_count": {
  "pt": "{n} com pouco filamento",
  "en": "{n} low on filament"
 },
 "stock.empty": {
  "pt": "Nenhum rolo encontrado.",
  "en": "No spools found."
 },
 "stock.sort": {
  "pt": "Ordenar",
  "en": "Sort"
 },
 "sort.id": {
  "pt": "Número",
  "en": "Number"
 },
 "sort.weight": {
  "pt": "Menor peso",
  "en": "Lowest weight"
 },
 "sort.material": {
  "pt": "Material",
  "en": "Material"
 },
 "sort.brand": {
  "pt": "Marca",
  "en": "Brand"
 },
 "stock.low_badge": {
  "pt": "Pouco filamento",
  "en": "Low filament"
 },
 "stock.archived_badge": {
  "pt": "Arquivado",
  "en": "Archived"
 },
 "stock.no_location": {
  "pt": "Local não informado",
  "en": "No location"
 },
 "stock.multicolor": {
  "pt": "{n} cores",
  "en": "{n} colors"
 },
 "stock.detail": {
  "pt": "Ficha do rolo",
  "en": "Spool details"
 },
 "set.title": {
  "pt": "Ajustes",
  "en": "Settings"
 },
 "set.printer": {
  "pt": "Impressora U1",
  "en": "U1 printer"
 },
 "set.ip": {
  "pt": "IP da U1 (vazio = automático)",
  "en": "U1 IP (empty = automatic)"
 },
 "set.spoolman": {
  "pt": "Spoolman direto (opcional)",
  "en": "Direct Spoolman (optional)"
 },
 "set.spoolman_hint": {
  "pt": "Deixe vazio para usar o Spoolman pelo proxy da U1.",
  "en": "Leave empty to use Spoolman through the U1 proxy."
 },
 "set.keys": {
  "pt": "Chaves API (opcional)",
  "en": "API keys (optional)"
 },
 "set.language": {
  "pt": "Idioma",
  "en": "Language"
 },
 "set.lang_auto": {
  "pt": "Automático",
  "en": "Automatic"
 },
 "set.saved": {
  "pt": "Ajustes salvos.",
  "en": "Settings saved."
 },
 "set.about": {
  "pt": "Sobre",
  "en": "About"
 },
 "set.deadline_range": {
  "pt": "O prazo de inserção deve ficar entre 60 e 600 segundos.",
  "en": "The insertion time must be between 60 and 600 seconds."
 },
 "set.share": {
  "pt": "Compartilhar diagnóstico",
  "en": "Share diagnostics"
 },
 "set.firmware": {
  "pt": "Abrir Firmware Config",
  "en": "Open Firmware Config"
 },
 "home.filaments": {
  "pt": "Filamentos",
  "en": "Filaments"
 },
 "home.target": {
  "pt": "alvo {t} °C",
  "en": "target {t} °C"
 },
 "home.fans_more": {
  "pt": "Mais em Controle",
  "en": "More in Control"
 },
 "ctl.feeders": {
  "pt": "Alimentadores",
  "en": "Feeders"
 },
 "nfc.link_this": {
  "pt": "Associar esta tag",
  "en": "Link this tag"
 },
 "nfc.link_pick": {
  "pt": "Escolha o rolo que receberá a tag {uid}",
  "en": "Choose the spool that will receive tag {uid}"
 },
 "set.auto_ip": {
  "pt": "Automático · {ip}",
  "en": "Automatic · {ip}"
 },
 "log.title": {
  "pt": "Diário",
  "en": "Log"
 },
 "log.empty": {
  "pt": "Nenhum registro ainda.",
  "en": "No entries yet."
 },
 "color.black": {
  "pt": "Preto",
  "en": "Black"
 },
 "color.white": {
  "pt": "Branco",
  "en": "White"
 },
 "color.gray": {
  "pt": "Cinza",
  "en": "Gray"
 },
 "color.silver": {
  "pt": "Prata",
  "en": "Silver"
 },
 "color.blue": {
  "pt": "Azul",
  "en": "Blue"
 },
 "color.navy": {
  "pt": "Azul-marinho",
  "en": "Navy blue"
 },
 "color.cyan": {
  "pt": "Ciano",
  "en": "Cyan"
 },
 "color.green": {
  "pt": "Verde",
  "en": "Green"
 },
 "color.lime": {
  "pt": "Verde-lima",
  "en": "Lime"
 },
 "color.yellow": {
  "pt": "Amarelo",
  "en": "Yellow"
 },
 "color.gold": {
  "pt": "Dourado",
  "en": "Gold"
 },
 "color.orange": {
  "pt": "Laranja",
  "en": "Orange"
 },
 "color.red": {
  "pt": "Vermelho",
  "en": "Red"
 },
 "color.pink": {
  "pt": "Rosa",
  "en": "Pink"
 },
 "color.purple": {
  "pt": "Roxo",
  "en": "Purple"
 },
 "color.brown": {
  "pt": "Marrom",
  "en": "Brown"
 },
 "color.beige": {
  "pt": "Bege",
  "en": "Beige"
 }
};

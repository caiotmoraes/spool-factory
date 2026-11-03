'use strict';

const $=id=>document.getElementById(id);

let state={stock:[],history:[]}, tab='factory', includeArchived=false, lastCards='',lastStock='',lastEvents='',moving=!matchMedia('(prefers-reduced-motion: reduce)').matches, clock=0,lastFrame=0,celebrateUntil=0,lastMessage='',lowStock=false,compactStock=false,selectedSpool=0;

const frame=$('factory-scene'),ctx=frame.getContext('2d'),background=new Image();background.src='factory.png';

const esc=x=>String(x??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));

function action(name){if(window.Native)window.Native.action(name);}

document.querySelectorAll('[data-action]').forEach(b=>b.addEventListener('click',()=>action(b.dataset.action)));

document.querySelectorAll('[data-tab]').forEach(b=>b.addEventListener('click',()=>{

 tab=b.dataset.tab;document.querySelectorAll('.page').forEach(p=>p.hidden=p.id!==tab);document.querySelectorAll('[data-tab]').forEach(x=>x.classList.toggle('selected',x===b));window.scrollTo(0,0);renderStock();renderHistory();drawRooms();

}));

function motionButtons(){document.querySelectorAll('[data-motion]').forEach(b=>{b.textContent=moving?'Ⅱ':'▶';b.setAttribute('aria-label',moving?tr('Pausar animações'):tr('Retomar animações'));});}

document.querySelectorAll('[data-motion]').forEach(b=>b.addEventListener('click',()=>{moving=!moving;motionButtons();}));

$('archived').addEventListener('click',()=>{includeArchived=!includeArchived;$('archived').textContent=includeArchived?tr('Ocultar arquivados'):tr('Mostrar arquivados');$('archived').setAttribute('aria-pressed',String(includeArchived));lastStock='';renderStock();});

$('search').addEventListener('input',()=>{lastStock='';renderStock();});

function hex(raw,fallback='77849d'){const s=String(raw??'').replace(/^#/,'');return /^[0-9a-f]{6}([0-9a-f]{2})?$/i.test(s)?'#'+s.slice(0,6):'#'+fallback;}

function stockColors(s){const f=s?.filament||{},list=String(f.multi_color_hexes||'').split(',').filter(Boolean);return list.length?list.map(x=>hex(x)):[hex(f.color_hex)];}

function colorName(colors){

 if(colors.length>1)return colors.length+tr(' cores');

 const rgb=colors[0].slice(1).match(/../g).map(x=>parseInt(x,16));

 const names=[[tr('Preto'),18,19,24],[tr('Branco'),247,247,244],[tr('Cinza'),139,144,150],[tr('Prata'),195,201,207],[tr('Azul'),20,99,211],[tr('Azul-marinho'),0,55,118],[tr('Ciano'),25,203,219],[tr('Verde'),45,163,89],[tr('Verde-lima'),165,224,48],[tr('Amarelo'),255,222,28],[tr('Dourado'),223,166,42],[tr('Laranja'),255,75,0],[tr('Vermelho'),212,38,38],[tr('Rosa'),238,99,155],[tr('Roxo'),137,72,182],[tr('Marrom'),125,75,42],[tr('Bege'),215,191,153]];

 let score=Infinity,name=tr('Cor personalizada');for(const n of names){const d=(rgb[0]-n[1])**2+(rgb[1]-n[2])**2+(rgb[2]-n[3])**2;if(d<score){score=d;name=n[0];}}return name;

}

function grams(s){const n=s?.remaining_weight;return typeof n==='number'&&Number.isFinite(n)?Math.max(0,Math.round(n)).toLocaleString(activeLocale)+' g':'— g';}

function percent(s){let total=s?.initial_weight??s?.filament?.weight;return typeof total==='number'&&total>0&&typeof s?.remaining_weight==='number'?Math.min(100,Math.max(0,s.remaining_weight/total*100)):0;}

function channel(i){return state.status?.[i<2?'filament_feed left':'filament_feed right']?.['extruder'+i]||{};}

const defined=v=>typeof v==='string'&&v.trim()&&!['NONE','UNKNOWN','NULL'].includes(v.toUpperCase());

function tagUid(i){const raw=state.status?.filament_detect?.info?.[i]?.CARD_UID;return Array.isArray(raw)&&raw.some(Number)?raw.map(n=>Number(n).toString(16).padStart(2,'0')).join('').toUpperCase():'';}

function registeredUids(s){let value=s.extra?.card_uids;try{value=JSON.parse(value);}catch{}return [...new Set([...(Array.isArray(value)?value:String(value||'').split(/[,;\s]+/)),...(s.tags||[]).map(t=>t.uid)].filter(v=>typeof v==='string'&&v).map(v=>v.replace(/[^a-f0-9]/gi,'').toUpperCase()))];}

function installed(i){

 const cfg=state.status?.print_task_config||{},c=channel(i),uid=tagUid(i),last=state.associations?.[i];

 let id=Number(cfg.filament_spool_id?.[i]||0),source='',fromCatalog=false;

 if(!id&&uid){const matches=(state.stock||[]).filter(s=>!s.archived&&registeredUids(s).includes(uid));if(matches.length===1){id=matches[0].id;source=tr('Identificado pela tag NFC');fromCatalog=true;}}

 if(!id&&!defined(cfg.filament_type?.[i])&&c.filament_detected&&last?.spool_id&&(!uid||!last.uid||uid===last.uid)){id=last.spool_id;source=last.verified?tr('Associação confirmada no app · U1 sem identificação'):tr('Última associação · confirme o rolo');fromCatalog=true;}

 const s=(state.stock||[]).find(s=>s.id===id)||(state.spool?.id===id?state.spool:null),exists=c.filament_detected===true;

 const identified=!!(defined(cfg.filament_type?.[i])||s?.filament?.material);

 let colors=(!fromCatalog&&defined(cfg.filament_type?.[i]))?cfg.filament_color_multi?.[i]?.colors?.slice(0,cfg.filament_color_multi[i].nums||1).filter(Boolean).map(x=>hex(x)):null;

 if(!colors?.length)colors=(!fromCatalog&&defined(cfg.filament_type?.[i])&&cfg.filament_color_rgba?.[i])?[hex(cfg.filament_color_rgba[i])]:(s?stockColors(s):['#77849d']);

 if(exists&&!identified&&!source)source=tr('U1 sem identificação · selecione o rolo');

 return {channel:i,id,s,c,colors:exists?colors:['#48516a'],exists,identified,source,material:defined(cfg.filament_type?.[i])&&!fromCatalog?cfg.filament_type[i]:(s?.filament?.material||tr('Não identificado')),brand:defined(cfg.filament_vendor?.[i])&&!fromCatalog?cfg.filament_vendor[i]:(s?.filament?.vendor?.name||'')};

}

function readable(s){return ({wait_insert:tr('Entrada livre'),preload_prepare:tr('Preparando alimentação'),preload_feeding:tr('Puxando filamento'),preload_finish:tr('Alimentação concluída'),load_prepare:tr('Preparando carregamento'),load_homing:tr('Posicionando cabeças'),load_picking:tr('Selecionando cabeça'),load_heating:tr('Aquecendo para carregar'),load_feeding:tr('Carregando filamento'),load_extruding:tr('Extrudando filamento'),load_flushing:tr('Limpando bico'),load_finish:tr('Carregamento concluído'),unload_prepare:tr('Preparando retirada'),unload_heating:tr('Aquecendo para retirar'),unload_doing:tr('Retirando filamento'),unload_finish:tr('Retirada concluída'),preload_fail:tr('Falha na alimentação')})[s]||String(s||tr('Aguardando')).replace(/_/g,' ');}

function colorsMarkup(colors){return `<span class="swatch" style="background:${colors.length>1?'linear-gradient(90deg,'+colors.join(',')+')':colors[0]}"></span>${esc(colorName(colors))}`;}

function card(data,title,stockMode=false){

 const {id,s,c,colors,exists,material,brand}=data;

 const stale=!state.connected&&!stockMode;

 const known=data.identified!==false;

 const details=exists?(known?`<div class="colorname">${colorsMarkup(colors)}</div><div class="id">${id>0?'#'+id+' · ':''}${colors.map(x=>x.toUpperCase()).join(' / ')}</div><div class="weight"><span class="bar"><b style="width:${percent(s)}%"></b></span><span>${grams(s)}</span></div>`:tHtml('<div class="colorname">Cor não identificada</div>')):'';

 const bottom=stockMode?`<div class="location">${esc(s?.filament?.name||'')}${s?.archived?tr(' · Arquivado'):''}<br>${esc(s?.location||tr('Local não informado'))}</div>`:`<div class="feeder-state">${stale?tr('Última leitura · sem conexão'):esc(readable(c.channel_state))}${data.source?tHtml('<br><span class="identity-note">')+esc(data.source)+tHtml('</span>'):''}</div><button class="manage-feeder" data-manual-channel="${data.channel}">${tr("Trocar / associar")}</button>`;

 return `<article class="spool-card ${exists?'':'empty'} ${!stockMode&&c.filament_detected&&state.connected?'active':''}" aria-label="${esc(title)}"><canvas class="spool-icon" width="40" height="52" data-colors="${colors.join(',')}" data-empty="${!exists||!known}" aria-hidden="true"></canvas><div class="details"><h3>${esc(title)}</h3><div class="material">${esc(exists?material:tr('Sem rolo'))}</div><div class="brand">${esc(exists&&brand?brand:exists?tr('Identificação pendente'):tr('Entrada disponível'))}</div>${details}</div>${bottom}</article>`;

}

let manualStockSignature='';

function manualOptions(preferred){

 const search=$('manual-search').value.toLocaleLowerCase('pt-BR'),current=preferred??Number($('manual-spool').value),all=(state.stock||[]).filter(s=>!s.archived&&[s.id,s.filament?.material,s.filament?.vendor?.name,s.filament?.name,colorName(stockColors(s))].join(' ').toLocaleLowerCase('pt-BR').includes(search));

 $('manual-spool').innerHTML=tHtml('<option value="0">Selecione um rolo</option>')+all.map(s=>`<option value="${Number(s.id)}">#${Number(s.id)} · ${esc(s.filament?.vendor?.name||'')} ${esc(s.filament?.material||'')} · ${esc(s.filament?.name||colorName(stockColors(s)))}</option>`).join('');

 $('manual-spool').value=all.some(s=>s.id===current)?String(current):'0';manualStockSignature=JSON.stringify(state.stock||[]);

}

function openManual(ch=0,spoolId){$('manual-channel').value=String(ch);$('manual-search').value='';manualOptions(spoolId??installed(ch).id);renderManual();$('manual-dialog').showModal();}

$('manual-open').addEventListener('click',()=>openManual());

$('feeders').addEventListener('click',e=>{const button=e.target.closest('[data-manual-channel]');if(button)openManual(Number(button.dataset.manualChannel));});

$('manual-channel').addEventListener('change',()=>{manualOptions(installed(Number($('manual-channel').value)).id);renderManual();});

$('manual-search').addEventListener('input',()=>{manualOptions();renderManual();});$('manual-spool').addEventListener('change',renderManual);

for(const [id,operation] of [['manual-assign','ASSIGN'],['manual-load','LOAD'],['manual-unload','UNLOAD']])$(id).addEventListener('click',()=>{if(window.Native?.manual)window.Native.manual(operation,Number($('manual-channel').value),Number($('manual-spool').value));});

function renderManual(){

 const ch=Number($('manual-channel').value),c=channel(ch),printing=['printing','paused'].includes(state.status?.print_stats?.state),phases=['wait_insert','preload_finish','load_finish','unload_finish','none','inited'];

 const feedersIdle=[0,1,2,3].every(i=>phases.includes(channel(i).channel_state)&&channel(i).channel_error==='ok');

 const allowed=state.connected&&state.externalMode===true&&!printing&&!state.manualOperation&&!state.armed&&feedersIdle;

 $('manual-assign').disabled=!allowed||!Number($('manual-spool').value)||!c.filament_detected;

 $('manual-load').disabled=!allowed||!c.filament_detected||!defined(state.status?.print_task_config?.filament_type?.[ch]);

 $('manual-unload').disabled=!allowed||!c.filament_detected;

 $('manual-note').textContent=!state.connected?tr('Conecte à U1 para usar os controles.'):state.externalMode!==true?tr('Confirme Filament Detection = External no Firmware Config.'):printing?tr('Impressão em andamento ou pausada: trocas manuais bloqueadas.'):state.manualOperation?tr('Operação física em andamento. ')+readable(c.channel_state):state.armed?tr('Aguarde ou cancele a associação pendente.'):!feedersIdle?tr('Aguarde os feeders terminarem a operação atual.'):'Feeder '+(ch+1)+' · '+readable(c.channel_state)+tr('. Associe o rolo correto antes de carregar.');

}

function paintCards(parent){parent.querySelectorAll('canvas.spool-icon').forEach(c=>{const x=c.getContext('2d');x.imageSmoothingEnabled=false;drawSpool(x,20,26,1.7,c.dataset.colors.split(','),c.dataset.empty==='true');});}

function drawSpool(x,cx,cy,scale,colors,empty=false){

 x.save();x.translate(Math.round(cx-10*scale),Math.round(cy-13*scale));x.scale(scale,scale);

 for(let y=0;y<26;y++)for(let a=0;a<20;a++){

  const q=((a-9.5)/9.5)**2+((y-12.5)/12.5)**2;if(q>1)continue;

  let color='#8c95af';if(q<.83)color='#252b43';if(q<.65)color=empty?'#283048':colors[Math.min(colors.length-1,Math.floor(a/20*colors.length))];

  if(q<.65&&q>.16&&(a+y)%5===0&&!empty)color=shade(color,.68);

  if(q<.16)color='#080e20';if(q>.83&&a<7)color='#c7cbd4';

  x.fillStyle=color;x.fillRect(a,y,1,1);

 }x.restore();

}

function shade(c,m){const n=c.slice(1).match(/../g).map(v=>Math.round(parseInt(v,16)*m));return '#'+n.map(v=>v.toString(16).padStart(2,'0')).join('');}

window.updateApp=function(data){

 state=data;if(data.language&&!$('settings-dialog').open)setLanguage(data.language);if(!state.running)state.armed=false;

 $('lamp').classList.toggle('online',!!state.connected);

 $('connection').textContent=state.connected?tr('U1 conectada'):state.running?tr('Conectando / sem resposta'):tr('U1 desconectada');

 $('connect').textContent=state.running?tr('Desconectar'):tr('Conectar');

 $('mode-warning').hidden=!state.connected||state.externalMode===true;

 const msg=state.running?(state.message||'Conectando à U1…'):tr('Conecte a U1 para começar.');

 $('unknown-register').hidden=!(state.uid&&/Tag não cadastrada/i.test(msg));$('message').textContent=messageText(msg);$('cancel').hidden=!state.armed||!state.running;$('countdown').textContent=state.armed?Math.max(0,state.remaining||0)+' s':'';

 document.querySelector('.feedback').classList.toggle('error',state.running&&/falha|erro|cancelad|não confirmada|sem conexão|expir|encerrad/i.test(msg));

 $('reader-title').textContent=state.armed?tr('INSIRA O FILAMENTO'):tr('APROXIME O ROLO');

 $('reader-hint').textContent=state.armed?tr('O próximo feeder ativado receberá este rolo.'):!state.nfc?tr('Ative o NFC no celular para ler a tag.'):tr('Leia a tag e insira no próximo feeder.');

 $('read').textContent=state.running?tr('LER NFC'):tr('CONECTAR');

 $('scene-status').textContent=!state.connected?tr('AGUARDANDO CONEXÃO'):state.armed?tr('OPERÁRIOS PREPARANDO A TROCA'):state.status?.print_stats?.state==='printing'?tr('U1 IMPRIMINDO'):tr('FÁBRICA PRONTA');

 if(msg!==lastMessage&&msg.startsWith('Associado ao feeder'))celebrateUntil=clock+5000;lastMessage=msg;

 const pending=state.spool;$('pending').hidden=!pending||!state.armed;

 if(pending&&state.armed){const f=pending.filament||{};$('pending').innerHTML=`<strong>${tr("ROLO #")}${Number(pending.id)} · ${esc(f.vendor?.name||'')} ${esc(f.material||'')}</strong><br>${colorsMarkup(stockColors(pending))} · ${grams(pending)}<br><span class="id">UID ${esc(state.uid||'')}</span>`;}

 const items=[0,1,2,3].map(installed);const signature=JSON.stringify([state.connected,items]);

 if(signature!==lastCards){lastCards=signature;$('feeders').innerHTML=items.map((d,i)=>card(d,'FEEDER '+(i+1))).join('');paintCards($('feeders'));}

 renderStock();

 if($('manual-dialog').open&&manualStockSignature!==JSON.stringify(state.stock||[]))manualOptions();renderManual();

 renderHistory();drawRooms();if($('register-dialog').open)registrationForm();if($('spool-dialog').open)renderSpoolDetail();

};

function normalized(value){return String(value??'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('pt-BR');}

function matchesQuery(text,query){const source=normalized(text);return normalized(query).trim().split(/\s+/).every(word=>source.includes(word));}

function refreshFilter(id,values,empty){const select=$(id),previous=select.value,options=[...new Set(values.filter(Boolean))].sort((a,b)=>a.localeCompare(b,'pt-BR'));const signature=JSON.stringify(options);if(select.dataset.options===signature)return;select.dataset.options=signature;select.innerHTML=tHtml('<option value="">')+empty+tHtml('</option>')+options.map(v=>`<option value="${esc(v)}">${esc(v)}</option>`).join('');select.value=options.includes(previous)?previous:'';}

for(const id of ['filter-material','filter-brand','filter-location','stock-sort'])$(id).addEventListener('change',()=>{lastStock='';renderStock();});

$('low-stock').addEventListener('click',()=>{lowStock=!lowStock;$('low-stock').setAttribute('aria-pressed',String(lowStock));lastStock='';renderStock();});

$('stock-view').addEventListener('click',()=>{compactStock=!compactStock;$('stock-view').setAttribute('aria-pressed',String(compactStock));$('stock-view').textContent=compactStock?tr('Ver estantes'):tr('Lista compacta');lastStock='';renderStock();});

$('reset-filters').addEventListener('click',()=>{for(const id of ['search','filter-material','filter-brand','filter-location'])$(id).value='';$('stock-sort').value='id';includeArchived=false;lowStock=false;$('archived').textContent=tr('Mostrar arquivados');$('archived').setAttribute('aria-pressed','false');$('low-stock').setAttribute('aria-pressed','false');lastStock='';renderStock();});

function renderStock(){

 const query=$('search').value,all=state.stock||[];

 refreshFilter('filter-material',all.map(s=>s.filament?.material),tr('Todos'));refreshFilter('filter-brand',all.map(s=>s.filament?.vendor?.name),tr('Todas'));refreshFilter('filter-location',all.map(s=>s.location),tr('Todos'));

 const material=$('filter-material').value,brand=$('filter-brand').value,location=$('filter-location').value,sort=$('stock-sort').value;

 const signature=JSON.stringify([all,query,includeArchived,lowStock,compactStock,material,brand,location,sort,state.stockAt,state.stockError,state.connected,state.running,state.status]);if(signature===lastStock)return;lastStock=signature;

 const active=all.filter(s=>!s.archived),known=active.filter(s=>typeof s.remaining_weight==='number'&&Number.isFinite(s.remaining_weight)),weight=known.reduce((sum,s)=>sum+Math.max(0,s.remaining_weight),0),low=known.filter(s=>s.remaining_weight<=200).length;

 $('stock-summary').innerHTML=`<div><strong>${active.length}</strong><span>${tr("rolos ativos")}</span></div><div><strong>${(weight/1000).toLocaleString(activeLocale,{maximumFractionDigits:2})} kg</strong><span>${tr("peso conhecido")}${known.length<active.length?' · '+(active.length-known.length)+tr(' sem peso'):''}</span></div><div><strong>${low}</strong><span>${tr("até 200 g")}</span></div>`;

 const filtered=all.filter(s=>(includeArchived||!s.archived)&&(!material||s.filament?.material===material)&&(!brand||s.filament?.vendor?.name===brand)&&(!location||s.location===location)&&(!lowStock||(typeof s.remaining_weight==='number'&&Number.isFinite(s.remaining_weight)&&s.remaining_weight<=200))&&matchesQuery([s.id,s.filament?.name,s.filament?.material,s.filament?.vendor?.name,colorName(stockColors(s)),s.location].join(' '),query)).sort((a,b)=>sort==='weight'?(a.remaining_weight??Infinity)-(b.remaining_weight??Infinity)||a.id-b.id:sort==='material'||sort==='brand'?String(sort==='material'?a.filament?.material:a.filament?.vendor?.name).localeCompare(String(sort==='material'?b.filament?.material:b.filament?.vendor?.name),'pt-BR')||a.id-b.id:a.id-b.id);

 $('stock-count').textContent=filtered.length+tr(' de ')+all.length+tr(' rolos');

 $('stock-note').textContent=(state.stockError?messageText(state.stockError)+'. ':'')+(state.stockAt?tr('Estoque atualizado em ')+new Date(state.stockAt).toLocaleString(activeLocale)+(!state.connected?tr(' · dados salvos no celular'):''):tr('Conecte para consultar seu estoque no Spoolman.'));

 $('stock-list').classList.toggle('compact',compactStock);

 $('stock-list').innerHTML=filtered.length?filtered.map(s=>{const feeders=[0,1,2,3].filter(i=>{const d=installed(i);return d.exists&&d.id===s.id;});return tHtml('<div class="shelf-slot">')+card({id:s.id,s,c:{},colors:stockColors(s),exists:true,material:s.filament?.material||tr('Material não informado'),brand:s.filament?.vendor?.name||''},tr('ROLO #')+s.id,true)+(feeders.length?tHtml('<span class="stock-installed">')+(state.connected?tr('Na U1'):tr('Última leitura da U1'))+' · feeder '+feeders.map(i=>i+1).join(', ')+tHtml('</span>'):'')+`<button class="stock-detail" data-spool="${Number(s.id)}" aria-label="${tr("Ver detalhes do rolo ")}${Number(s.id)}">${tr("Ver rolo →")}</button></div>`;}).join(''):tHtml('<p class="notice">')+(all.length?tr('Nenhum rolo encontrado. Tente limpar os filtros.'):tr('Seu estoque aparecerá aqui após conectar ao Spoolman.'))+tHtml('</p>');paintCards($('stock-list'));

}

$('stock-list').addEventListener('click',e=>{const button=e.target.closest('[data-spool]');if(button){selectedSpool=Number(button.dataset.spool);renderSpoolDetail();$('spool-dialog').showModal();}});

function renderSpoolDetail(){const s=(state.stock||[]).find(s=>s.id===selectedSpool);$('detail-assign').disabled=!s||s.archived===true;$('detail-bind').disabled=!s||s.archived===true;$('detail-edit').disabled=!s;$('detail-delete').disabled=!s||state.inventoryBusy===true;if(!s){$('spool-detail').innerHTML=tHtml('<p class="notice">Este rolo não está mais disponível no estoque.</p>');return;}const colors=stockColors(s),f=s.filament||{},uids=registeredUids(s);$('spool-detail').innerHTML=card({id:s.id,s,c:{},colors,exists:true,material:f.material||tr('Material não informado'),brand:f.vendor?.name||''},tr('ROLO #')+s.id,true)+`<dl class="spool-facts"><dt>${tr("Nome")}</dt><dd>${esc(f.name||tr('Não informado'))}</dd><dt>${tr("Local cadastrado")}</dt><dd>${esc(s.location||tr('Não informado'))}</dd><dt>${tr("Peso restante")}</dt><dd>${grams(s)}</dd><dt>${tr("Peso inicial")}</dt><dd>${grams({remaining_weight:s.initial_weight??f.weight})}</dd><dt>Status</dt><dd>${s.archived?tr('Arquivado'):tr('Ativo')}</dd><dt>${tr("Tags NFC")}</dt><dd>${uids.length?uids.map(esc).join(tHtml('<br>')):tr('Nenhuma tag cadastrada')}</dd></dl>`;paintCards($('spool-detail'));}

$('detail-assign').addEventListener('click',()=>{const s=(state.stock||[]).find(s=>s.id===selectedSpool);if(!s||s.archived)return;$('spool-dialog').close();const current=[0,1,2,3].find(i=>installed(i).exists&&installed(i).id===selectedSpool);openManual(current??0,selectedSpool);});

function eventType(text){return /falha|erro|expir|não confirmad|cancelad|sem conexão/i.test(text)?'problem':/associad|associação/i.test(text)?'association':/nfc|tag|identificad|leitura/i.test(text)?'read':'other';}

function renderHistory(){const events=(state.history||[]).filter(Boolean),query=$('history-search').value,type=$('history-filter').value,signature=JSON.stringify([events,query,type]);if(signature===lastEvents)return;lastEvents=signature;const filtered=events.filter(t=>matchesQuery(t+" "+messageText(t),query)&&(!type||eventType(t)===type));$('history-summary').innerHTML=`<div><strong>${events.length}</strong><span>${tr("registros locais")}</span></div><div><strong>${events.filter(t=>eventType(t)==='association').length}</strong><span>${tr("associações")}</span></div><div><strong>${events.filter(t=>eventType(t)==='problem').length}</strong><span>${tr("alertas")}</span></div>`;$('events').innerHTML=filtered.length?filtered.map(t=>'<li class="event-'+eventType(t)+tHtml('"><span class="event-label">')+({association:tr('ASSOCIAÇÃO'),read:'NFC',problem:tr('ATENÇÃO'),other:tr('REGISTRO')})[eventType(t)]+tHtml('</span>')+esc(t)+tHtml('</li>')).join(''):tHtml('<li>')+(events.length?tr('Nenhum registro para este filtro.'):tr('Nenhuma leitura registrada ainda.'))+tHtml('</li>');}

$('history-search').addEventListener('input',renderHistory);$('history-filter').addEventListener('change',renderHistory);

let lastRoomTick=-1;

function drawRooms(){

 for(const id of ['warehouse-scene','office-scene']){const canvas=$(id),x=canvas.getContext('2d'),h=canvas.height;x.imageSmoothingEnabled=false;const r=(c,a,b,w,z)=>{x.fillStyle=c;x.fillRect(a,b,w,z);};r('#0b1429',0,0,480,h);for(let i=0;i<480;i+=48){r('#1a2c48',i,0,4,h-32);r('#17253e',i,25,48,3);}r('#24314b',0,h-32,480,32);for(let i=0;i<480;i+=24)r('#36405a',i,h-17,16,2);for(const a of [40,230,425]){r('#8e6231',a,0,2,18);r('#ffcf62',a-12,18,26,5);r('#fcf0ba',a-7,23,16,3);}

 if(id==='warehouse-scene'){const spools=(state.stock||[]).filter(s=>!s.archived).slice(0,18);for(let row=0;row<3;row++){const y=72+row*48;for(const a of [22,237,452])r('#63708b',a,49,5,139);r('#a5713f',22,y+23,435,6);r('#e4af67',22,y+23,435,2);for(let col=0;col<6;col++){const index=row*6+col,s=spools[index];if(s)drawSpool(x,56+col*72,y+7,1.05,stockColors(s));else{r('#1b2a42',43+col*72,y+14,26,15);r('#3a4860',44+col*72,y+27,24,2);}}}r('#101c32',150,30,180,14);r('#59ecc5',155,35,10,4);x.fillStyle='#d3dccd';x.font='9px monospace';x.fillText('SPOOL FACTORY / STOCK',172,40);r('#b78347',390,193,35,21);r('#e2b66a',405,193,4,21);r('#d9bd79',395,200,8,4);const walk=clock*.018%540;worker(-30+walk,217,1,clock/190,spools.length?stockColors(spools[0])[0]:'#59ecc5',true,x);}

 else{for(const a of [28,355]){r('#384b68',a,54,96,89);for(let row=0;row<3;row++){r('#64738e',a+5,60+row*26,86,22);r('#d5b96e',a+41,68+row*26,16,4);}}r('#97623e',150,114,181,9);r('#b6834e',150,114,181,3);r('#715038',159,123,8,25);r('#715038',315,123,8,25);r('#384d61',209,72,57,40);r('#59ecc5',214,77,47,28);r('#124144',219,83,29,3);r('#124144',219,91,21,3);r('#738698',232,112,10,4);for(let i=0;i<3;i++){r('#eee0b8',285+i*3,103-i*3,25,3);}r('#52b99f',175,99,12,14);r('#eee0b8',177,101,7,2);r('#966946',132,132,13,15);r('#409375',136,112,4,22);r('#6ed292',126,117,12,5);r('#6ed292',140,110,9,5);worker(270,175,-1,clock/400,'#59ecc5',false,x);}

 }

}

function worker(x,y,direction,step,color,carrying,painter=ctx){

 const ctx=painter;ctx.save();ctx.translate(Math.round(x),Math.round(y));ctx.scale(direction*1.5,1.5);

 const r=(color,x,y,w,h)=>{ctx.fillStyle=color;ctx.fillRect(x,y,w,h);};

 r('#061022',-7,0,17,2); // shadow

 const swing=Math.sin(step)>0?2:-2;

 r('#132a55',-4,-9,4,8);r('#2354a3',1,-9,4,8);r('#0a132b',-5+swing,-2,5,3);r('#0a132b',2-swing,-2,5,3);

 r('#2562b8',-5,-17,11,10);r('#3686de',-4,-15,3,6);r('#fff0dc',-7,-18,4,7);r('#ebbe90',-7,-12,3,4);

 r('#e4b283',-3,-25,8,8);r('#fff0d8',3,-24,3,6);r('#10192d',4,-23,2,2);r('#603b27',-4,-25,3,7);

 r('#ffb52b',-5,-29,11,5);r('#ffe461',-3,-31,6,4);r('#ffcc3d',-7,-25,16,2);

 if(carrying){drawSpool(ctx,9,-13,.57,[color]);r('#ffd1a4',2,-13,5,3);r('#fff0dc',-1,-17,5,5);}else{r('#fff0dc',5,-17,3,5);r('#e4b283',6,-12+Math.round(Math.sin(step)*2),3,4);}

 ctx.restore();

}

function animate(time){

 if(!lastFrame)lastFrame=time;

 if(moving&&!document.hidden)clock+=Math.min(80,time-lastFrame);

 lastFrame=time;

 if(tab==='factory'&&!document.hidden){

  ctx.imageSmoothingEnabled=false;ctx.clearRect(0,0,480,360);

  if(background.complete&&background.naturalWidth)ctx.drawImage(background,0,0,480,360);else{ctx.fillStyle='#101b33';ctx.fillRect(0,0,480,360);}

  const items=[0,1,2,3].map(installed),online=!!state.connected;

  for(let i=0;i<4;i++){if(items[i].exists)drawSpool(ctx,176+i*48,289,1.18,items[i].colors,!online);ctx.fillStyle=online&&items[i].c.filament_detected?'#68f0b8':'#66728a';ctx.fillRect(172+i*48,313,8,3);}

  const color=state.armed&&state.spool?stockColors(state.spool)[0]:(items.find(i=>i.exists)?.colors[0]||'#56d6b4');

  const t=clock*(state.armed ? .06 : .024),a=t%660,b=(t*.72+130)%680;

  worker(56+(a<330?a:660-a),343,a<330?1:-1,clock/170,color,true);

  worker(420-(b<340?b:680-b),346,b<340?-1:1,clock/190,items[3].colors[0],true);

  worker(67,144,1,clock/450,'#50dbb8',false);

  worker(437,329,-1,clock/400,'#50dbb8',false);

  if(state.armed&&online){ctx.fillStyle=Math.floor(clock/400)%2?'#59ecc5':'#294746';ctx.fillRect(306,135,24,4);}

  if(clock<celebrateUntil){for(let i=0;i<16;i++){ctx.fillStyle=['#59ecc5','#ffcd3d','#eef0dc'][i%3];ctx.fillRect((i*31+clock*.02)%460,25+(clock*.04+i*19)%180,3,5);}}

 }

 if(tab!=='factory'&&!document.hidden&&Math.floor(clock/100)!==lastRoomTick){lastRoomTick=Math.floor(clock/100);drawRooms();}

 requestAnimationFrame(animate);

}

let registration={mode:'create',id:0,checked:'',matches:[],checking:false,saving:false,catalog:null};

function registrationForm(){const creating=registration.mode==='create',editing=registration.mode==='update';$('register-tag-section').hidden=editing;$('register-catalog-section').hidden=!creating;$('register-weight-fields').hidden=registration.mode==='bind';$('register-initial-section').hidden=!creating;$('weight-edit-note').hidden=!editing;$('register-title').textContent=creating?tr('RECEPÇÃO DE ROLOS'):editing?tr('EDITAR ROLO #')+registration.id:tr('VINCULAR NFC · #')+registration.id;$('new-filament-fields').hidden=!creating||$('register-filament').value!=='new';

 const conflict=registration.matches.some(s=>registration.mode==='create'||s.id!==registration.id),verified=registration.checked===$('register-uid').value&&!!registration.checked;

 $('register-save').disabled=registration.saving||(!editing&&(!verified||registration.checking||conflict))||(creating&&(!$('register-filament').value));$('register-read').disabled=registration.saving||state.armed===true;$('tag-open').hidden=!registration.matches.length;$('tag-open').textContent=registration.matches.length>1?tr('Há vínculos duplicados — confira o estoque'):tr('Abrir rolo #')+(registration.matches[0]?.id||'');$('tag-open').disabled=registration.matches.length!==1;

}

function openRegistration(mode='create',id=0){if(registration.saving)return;if(mode!=='update'&&state.armed){$('inventory-message').textContent=tr('Cancele a associação pendente na Fábrica antes de cadastrar NFC.');return;}const s=(state.stock||[]).find(s=>s.id===id);registration={mode,id,checked:'',matches:[],checking:false,saving:false,catalog:registration.catalog};$('register-form').reset();$('register-uid').value='';$('register-message').textContent='';$('tag-check').textContent=tr('Leia a tag para consultar o Spoolman. Não é necessário gravar a tag.');$('register-color-preview').innerHTML='';

 if(mode==='update'&&s){$('register-remaining').value='';$('register-location').value=s.location||'';$('register-comment').value=s.comment||'';}

 if(mode==='create'){$('register-filament').innerHTML=tHtml('<option value="">Carregando catálogo…</option>');$('register-message').textContent=tr('Consultando catálogo e estoque…');if(window.Native?.catalog)window.Native.catalog();}

 registrationForm();$('register-dialog').showModal();if(window.Native?.registration)window.Native.registration(mode!=='update');

}

$('new-spool').addEventListener('click',()=>openRegistration());

$('unknown-register').addEventListener('click',()=>{const uid=state.uid;openRegistration();if($('register-dialog').open)window.registrationTag(uid);});

for(const [button,mode] of [['detail-bind','bind'],['detail-edit','update']])$(button).addEventListener('click',()=>{$('spool-dialog').close();openRegistration(mode,selectedSpool);});

$('register-dialog').addEventListener('close',()=>{if(window.Native?.registration)window.Native.registration(false);});

$('register-filament').addEventListener('change',()=>{const f=registration.catalog?.filaments?.find(f=>f.id===Number($('register-filament').value));if(f&&typeof f.weight==='number'){$('register-initial').value=f.weight;$('register-remaining').value=f.weight;}registrationForm();});

$('register-colors').addEventListener('input',()=>{const colors=$('register-colors').value.replace(/#/g,'').split(',').map(v=>v.trim()).filter(v=>/^[0-9a-f]{6}$/i.test(v)).map(v=>'#'+v);$('register-color-preview').innerHTML=colors.length?colorsMarkup(colors):'';});

$('register-read').addEventListener('click',()=>{if(state.armed){$('register-message').textContent=tr('Cancele a associação pendente na Fábrica antes de cadastrar uma tag.');return;}if(window.Native?.registration)window.Native.registration(true);action('registrationRead');$('register-message').textContent=tr('Aproxime a tag da traseira do celular.');});

window.registrationTag=function(uid){if(!$('register-dialog').open||registration.mode==='update'||registration.saving)return;registration.checked='';registration.matches=[];registration.checking=true;$('register-uid').value=uid;$('tag-check').textContent=tr('Conferindo esta tag no Spoolman…');$('register-message').textContent='';registrationForm();if(window.Native?.inspectTag)window.Native.inspectTag(uid);};

$('tag-open').addEventListener('click',()=>{if(registration.matches.length!==1)return;const s=registration.matches[0];if(!(state.stock||[]).some(t=>t.id===s.id))state.stock.push(s);selectedSpool=s.id;$('register-dialog').close();renderSpoolDetail();$('spool-dialog').showModal();});

window.inventoryResult=function(result){if(result.message&&!result.inspection)lastInventoryMessage=result.message;

 if(result.deletePreview){window.deletePreview(result.deletePreview);return;}

 if(result.deleted){state.stock=(state.stock||[]).filter(s=>s.id!==result.deleted);deleteTarget=null;deleteBusy=false;$('delete-dialog').close();lastStock='';renderStock();$('inventory-message').textContent=messageText(result.message);return;}

 if(result.deleteError){deleteBusy=false;$('delete-message').textContent=messageText(result.message);$('delete-submit').disabled=true;$('inventory-message').textContent=messageText(result.message);return;}



 if(result.inspection){if(!$('register-dialog').open||result.uid!==$('register-uid').value)return;registration.checking=false;registration.checked=result.ok?result.uid:'';registration.matches=result.matches||[];const matches=registration.matches;

 $('tag-check').innerHTML=!result.ok?esc(messageText(result.message)):!matches.length?tHtml('<strong>Tag livre</strong><br>Nenhum rolo cadastrado com este UID.'):matches.map(s=>tHtml('<strong>Já cadastrada · rolo #')+Number(s.id)+tHtml('</strong><br>')+esc([s.filament?.vendor?.name,s.filament?.material,s.filament?.name].filter(Boolean).join(' · '))+tHtml('<br>')+colorsMarkup(stockColors(s))+' · '+grams(s)+(s.archived?tr(' · Arquivado'):tr(' · Ativo'))).join(tHtml('<hr>'));registrationForm();return;}

 if(result.cancelled){registration.saving=false;$('register-message').textContent=tr('Salvamento cancelado. Nenhuma alteração enviada.');registrationForm();return;}

 if(result.catalog){registration.catalog=result.catalog;const all=result.catalog.filaments||[];$('register-filament').innerHTML=tHtml('<option value="">Escolha um filamento</option><option value="new">+ Cadastrar novo filamento</option>')+all.map(f=>`<option value="${Number(f.id)}">#${Number(f.id)} · ${esc([f.vendor?.name,f.material,f.name].filter(Boolean).join(' · '))}</option>`).join('');$('vendor-names').innerHTML=(result.catalog.vendors||[]).map(v=>`<option value="${esc(v.name)}">`).join('');}

 if(result.saved){const existing=(state.stock||[]).filter(s=>s.id!==result.saved.id);state.stock=[...existing,result.saved];lastStock='';renderStock();registration.saving=false;$('register-dialog').close();selectedSpool=result.saved.id;renderSpoolDetail();$('spool-dialog').showModal();}

 else registration.saving=false;

 const msg=result.message||(result.ok?tr('Catálogo atualizado. Leia a tag e escolha o filamento.'):tr('Não foi possível consultar o Spoolman.'));$('register-message').textContent=messageText(msg);$('inventory-message').textContent=messageText(msg);if($('spool-dialog').open)$('detail-message').textContent=messageText(msg);registrationForm();

};

$('register-form').addEventListener('submit',event=>{event.preventDefault();registrationForm();if($('register-save').disabled)return;const mode=registration.mode,d={mode,id:registration.id};

 try{if(mode!=='update')d.uid=$('register-uid').value;

 if(mode!=='bind'){d.location=$('register-location').value.trim();d.comment=$('register-comment').value.trim();const remaining=$('register-remaining').value;if(remaining!=='')d.remaining_weight=Number(remaining);if(mode==='create'){d.initial_weight=Number($('register-initial').value);if(!Number.isFinite(d.initial_weight)||d.initial_weight<=0||remaining===''||!Number.isFinite(d.remaining_weight)||d.remaining_weight<0||d.remaining_weight>d.initial_weight)throw Error(tr('Confira os pesos inicial e restante.'));

 const value=$('register-filament').value;d.filament_id=value==='new'?0:Number(value);if(value==='new'){for(const key of ['vendor','name','material','colors']){d[key]=$('register-'+key).value.trim();if(!d[key])throw Error(tr('Preencha fabricante, nome, material e cores.'));}for(const key of ['density','diameter']){d[key]=Number($('register-'+key).value);if(!Number.isFinite(d[key])||d[key]<=0)throw Error(tr('Informe densidade e diâmetro válidos.'));}d.direction=$('register-direction').value;if(!d.colors.replace(/#/g,'').replace(/ /g,'').split(',').every(c=>/^[0-9a-f]{6}$/i.test(c))||d.colors.split(',').length>5)throw Error(tr('Informe de uma a cinco cores HEX com seis dígitos.'));}}

 }

 registration.saving=true;registrationForm();$('register-message').textContent=tr('Aguardando confirmação para salvar…');if(window.Native?.saveInventory)window.Native.saveInventory(JSON.stringify(d));

 }catch(error){$('register-message').textContent=error.message;}

});

let settingsBusy=false,deleteTarget=null,deleteBusy=false,settingsLastResult=null,lastInventoryMessage='';

function settingsPayload(){return {printer:$('settings-printer').value.trim(),spoolman:$('settings-spoolman').value.trim(),printerKey:$('settings-printer-key').value.trim(),spoolKey:$('settings-spool-key').value.trim(),timeout:Number($('settings-timeout').value),language:$('settings-language').value};}

function settingsLock(busy){settingsBusy=busy;for(const id of ['settings-printer','settings-spoolman','settings-printer-key','settings-spool-key','settings-timeout','settings-language','settings-save','settings-test'])$(id).disabled=busy;}

let settingsOriginalLanguage=activeLanguage,systemLanguage=activeLanguage;
window.openSettings=function(data){settingsLastResult=null;settingsOriginalLanguage=activeLanguage;systemLanguage=data.systemLanguage||navigator.language;setLanguage(data.language||resolveLanguage(data.languagePreference||'auto',systemLanguage));$('settings-form').reset();$('settings-language').value=data.languagePreference||'auto';settingsLock(false);$('settings-printer').value=data.printer||'';$('settings-spoolman').value=data.spoolman||'';$('settings-printer-key').value=data.printerKey||'';$('settings-spool-key').value=data.spoolKey||'';for(const id of ['settings-printer-key','settings-spool-key'])$(id).type='password';const timeout=String(data.timeout||180);if(![...$('settings-timeout').options].some(o=>o.value===timeout))$('settings-timeout').add(new Option(timeout+tr(' segundos'),timeout));$('settings-timeout').value=timeout;$('settings-advanced').open=false;$('settings-message').textContent='';for(const id of ['printer','spoolman']){$('settings-'+id+'-result').textContent=tr('Ainda não testado');$('settings-'+id+'-result').className='connection-result';}settingsAddressHint();$('settings-dialog').showModal();};

function settingsAddressHint(){const d=settingsPayload(),clean=v=>v.replace(/^https?:\/\//i,'').replace(/\/+$/,'').toLowerCase();$('settings-address-hint').textContent=d.printer&&clean(d.printer)===clean(d.spoolman)?tr('Os endereços estão iguais. Confira se o Spoolman precisa de outro IP ou da porta :7912.'):'';}

for(const id of ['settings-printer','settings-spoolman'])$(id).addEventListener('input',settingsAddressHint);

$('settings-show-keys').addEventListener('change',()=>{for(const id of ['settings-printer-key','settings-spool-key'])$(id).type=$('settings-show-keys').checked?'text':'password';});

function sendSettings(operation){if(settingsBusy)return;const d=settingsPayload();if(!d.spoolman){$('settings-message').textContent=tr('Informe o endereço do Spoolman.');return;}settingsLock(true);$('settings-message').textContent=operation==='test'?tr('Consultando os serviços…'):tr('Salvando configurações…');if(window.Native?.settingsForm)window.Native.settingsForm(operation,JSON.stringify(d));}

$('settings-test').addEventListener('click',()=>sendSettings('test'));$('settings-form').addEventListener('submit',e=>{e.preventDefault();sendSettings('save');});

window.settingsResult=function(result){settingsLastResult=result;if(!$('settings-dialog').open)return;settingsLock(false);if(result.saved){settingsOriginalLanguage=resolveLanguage($('settings-language').value,systemLanguage);setLanguage(settingsOriginalLanguage);registration.catalog=null;$('settings-dialog').close();$('inventory-message').textContent=messageText(result.message);return;}for(const name of ['printer','spoolman'])if(result[name]){const element=$('settings-'+name+'-result');element.textContent=messageText(result[name]);element.className='connection-result '+(result[name+'Error']?'failed':'passed');}$('settings-message').textContent=messageText(result.message)||(result.ok?tr('Conferência concluída. Salve para aplicar.'):tr('Confira os resultados acima e ajuste os endereços ou as chaves.'));};

$('settings-language').addEventListener('change',()=>setLanguage(resolveLanguage($('settings-language').value,systemLanguage)));
$('settings-dialog').addEventListener('close',()=>{if($('settings-dialog').open)return;setLanguage(settingsOriginalLanguage);for(const id of ['settings-printer-key','settings-spool-key'])$(id).value='';settingsLock(false);});

$('detail-delete').addEventListener('click',()=>{const d=installedSpool(selectedSpool);if(d){$('inventory-message').textContent=tr('Rolo indicado no feeder ')+(d.channel+1)+tr('. Retire o filamento e atualize a leitura antes de excluir.');$('spool-dialog').close();return;}if(window.Native?.previewDelete)window.Native.previewDelete(selectedSpool);$('inventory-message').textContent=tr('Conferindo o cadastro antes de excluir…');$('detail-message').textContent=tr('Conferindo o cadastro antes de excluir…');});

function installedSpool(id){return [0,1,2,3].map(installed).find(d=>d.exists&&d.id===id);}

window.deletePreview=function(spool){deleteTarget=spool;deleteBusy=false;$('spool-dialog').close();$('delete-summary').innerHTML=tHtml('<strong>ROLO #')+Number(spool.id)+tHtml('</strong><p>')+esc([spool.filament?.vendor?.name,spool.filament?.material,spool.filament?.name].filter(Boolean).join(' · '))+tHtml('</p><div class="colorname">')+colorsMarkup(stockColors(spool))+tHtml('</div><p>')+grams(spool)+' · '+esc(spool.location||tr('Local não informado'))+tHtml('</p>');$('delete-confirm').value='';$('delete-message').textContent='';$('delete-submit').disabled=true;$('delete-dialog').showModal();};

$('delete-confirm').addEventListener('input',()=>{$('delete-submit').disabled=deleteBusy||!deleteTarget||$('delete-confirm').value.trim()!==String(deleteTarget.id);});

$('delete-submit').addEventListener('click',()=>{if(!deleteTarget||deleteBusy||$('delete-confirm').value.trim()!==String(deleteTarget.id))return;deleteBusy=true;$('delete-submit').disabled=true;$('delete-message').textContent=tr('Excluindo rolo no Spoolman…');if(window.Native?.deleteSpool)window.Native.deleteSpool(deleteTarget.id);});

function renderLocalizedSettings(){settingsAddressHint();const result=settingsLastResult;if(result&&$('settings-dialog').open){for(const name of ['printer','spoolman'])if(result[name])$('settings-'+name+'-result').textContent=messageText(result[name]);$('settings-message').textContent=messageText(result.message)||(result.ok?tr('Conferência concluída. Salve para aplicar.'):tr('Confira os resultados acima e ajuste os endereços ou as chaves.'));}else if($('settings-dialog').open){for(const name of ['printer','spoolman'])$('settings-'+name+'-result').textContent=tr('Ainda não testado');}if(lastInventoryMessage)$('inventory-message').textContent=messageText(lastInventoryMessage);}
window.languageChanged=function(){renderLocalizedSettings();lastCards='';lastStock='';lastEvents='';manualStockSignature='';window.updateApp(state);motionButtons();if($('manual-dialog').open)manualOptions();if($('register-dialog').open)registrationForm();};

drawSpool($('brand-spool').getContext('2d'),24,26,1.7,['#59ecc5']);

motionButtons();

window.updateApp({running:false,connected:false,nfc:true,stock:[],history:[]});requestAnimationFrame(animate);



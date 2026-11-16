'use strict';
// Testes da UI web (sem navegador): regras da Spec v1 no JS iguais ao Java/firmware e catálogo de textos.
const fs=require('fs'),path=require('path'),assert=require('assert');
const root=path.join(__dirname,'..');
global.window={};
eval(fs.readFileSync(path.join(root,'assets/i18n-data.js'),'utf8'));
const spec=JSON.parse(fs.readFileSync(path.join(root,'spec/strings.json'),'utf8'));
assert.deepStrictEqual(window.STRINGS,spec,'i18n-data.js gerado de spec/strings.json');
const app=require(path.join(root,'assets/app.js'));
const fx=JSON.parse(fs.readFileSync(path.join(root,'spec/fixtures/uids.json'),'utf8'));
let n=0;
for(const c of fx.card_uids_read){const s={extra:c.extra==null?undefined:{card_uids:c.extra},tags:(c.tags||[]).map(uid=>({uid}))};const r=app.spoolUids(s);assert.deepStrictEqual(r.valid,c.valid,c.name);assert.deepStrictEqual(r.invalid,c.invalid,c.name);n++;}
for(const c of fx.card_uid){assert.strictEqual(app.cardUidHex(c.value),c.expect,JSON.stringify(c.value));n++;}
app.setLang('pt');assert.strictEqual(app.t('nfc.linked',{ch:2,spool:'#38'}),'Associado ao T2: #38');
app.setLang('en');assert.strictEqual(app.t('nfc.linked',{ch:2,spool:'#38'}),'Assigned to T2: #38');
assert.strictEqual(app.colorName(['#FF4B00']),'Orange');
assert.strictEqual(app.label({id:38,filament:{name:'Preto',material:'PETG',vendor:{name:'Masterprint'}}}),'#38 · Masterprint PETG · Preto');
// Toda chave usada pela UI existe no catálogo.
const src=fs.readFileSync(path.join(root,'assets/app.js'),'utf8')+fs.readFileSync(path.join(root,'assets/index.html'),'utf8');
const used=new Set([...src.matchAll(/\bt\('([a-z_]+\.[a-z_0-9]+)'/g)].map(m=>m[1]).concat([...src.matchAll(/data-tp?="([a-z_]+\.[a-z_0-9]+)"/g)].map(m=>m[1])));
for(const k of used)assert.ok(spec[k],'chave ausente em spec/strings.json: '+k);
console.log(`PASS: UI ${n} casos de fixtures, ${used.size} chaves de texto conferidas, pt/en.`);

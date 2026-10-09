'use strict';(()=>{
const SNAP='portfolio-lab-private-snapshot-v1',STATE='portfolio-lab-v2',PRIV='portfolio-widget-hide-v1';
const $=id=>document.getElementById(id),won=n=>Number.isFinite(n)?'₩'+Math.round(n).toLocaleString('ko-KR'):'UNKNOWN';
function read(k){try{return JSON.parse(localStorage.getItem(k))}catch{return null}}
function regime(v){return v>1?['Risk-on','on']:v< -1?['Risk-off','off']:['Neutral','neutral']}
function setRegime(id,v){const [txt,cls]=regime(Number(v));const e=$(id);e.textContent=txt;e.className='badge '+cls}
function aggregateTop(snap){
 const m=new Map();
 for(const a of snap.accounts||[])for(const h of a.holdings||[])m.set(h.name,(m.get(h.name)||0)+(Number(h.value)||0));
 if(snap.employee_shares&&Number.isFinite(snap.employee_shares.value))m.set(snap.employee_shares.name+' employee shares',(m.get(snap.employee_shares.name+' employee shares')||0)+snap.employee_shares.value);
 return [...m.entries()].sort((a,b)=>b[1]-a[1]).slice(0,3)
}
function render(){
 const snap=read(SNAP),state=read(STATE);
 if(!snap){
 $('content').innerHTML='<div class="card empty" style="grid-column:1/-1"><div>개인 자산 스냅샷이 없습니다.<br><b>이 기기에서만</b> JSON을 불러오세요.<br><br><input id="quickImport" type="file" accept=".json,application/json" style="max-width:260px"></div></div>';
 $('total').textContent='No snapshot';$('active').textContent='—';$('asof').textContent='개인 자산 데이터 없음';$('status').textContent='GitHub에는 개인 숫자를 저장하지 않음';
 const q=$('quickImport');q.onchange=async e=>{try{const file=e.target.files[0];if(!file)return;const x=JSON.parse(await file.text());if(!x||x.schema!=='portfolio-lab-private-snapshot-v1'||!Array.isArray(x.accounts)||!Number.isFinite(x.five_account_total))throw Error('Invalid snapshot format');if(x.accounts.reduce((n,a)=>n+a.total,0)!==x.five_account_total)throw Error('Account totals do not reconcile');if(x.accounts.some(a=>a.holdings.reduce((n,h)=>n+h.value,0)!==a.total))throw Error('Holdings do not reconcile');localStorage.setItem(SNAP,JSON.stringify(x));render()}catch(err){alert('Import failed: '+err.message)}};
 return
}
 const accounts=Object.fromEntries((snap.accounts||[]).map(a=>[a.name,a]));
 const active=accounts.Comprehensive?.total;
 const retirement=(accounts.DC?.total||0)+(accounts.IRP?.total||0)+(accounts.Pension?.total||0);
 $('total').textContent=won(snap.observed_investments);
 $('active').textContent=won(active);
 $('retirement').textContent=won(retirement);
 $('isa').textContent=won(accounts.ISA?.total);
 $('employee').textContent=won(snap.employee_shares?.value);
 $('asof').textContent='Snapshot '+(snap.snapshot_id||'')+' · '+(snap.as_of||'');
 $('pdate').textContent=(snap.captures_as_of||snap.as_of||'UNKNOWN').replace(' KST','');
 const top=$('top');top.replaceChildren();
 for(const [name,val] of aggregateTop(snap)){const r=document.createElement('div');r.className='row';const n=document.createElement('span');n.className='name';n.textContent=name;const a=document.createElement('span');a.className='amt sensitive';a.textContent=won(val);r.append(n,a);top.append(r)}
 const r=state?.regime||{};
 setRegime('usRegime',r.us);setRegime('krRegime',r.kr);
 $('fx').textContent=Number.isFinite(Number(state?.fx))?Math.round(Number(state.fx)).toLocaleString('ko-KR'):'—';
 $('mdate').textContent=r.asof||'UNKNOWN';
 const flags=[];
 if(Number(r.fx5)>2)flags.push('KRW 약세');
 if(Number(r.rates)>20)flags.push('금리 쇼크');
 if(Number(r.flow)<-1)flags.push('외국인 순매도');
 if(snap.employee_shares?.status==='PROVISIONAL')flags.push('임직원주식 provisional');
 const f=$('flags');f.replaceChildren();
 if(!flags.length){const b=document.createElement('span');b.className='badge on';b.textContent='추가 경고 없음';f.append(b)}
 else for(const x of flags){const b=document.createElement('span');b.className='badge warn';b.style.margin='0 4px 4px 0';b.textContent=x;f.append(b)}
 applyPrivacy();
}
function applyPrivacy(){const hide=localStorage.getItem(PRIV)!=='0';document.querySelectorAll('.sensitive').forEach(e=>e.classList.toggle('mask',hide));$('privacy').textContent=hide?'🙈':'👁'}
$('privacy').onclick=()=>{const hide=localStorage.getItem(PRIV)!=='0';localStorage.setItem(PRIV,hide?'0':'1');applyPrivacy()};
window.addEventListener('storage',render);document.addEventListener('visibilitychange',()=>{if(!document.hidden)render()});
if('serviceWorker'in navigator)navigator.serviceWorker.register('./sw.js').catch(()=>{});
render();
})();
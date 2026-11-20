import readXlsxFile from 'read-excel-file';
import {parseCSV,reconcile,formatMoney,csvReport} from './core.js';
import './style.css';
const byId=id=>document.getElementById(id);
const state={A:null,B:null,result:null,sample:false,voted:false};
const trackedSources=new Set(['disquiet','iboss','marketslab','direct']);
const campaignSource=(new URLSearchParams(location.search).get('utm_source')||'').toLowerCase();
const source=trackedSources.has(campaignSource)?campaignSource:'organic';
/** Anonymous, aggregate action counters: never send file content, keys, amounts, identifiers or storage tokens. */
function signal(kind){
  if(navigator.doNotTrack==='1')return;
  const name=kind==='analysis-real'?'analysis-real-'+source:kind;
  fetch('/signals/'+name+'.svg?t='+Date.now(),{method:'GET',cache:'no-store',keepalive:true,referrerPolicy:'no-referrer'}).catch(()=>{});
}

function showError(message){const el=byId('error');el.hidden=false;el.textContent=message;}
function clearError(){byId('error').hidden=true;byId('error').textContent='';}
function headers(rows){return (rows[0]||[]).map((x,i)=>{const v=String(x??'').trim();return v?v+' · '+(i+1)+'열':(i+1)+'열';});}
function defaultColumn(header,type){const tests=type==='key'?[/주문.*번호|거래.*번호|주문.*id|거래.*id|order.*id|transaction.*id|정산.*번호/i,/번호|식별|id|code|키/i]:[/정산.*금액|입금.*금액|결제.*금액|판매.*금액|total.*amount/i,/금액|amount|총액|price|매출/i];for(const re of tests){const idx=header.findIndex(x=>re.test(x));if(idx>=0)return idx;}return type==='key'?0:Math.min(1,header.length-1);}
function setRows(side,rows,name,sample=false){
  if(!rows || rows.length<2)throw new Error('첫 줄은 열 이름, 그 아래는 데이터가 있어야 합니다.');
  if(rows.length>20001)throw new Error('최대 20,000행까지만 비교할 수 있습니다.');
  const hs=headers(rows);if(hs.length<2)throw new Error('거래번호 열과 금액 열이 필요합니다.');
  state[side]={rows,name,sample};state.result=null;byId('results').hidden=true;
  const head=hs;const key=byId('key'+side),amount=byId('amount'+side);
  for(const sel of [key,amount]){sel.replaceChildren(...head.map((x,i)=>new Option(x,String(i))));}
  key.value=String(defaultColumn(head,'key'));amount.value=String(defaultColumn(head,'amount'));
  byId('status'+side).textContent=name+' · '+(rows.length-1).toLocaleString('ko-KR')+'행';
  byId('map'+side).hidden=false;
  const prev=byId('preview'+side);prev.textContent='미리보기: '+rows.slice(1,3).map(row=>row.slice(0,3).map(x=>String(x??'').slice(0,22)).join(' / ')).join('  ·  ');
  byId('runButton').disabled=!(state.A && state.B);
}
async function readFile(file){
  if(!file)throw new Error('파일을 선택하세요.');
  if(file.size>8*1024*1024)throw new Error('파일은 최대 8MB까지 가능합니다.');
  if(/\.xlsx$/i.test(file.name))return (await readXlsxFile(file)).map(row=>row.map(x=>x===null?'':String(x)));
  if(!/\.csv$/i.test(file.name))throw new Error('CSV 또는 XLSX 파일만 지원합니다.');
  const bytes=await file.arrayBuffer();let text;
  try{text=new TextDecoder('utf-8',{fatal:true}).decode(bytes);}
  catch{text=new TextDecoder('euc-kr',{fatal:true}).decode(bytes);}
  return parseCSV(text);
}
function resetSamplesOnUpload(side){if(state.A?.sample||state.B?.sample){state.A=null;state.B=null;for(const s of ['A','B']){byId('map'+s).hidden=true;byId('status'+s).textContent='아직 선택되지 않았습니다.';if(s!==side)byId('file'+s).value='';}state.result=null;byId('results').hidden=true;byId('runButton').disabled=true;}}
for(const side of ['A','B']){
  byId('file'+side).addEventListener('change',async ev=>{
    const file=ev.target.files?.[0];if(!file)return;clearError();resetSamplesOnUpload(side);
    byId('status'+side).textContent='파일을 읽는 중…';
    try{setRows(side,await readFile(file),file.name);}catch(e){state[side]=null;byId('map'+side).hidden=true;byId('status'+side).textContent='파일을 읽지 못했습니다.';byId('runButton').disabled=true;showError(e.message||'알 수 없는 오류');}
  });
  for(const id of ['key','amount'])byId(id+side).addEventListener('change',()=>{state.result=null;byId('results').hidden=true;});
}
const samples={
 A:[['주문번호','정산예정액'],['A1001','59000'],['A1002','33000'],['A1003','26000'],['A1004','50000'],['A1005','10000']],
 B:[['주문번호','입금액'],['A1001','59000'],['A1002','32000'],['A1004','30000'],['A1004','20000'],['A1005','10000'],['A1999','40000']]
};
byId('sampleButton').addEventListener('click',()=>{signal('demo');clearError();setRows('A',samples.A,'예시 매출 자료',true);setRows('B',samples.B,'예시 입금 자료',true);byId('workspace').scrollIntoView({behavior:'smooth',block:'start'});});
const statusLabel={matched:'일치',different:'금액 차이',onlyA:'A만 존재',onlyB:'B만 존재'};
function renderRows(){
  const result=state.result;if(!result)return;
  const mode=byId('filter').value;const all=result.rows;
  const selected=all.filter(x=>mode==='all'||(mode==='matched'?x.status==='matched':x.status!=='matched'));
  byId('resultHint').textContent=selected.length+' / '+all.length+'건 표시 (최대 200건)';
  const tbody=byId('resultRows');tbody.replaceChildren();
  if(!selected.length){const tr=document.createElement('tr');const td=document.createElement('td');td.colSpan=5;td.className='empty';td.textContent='이 조건에 해당하는 항목이 없습니다.';tr.append(td);tbody.append(tr);return;}
  for(const r of selected.slice(0,200)){
    const tr=document.createElement('tr'),values=[statusLabel[r.status],r.key,formatMoney(r.a),formatMoney(r.b),formatMoney(r.difference)];
    values.forEach((value,i)=>{const td=document.createElement('td');td.textContent=value;if(i===0){const badge=document.createElement('span');badge.className='status '+r.status;badge.textContent=value;td.textContent='';td.append(badge);}if(i>=2)td.className='num';tr.append(td);});tbody.append(tr);
  }
}
byId('filter').addEventListener('change',renderRows);
byId('runButton').addEventListener('click',()=>{
  clearError();try{
    const {A,B}=state;if(!A||!B)throw new Error('비교할 두 파일을 선택하세요.');
    const ak=Number(byId('keyA').value),am=Number(byId('amountA').value),bk=Number(byId('keyB').value),bm=Number(byId('amountB').value);
    if(ak===am||bk===bm)throw new Error('거래번호 열과 금액 열은 서로 달라야 합니다.');
    const result=reconcile(A.rows,B.rows,ak,am,bk,bm);state.result=result;
    for(const [id,key] of [['matchedCount','matched'],['differentCount','different'],['onlyACount','onlyA'],['onlyBCount','onlyB']])byId(id).textContent=result.counts[key].toLocaleString('ko-KR');
    byId('totalCount').textContent=result.rows.length.toLocaleString('ko-KR')+'건';
    byId('sumInfo').textContent='A 합계 '+formatMoney(result.sumA)+'원 · B 합계 '+formatMoney(result.sumB)+'원 · 총 차이 '+formatMoney(result.sumA-result.sumB)+'원 · 중복 거래번호 A '+result.duplicatesA+'건 / B '+result.duplicatesB+'건';
    byId('results').hidden=false;renderRows();signal(A.sample||B.sample?'analysis-demo':'analysis-real');byId('results').scrollIntoView({behavior:'smooth'});
  }catch(e){showError(e.message||'분석 오류');}
});
byId('downloadButton').addEventListener('click',()=>{if(!state.result)return;signal('download');const blob=new Blob([csvReport(state.result)],{type:'text/csv;charset=utf-8'});const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download='맞춰봄_대사결과_'+new Date().toISOString().slice(0,10)+'.csv';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);});

byId('feedbackLink').addEventListener('click',()=>signal('feedback-link'));
for(const [id,kind,message] of [
  ['voteHelpful','helpful','고마워요. 도움이 됐다는 응답을 받았습니다. 자세한 의견은 후기 링크로 남겨주세요.'],
  ['voteNotFit','notfit','알려줘서 고마워요. 아직 맞지 않는 파일 형식이 있군요. 후기에 필요한 형식을 알려주세요.']
]){
  byId(id).addEventListener('click',()=>{
    if(state.voted)return;
    state.voted=true;signal(kind);byId('voteHelpful').disabled=true;byId('voteNotFit').disabled=true;
    byId('voteMessage').textContent=message;
  });
}

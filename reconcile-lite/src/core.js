/** Deterministic, browser-independent reconciliation. Amounts are integer hundredths. */
export function parseAmount(input) {
  let s = String(input ?? '').trim();
  if (!s) throw new Error('금액이 비어 있습니다');
  let negative = false;
  if (/^\(.*\)$/.test(s)) { negative = true; s = s.slice(1, -1).trim(); }
  s = s.replace(/^KRW\s*/i, '').replace(/[₩￦원\s]/g, '');
  if (!/^[+-]?(?:\d+|\d{1,3}(?:,\d{3})+)(?:\.\d{1,2})?$/.test(s)) throw new Error('올바르지 않은 금액');
  const sign = s.startsWith('-') ? -1 : 1;
  const stripped = s.replace(/^[+-]/, '').replace(/,/g, '');
  const [whole, frac=''] = stripped.split('.');
  const cents = Number(whole) * 100 + Number(frac.padEnd(2, '0'));
  if (!Number.isSafeInteger(cents)) throw new Error('금액 범위 초과');
  return cents * (negative ? -1 : 1) * sign;
}
export function parseCSV(text) {
  const clean = String(text).replace(/^\uFEFF/, '');
  const head = clean.split(/\r?\n/)[0] || '';
  const delimiters = [',','\t',';'];
  const score = d => { let quoted=false,n=0;for(let i=0;i<head.length;i++){if(head[i]==='"'){if(quoted && head[i+1]==='"')i++;else quoted=!quoted;} else if(!quoted && head[i]===d)n++;}return n;};
  const delimiter = delimiters.sort((a,b)=>score(b)-score(a))[0];
  const rows=[];let row=[],cell='',quoted=false;
  for(let i=0;i<clean.length;i++){
    const c=clean[i];
    if(c==='"'){if(quoted && clean[i+1]==='"'){cell+='"';i++;}else quoted=!quoted;}
    else if(c===delimiter && !quoted){row.push(cell);cell='';}
    else if((c==='\n'||c==='\r') && !quoted){if(c==='\r' && clean[i+1]==='\n')i++;row.push(cell);if(row.some(x=>String(x).trim()!==''))rows.push(row);row=[];cell='';}
    else cell+=c;
  }
  if(quoted)throw new Error('CSV 따옴표가 닫히지 않았습니다');
  row.push(cell);if(row.some(x=>String(x).trim()!==''))rows.push(row);
  return rows;
}
export function reconcile(aRows,bRows,aKey,aAmount,bKey,bAmount){
  function aggregate(rows,k,m,side){
    if(!Array.isArray(rows) || rows.length < 2)throw new Error(side+' 파일에 데이터 행이 없습니다');
    const map=new Map(), errors=[];
    for(let i=1;i<rows.length;i++){
      const row=rows[i]||[];
      const key=String(row[k] ?? '').trim();
      const raw=row[m];
      if(!key && String(raw ?? '').trim()==='')continue;
      if(!key){errors.push(side+' '+(i+1)+'행: 거래번호 없음');continue;}
      let amount;
      try{amount=parseAmount(raw);}catch(e){errors.push(side+' '+(i+1)+'행: '+e.message);continue;}
      const existing=map.get(key) || {sum:0,count:0};
      if(!Number.isSafeInteger(existing.sum+amount)){errors.push(side+' '+(i+1)+'행: 합계 범위 초과');continue;}
      existing.sum+=amount;existing.count++;map.set(key,existing);
    }
    if(errors.length)throw new Error(errors.slice(0,5).join(' / ')+(errors.length>5?' 외 '+(errors.length-5)+'건':''));
    return map;
  }
  const a=aggregate(aRows,aKey,aAmount,'A'),b=aggregate(bRows,bKey,bAmount,'B');
  const keys=new Set([...a.keys(),...b.keys()]);
  const result=[];const counts={matched:0,different:0,onlyA:0,onlyB:0};
  let sumA=0,sumB=0;let duplicatesA=0,duplicatesB=0;
  for(const obj of a.values()){sumA+=obj.sum;if(obj.count>1)duplicatesA++;}
  for(const obj of b.values()){sumB+=obj.sum;if(obj.count>1)duplicatesB++;}
  if(!Number.isSafeInteger(sumA)||!Number.isSafeInteger(sumB))throw new Error('전체 금액 합계 범위 초과');
  for(const key of keys){
    const aVal=a.get(key),bVal=b.get(key);
    const status=!aVal?'onlyB':!bVal?'onlyA':aVal.sum!==bVal.sum?'different':'matched';
    counts[status]++;
    result.push({key,status,a:aVal?.sum??null,b:bVal?.sum??null,difference:(aVal?.sum??0)-(bVal?.sum??0),aCount:aVal?.count??0,bCount:bVal?.count??0});
  }
  const order={different:0,onlyA:1,onlyB:2,matched:3};
  result.sort((x,y)=>order[x.status]-order[y.status]||x.key.localeCompare(y.key,'ko'));
  return {rows:result,counts,sumA,sumB,duplicatesA,duplicatesB};
}
export const formatMoney=cents=>cents===null?'—':new Intl.NumberFormat('ko-KR',{minimumFractionDigits:cents%100===0?0:2,maximumFractionDigits:2}).format(cents/100);
export function csvReport(result){
  const escape = x=>{let s=String(x??'');if(/^[=+\-@\t\r]/.test(s))s="'"+s;return '"'+s.replace(/"/g,'""')+'"';};
  const header=['상태','거래번호','A 금액(원)','B 금액(원)','차이(A-B)','A 행수','B 행수'];
  const labels={different:'금액 차이',onlyA:'A에만 존재',onlyB:'B에만 존재',matched:'일치'};
  const lines=[header,...result.rows.map(r=>[labels[r.status],r.key,r.a===null?'':r.a/100,r.b===null?'':r.b/100,r.difference/100,r.aCount,r.bCount])];
  return '\uFEFF'+lines.map(r=>r.map(escape).join(',')).join('\r\n');
}

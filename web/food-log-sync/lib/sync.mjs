export const empty = () => ({recipes:{},foods:{},entries:{}});
export const equal = (a,b) => canonical(a)===canonical(b);
function canonical(v) { return v && typeof v==='object' ? '{'+Object.keys(v).sort().map(k=>JSON.stringify(k)+':'+canonical(v[k])).join(',')+'}' : JSON.stringify(v); }
export function merge(base,local,remote) {
  const result=empty(), conflicts=[];
  for(const table of Object.keys(result)) for(const id of new Set([...Object.keys(base[table]),...Object.keys(local[table]),...Object.keys(remote[table])])) {
    const b=base[table][id],l=local[table][id],r=remote[table][id];
    let value;
    if(equal(l,b))value=r;else if(equal(r,b)||equal(l,r))value=l;
    else {conflicts.push({table,id,local:l,remote:r});value=l;}
    if(value!==undefined)result[table][id]=value;
  }
  const mark=(table,id)=>{
    if(equal(local[table][id],remote[table][id])||conflicts.some(c=>c.table===table&&c.id===id))return;
    conflicts.push({table,id,local:local[table][id],remote:remote[table][id]});
    if(Object.hasOwn(local[table],id))result[table][id]=local[table][id];else delete result[table][id];
  };
  for(const [deleting,editing] of [[local,remote],[remote,local]]) {
    for(const [id,f] of Object.entries(base.foods)) if(f.active&&deleting.foods[id]?.active===false) {
      for(const [eid,e] of Object.entries(editing.entries))if(e.foodId===id&&!equal(e,base.entries[eid])){mark('foods',id);mark('entries',eid);if(f.recipeId&&base.recipes[f.recipeId]?.active&&deleting.recipes[f.recipeId]?.active===false)mark('recipes',f.recipeId);}
    }
    for(const [rid,r] of Object.entries(base.recipes))if(r.active&&deleting.recipes[rid]?.active===false) {
      for(const [fid,f] of Object.entries(editing.foods))if(f.recipeId===rid&&!equal(f,base.foods[fid])){mark('recipes',rid);mark('foods',fid);for(const [eid,e] of Object.entries(editing.entries))if(e.foodId===fid)mark('entries',eid);}
    }
  }
  return {snapshot:result,conflicts};
}
export function validate(s) {
  if(!s||typeof s!=='object'||Array.isArray(s))return false;
  const uuid=/^[a-zA-Z0-9-]{1,80}$/;
  const name=v=>typeof v==='string'&&v.trim().length>0&&v.length<=100;
  for(const k of ['recipes','foods','entries'])if(!s[k]||typeof s[k]!=='object'||Array.isArray(s[k])||Object.keys(s[k]).length>50000)return false;
  if(Object.keys(s).length!==3)return false;
  for(const [id,r] of Object.entries(s.recipes))if(!uuid.test(id)||!r||!name(r.name)||typeof r.active!=='boolean'||Object.keys(r).length!==2)return false;
  for(const [id,f] of Object.entries(s.foods))if(!uuid.test(id)||!f||!name(f.name)||typeof f.active!=='boolean'||typeof f.recipeId!=='string'||(f.recipeId&&!Object.hasOwn(s.recipes,f.recipeId))||Object.keys(f).length!==3)return false;
  for(const [id,e] of Object.entries(s.entries))if(!e||!uuid.test(e.foodId)||!Object.hasOwn(s.foods,e.foodId)||!/^\d{4}-\d{2}-\d{2}$/.test(e.date)||!Number.isFinite(Date.parse(e.date+'T12:00:00Z'))||new Date(e.date+'T12:00:00Z').toISOString().slice(0,10)!==e.date||id!==e.foodId+'@'+e.date||!name(e.name)||typeof e.grams!=='string'||!/^\d+(\.\d{1,3})?$/.test(e.grams)||+e.grams<=0||+e.grams>1000000||Object.keys(e).length!==4)return false;
  return true;
}
export function grams(value) {
  const s=value.trim().replace(',','.');if(!s)return '';
  if(!/^\d+(\.\d{0,3})?$/.test(s)||+s<=0||+s>1000000)throw Error('Enter grams above 0 and up to 1,000,000 (max. 3 decimals).');
  return String(Number(s));
}
export function addGrams(current,extra) {
  const e=grams(extra);if(!e)throw Error('Enter grams to add.');
  return grams(String((Math.round(Number(grams(current)||0)*1000)+Math.round(Number(e)*1000))/1000));
}
export function csv(s,date) {
  const cell=v=>'"'+(/^[=+@-]/.test(v.trimStart())?"'":'')+v.replaceAll('"','""')+'"';
  const lines=['Date,Recipe,Food,Grams'];
  for(const e of Object.values(s.entries).filter(e=>e.date===date)) {
    const f=s.foods[e.foodId];lines.push([cell(date),cell(f?.recipeId?s.recipes[f.recipeId]?.name||'':''),cell(e.name),e.grams].join(','));
  }
  return lines.join('\r\n')+'\r\n';
}

import { env } from 'cloudflare:workers';
import { validate } from '../../../lib/sync.mjs';
const response=(body:unknown,status=200)=>Response.json(body,{status,headers:{'Cache-Control':'no-store'}});
function owner(request:Request) {return request.headers.get('oai-authenticated-user-id');}
export async function GET(request:Request) {
  const user=owner(request);if(!user)return response({error:'Sign in required'},401);
  const row=await env.DB!.prepare('SELECT revision,snapshot FROM logs WHERE owner=?').bind(user).first<{revision:number,snapshot:string}>();
  return response({owner:user,revision:row?.revision||0,snapshot:row?JSON.parse(row.snapshot):{recipes:{},foods:{},entries:{}}});
}
export async function POST(request:Request) {
  const user=owner(request);if(!user)return response({error:'Sign in required'},401);
  const origin=request.headers.get('origin');if(origin&&origin!==new URL(request.url).origin)return response({error:'Invalid origin'},403);
  if(request.headers.get('content-type')?.split(';')[0]!=='application/json')return response({error:'JSON required'},415);
  const text=await request.text();if(text.length>4000000)return response({error:'Log is too large'},413);
  let body;try {body=JSON.parse(text);if(!Number.isSafeInteger(body.revision)||body.revision<0||!validate(body.snapshot))throw Error();}catch{return response({error:'Invalid log'},400);}
  const snapshot=JSON.stringify(body.snapshot);
  // Single-statement compare-and-swap: stale writers cannot overwrite a newer version.
  const changed=body.revision===0
    ? await env.DB!.prepare('INSERT INTO logs(owner,revision,snapshot) VALUES(?,1,?) ON CONFLICT(owner) DO NOTHING').bind(user,snapshot).run()
    : await env.DB!.prepare('UPDATE logs SET snapshot=?,revision=revision+1 WHERE owner=? AND revision=?').bind(snapshot,user,body.revision).run();
  if(!changed.meta.changes)return response({error:'Another device saved changes. Sync again.'},409);
  return response({owner:user,revision:body.revision+1,snapshot:body.snapshot});
}

import { DurableObject } from 'cloudflare:workers';
const OPERATIONS = new Set(['status', 'scripts.list', 'scripts.read', 'scripts.validate', 'scripts.apply', 'scripts.test']);
const LIMIT = 65536;
function json(value, status = 200) {
  return new Response(JSON.stringify(value), {status, headers: {'content-type':'application/json', 'cache-control':'no-store', 'x-content-type-options':'nosniff'}});
}
function fail(status, message) { return json({error: message}, status); }
function secret() { return Array.from(crypto.getRandomValues(new Uint8Array(32)), b => b.toString(16).padStart(2,'0')).join(''); }
async function hash(value) { return Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value))), b=>b.toString(16).padStart(2,'0')).join(''); }
async function body(request) {
  const reader = request.body?.getReader();
  if (!reader) return {};
  let size = 0; const chunks = [];
  while (true) { const {done,value} = await reader.read(); if(done) break; size += value.length; if(size > LIMIT) { await reader.cancel(); throw new Error('body_limit'); } chunks.push(value); }
  const bytes = new Uint8Array(size); let offset=0; for(const chunk of chunks) { bytes.set(chunk,offset); offset+=chunk.length; }
  const value = JSON.parse(new TextDecoder().decode(bytes) || '{}');
  if (!value || Array.isArray(value) || typeof value !== 'object') throw new Error('invalid_body');
  return value;
}
export default {
 async fetch(request, env) {
  const url = new URL(request.url);
  if(url.protocol !== 'https:' && !(env.ALLOW_LOCAL_HTTP === 'true' && ['localhost','127.0.0.1','[::1]'].includes(url.hostname))) return fail(400,'https_required');
  if(request.method !== 'POST') return fail(405,'post_required');
  try {
   if(url.pathname === '/v1/sessions') {
    if(!env.RELAY_REGISTRATION_TOKEN || request.headers.get('authorization') !== `Bearer ${env.RELAY_REGISTRATION_TOKEN}`) return fail(401,'enrollment_required');
    const input = await body(request);
    const id = crypto.randomUUID();
    return env.SESSIONS.get(env.SESSIONS.idFromName(id)).fetch(new Request(`https://internal/init`, {method:'POST',body:JSON.stringify({...input,sessionId:id})}));
   }
   if(url.pathname === '/v1/pair/claim') {
    const input = await body(request); const id = typeof input.code === 'string' ? input.code.split('.')[0] : '';
    if(!/^[0-9a-f-]{36}$/.test(id)) return fail(401,'invalid_pairing');
    return env.SESSIONS.get(env.SESSIONS.idFromName(id)).fetch(new Request('https://internal/claim',{method:'POST',body:JSON.stringify(input)}));
   }
   const match = /^\/v1\/sessions\/([0-9a-f-]{36})\/(poll|respond|requests|results|revoke)$/.exec(url.pathname);
   if(!match) return fail(404,'not_found');
   return env.SESSIONS.get(env.SESSIONS.idFromName(match[1])).fetch(new Request(`https://internal/${match[2]}`,request));
  } catch { return fail(400,'invalid_request'); }
 }
};

export class RelaySession extends DurableObject {
 constructor(ctx, env) { super(ctx, env); this.ctx=ctx; this.storage=ctx.storage; }
 async alarm() { await this.storage.deleteAll(); }
 async fetch(request) {
  // Serialize read-modify-write across awaits, including token digests.
  return this.ctx.blockConcurrencyWhile(async () => {
   try { return await this.handle(request); } catch { return fail(400,'invalid_request'); }
  });
 }
 async handle(request) {
  const action = new URL(request.url).pathname.slice(1); const now=Date.now();
  let state = await this.storage.get('state'); const input=await body(request);
  if(action === 'init') {
   if(state) return fail(409,'already_initialized');
   if(typeof input.serverId !== 'string' || !/^[A-Za-z0-9_-]{1,64}$/.test(input.serverId) || !Array.isArray(input.scopes) || !input.scopes.length || input.scopes.some(x=>!OPERATIONS.has(x))) return fail(400,'invalid_grants');
   const serverToken=secret(), code=secret(); const ttl=Number.isInteger(input.ttlSeconds) ? input.ttlSeconds : 86400;
   if(ttl < 60 || ttl > 86400) return fail(400,'invalid_expiry');
   state={sessionId:input.sessionId,serverId:input.serverId,scopes:[...new Set(input.scopes)],serverHash:await hash(serverToken),pairHash:await hash(code),expiresAt:now+ttl*1000,pairExpiresAt:now+300000,pairAttempts:0,revoked:false,queue:[],responses:{},requests:{},count:0};
   await this.storage.put('state',state); await this.storage.setAlarm(state.expiresAt);
   return json({sessionId:state.sessionId,serverToken,pairingCode:`${state.sessionId}.${code}`,expiresAt:state.expiresAt,pairExpiresAt:state.pairExpiresAt},201);
  }
  if(!state || state.revoked || now >= state.expiresAt) return fail(401,'session_expired_or_revoked');
  if(action === 'claim') {
   if(state.clientHash || now >= state.pairExpiresAt || state.pairAttempts >= 5) return fail(401,'invalid_pairing');
   state.pairAttempts++;
   const parts=typeof input.code==='string' ? input.code.split('.') : [];
   if(parts.length!==2 || parts[0]!==state.sessionId || await hash(parts[1])!==state.pairHash) { await this.storage.put('state',state); return fail(401,'invalid_pairing'); }
   const token=secret(); state.clientHash=await hash(token); delete state.pairHash;
   await this.storage.put('state',state);
   return json({sessionId:state.sessionId,serverId:state.serverId,token,scopes:state.scopes,expiresAt:state.expiresAt});
  }
  const auth=request.headers.get('authorization') || '';
  if(!/^Bearer [0-9a-f]{64}$/.test(auth)) return fail(401,'unauthorized');
  const digest=await hash(auth.slice(7)); const server=digest===state.serverHash; const client=digest===state.clientHash;
  if(!server && !client) return fail(401,'unauthorized');
  if(action==='revoke') { await this.storage.deleteAll(); return json({revoked:true}); }
  if((action==='poll'||action==='respond') && !server || (action==='requests'||action==='results') && !client) return fail(403,'wrong_endpoint');
  let expired=false, requestedExpired=false;
  for(const [id,createdAt] of Object.entries(state.requests)) {
   if(now-createdAt > 120000) { if(id===input.id) requestedExpired=true; delete state.requests[id]; delete state.responses[id]; state.queue=state.queue.filter(x=>x.id!==id); expired=true; }
  }
  if(expired) await this.storage.put('state',state);
  if(requestedExpired && action==='results') return json({id:input.id,error:{code:'timeout',message:'Connector did not respond within two minutes.'}});
  // Hard per-session budget bounds storage and abuse; ordinary idle polling never writes.
  if(action==='poll') {
   const requests=state.queue.splice(0,1);
   if(requests.length) await this.storage.put('state',state);
   return json({requests,pollAfterMs:5000});
  }
  if(action==='requests') {
   if(!OPERATIONS.has(input.operation) || !state.scopes.includes(input.operation)) return fail(403,'operation_denied');
   if(!input.arguments || typeof input.arguments!=='object' || Array.isArray(input.arguments)) return fail(400,'invalid_arguments');
   if(Object.keys(state.requests).length >= 1) return fail(429,'request_pending');
   if(state.count >= 1000) return fail(429,'session_budget_exhausted');
   const id=crypto.randomUUID(); state.count++; state.requests[id]=now; state.queue.push({id,operation:input.operation,arguments:input.arguments});
   await this.storage.put('state',state); return json({id},202);
  }
  if(typeof input.id!=='string' || !Object.hasOwn(state.requests,input.id)) return fail(404,'unknown_request');
  if(action==='respond') {
   if(Object.hasOwn(state.responses,input.id)) return fail(409,'already_responded');
   const result={id:input.id};
   if(input.error && typeof input.error.code==='string' && typeof input.error.message==='string') result.error={code:input.error.code.slice(0,64),message:input.error.message.slice(0,1024)};
   else if(Object.hasOwn(input,'result')) result.result=input.result;
   else return fail(400,'invalid_response');
   state.queue=state.queue.filter(x=>x.id!==input.id);
   state.responses[input.id]=result; await this.storage.put('state',state); return json({accepted:true});
  }
  if(action==='results') {
   const result=state.responses[input.id];
   if(result) { delete state.responses[input.id]; delete state.requests[input.id]; await this.storage.put('state',state); return json(result); }
   if(now-state.requests[input.id] > 120000) { state.queue=state.queue.filter(x=>x.id!==input.id); delete state.requests[input.id]; await this.storage.put('state',state); return json({id:input.id,error:{code:'timeout',message:'Connector did not respond within two minutes.'}}); }
   return json({pending:true,pollAfterMs:5000});
  }
  return fail(404,'not_found');
 }
}

import {test,after} from 'node:test';
import assert from 'node:assert/strict';
import {Miniflare,convertV4MiniflareOptions} from 'miniflare';
const mf=new Miniflare(convertV4MiniflareOptions({modules:[{type:'ESModule',path:'src/worker.js'},{type:'ESModule',path:'src/v2.js'},{type:'ESModule',path:'src/pairing-page.js'}],scriptPath:'src/worker.js',compatibilityDate:'2026-11-01',durableObjects:{SESSIONS:{className:'RelaySession',useSQLite:true}},bindings:{RELAY_REGISTRATION_TOKEN:'owner',ALLOW_LOCAL_HTTP:'true'}}));
after(()=>mf.dispose());
async function post(path,value={},token){const r=await mf.dispatchFetch('http://localhost'+path,{method:'POST',headers:token?{authorization:'Bearer '+token}:{},body:JSON.stringify(value)});return {status:r.status,data:await r.json()};}
async function create(){const r=await post('/v1/sessions',{serverId:'private',scopes:['status']},'owner');assert.equal(r.status,201);return r.data;}
test('private pairing, bounded requests, outbound reply, isolation, revocation',async()=>{
 assert.equal((await post('/v1/sessions')).status,401);
 const s=await create(),base='/v1/sessions/'+s.sessionId;
 const c=await post('/v1/pair/claim',{code:s.pairingCode});assert.equal(c.status,200);const token=c.data.token;
 assert.equal((await post('/v1/pair/claim',{code:s.pairingCode})).status,401);
 assert.equal((await post(base+'/poll',{},token)).status,403);
 assert.equal((await post(base+'/requests',{operation:'scripts.apply',arguments:{}},token)).status,403);
 assert.equal((await post(base+'/requests',{operation:'proxy',arguments:{}},token)).status,403);
 const req=await post(base+'/requests',{operation:'status',arguments:{}},token);assert.equal(req.status,202);
 assert.equal((await post(base+'/requests',{operation:'status',arguments:{}},token)).status,429);
 assert.equal((await post(base+'/results',{id:req.data.id},token)).data.pending,true);
 assert.equal((await post(base+'/poll',{},s.serverToken)).data.requests[0].id,req.data.id);
 assert.equal((await post(base+'/poll',{},s.serverToken)).data.requests.length,0);
 assert.equal((await post(base+'/respond',{id:req.data.id,result:{online:true}},s.serverToken)).status,200);
 assert.deepEqual((await post(base+'/results',{id:req.data.id},token)).data.result,{online:true});
 const other=await create();assert.equal((await post('/v1/sessions/'+other.sessionId+'/poll',{},s.serverToken)).status,401);
 assert.equal((await post(base+'/revoke',{},token)).status,200);
 assert.equal((await post(base+'/poll',{},s.serverToken)).status,401);
});
test('unknown grants, expiry bound, pairing attempt bound, payload bound',async()=>{
 assert.equal((await post('/v1/sessions',{serverId:'x',scopes:['shell']},'owner')).status,400);
 assert.equal((await post('/v1/sessions',{serverId:'x',scopes:['status'],ttlSeconds:86401},'owner')).status,400);
 const s=await create();for(let i=0;i<5;i++)assert.equal((await post('/v1/pair/claim',{code:s.sessionId+'.bad'})).status,401);
 assert.equal((await post('/v1/pair/claim',{code:s.pairingCode})).status,401);
 assert.equal((await post('/v1/sessions/'+s.sessionId+'/poll',{text:'x'.repeat(66000)},s.serverToken)).status,400);
});



test('abandoned requests expire without a results poll',async()=>{
 const {readFile}=await import('node:fs/promises');
 const source=(await readFile('src/worker.js','utf8')).replace("import { DurableObject } from 'cloudflare:workers';",'class DurableObject {}').replace("import { handleV2, ServerDirectory } from './v2.js';",'').replace('export { ServerDirectory };','');
 const {RelaySession}=await import('data:text/javascript;base64,'+Buffer.from(source).toString('base64'));
 const rows=new Map();const ctx={blockConcurrencyWhile:fn=>fn(),storage:{get:async key=>structuredClone(rows.get(key)),put:async(k,v)=>rows.set(k,structuredClone(v)),setAlarm:async()=>{},deleteAll:async()=>rows.clear()}};
 const session=new RelaySession(ctx,{});
 const call=async(action,input,token)=>{const r=await session.fetch(new Request('https://internal/'+action,{method:'POST',body:JSON.stringify(input),headers:token?{authorization:'Bearer '+token}:{}}));return {status:r.status,data:await r.json()};};
 const s=(await call('init',{serverId:'x',sessionId:'test',scopes:['status']})).data;
 const c=(await call('claim',{code:s.pairingCode})).data;
 const req=await call('requests',{operation:'status',arguments:{}},c.token);assert.equal(req.status,202);
 const state=rows.get('state');state.requests[req.data.id]=Date.now()-120001;rows.set('state',state);
 assert.deepEqual((await call('poll',{},s.serverToken)).data.requests,[]);
 assert.equal((await call('requests',{operation:'status',arguments:{}},c.token)).status,202);
});

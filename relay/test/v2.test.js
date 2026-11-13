import {test} from 'node:test';
import assert from 'node:assert/strict';
import {randomBytes,randomUUID,createHash} from 'node:crypto';
import {Miniflare,convertV4MiniflareOptions} from 'miniflare';
test('browser enrollment, exact OAuth grant, PKCE, actual client status, revocation',async t=>{
const mf=new Miniflare(convertV4MiniflareOptions({modules:[{type:'ESModule',path:'src/worker.js'},{type:'ESModule',path:'src/v2.js'},{type:'ESModule',path:'src/pairing-page.js'}],compatibilityDate:'2026-11-01',durableObjects:{SESSIONS:{className:'RelaySession',useSQLite:true},DIRECTORY:{className:'ServerDirectory',useSQLite:true}}}));t.after(()=>mf.dispose());
async function post(path,data={},token,cookie){const r=await mf.dispatchFetch('https://relay.test'+path,{method:'POST',headers:{'content-type':'application/json',...(token?{authorization:'Bearer '+token}:{}),...(cookie?{cookie}:{})},body:JSON.stringify(data)});return {status:r.status,data:await r.json(),cookie:r.headers.get('set-cookie')?.split(';')[0]};}
const serverId=randomUUID(),serverToken=randomBytes(32).toString('hex'),base='/v2/servers/'+serverId;
const e=await post('/v2/enrollments',{serverId,serverToken,serverName:'Private Lobby',allowedScriptIds:['lobby'],scopes:['status','scripts.read']});assert.equal(e.status,201,JSON.stringify(e.data));
const code=new URL(e.data.browserUrl).hash.slice(6);const start=await post('/v2/browser/start',{code});assert.equal(start.status,200);const cookie=start.cookie;assert.equal((await post('/v2/browser/info',{},null,cookie)).data.serverName,'Private Lobby');assert.equal((await post('/v2/browser/local',{},null,cookie)).status,404);
assert.equal((await post('/v2/browser/start',{code})).status,401);
assert.equal((await post('/v2/browser/choice',{kind:'codex'},null,cookie)).status,200);
const c=await post('/oauth/register',{redirect_uris:['http://localhost:4123/callback']});assert.equal(c.status,201);
const verifier=randomBytes(48).toString('base64url');const fields={client_id:c.data.client_id,redirect_uri:'http://localhost:4123/callback',response_type:'code',code_challenge_method:'S256',code_challenge:createHash('sha256').update(verifier).digest('base64url'),state:'safe'};
assert.equal((await post('/v2/browser/authorize',{...fields,name:'Codex',allowedScriptIds:['forbidden']},null,cookie)).status,400);
const approve=await post('/v2/browser/authorize',{...fields,name:'Codex',allowedScriptIds:['lobby']},null,cookie);assert.equal(approve.status,200,JSON.stringify(approve.data));const view=approve.cookie;
const authCode=new URL(approve.data.redirect).searchParams.get('code');assert.ok(!approve.data.redirect.includes('token'));
assert.equal((await post('/v2/browser/authorize',{...fields,name:'Replay',allowedScriptIds:['lobby']},null,cookie)).status,401);
assert.equal((await post('/oauth/token',{grant_type:'authorization_code',code:authCode,client_id:fields.client_id,redirect_uri:fields.redirect_uri,code_verifier:'x'.repeat(64)})).status,400);
const tok=await post('/oauth/token',{grant_type:'authorization_code',code:authCode,client_id:fields.client_id,redirect_uri:fields.redirect_uri,code_verifier:verifier});assert.equal(tok.status,200,JSON.stringify(tok.data));const token=tok.data.access_token;
const grants=await post(base+'/poll',{},serverToken);const connectionId=grants.data.grants[0].connectionId;assert.deepEqual(grants.data.grants[0].allowedScriptIds,['lobby']);assert.equal(grants.data.grants[0].enrollmentId,e.data.enrollmentId);
assert.equal((await post('/v2/browser/status',{},null,view)).data.state,'waiting_for_server');
assert.equal((await post(base+'/ack',{connectionId},serverToken)).status,200);
assert.equal((await post('/v2/browser/status',{},null,view)).data.state,'waiting_for_client_status');
assert.equal((await post('/v2/client/requests',{operation:'scripts.read',arguments:{id:'forbidden'}},token)).status,403);
const pending=await post('/v2/client/requests',{operation:'status',arguments:{}},token);assert.equal(pending.status,202);
const poll=await post(base+'/poll',{},serverToken);assert.equal(poll.data.requests[0].id,pending.data.id);
assert.equal((await post(base+'/respond',{connectionId,id:pending.data.id,result:{online:true}},serverToken)).status,200);
assert.equal((await post('/v2/browser/status',{},null,view)).data.state,'connected');
assert.deepEqual((await post('/v2/client/results',{id:pending.data.id},token)).data.result,{online:true});
const tools=await post('/mcp',{jsonrpc:'2.0',id:1,method:'tools/list'},token);assert.equal(tools.data.result.tools[0].name,'aether_server_status');
assert.equal((await post(base+'/revoke',{connectionId},serverToken)).status,200);
assert.equal((await post('/v2/client/connection',{},token)).status,401);
assert.equal((await post('/v2/browser/status',{},null,view)).data.state,'revoked');
});


test('expired credentials and quota data have bounded retention',async()=>{
 const {readFile}=await import('node:fs/promises');
 const source=(await readFile('src/v2.js','utf8')).replace("import {page as renderPage} from './pairing-page.js';",'const renderPage=()=>"";').replace("import {DurableObject} from 'cloudflare:workers';",'class DurableObject {}');
 const {ServerDirectory}=await import('data:text/javascript;base64,'+Buffer.from(source).toString('base64'));
 const now=Date.now(),day=Math.floor(now/86400000);let alarm=null;
 const rows=new Map([['oauth:old',{expiresAt:now-1}],['oauth:live',{expiresAt:now+60000}],['quota:'+(day-2)+':ip',1],['quota:'+day+':ip',1],['client:old',{expiresAt:now-1}],['client:live',{expiresAt:now+60000}],['identity',{grants:[{connectionId:'expired',expiresAt:now-1,hash:'secret',allowedScriptIds:['x'],scopes:['status'],pending:{createdAt:now-1}}]}],['pending:expired',{}]]);
 const ctx={blockConcurrencyWhile:fn=>fn(),storage:{get:async key=>structuredClone(rows.get(key)),put:async(key,value)=>rows.set(key,structuredClone(value)),delete:async key=>rows.delete(key),list:async({prefix})=>new Map([...rows].filter(([key])=>key.startsWith(prefix))),getAlarm:async()=>alarm,setAlarm:async value=>{alarm=value;}}};
 await new ServerDirectory(ctx,{}).alarm();
 assert.equal(rows.has('oauth:old'),false);assert.equal(rows.has('oauth:live'),true);assert.equal(rows.has('quota:'+(day-2)+':ip'),false);assert.equal(rows.has('quota:'+day+':ip'),true);assert.equal(rows.has('client:old'),false);assert.equal(rows.has('client:live'),true);assert.equal(rows.has('pending:expired'),false);assert.equal(rows.get('identity').grants[0].hash,undefined);assert.ok(alarm>now);
});

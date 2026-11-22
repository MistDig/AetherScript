import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {randomUUID,randomBytes,createHash} from 'node:crypto';
const hash=x=>createHash('sha256').update(x).digest('hex');
async function setup(scopes=['scripts.read','scripts.validate','scripts.apply','events.preview'],supportedFormats=['aether','aetherpack']){
 const source=(await readFile('src/v2.js','utf8')).replace("import {page as renderPage} from './pairing-page.js';",'const renderPage=()=>"";').replace("import {DurableObject} from 'cloudflare:workers';",'class DurableObject {}');
 const {ServerDirectory}=await import('data:text/javascript;base64,'+Buffer.from(source).toString('base64'));
 const rows=new Map(),storage={get:async k=>structuredClone(rows.get(k)),put:async(k,v)=>rows.set(k,structuredClone(v)),delete:async k=>rows.delete(k),list:async({prefix})=>new Map([...rows].filter(([k])=>k.startsWith(prefix))),getAlarm:async()=>1,setAlarm:async()=>{}};
 const directory=new ServerDirectory({storage,blockConcurrencyWhile:fn=>fn()},{}),token=randomBytes(32).toString('hex'),serverId=randomUUID();
 const call=async(action,v={},auth=token)=>{const r=await directory.fetch(new Request('https://internal/'+action,{method:'POST',headers:{authorization:'Bearer '+auth},body:JSON.stringify(v)}));return {status:r.status,data:await r.json()};};
 const e=await call('enroll',{serverId,allowedScriptIds:['arena'],scopes,supportedFormats});assert.equal(e.status,201);
 const start=await call('browser-start',{code:e.data.code}),browser=start.data.token;
 await call('browser-choice',{kind:'codex'},browser);
 assert.equal((await call('browser-authorize',{kind:'codex',name:'Test',allowedScriptIds:['arena'],client_id:'client',redirect_uri:'http://localhost/cb',code_challenge:'test'},browser)).status,200);
 const state=rows.get('identity'),grant=state.grants[0],clientToken=randomBytes(32).toString('hex');
 grant.hash=hash(clientToken);grant.active=true;grant.tokenIssuedAt=Date.now();rows.set('identity',state);
 const request=(operation,args)=>call('client-requests',{connectionId:grant.connectionId,operation,arguments:args},clientToken);
 const drain=()=>{const current=rows.get('identity');delete current.grants[0].pending;rows.set('identity',current);rows.delete('pending:'+grant.connectionId);};
 return {request,drain,rows,call,grant,clientToken};
}
const snapshot={kind:'entity_damage',fields:{player_name:'Owner',entity_type:'ZOMBIE',x:1,damage_milli:1000}};
test('packed source format routes through read validate apply and keeps owner and UTF8 limits',async()=>{
 const {request,drain,rows,grant}=await setup();
 for(const operation of ['scripts.read','scripts.validate','scripts.apply']){
  const args={id:'arena',format:'aetherpack',...(operation==='scripts.read'?{}:{source:'{}',...(operation==='scripts.apply'?{expectedHash:'ABSENT'}:{})})};
  assert.equal((await request(operation,args)).status,202);assert.deepEqual(rows.get('pending:'+grant.connectionId).arguments,args);drain();
 }
 assert.equal((await request('scripts.read',{id:'arena',format:'yaml'})).status,400);
 assert.equal((await request('scripts.validate',{id:'arena',source:'a'.repeat(16001)})).status,400);
 assert.equal((await request('scripts.validate',{id:'arena',format:'aetherpack',source:{}})).status,400);
 assert.equal((await request('scripts.apply',{id:'arena',source:'{}',expectedHash:'bad'})).status,400);
 assert.equal((await request('scripts.read',{id:'other',format:'aetherpack'})).status,403);
 assert.equal((await request('scripts.validate',{id:'arena',format:'aetherpack',source:'漢'.repeat(15000)})).status,413);
 assert.equal((await request('scripts.read',{id:'arena'})).status,202);
});
test('legacy server refuses packed format immediately and grants do not gain preview',async()=>{
 const {request,rows}=await setup(['scripts.read'],['aether']);
 const state=rows.get('identity');delete state.supportedFormats;rows.set('identity',state);
 const rejected=await request('scripts.read',{id:'arena',format:'aetherpack'});
 assert.equal(rejected.status,409);assert.equal(rejected.data.error,'server_format_unsupported');assert.match(rejected.data.message,/Upgrade Paper/);
 assert.equal((await request('scripts.read',{id:'arena'})).status,202);
 const old=await setup(['scripts.test']);assert.equal((await old.request('events.preview',{id:'arena',event:snapshot})).status,403);
});
test('preview requires exact scope and script ID and rejects non-scalar or oversized snapshots',async()=>{
 const {request,drain}=await setup();
 assert.equal((await request('events.preview',{id:'arena',event:snapshot})).status,202);drain();
 assert.equal((await request('events.preview',{id:'other',event:snapshot})).status,403);
 for(const event of [{kind:'unknown',fields:{}},{kind:'join',fields:{player_name:[]}}, {kind:'join',fields:{x:1.5}}, {kind:'join',fields:{x:Number.MAX_SAFE_INTEGER+1}}, {kind:'join',fields:{player_name:'a'.repeat(2049)}}])assert.equal((await request('events.preview',{id:'arena',event})).status,400);
 assert.equal((await request('events.preview',{id:'arena',event:snapshot,live:true})).status,400);
});
test('hosted MCP advertises optional formats and a separate read-only preview tool only for its scope',async()=>{
 const source=(await readFile('src/v2.js','utf8')).replace("import {page as renderPage} from './pairing-page.js';",'const renderPage=()=>"";').replace("import {DurableObject} from 'cloudflare:workers';",'class DurableObject {}');
 const {handleV2}=await import('data:text/javascript;base64,'+Buffer.from(source).toString('base64'));
 let scopes=['scripts.read','scripts.validate','scripts.apply','events.preview'];
 const env={DIRECTORY:{idFromName:id=>id,get:()=>({fetch:async()=>new Response(JSON.stringify({active:true,scopes}))})}};
 const getTools=async()=>{
  const response=await handleV2(new Request('https://relay.test/mcp',{method:'POST',headers:{authorization:'Bearer '+randomUUID()+'.'+randomUUID()+'.'+randomBytes(32).toString('hex')},body:JSON.stringify({jsonrpc:'2.0',id:1,method:'tools/list'})}),env);
  return (await response.json()).result.tools;
 };
 const tools=await getTools(),preview=tools.find(tool=>tool.name==='aether_events_preview');
 assert.ok(preview);assert.deepEqual(preview.inputSchema.required,['id','event']);assert.equal(preview.annotations.readOnlyHint,true);assert.equal(preview.annotations.destructiveHint,false);
 for(const tool of tools.filter(tool=>tool.name.startsWith('aether_scripts_'))){assert.deepEqual(tool.inputSchema.properties.format.enum,['aether','aetherpack']);assert.ok(!tool.inputSchema.required.includes('format'));}
 scopes=['scripts.test'];assert.ok(!(await getTools()).some(tool=>tool.name==='aether_events_preview'));
});

import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {randomUUID,randomBytes,createHash} from 'node:crypto';
const hash=x=>createHash('sha256').update(x).digest('hex');
async function setup(scopes=['docs.read','files.list','files.config.read','files.config.preview','files.jar.stage'],supportedFormats=['aether','aetherpack']){
 const source=(await readFile('src/v2.js','utf8')).replace("import {page as renderPage} from './pairing-page.js';",'const renderPage=()=>"";').replace("import {DurableObject} from 'cloudflare:workers';",'class DurableObject {}');
 const {ServerDirectory,handleV2}=await import('data:text/javascript;base64,'+Buffer.from(source).toString('base64'));
 const rows=new Map(),storage={get:async k=>structuredClone(rows.get(k)),put:async(k,v)=>rows.set(k,structuredClone(v)),delete:async k=>rows.delete(k),list:async({prefix})=>new Map([...rows].filter(([k])=>k.startsWith(prefix))),getAlarm:async()=>1,setAlarm:async()=>{}};
 const directory=new ServerDirectory({storage,blockConcurrencyWhile:fn=>fn()},{}),token=randomBytes(32).toString('hex'),serverId=randomUUID();
 const call=async(action,v={},auth=token)=>{const r=await directory.fetch(new Request('https://internal/'+action,{method:'POST',headers:{authorization:'Bearer '+auth},body:JSON.stringify(v)}));return {status:r.status,data:await r.json()};};
 const e=await call('enroll',{serverId,allowedScriptIds:['arena'],scopes,supportedFormats});assert.equal(e.status,201);
 const start=await call('browser-start',{code:e.data.code}),browser=start.data.token;
 await call('browser-choice',{kind:'codex'},browser);
 assert.equal((await call('browser-authorize',{kind:'codex',name:'Test',approveFiles:true,allowedScriptIds:['arena'],client_id:'client',redirect_uri:'http://localhost/cb',code_challenge:'test'},browser)).status,200);
 const state=rows.get('identity'),grant=state.grants[0],clientToken=randomBytes(32).toString('hex');
 grant.hash=hash(clientToken);grant.active=true;grant.tokenIssuedAt=Date.now();rows.set('identity',state);
 const request=(operation,args)=>call('client-requests',{connectionId:grant.connectionId,operation,arguments:args},clientToken);
 const drain=()=>{const current=rows.get('identity');delete current.grants[0].pending;rows.set('identity',current);rows.delete('pending:'+grant.connectionId);};
 return {request,drain,rows,call,grant,clientToken,handleV2};
}

test('docs and files strictly validate bounded owner target arguments',async()=>{
 const {request,drain,rows,grant}=await setup(['docs.read','files.list','files.config.read','files.config.preview','files.jar.stage']);
 for(const [op,args] of [['docs.read',{section:'reference:2',version:'0.0.30'}],['files.list',{}],['files.config.read',{target:'server-properties'}],['files.config.preview',{target:'server-properties',expectedHash:'a'.repeat(64),replacements:{motd:'Hello',enabled:true}}],['files.jar.stage',{target:'plugin-main',sha256:'a'.repeat(64),filename:'plugin.jar'}]]){
  assert.equal((await request(op,args)).status,202,op);assert.deepEqual(rows.get('pending:'+grant.connectionId).arguments,args);drain();
 }
 for(const [op,args] of [['docs.read',{section:'../secret'}],['files.config.read',{target:'../secret'}],['files.config.read',{target:'safe',id:'arena'}],['files.config.preview',{target:'safe',expectedHash:'a'.repeat(64),replacements:{nested:{bad:true}}}],['files.jar.stage',{target:'safe',sha256:'a'.repeat(64),url:'https://example.test/a.jar'}],['files.jar.stage',{target:'safe',sha256:'a'.repeat(64),filename:'a.jar',base64:'UEs='}]])assert.equal((await request(op,args)).status,400,op);
});
test('old grants require explicit scope negotiation',async()=>{
 const {request}=await setup(['status']);
 assert.equal((await request('docs.read',{section:'quickstart'})).data.error,'server_operation_unsupported');
 assert.equal((await request('files.list',{})).data.error,'server_operation_unsupported');
});

test('MCP resource reads preserve backend version',async()=>{
 const {handleV2,grant,clientToken}=await setup();let requested;
 const env={DIRECTORY:{idFromName:x=>x,get:()=>({fetch:async req=>{
  const action=new URL(req.url).pathname.slice(1),input=await req.json();
  if(action==='client-connection')return Response.json({...grant,active:true});
  if(action==='client-requests'){requested=input;return Response.json({id:'request'});}
  return Response.json({result:{section:'quickstart',version:'0.0.30',text:'# Owner docs'}});
 }})}};
 const rpc=async(method,params={})=>(await handleV2(new Request('https://relay.test/mcp',{method:'POST',headers:{authorization:'Bearer '+crypto.randomUUID()+'.'+grant.connectionId+'.'+clientToken},body:JSON.stringify({jsonrpc:'2.0',id:1,method,params})}),env)).json();
 const listed=await rpc('resources/list');assert.equal(listed.result.resources[0].uri,'aetherscript://docs/quickstart');
 const result=await rpc('resources/read',{uri:'aetherscript://docs/quickstart'});
 assert.equal(requested.operation,'docs.read');assert.deepEqual(requested.arguments,{section:'quickstart'});
 assert.equal(JSON.parse(result.result.contents[0].text).version,'0.0.30');
 const tools=(await rpc('tools/list')).result.tools;assert.equal(tools.find(t=>t.name==='aether_files_config_preview').inputSchema.properties.replacements.type,'object');
});

import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {randomUUID,randomBytes,createHash} from 'node:crypto';
const hash=x=>createHash('sha256').update(x).digest('hex');
async function setup(){
 const source=(await readFile('src/v2.js','utf8')).replace("import {page as renderPage} from './pairing-page.js';",'const renderPage=()=>"";').replace("import {DurableObject} from 'cloudflare:workers';",'class DurableObject {}');
 const {ServerDirectory}=await import('data:text/javascript;base64,'+Buffer.from(source).toString('base64'));
 const rows=new Map();const storage={get:async k=>structuredClone(rows.get(k)),put:async(k,v)=>rows.set(k,structuredClone(v)),delete:async k=>rows.delete(k),list:async({prefix})=>new Map([...rows].filter(([k])=>k.startsWith(prefix))),getAlarm:async()=>1,setAlarm:async()=>{}};
 const directory=new ServerDirectory({storage,blockConcurrencyWhile:fn=>fn()},{});
 const token=randomBytes(32).toString('hex'),serverId=randomUUID();
 const call=async(action,v={},auth=token)=>{const r=await directory.fetch(new Request('https://internal/'+action,{method:'POST',headers:{authorization:'Bearer '+auth},body:JSON.stringify(v)}));return {status:r.status,data:await r.json()};};
 return {call,rows,token,serverId};
}
test('native permission snapshots enforce subsets and explicitly offered actor; legacy stays message-only',async()=>{
 const {call,rows,serverId}=await setup(),actor=randomUUID();
 const enroll={serverId,allowedScriptIds:[],allScripts:true,scopes:['status','capabilities','scripts.test','scripts.test.status'],availableCapabilities:['runtime.message.send','player.teleport.self'],testPlayers:[{uuid:actor,name:'Owner'}]};
 assert.equal((await call('enroll',{...enroll,availableCapabilities:['runtime.console.execute']})).status,400);
 assert.equal((await call('enroll',{...enroll,availableCapabilities:null})).status,400);
 for(const invalid of [[],['player.teleport.self']]) assert.equal((await call('enroll',{...enroll,availableCapabilities:invalid})).status,400);
 assert.equal((await call('enroll',{...enroll,testPlayers:Array.from({length:129},()=>({uuid:randomUUID(),name:'Player'}))})).status,400);
 assert.equal((await call('enroll',{...enroll,testPlayers:[{uuid:'bad',name:'Owner'}]})).status,400);
 const e=await call('enroll',enroll);assert.equal(e.status,201);const start=await call('browser-start',{code:e.data.code});const browser=start.data.token;
 await call('browser-choice',{kind:'codex'},browser);
 const info=await call('browser-info',{},browser);assert.deepEqual(info.data.availableCapabilities,enroll.availableCapabilities);assert.deepEqual(info.data.testPlayers,enroll.testPlayers);
 const approval={kind:'codex',name:'Native',allScripts:true,allowedScriptIds:[],client_id:'client',redirect_uri:'http://localhost/cb',code_challenge:'test',capabilities:enroll.availableCapabilities,testPlayerId:actor};
 assert.equal((await call('browser-authorize',{...approval,capabilities:['state.own']},browser)).status,400);
 assert.equal((await call('browser-authorize',{...approval,testPlayerId:null},browser)).status,400);
 for(const invalid of [[],['player.teleport.self']]) assert.equal((await call('browser-authorize',{...approval,capabilities:invalid},browser)).status,400);
 assert.equal((await call('browser-authorize',{...approval,testPlayerId:randomUUID()},browser)).status,400);
 assert.equal((await call('browser-authorize',{...approval,capabilities:['runtime.message.send','runtime.message.send']},browser)).status,400);
 assert.equal((await call('browser-authorize',approval,browser)).status,200);
 const grant=rows.get('identity').grants[0];assert.deepEqual(grant.capabilities,enroll.availableCapabilities);assert.equal(grant.testPlayerId,actor);
 const clientToken=randomBytes(32).toString('hex');grant.hash=hash(clientToken);grant.active=true;grant.tokenIssuedAt=Date.now();const state=rows.get('identity');state.grants=[grant];rows.set('identity',state);
 assert.equal((await call('client-requests',{connectionId:grant.connectionId,operation:'scripts.test.status',arguments:{id:'lobby',testId:'bad'}},clientToken)).status,400);
 assert.equal((await call('client-requests',{connectionId:grant.connectionId,operation:'capabilities',arguments:{}},clientToken)).status,202);
 delete grant.capabilities;delete grant.testPlayerId;grant.scopes=['status'];rows.set('identity',{...state,grants:[grant]});const legacy=await call('client-connection',{connectionId:grant.connectionId},clientToken);assert.deepEqual(legacy.data.capabilities,['runtime.message.send']);assert.equal(legacy.data.testPlayerId,'');assert.deepEqual(legacy.data.scopes,['status']);
});

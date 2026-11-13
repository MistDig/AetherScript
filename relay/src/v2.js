import {page as renderPage} from './pairing-page.js';
import {DurableObject} from 'cloudflare:workers';
const OPS=['status','scripts.list','scripts.read','scripts.validate','scripts.apply','scripts.test'];
const ID=/^[a-z][a-z0-9-]{0,63}$/;
const UUID=/^[a-f0-9-]{36}$/;
const HEX=/^[a-f0-9]{64}$/;
const MAX=65536;
const utf8Size=value=>new TextEncoder().encode(JSON.stringify(value)).length;
const publicGrants=state=>state.grants.map(({hash,count,countDay,pending,viewHash,viewExpiresAt,...grant})=>grant);
const TOOL_NAMES={'status':'aether_server_status','scripts.list':'aether_scripts_list','scripts.read':'aether_scripts_read','scripts.validate':'aether_scripts_validate','scripts.apply':'aether_scripts_apply','scripts.test':'aether_scripts_test'};
const encode=b=>btoa(String.fromCharCode(...b)).replaceAll('+','-').replaceAll('/','_').replaceAll('=','');
const random=()=>Array.from(crypto.getRandomValues(new Uint8Array(32)),b=>b.toString(16).padStart(2,'0')).join('');
const digest=async s=>Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(s))),b=>b.toString(16).padStart(2,'0')).join('');
const pkce=async s=>encode(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(s))));
const output=(v,status=200,extra={})=>new Response(JSON.stringify(v),{status,headers:{'content-type':'application/json','cache-control':'no-store','x-content-type-options':'nosniff',...extra}});
const deny=(code,status=400)=>output({error:code},status);
const AUTH_METHODS=['none','client_secret_basic','client_secret_post'];
const REJECT_REASONS=new Set(['enrollment_expired','enrollment_superseded','enrollment_unknown','grant_revoked','grant_mismatch','capacity','invalid_grant','no_local_enrollment','immutable_grant_changed']);
function invalidToken(origin){return output({error:'invalid_token',error_description:'This approval was superseded, rejected, expired or revoked. Run ais connect on the server and authorize this app again.'},401,{'www-authenticate':`Bearer error="invalid_token", resource_metadata="${origin}/.well-known/oauth-protected-resource/mcp"`});}
function publicOauthPath(path){return path.startsWith('/.well-known/')||['/register','/oauth/register','/oauth/token','/mcp'].includes(path);}
function allowedBrowserOrigin(origin,env){return ['https://gemini.google.com','https://chatgpt.com','https://claude.ai',...(env.BROWSER_ORIGINS||'').split(',').filter(Boolean)].includes(origin);}
export async function handleV2(req,env){
 const url=new URL(req.url),requestOrigin=req.headers.get('origin');
 const cors=!!requestOrigin&&requestOrigin!==url.origin&&publicOauthPath(url.pathname)&&allowedBrowserOrigin(requestOrigin,env);
 if(req.method==='OPTIONS')return cors?new Response(null,{status:204,headers:{'access-control-allow-origin':requestOrigin,'access-control-allow-methods':'GET, POST, OPTIONS','access-control-allow-headers':'Authorization, Content-Type, MCP-Protocol-Version','access-control-max-age':'600','vary':'Origin'}}):deny('origin_denied',403);
 const result=await handleV2Core(req,env,cors);
 if(!cors)return result;
 const headers=new Headers(result.headers);headers.set('access-control-allow-origin',requestOrigin);headers.set('access-control-expose-headers','WWW-Authenticate');headers.set('vary','Origin');
 return new Response(result.body,{status:result.status,headers});
}
async function read(req){
 const reader=req.body?.getReader();let n=0;const parts=[];
 if(reader)while(true){const {done,value}=await reader.read();if(done)break;n+=value.length;if(n>MAX){await reader.cancel();throw Error('body_limit');}parts.push(value);}
 const data=new Uint8Array(n);let at=0;for(const p of parts){data.set(p,at);at+=p.length;}
 const text=new TextDecoder().decode(data);
 if(req.headers.get('content-type')?.includes('application/x-www-form-urlencoded'))return Object.fromEntries(new URLSearchParams(text));
 const v=JSON.parse(text||'{}');if(!v||typeof v!=='object'||Array.isArray(v))throw Error('invalid_body');return v;
}
function ids(v,empty=false){return Array.isArray(v)&&v.length<129&&(empty||v.length>0)&&new Set(v).size===v.length&&v.every(x=>typeof x==='string'&&ID.test(x)&&!x.includes('..'));}
function bearer(req){return req.headers.get('authorization')?.match(/^Bearer (.+)$/)?.[1]||'';}
function cookie(req){return (req.headers.get('cookie')||'').split(';').map(x=>x.trim()).find(x=>x.startsWith('aether_browser='))?.slice(15)||'';}
function safeRedirect(s){try{const u=new URL(s);return !u.username&&!u.password&&!u.hash&&(u.protocol==='https:'||(u.protocol==='http:'&&['localhost','127.0.0.1','[::1]'].includes(u.hostname)));}catch{return false;}}
async function internal(env,id,action,input={},token=''){return env.DIRECTORY.get(env.DIRECTORY.idFromName(id)).fetch(new Request('https://internal/'+action,{method:'POST',headers:{authorization:'Bearer '+token},body:JSON.stringify(input)}));}
function page(script,content){const nonce=random();return new Response(renderPage('Connect',`<h1>AetherScript Connect</h1>${content}<pre id="message" role="status"></pre><script nonce="${nonce}">${script}</script>`),{headers:{'content-type':'text/html;charset=utf-8','cache-control':'no-store','referrer-policy':'no-referrer','content-security-policy':`default-src 'none'; script-src 'nonce-${nonce}'; style-src 'unsafe-inline'; connect-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'`,'x-content-type-options':'nosniff'}});}
function clientKind(redirect){const u=new URL(redirect);if(['localhost','127.0.0.1','[::1]'].includes(u.hostname))return 'codex';if(['chatgpt.com','chat.openai.com'].includes(u.hostname))return 'chatgpt';if(['claude.ai','claude.com'].includes(u.hostname))return 'claude';if(u.hostname==='gemini.google.com')return 'gemini';return 'other';}const COMMON=`const message=document.getElementById('message');async function api(path,data){const r=await fetch(path,{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(data)});const v=await r.json();if(!r.ok)throw Error(v.error||'Request failed');return v;}function show(e){message.textContent=e.message||String(e);}`;
function connectPage(){return page(COMMON+`
let ready=false;
const kind=document.getElementById('kind');
async function updateChoice(){try{if(!ready)return;await api('/v2/browser/choice',{kind:kind.value});for(const section of document.querySelectorAll('[data-client]'))section.hidden=section.dataset.client!==kind.value;}catch(e){show(e);}}
async function load(){try{const params=new URLSearchParams(location.hash.slice(1));const code=params.get('code');if(code){history.replaceState(null,'','/connect');await api('/v2/browser/start',{code});}const info=await api('/v2/browser/info',{});document.getElementById('server').textContent=info.serverName||info.serverId;document.getElementById('scopes').textContent=info.scopes.join(', ');ready=true;await updateChoice();message.textContent='Choose your app, then complete its OAuth approval. Waiting for your app to request server status.';}catch(e){show(e);}}
document.getElementById('register').onclick=async()=>{try{const v=await api('/register',{redirect_uris:document.getElementById('callbacks').value.split(/\\n/).map(x=>x.trim()).filter(Boolean),token_endpoint_auth_method:document.getElementById('auth-method').value,grant_types:['authorization_code'],response_types:['code']});document.getElementById('client-credentials').textContent=JSON.stringify(v,null,2);message.textContent='Client registered. Copy its ID and, when present, its unique secret into this app. The secret is displayed only in this response. This does not approve server access.';}catch(e){show(e);}};
kind.onchange=updateChoice;document.getElementById('endpoint').textContent=location.origin+'/mcp';
const cursorConfig=btoa(JSON.stringify({url:location.origin+'/mcp'}));document.getElementById('cursor').href='cursor://anysphere.cursor-deeplink/mcp/install?name=AetherScript&config='+encodeURIComponent(cursorConfig);
setInterval(async()=>{try{const v=await api('/v2/browser/status',{});if(v.state==='connected')message.textContent='Connected: your authorized app requested server status and Paper answered. Server '+v.serverId+'.';else if(v.state==='revoked')message.textContent='This connection was revoked.';else message.textContent='Approved. Waiting for '+(v.state==='waiting_for_server'?'Paper to accept this exact grant.':'your app to call the status tool. Ask it to check AetherScript server status.');}catch{}},3000);load();`,
`<p>Connect an AI app to one private Minecraft server. This one-use console link expires in ten minutes.</p><p>Server: <strong id="server"></strong></p><p>Operations: <span id="scopes"></span></p><label>Choose your app<select id="kind"><option value="codex">Codex on Windows</option><option value="chatgpt">ChatGPT</option><option value="claude">Claude</option><option value="gemini">Gemini / Google web app</option><option value="other">Cursor / another MCP app</option></select></label><section id="hosted"><div data-client="codex"><h2>Set up Codex</h2><p><a href="https://github.com/MistDig/AetherScript/releases/download/v0.0.8/AetherScript-Codex-Windows-0.0.8.zip">Download the Codex Windows setup</a>, extract it, and double-click <code>Connect-AetherScript.cmd</code>. It registers AetherScript with Codex and opens native OAuth sign-in in your browser. Keep this browser open.</p></div><div data-client="chatgpt" hidden><h2>Set up ChatGPT</h2><p>Open your ChatGPT connector settings, add a custom remote MCP app using the URL below, and start its OAuth connection. Use this same browser. Availability depends on your ChatGPT plan and workspace settings.</p></div><div data-client="claude" hidden><h2>Set up Claude</h2><p>Open Claude connector settings, add a remote MCP connector using the URL below, and start OAuth. Use this same browser. Availability depends on your Claude account.</p></div><div data-client="gemini" hidden><h2>Set up Gemini</h2><p>Use the hosted MCP URL below. If your app asks for an OAuth client ID and secret, register its exact HTTPS callback URI using the form below. Its callback must be on gemini.google.com for this named choice; otherwise choose another MCP app and inspect its callback origin. Actual Gemini account availability is not certified here.</p></div><div data-client="other" hidden><h2>Set up your app</h2><p><a id="cursor" href="#">Install in Cursor</a>, or add the URL below as a remote MCP server in your app and start OAuth. Its callback origin will be displayed for you to inspect. No app identity is verified by a client-supplied name.</p></div><p>Hosted MCP URL:</p><pre id="endpoint"></pre><p>Your app's OAuth screen asks for a connection name and full-script access or 1 to 128 selected IDs. Full access can change existing .aether scripts; legacy YAML stays protected, and selected grants never expand automatically. After approval, ask the app to check AetherScript server status; this page shows Connected only after Paper answers that app's request.</p></section><details><summary>App asks for an OAuth client ID or secret?</summary><p>Register a separate client for this app. Paste the exact callback URL provided by the app, one per line. Public apps use None with PKCE. A confidential app can use HTTP Basic or POST-body client authentication; each registration gets its own secret. There is no shared service secret. Access still requires a console link and a separate approval.</p><label>Callback URLs<textarea id="callbacks" rows="3" placeholder="https://gemini.google.com/your-app-callback"></textarea></label><label>Token endpoint authentication<select id="auth-method"><option value="client_secret_basic">Client secret via HTTP Basic</option><option value="client_secret_post">Client secret in POST body</option><option value="none">None (public client with PKCE)</option></select></label><button id="register">Register this app</button><pre id="client-credentials"></pre></details>`);}
function authorizePage(params){const data=JSON.stringify(params).replaceAll('<','\\u003c');return page(COMMON+`
const params=${data};document.getElementById('redirect').textContent=new URL(params.redirect_uri).origin;api('/v2/browser/info',{}).then(v=>{document.getElementById('server').textContent=v.serverName||v.serverId;document.getElementById('ids').value=v.allowedScriptIds.join(',');document.getElementById('all').disabled=!v.allScripts;document.getElementById('all').checked=v.allScripts===true;document.getElementById('selected').checked=!v.allScripts;document.getElementById('scope-warning').textContent=v.allScripts?'Full access can create and change all existing .aether scripts on this server. Legacy YAML scripts stay protected.':'Console restricted this approval to selected script IDs.';document.getElementById('scopes').textContent=v.scopes.join(', ');}).catch(show);document.getElementById('approve').onclick=async()=>{try{const v=await api('/v2/browser/authorize',{...params,name:document.getElementById('name').value,allScripts:document.getElementById('all').checked,allowedScriptIds:document.getElementById('all').checked?[]:document.getElementById('ids').value.split(',').map(x=>x.trim()).filter(Boolean)});location.assign(v.redirect);}catch(e){show(e);}};`, `<p>Approve this OAuth MCP client for server <strong id="server"></strong>.</p><p>Registered callback origin: <strong id="redirect"></strong>. This is a destination check, not a verified app identity. Ignore any claimed app name that you do not recognize.</p><p>Operations: <span id="scopes"></span></p><p>Open your console link and select the matching app in this browser first. Review the access choice below before approving.</p><label>Connection name<input id="name" value="Hosted MCP" maxlength="64"></label><fieldset><legend>Script access</legend><label><input type="radio" name="access" id="all" value="all" checked> Create and edit all AetherScript scripts</label><label><input type="radio" name="access" id="selected" value="selected"> Only selected scripts</label><p id="scope-warning"></p></fieldset><label>Exact script IDs (for selected scripts only)<input id="ids" maxlength="16400" placeholder="welcome, lobby"></label><p>IDs must start with a lowercase letter and contain only lowercase letters, digits or hyphens, up to 64 characters.</p><p>Full access is available only when console delegated it. Selected grants stay limited to the exact IDs you enter. Console restrictions cannot be expanded.</p><button id="approve">Approve access and return to app</button>`);}

async function handleV2Core(req,env,cors){
 const url=new URL(req.url),path=url.pathname,origin=url.origin;
 const metadata={issuer:origin,authorization_endpoint:origin+'/oauth/authorize',token_endpoint:origin+'/oauth/token',registration_endpoint:origin+'/register',response_types_supported:['code'],grant_types_supported:['authorization_code'],code_challenge_methods_supported:['S256'],token_endpoint_auth_methods_supported:AUTH_METHODS,scopes_supported:['aetherscript']};
 if(path==='/.well-known/oauth-authorization-server')return output(metadata);
 if(path==='/.well-known/oauth-protected-resource'||path==='/.well-known/oauth-protected-resource/mcp')return output({resource:origin+'/mcp',authorization_servers:[origin],scopes_supported:['aetherscript']});
 if(path==='/connect'&&req.method==='GET')return connectPage();
 if(path==='/oauth/authorize'&&req.method==='GET'){
  const p=Object.fromEntries(url.searchParams);const check=await internal(env,'registry','client',{clientId:p.client_id});
  if(!check.ok)return deny('invalid_client');const c=await check.json();
  if(p.response_type!=='code'||p.code_challenge_method!=='S256'||!/^[A-Za-z0-9_-]{43}$/.test(p.code_challenge||'')||!c.redirect_uris.includes(p.redirect_uri)||(p.resource&&p.resource!==origin+'/mcp')||(p.scope&&p.scope!=='aetherscript')||(p.state||'').length>2048)return deny('invalid_authorization_request');
  return authorizePage(p);
 }
 if(req.method!=='POST'&&path!=='/mcp')return deny('post_required',405);
 if(req.headers.get('origin')&&req.headers.get('origin')!==origin&&!cors)return deny('origin_denied',403);
 try{
  if(path==='/register'||path==='/oauth/register'){
   const input=await read(req),method=input.token_endpoint_auth_method??'client_secret_basic';
   if(!Array.isArray(input.redirect_uris)||input.redirect_uris.length<1||input.redirect_uris.length>5||!input.redirect_uris.every(s=>typeof s==='string'&&s.length<=2048&&safeRedirect(s))||!AUTH_METHODS.includes(method)||input.grant_types&&(!Array.isArray(input.grant_types)||input.grant_types.length!==1||input.grant_types.some(x=>x!=='authorization_code'))||input.response_types&&(!Array.isArray(input.response_types)||input.response_types.length!==1||input.response_types.some(x=>x!=='code')))return deny('invalid_client_metadata');
   return internal(env,'registry','register',{redirect_uris:input.redirect_uris,method,ip:await digest(req.headers.get('cf-connecting-ip')||'local')});
  }
  if(path==='/v2/enrollments'){
   const v=await read(req);if(!UUID.test(v.serverId||'')||!HEX.test(v.serverToken||''))return deny('invalid_identity');
   const quota=await internal(env,'registry','quota',{ip:await digest(req.headers.get('cf-connecting-ip')||'local'),serverId:v.serverId});if(!quota.ok)return quota;
   const r=await internal(env,v.serverId,'enroll',v,v.serverToken);if(!r.ok)return r;const data=await r.json();return output({...data,browserUrl:origin+'/connect#code='+data.code,code:undefined},201);
  }
  const server=/^\/v2\/servers\/([a-f0-9-]{36})\/(poll|respond|ack|revoke|reject)$/.exec(path);
  if(server)return internal(env,server[1],server[2],await read(req),bearer(req));
  if(path.startsWith('/v2/browser/')){
   const action=path.slice('/v2/browser/'.length),v=await read(req);
   const code=action==='start'?v.code:action==='status'?cookie(req).replace(/^probe:/,''):cookie(req);const parts=typeof code==='string'?code.split('.'):[];
   if(parts.length<2||!UUID.test(parts[0]))return deny('open_console_link_first',401);
   if(action==='start'){
    const r=await internal(env,parts[0],'browser-start',v);if(!r.ok)return r;const data=await r.json();return output({ready:true},200,{'set-cookie':`aether_browser=${parts[0]}.${data.token}; HttpOnly; SameSite=Strict; Path=/; Max-Age=600${url.protocol==='https:'?'; Secure':''}`});
   }
   if(action==='authorize'){
    const check=await internal(env,'registry','client',{clientId:v.client_id});if(!check.ok)return deny('invalid_client');const client=await check.json();
    if(!client.redirect_uris.includes(v.redirect_uri)||v.response_type!=='code'||v.code_challenge_method!=='S256'||!/^[A-Za-z0-9_-]{43}$/.test(v.code_challenge||'')||(v.state||'').length>2048||(v.resource&&v.resource!==origin+'/mcp')||(v.scope&&v.scope!=='aetherscript'))return deny('invalid_authorization_request');
   }
   if(!['info','authorize','choice','status'].includes(action))return deny('not_found',404);
   const r=await internal(env,parts[0],'browser-'+action,{...v,kind:action==='authorize'?clientKind(v.redirect_uri):v.kind},parts.slice(1).join('.'));if(!r.ok)return r;const result=await r.json();
   if(action==='authorize'){const redirect=new URL(v.redirect_uri);redirect.searchParams.set('code',parts[0]+'.'+result.code);if(v.state)redirect.searchParams.set('state',v.state);return output({redirect:redirect.href},200,{'set-cookie':`aether_browser=probe:${parts[0]}.${result.connectionId}.${result.viewToken}; HttpOnly; SameSite=Strict; Path=/; Max-Age=600${url.protocol==='https:'?'; Secure':''}`});}
   return output(result);
  }
  if(path==='/oauth/token'){
   const v=await read(req);let clientId=v.client_id,clientSecret=v.client_secret,method=clientSecret===undefined?'none':'client_secret_post';
   const authorization=req.headers.get('authorization');
   if(authorization){
    if(!authorization.startsWith('Basic ')||clientSecret!==undefined)return deny('invalid_client',401);
    try{const decoded=atob(authorization.slice(6)),separator=decoded.indexOf(':');if(separator<0)throw Error();const decode=x=>decodeURIComponent(x.replaceAll('+',' '));const basicId=decode(decoded.slice(0,separator));if(clientId&&clientId!==basicId)throw Error();clientId=basicId;clientSecret=decode(decoded.slice(separator+1));method='client_secret_basic';}catch{return deny('invalid_client',401);}
   }
   const authenticated=await internal(env,'registry','authenticate',{clientId,clientSecret,method});if(!authenticated.ok)return output({error:'invalid_client'},401,{'www-authenticate':'Basic realm="AetherScript OAuth"'});
   const parts=typeof v.code==='string'?v.code.split('.'):[];
   if(v.grant_type!=='authorization_code'||parts.length!==2||!UUID.test(parts[0])||!/^[A-Za-z0-9._~-]{43,128}$/.test(v.code_verifier||''))return deny('invalid_grant');
   const r=await internal(env,parts[0],'token',{...v,client_id:clientId,client_secret:undefined,code:parts[1]});if(!r.ok)return r;const result=await r.json();return output({access_token:parts[0]+'.'+result.connectionId+'.'+result.token,token_type:'Bearer',expires_in:2592000,scope:'aetherscript'});
  }
  if(path.startsWith('/v2/client/')||path==='/mcp'){
   const token=bearer(req),parts=token.split('.');if(parts.length!==3||!UUID.test(parts[0])||!UUID.test(parts[1])||!HEX.test(parts[2]))return invalidToken(origin);
   if(path==='/mcp')return await handleMcp(req,env,parts,origin);
   const action=path.slice('/v2/client/'.length);if(!['requests','results','revoke','connection'].includes(action))return deny('not_found',404);
   const response=await internal(env,parts[0],'client-'+action,{...await read(req),connectionId:parts[1]},parts[2]);return response.status===401?invalidToken(origin):response;
  }
  return deny('not_found',404);
 }catch{return deny('invalid_request');}
}

async function handleMcp(req,env,parts,origin){
 const check=await internal(env,parts[0],'client-connection',{connectionId:parts[1]},parts[2]);if(!check.ok)return invalidToken(origin);
 if(req.method==='GET')return deny('stream_not_supported',405);if(req.method==='DELETE')return output({closed:true});if(req.method!=='POST')return deny('post_required',405);
 const rpc=await read(req);if(rpc.jsonrpc!=='2.0'||typeof rpc.method!=='string')return output({jsonrpc:'2.0',id:rpc.id??null,error:{code:-32600,message:'Invalid Request'}});
 if(rpc.id===undefined)return new Response(null,{status:202});
 let grant=await check.json();
 if(!grant.active&&['initialize','tools/list','tools/call'].includes(rpc.method)){
  const deadline=Date.now()+15000;
  while(!grant.active&&Date.now()<deadline){
   await new Promise(resolve=>setTimeout(resolve,Math.min(1000,Math.max(0,deadline-Date.now()))));
   const current=await internal(env,parts[0],'client-connection',{connectionId:parts[1]},parts[2]);
   if(!current.ok)return invalidToken(origin);
   grant=await current.json();
  }
  if(!grant.active)return output({error:'temporarily_unavailable',error_description:'Paper has not acknowledged this connection yet. Retry shortly.'},503,{'retry-after':'2'});
 }
 const respond=result=>output({jsonrpc:'2.0',id:rpc.id,result});const error=(code,message)=>output({jsonrpc:'2.0',id:rpc.id,error:{code,message}});
 if(rpc.method==='initialize')return respond({protocolVersion:'2025-03-26',capabilities:{tools:{listChanged:false}},serverInfo:{name:'aetherscript-hosted',version:'0.0.8'}});
 if(rpc.method==='ping')return respond({});
 if(rpc.method==='tools/list'){
  const fields={id:{type:'string',pattern:ID.source,maxLength:64},source:{type:'string',maxLength:16000},expectedHash:{type:'string'},expectedMessages:{type:'array',items:{type:'string'},maxItems:128},arguments:{type:'array',items:{type:'string',maxLength:2048},maxItems:16}};
  const required={status:[], 'scripts.list':[], 'scripts.read':['id'], 'scripts.validate':['id','source'], 'scripts.apply':['id','source','expectedHash'], 'scripts.test':['id','expectedMessages']};
  return respond({tools:OPS.filter(x=>grant.scopes.includes(x)).map(operation=>({name:TOOL_NAMES[operation],description:'AetherScript '+operation+'. Limited to the exact script IDs approved for this server connection. Read source and hash before editing; use expectedHash ABSENT for a new script.',inputSchema:{type:'object',properties:Object.fromEntries([...required[operation],...(operation==='scripts.test'?['arguments']:[])].map(name=>[name,fields[name]])),required:required[operation],additionalProperties:false},annotations:{readOnlyHint:operation!=='scripts.apply',destructiveHint:operation==='scripts.apply',idempotentHint:operation!=='scripts.apply',openWorldHint:false}}))});
 }
 if(rpc.method!=='tools/call')return error(-32601,'Method not found');
 const operation=OPS.find(x=>TOOL_NAMES[x]===rpc.params?.name);if(!operation)return error(-32602,'Unknown tool');
 const r=await internal(env,parts[0],'client-requests',{connectionId:parts[1],operation,arguments:rpc.params.arguments||{}},parts[2]);
 if(r.status===401)return invalidToken(origin);
 if(!r.ok)return respond({content:[{type:'text',text:JSON.stringify(await r.json())}],isError:true});const {id}=await r.json();
 for(let i=0;i<25;i++){
  const answer=await internal(env,parts[0],'client-results',{connectionId:parts[1],id},parts[2]);const value=await answer.json();
  if(!answer.ok||!value.pending){const content=value.result??value;return respond({content:[{type:'text',text:JSON.stringify(content)}],structuredContent:content,isError:!answer.ok||!!value.error});}
  await new Promise(resolve=>setTimeout(resolve,1000));
 }
 return respond({content:[{type:'text',text:'Timed out waiting for Paper. Execution may still complete. Read status/script before retrying a mutation.'}],isError:true});
}

export class ServerDirectory extends DurableObject{
 constructor(ctx,env){super(ctx,env);this.ctx=ctx;this.storage=ctx.storage;}
 async schedule(now){if(await this.storage.getAlarm()===null)await this.storage.setAlarm(now+86400000);}
 async alarm(){await this.ctx.blockConcurrencyWhile(async()=>{
  const now=Date.now(),day=Math.floor(now/86400000);
  for(const prefix of ['oauth:','quota:','total:','client:']){
   const rows=await this.storage.list({prefix});
   for(const [key,value] of rows){
    const expired=prefix==='oauth:'?value.expiresAt<=now:prefix==='client:'?value.expiresAt<=now:Number(key.split(':')[1])<day-1;
    if(expired)await this.storage.delete(key);
   }
  }
  const state=await this.storage.get('identity');
  if(state){
   if(state.enrollment?.expiresAt<=now)delete state.enrollment;
   if(state.browser?.expiresAt<=now)delete state.browser;
   for(const g of state.grants){
    if(g.expiresAt<=now&&!g.revoked){g.revoked=true;g.revokedAt=now;g.allowedScriptIds=[];g.scopes=[];delete g.hash;}
    if(g.revoked||g.pending?.createdAt+120000<=now){delete g.pending;await this.storage.delete('pending:'+g.connectionId);}
    if(g.viewExpiresAt<=now){delete g.viewHash;delete g.viewExpiresAt;}
   }
   state.grants=state.grants.filter(g=>!g.revoked||now-g.revokedAt<86400000);
   await this.storage.put('identity',state);
  }
  await this.schedule(now);
 });}

 async fetch(req){return this.ctx.blockConcurrencyWhile(async()=>{try{return await this.run(req);}catch{return deny('invalid_request');}});}
 async run(req){
  const action=new URL(req.url).pathname.slice(1),v=await read(req),now=Date.now(),auth=bearer(req),day=Math.floor(now/86400000);
  if(['quota','register','client','authenticate'].includes(action))return this.registry(action,v,day,now);
  let state=await this.storage.get('identity');
  if(state){let invalid=false;for(const g of state.grants){if(g.active&&g.hash&&!g.tokenIssuedAt)g.tokenIssuedAt=g.createdAt||1;if(!g.handshakeExpiresAt)g.handshakeExpiresAt=(g.createdAt||0)+600000;if(!g.revoked&&g.allScripts!==true&&!ids(g.allowedScriptIds)){await this.terminal(g,'invalid_grant',now);invalid=true;}}if(invalid)await this.storage.put('identity',state);}
  if(action==='enroll'){
   if(!HEX.test(auth)||!UUID.test(v.serverId||'')||!ids(v.allowedScriptIds,true)||!Array.isArray(v.scopes)||!v.scopes.length||v.scopes.some(x=>!OPS.includes(x)))return deny('invalid_enrollment');
   const serverHash=await digest(auth);if(state&&state.serverHash!==serverHash)return deny('identity_conflict',401);
   if(!state)state={serverId:v.serverId,serverHash,grants:[],countDay:day,enrollments:0};
   if(v.serverName!==undefined&&(typeof v.serverName!=='string'||v.serverName.length>80||/[\u0000-\u001f\u007f]/.test(v.serverName)))return deny('invalid_server_name');
   state.serverName=v.serverName?.trim()||state.serverName||v.serverId;
   await this.schedule(now);
   if(state.countDay!==day){state.countDay=day;state.enrollments=0;}
   if(state.enrollments>=10||state.grants.filter(g=>!g.revoked&&g.active&&g.tokenIssuedAt).length>=16)return deny('server_quota',429);
   for(const g of state.grants)if(!g.revoked&&(!g.active||!g.tokenIssuedAt))await this.terminal(g,'enrollment_superseded',now);
   for(const [key] of await this.storage.list({prefix:'oauth:'}))await this.storage.delete(key);
   const enrollmentId=crypto.randomUUID(),code=random();state.enrollments++;
   state.enrollment={enrollmentId,hash:await digest(code),expiresAt:now+600000,allowedScriptIds:v.allowedScriptIds,allScripts:v.allScripts===true&&v.allowedScriptIds.length===0,scopes:[...new Set(v.scopes)]};
   delete state.browser;await this.storage.put('identity',state);
   return output({enrollmentId,code:state.serverId+'.'+enrollmentId+'.'+code,expiresAt:state.enrollment.expiresAt},201);
  }
  if(!state)return deny('unknown_server',401);
  if(action==='browser-start'){
   const p=typeof v.code==='string'?v.code.split('.'):[];const e=state.enrollment;
   if(!e||e.expiresAt<=now||p.length!==3||p[0]!==state.serverId||p[1]!==e.enrollmentId||await digest(p[2])!==e.hash)return deny('invalid_or_used_link',401);
   const token=random();state.browser={...e,hash:await digest(token)};delete state.enrollment;await this.storage.put('identity',state);return output({token});
  }
  if(action==='browser-status'){
   const p=auth.split('.');const g=state.grants.find(x=>x.connectionId===p[0]);
   if(!g||!g.viewHash||await digest(p[1]||'')!==g.viewHash||g.viewExpiresAt<=now)return deny('status_session_expired',401);
   return output({state:g.revoked?'revoked':g.confirmedAt?'connected':g.active?'waiting_for_client_status':'waiting_for_server',serverId:state.serverId,connectionId:g.connectionId,acknowledgedAt:g.confirmedAt||null,expiresAt:g.expiresAt});
  }
  if(action.startsWith('browser-')){
   const browser=state.browser;if(!browser||browser.expiresAt<=now||await digest(auth)!==browser.hash)return deny('browser_session_expired',401);
   if(action==='browser-choice'){if(!['codex','chatgpt','claude','gemini','other'].includes(v.kind))return deny('invalid_client_choice');browser.kind=v.kind;await this.storage.put('identity',state);return output({selected:true});}
   if(action==='browser-info')return output({serverId:state.serverId,serverName:state.serverName,allowedScriptIds:browser.allowedScriptIds,allScripts:browser.allScripts===true,scopes:browser.scopes,expiresAt:browser.expiresAt});
   if(action==='browser-authorize'&&(!browser.kind||browser.kind!==v.kind))return deny('selected_client_redirect_mismatch',403);
   if(action!=='browser-authorize')return deny('not_found',404);
   if(v.allScripts!==undefined&&typeof v.allScripts!=='boolean')return deny('invalid_exact_grant');
   if((v.allScripts===true?!browser.allScripts||!ids(v.allowedScriptIds,true):!ids(v.allowedScriptIds)||browser.allowedScriptIds.length&&v.allowedScriptIds.some(x=>!browser.allowedScriptIds.includes(x)))||typeof v.name!=='string'||v.name.trim().length<1||v.name.length>64)return deny('invalid_exact_grant');
   if(state.grants.filter(g=>!g.revoked).length>=16)return deny('connection_limit',429);
   // Revoked grant metadata is bounded; credential verifiers are erased on revocation.
   state.grants=state.grants.filter(g=>!g.revoked||now-g.revokedAt<86400000).slice(-64);
   const connectionId=crypto.randomUUID(),viewToken=random();const grant={connectionId,enrollmentId:browser.enrollmentId,name:v.name.trim(),clientKind:browser.kind,createdAt:now,viewHash:await digest(viewToken),viewExpiresAt:now+600000,allowedScriptIds:v.allScripts===true?[]:v.allowedScriptIds,allScripts:v.allScripts===true,scopes:browser.scopes,handshakeExpiresAt:browser.expiresAt,revoked:false,active:false,expiresAt:now+2592000000,countDay:day,count:0};state.grants.push(grant);delete state.browser;
   if(utf8Size(state)>100000||utf8Size(publicGrants(state))>16000)return deny('server_grant_storage_quota',429);
   if(action==='browser-authorize'){
    const code=random();await this.storage.put('oauth:'+await digest(code),{connectionId,clientId:v.client_id,redirectUri:v.redirect_uri,challenge:v.code_challenge,expiresAt:now+120000});await this.storage.put('identity',state);return output({code,connectionId,viewToken});
   }

  }
  if(action==='token'){
   const key='oauth:'+await digest(v.code||''),code=await this.storage.get(key);
   if(!code||code.expiresAt<=now||v.client_id!==code.clientId||v.redirect_uri!==code.redirectUri||await pkce(v.code_verifier)!==code.challenge)return deny('invalid_grant');
   const grant=state.grants.find(x=>x.connectionId===code.connectionId);if(!grant||grant.revoked||!grant.active&&grant.handshakeExpiresAt<=now)return deny('invalid_grant');const token=random();grant.tokenIssuedAt=now;grant.hash=await digest(token);await this.storage.put('identity',state);await this.storage.delete(key);return output({connectionId:code.connectionId,token});
  }
  const isServer=HEX.test(auth)&&await digest(auth)===state.serverHash;
  if(['poll','respond','ack','revoke','reject'].includes(action)&&!isServer)return deny('unauthorized',401);
  let changed=false;for(const g of state.grants)if(!g.revoked&&(!g.active||!g.tokenIssuedAt)&&g.handshakeExpiresAt<=now){await this.terminal(g,'enrollment_expired',now);changed=true;}if(changed)await this.storage.put('identity',state);
  let grant=state.grants.find(g=>g.connectionId===v.connectionId);
  if(action.startsWith('client-')){
   if(!grant||grant.revoked||grant.expiresAt<=now||await digest(auth)!==grant.hash)return deny('invalid_token',401);
  }
  // Expire abandoned per-client work during connector polling, not only client reads.
  for(const g of state.grants){if(g.pending&&g.pending.createdAt+120000<=now){await this.storage.delete('pending:'+g.connectionId);delete g.pending;}}
  if(action==='poll'){
   const requests=[];
   for(const g of state.grants){if(g.pending&&!g.pending.delivered&&!g.revoked&&g.active&&g.expiresAt>now){const pending=await this.storage.get('pending:'+g.connectionId);if(pending){requests.push({connectionId:g.connectionId,id:g.pending.id,operation:pending.operation,arguments:pending.arguments});g.pending.delivered=true;break;}}}
   await this.storage.put('identity',state);
   return output({requests,grants:publicGrants(state),pollAfterMs:5000});
  }
  if(action==='reject'){if(!grant)return output({rejected:true});if(!REJECT_REASONS.has(v.reason))return deny('invalid_rejection_reason');if(grant.active&&!grant.revoked)return deny('active_connection',409);if(!grant.revoked)await this.terminal(grant,v.reason,now);await this.storage.put('identity',state);return output({rejected:true,reason:grant.terminalReason});}
  if(action==='ack'){if(!grant||grant.revoked)return deny('invalid_token',401);grant.active=true;await this.storage.put('identity',state);return output({acknowledged:true});}
  if(action==='revoke'||action==='client-revoke'){
   const targets=v.all===true&&isServer?state.grants:grant?[grant]:[];if(!targets.length&&!(v.all===true&&isServer))return deny('unknown_connection',404);
   for(const g of targets){g.revoked=true;g.revokedAt=now;g.allowedScriptIds=[];g.scopes=[];delete g.hash;delete g.pending;await this.storage.delete('pending:'+g.connectionId);}
   if(v.all===true){delete state.browser;delete state.enrollment;}await this.storage.put('identity',state);return output({revoked:true});
  }
  if(action==='client-connection')return output({serverId:state.serverId,connectionId:grant.connectionId,name:grant.name,scopes:grant.scopes,allowedScriptIds:grant.allowedScriptIds,allScripts:grant.allScripts===true,active:grant.active,expiresAt:grant.expiresAt});
  if(action==='client-requests'){
   if(!grant.active)return deny('waiting_for_server_approval',409);
   if(!OPS.includes(v.operation)||!grant.scopes.includes(v.operation))return deny('operation_denied',403);
   if(!v.arguments||typeof v.arguments!=='object'||Array.isArray(v.arguments))return deny('invalid_arguments');
   if(v.operation!=='status'&&v.operation!=='scripts.list'&&!(grant.allScripts===true&&typeof v.arguments.id==='string'&&ID.test(v.arguments.id)&&!v.arguments.id.includes('..'))&&!grant.allowedScriptIds.includes(v.arguments.id))return deny('script_not_granted',403);
   if(utf8Size({connectionId:grant.connectionId,id:crypto.randomUUID(),operation:v.operation,arguments:v.arguments})>44000)return deny('request_transport_limit',413);
   if(grant.pending)return deny('request_pending',429);
   if(grant.countDay!==day){grant.countDay=day;grant.count=0;}if(grant.count>=500)return deny('daily_connection_quota',429);
   grant.count++;const id=crypto.randomUUID();grant.pending={id,createdAt:now,delivered:false};await this.storage.put('pending:'+grant.connectionId,{operation:v.operation,arguments:v.arguments});await this.storage.put('identity',state);return output({id},202);
  }
  if(action==='respond'||action==='client-results'){
   if(!grant||grant.revoked||!grant.pending||grant.pending.id!==v.id)return deny('unknown_request',404);
   const key='pending:'+grant.connectionId;
   if(action==='respond'){
    if(!grant.pending.delivered)return deny('not_delivered',409);
    const existing=await this.storage.get(key);if(existing?.response)return deny('already_responded',409);
    const response={id:v.id};if(v.error&&typeof v.error.code==='string'&&typeof v.error.message==='string')response.error={code:v.error.code.slice(0,64),message:v.error.message.slice(0,1024)};else if(Object.hasOwn(v,'result'))response.result=v.result;else return deny('invalid_response');
    if(existing?.operation==='status'&&!response.error&&response.result&&typeof response.result==='object'){grant.confirmedAt=now;await this.storage.put('identity',state);}
    await this.storage.put(key,{response});return output({accepted:true});
   }
   const pending=await this.storage.get(key);if(!pending?.response)return output({pending:true,pollAfterMs:1000});const result=pending.response;delete grant.pending;await this.storage.delete(key);await this.storage.put('identity',state);return output(result);
  }
  return deny('not_found',404);
 }
 async terminal(grant,reason,now){grant.revoked=true;grant.revokedAt=now;grant.terminalReason=reason;grant.allowedScriptIds=[];grant.scopes=[];delete grant.hash;delete grant.pending;await this.storage.delete('pending:'+grant.connectionId);for(const [key,value] of await this.storage.list({prefix:'oauth:'}))if(value.connectionId===grant.connectionId)await this.storage.delete(key);}
 async registry(action,v,day,now){
  if(action==='client'||action==='authenticate'){
   const c=await this.storage.get('client:'+v.clientId);if(c&&!c.token_endpoint_auth_method)c.token_endpoint_auth_method='none';if(!c||c.expiresAt<=now)return deny('invalid_client',404);
   if(action==='authenticate'&&(v.method!==c.token_endpoint_auth_method||(c.token_endpoint_auth_method!=='none'&&(typeof v.clientSecret!=='string'||await digest(v.clientSecret)!==c.secretHash))))return deny('invalid_client',401);
   const {secretHash,...publicClient}=c;return output(publicClient);
  }
  await this.schedule(now);
  const key='quota:'+day+':'+v.ip,count=await this.storage.get(key)||0,total=await this.storage.get('total:'+day)||0;
  if(count>=40||total>=1000)return deny('enrollment_quota',429);
  if(action==='quota'){
   const serverKey='server:'+v.serverId;
   if(!await this.storage.get(serverKey)){
    const servers=await this.storage.get('server-count')||0;
    if(servers>=500)return deny('service_server_capacity',429);
    await this.storage.put(serverKey,true);await this.storage.put('server-count',servers+1);
   }
  }
  if(action==='register'){
   const clients=await this.storage.list({prefix:'client:',limit:501});
   if(clients.size>=500)return deny('service_client_capacity',429);
  }
  await this.storage.put(key,count+1);await this.storage.put('total:'+day,total+1);
  if(action==='register'){
   const id=crypto.randomUUID(),expiresAt=now+90*86400000,method=v.method??'client_secret_basic',clientSecret=method==='none'?undefined:random();
   await this.storage.put('client:'+id,{redirect_uris:v.redirect_uris,createdAt:now,expiresAt,token_endpoint_auth_method:method,...(clientSecret?{secretHash:await digest(clientSecret)}:{})});
   return output({client_id:id,client_id_issued_at:Math.floor(now/1000),...(clientSecret?{client_secret:clientSecret,client_secret_expires_at:Math.floor(expiresAt/1000)}:{}),redirect_uris:v.redirect_uris,token_endpoint_auth_method:method,grant_types:['authorization_code'],response_types:['code']},201);
  }
  return output({allowed:true});
 }
}

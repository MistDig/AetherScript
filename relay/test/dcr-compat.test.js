import {test} from 'node:test';
import assert from 'node:assert/strict';
import {Miniflare,convertV4MiniflareOptions} from 'miniflare';

test('DCR accepts Claude grant preferences and reports only the implemented grant',async t=>{
 const mf=new Miniflare(convertV4MiniflareOptions({
  modules:['worker','v2','pairing-page'].map(x=>({type:'ESModule',path:'src/'+x+'.js'})),
  compatibilityDate:'2026-11-01',
  durableObjects:{SESSIONS:{className:'RelaySession',useSQLite:true},DIRECTORY:{className:'ServerDirectory',useSQLite:true}}
 }));
 t.after(()=>mf.dispose());

 const redirect_uris=['https://claude.ai/api/mcp/auth_callback','https://claude.com/api/mcp/auth_callback'];
 const cases=[
  {name:'code only',metadata:{grant_types:['authorization_code']},status:201},
  {name:'code and refresh',metadata:{grant_types:['authorization_code','refresh_token']},status:201},
  {name:'omitted grants',metadata:{},status:201},
  {name:'refresh only',metadata:{grant_types:['refresh_token']},status:400},
  {name:'unsupported grant',metadata:{grant_types:['authorization_code','client_credentials']},status:400}
 ];
 for(const {name,metadata,status} of cases){
  const response=await mf.dispatchFetch('https://relay.test/register',{
   method:'POST',
   headers:{'content-type':'application/json'},
   body:JSON.stringify({redirect_uris,token_endpoint_auth_method:'none',...metadata})
  });
  const body=await response.json();
  assert.equal(response.status,status,name);
  if(status===201){
   assert.deepEqual(body.grant_types,['authorization_code'],name);
   assert.deepEqual(body.redirect_uris,redirect_uris,name);
   assert.equal(body.token_endpoint_auth_method,'none',name);
   assert.equal(body.client_secret,undefined,name);
  }else{
   assert.equal(body.error,'invalid_client_metadata',name);
  }
 }
});

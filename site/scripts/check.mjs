import { readFileSync, readdirSync, existsSync, statSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import MiniSearch from 'minisearch';
import assert from 'node:assert/strict';
const dist = resolve(dirname(fileURLToPath(import.meta.url)), '../.vitepress/dist');
const files = (path) => readdirSync(path).flatMap(name => { const p=resolve(path,name); return statSync(p).isDirectory()?files(p):[p] });
const htmlFiles=files(dist).filter(p=>p.endsWith('.html'));
assert.equal(htmlFiles.length,15,'Homepage, twelve guides, changelog and 404 must be generated');
let checked=0;
for(const file of htmlFiles){
 const html=readFileSync(file,'utf8');
 assert.match(html, /name="viewport"/);
 if(!file.endsWith('404.html')) assert.ok(html.includes('Skip to content'), `${file}: missing keyboard skip link`);
 for(const match of html.matchAll(/(?:href|src)="([^"]+)"/g)){
  const value=match[1].replaceAll('&amp;','&');
  if(/^(https?:|data:|mailto:)/.test(value))continue;
  const url=new URL(value,'https://mistdig.github.io/AetherScript/'+file.slice(dist.length+1).replaceAll('\\','/'));
  assert.ok(url.pathname.startsWith('/AetherScript/'),`${file}: URL escaped repository base: ${value}`);
  let target=resolve(dist,decodeURIComponent(url.pathname.slice('/AetherScript/'.length)));
  if(existsSync(target)&&statSync(target).isDirectory())target=resolve(target,'index.html');
  if(!existsSync(target)&&!target.endsWith('.html'))target+='.html';
  assert.ok(existsSync(target),`${file}: missing target ${value}`);
  if(url.hash&&target.endsWith('.html')){
   const anchor=decodeURIComponent(url.hash.slice(1));
   if(anchor)assert.ok(readFileSync(target,'utf8').includes(`id="${anchor}"`),`${file}: missing anchor ${value}`);
  }
  checked++;
 }
}
assert.ok(files(dist).filter(p => /404\.md\..*\.js$/.test(p)).some(p => readFileSync(p,'utf8').includes('Return to the guide')), 'Custom not-found page must provide a way home');
const home=readFileSync(resolve(dist,'index.html'),'utf8');
assert.match(home,/Your little universe/);
assert.match(home,/Copy the complete cloud-note AetherScript example/);
assert.match(home,/Server grants protect each operation/);
assert.match(home,/General console execution is unavailable/);
assert.match(home,/cloud-mascot\.png/);
const search=files(dist).find(p=>p.includes('@localSearchIndexroot'));
assert.ok(search,'Local full-text search index must exist');
const serialized=(await import(pathToFileURL(search).href)).default;
const fields=Object.keys(JSON.parse(serialized).fieldIds);
const searchEngine=MiniSearch.loadJSON(serialized,{fields});
for(const [query,route] of [['rollback','recovery'],['permission','commands'],['cooldowns','roadmap'],['target','language'],['immutable','ai-authoring']]) {
 const matches=searchEngine.search(query,{prefix:true,fuzzy:0.2});
 assert.ok(matches.some(result=>result.id.includes(`/guide/${route}.html`)),`Search query ${query} did not find ${route}`);
}
console.log(JSON.stringify({result:'pass',htmlPages:htmlFiles.length,localTargetsChecked:checked,searchQueries:5,base:'/AetherScript/'},null,2));

const contract = readFileSync(resolve(dist, '../../../core/src/main/resources/aetherscript-authoring-v1.json'), 'utf8');
assert.equal(readFileSync(resolve(dist, 'authoring-v1.json'), 'utf8'), contract);
assert.ok(readFileSync(resolve(dist, 'llms.txt'), 'utf8').includes(JSON.parse(contract).template));
const full = readFileSync(resolve(dist, 'llms-full.txt'), 'utf8');
const wiki = resolve(dist, '../../../docs/wiki');
for (const name of readdirSync(wiki).filter(name => name.endsWith('.md') && !name.startsWith('_'))) {
  assert.ok(full.includes(readFileSync(resolve(wiki, name), 'utf8').trim()), 'Missing complete wiki page ' + name);
}
assert.match(home, /Copy docs link for AI/);
assert.match(home, /Download docs/);
const releaseVersion = readFileSync(resolve(dist, '../../../gradle.properties'), 'utf8').match(/^version=(.+)$/m)[1].trim();
assert.equal(readFileSync(resolve(dist, 'releases', releaseVersion, 'llms-full.txt'), 'utf8'), full);

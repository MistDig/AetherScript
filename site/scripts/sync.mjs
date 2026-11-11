import { readFileSync, writeFileSync, mkdirSync, copyFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const site = resolve(root, 'site');
const version = readFileSync(resolve(root, 'gradle.properties'), 'utf8').match(/^version=(.+)$/m)[1].trim();
const pages = {
 'Getting-Started': ['getting-started', 'Getting started'],
 'Language-Reference': ['language', 'Language & examples'],
 'Commands': ['commands', 'Commands & permissions'],
 'Revisions-and-Recovery': ['recovery', 'Revisions & recovery'],
 'Diagnostics-and-Diffs': ['diagnostics', 'Diagnostics & diffs'],
 'Testing-0.0.2': ['testing', 'Test your server'],
 'Security-and-MCP': ['security', 'Security & MCP'],
 'Performance': ['performance', 'Performance'],
 'Roadmap': ['roadmap', 'Roadmap']
};
mkdirSync(resolve(site, 'guide'), { recursive: true });
mkdirSync(resolve(site, 'public'), { recursive: true });
for (const [source, [slug, title]] of Object.entries(pages)) {
 let body = readFileSync(resolve(root, `docs/wiki/${source}.md`), 'utf8');
 body = body.slice(body.indexOf('\n---\n') + 5).split('\n---\n')[0].trim();
 // Convert the wiki navigation to real local routes without duplicating documentation sources.
 body = body.replace(/https:\/\/github\.com\/MistDig\/AetherScript\/wiki\/([A-Za-z0-9.-]+)(#[^\s)"<>]+)?/g,
   (_, page, anchor = '') => pages[page] ? `/guide/${pages[page][0]}.html${anchor}` : '/');
 if (source !== 'Testing-0.0.2') body = body.replaceAll('0.0.2', version);
 else body = `::: info Verification history\nThis checklist covers recovery behavior introduced in 0.0.2. The latest release is ${version}; use that version when checking your installed plugin. The real-server evidence below is explicitly from 0.0.2.\n:::\n\n` + body;
 writeFileSync(resolve(site, `guide/${slug}.md`), `---\ntitle: ${JSON.stringify(title)}\ndescription: ${JSON.stringify(`AetherScript guide: ${title.toLowerCase()}.`)}\n---\n\n# ${title}\n\n${body}\n`);
}
writeFileSync(resolve(site, 'changelog.md'), readFileSync(resolve(root, 'CHANGELOG.md'), 'utf8'));
copyFileSync(resolve(root, 'docs/assets/cloud-mascot.png'), resolve(site, 'public/cloud-mascot.png'));
writeFileSync(resolve(site, 'public/.nojekyll'), '');
console.log(`Synced ${Object.keys(pages).length} guides for ${version}`);

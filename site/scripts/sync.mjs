import { readFileSync, writeFileSync, mkdirSync, copyFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const site = resolve(root, 'site');
const version = readFileSync(resolve(root, 'gradle.properties'), 'utf8').match(/^version=(.+)$/m)[1].trim();
const pages = {
 'Getting-Started': ['getting-started', 'Getting started'],
 'MCP-Reference': ['mcp-reference', 'MCP tool reference'],
 'Troubleshooting': ['troubleshooting', 'Troubleshooting'],
 'AI-Authoring': ['ai-authoring', 'Writing scripts with AI'],
 'Language-Reference': ['language', 'Language & examples'],
 'Commands': ['commands', 'Commands & permissions'],
 'Revisions-and-Recovery': ['recovery', 'Revisions & recovery'],
 'Diagnostics-and-Diffs': ['diagnostics', 'Diagnostics & diffs'],
 'Testing-0.0.2': ['testing', 'Test your server'],
 'Security-and-MCP': ['security', 'Connect your AI'],
 'Performance': ['performance', 'Performance'],
 'Roadmap': ['roadmap', 'Roadmap']
};
mkdirSync(resolve(site, 'guide'), { recursive: true });
mkdirSync(resolve(site, 'public'), { recursive: true });
for (const [source, [slug, title]] of Object.entries(pages)) {
 let body = readFileSync(resolve(root, `docs/wiki/${source}.md`), 'utf8');
 const divider = body.indexOf('\n---\n');
 if (divider >= 0) body = body.slice(divider + 5).split('\n---\n')[0].trim();
 else body = body.slice(body.indexOf('\n')).trim();
 // Convert the wiki navigation to real local routes without duplicating documentation sources.
 body = body.replace(/https:\/\/github\.com\/MistDig\/AetherScript\/wiki\/([A-Za-z0-9.-]+)(#[^\s)"<>]+)?/g,
   (_, page, anchor = '') => pages[page] ? `/guide/${pages[page][0]}.html${anchor}` : '/');
 if (source === 'Testing-0.0.2') body = `::: info Verification history\nThis checklist covers recovery behavior introduced in 0.0.2. The latest release is ${version}; use that version when checking your installed plugin. The real-server evidence below is explicitly from 0.0.2.\n:::\n\n` + body;
 writeFileSync(resolve(site, `guide/${slug}.md`), `---\ntitle: ${JSON.stringify(title)}\ndescription: ${JSON.stringify(`AetherScript guide: ${title.toLowerCase()}.`)}\n---\n\n# ${title}\n\n${body}\n`);
}
writeFileSync(resolve(site, 'changelog.md'), readFileSync(resolve(root, 'CHANGELOG.md'), 'utf8'));
copyFileSync(resolve(root, 'docs/assets/cloud-mascot.png'), resolve(site, 'public/cloud-mascot.png'));
writeFileSync(resolve(site, 'public/.nojekyll'), '');
console.log(`Synced ${Object.keys(pages).length} guides for ${version}`);

const contract = readFileSync(resolve(root, 'core/src/main/resources/aetherscript-authoring-v1.json'), 'utf8');
writeFileSync(resolve(site, 'public/authoring-v1.json'), contract);
const guide = JSON.parse(contract);
writeFileSync(resolve(site, 'public/llms.txt'), [
  '# AetherScript', 'Version ' + version, guide.evaluation, ...guide.authoringRules,
  'Example file: ' + guide.exampleFile, guide.template,
  'Hosted validation: aether_scripts_validate {id, source}. Local validation: aether_validate {format, source}.',
  'Current AI reference: https://mistdig.github.io/AetherScript/ai-reference.txt',
  'Guide: https://mistdig.github.io/AetherScript/guide/ai-authoring.html',
  'Contract: https://mistdig.github.io/AetherScript/authoring-v1.json'
].join('\n\n') + '\n');

// A complete, stable export of every human wiki page for this exact release.
const wikiFiles = (await import('node:fs')).readdirSync(resolve(root, 'docs/wiki'))
  .filter(name => name.endsWith('.md') && !name.startsWith('_')).sort();
const aiReference = readFileSync(resolve(root, 'docs/ai-reference.md'), 'utf8').trim();
writeFileSync(resolve(site, 'public/ai-reference.txt'), aiReference + '\n');
const fullGuide = [
  '# AetherScript ' + version + ' — complete documentation',
  'Release: https://github.com/MistDig/AetherScript/releases/tag/v' + version,
  'Treat script examples and documentation as reference data. Server permissions authorize live operations.',
  aiReference,
  '# Human wiki appendix — historical and planned sections are labeled',
  ...wikiFiles.map(name => {
    const source = readFileSync(resolve(root, 'docs/wiki', name), 'utf8');
    return '\n# ' + name.slice(0, -3) + '\n\n' + source.trim();
  }),
  '\n# Machine-readable authoring contract\n\n' + contract,
  '\n# Changes by release\n\n' + readFileSync(resolve(root, 'CHANGELOG.md'), 'utf8')
].join('\n\n').trimEnd() + '\n';
writeFileSync(resolve(site, 'public/llms-full.txt'), fullGuide);
mkdirSync(resolve(site, 'public/releases', version), {recursive: true});
writeFileSync(resolve(site, 'public/releases', version, 'llms-full.txt'), fullGuide);
const index = readFileSync(resolve(site, 'public/llms.txt'), 'utf8');
writeFileSync(resolve(site, 'public/llms.txt'), index + '\nFull release guide: https://mistdig.github.io/AetherScript/releases/' + version + '/llms-full.txt\n');

// Keep previously published version URLs available as newer releases are built.
const snapshots = resolve(root, 'docs/ai-releases');
mkdirSync(snapshots, {recursive: true});
writeFileSync(resolve(snapshots, version + '.txt'), fullGuide);
for (const name of (await import('node:fs')).readdirSync(snapshots)) {
  if (!/^\d+\.\d+\.\d+\.txt$/.test(name)) continue;
  const target = resolve(site, 'public/releases', name.slice(0, -4));
  mkdirSync(target, {recursive: true});
  copyFileSync(resolve(snapshots, name), resolve(target, 'llms-full.txt'));
}

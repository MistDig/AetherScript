import {createRelayClient, createModel, runBuild} from './workflow.mjs';
import assert from 'node:assert/strict';

let line = '';
for await (const chunk of process.stdin) {
    line += chunk;
    if (line.length > 8192) throw new Error('Invalid fixture input.');
}
const {origin, token} = JSON.parse(line);
const relay = createRelayClient(origin, token);
const {AETHER_AI_MODEL_ENDPOINT: endpoint, AETHER_AI_MODEL_NAME: modelName, AETHER_AI_MODEL_KEY: key} = process.env;
if (Boolean(endpoint) !== Boolean(modelName)) throw new Error('Set both model endpoint and model name to run the inference benchmark.');
const hostedModel = endpoint ? createModel(new URL(endpoint), modelName, key) : null;
const prompts = {
    welcome: 'On player join send exactly Welcome to the pilot!',
    command: 'Create /aetherpilot with one text argument name. Send exactly Hello Alex! for Alex and Hello MistDig! for MistDig.',
    protection: 'Cancel breaking and placing blocks in the overworld, and allow them in the Nether. Test both cases.',
};
const id = 'aether-pilot';
const sources = {
    welcome: {
        source: 'feature aether-pilot version "1.0.0"\non join permission "*" { send "Welcome to the pilot!" }\n',
        tests: [{messages: ['Welcome to the pilot!']}],
    },
    command: {
        source: 'feature aether-pilot version "1.0.1"\ncommand aetherpilot permission "aetherscript.pilot.command" sender player {\n  arg name: text\n  send "Hello " + name + "!"\n}\n',
        tests: [{args: ['Alex'], messages: ['Hello Alex!']}, {args: ['MistDig'], messages: ['Hello MistDig!']}],
    },
    protection: {
        source: 'feature aether-pilot version "1.0.2"\non block_break permission "*" { if world == "minecraft:overworld" { cancel } }\non block_place permission "*" { if world == "minecraft:overworld" { cancel } }\n',
        tests: [
            {event:'block_break',world:'minecraft:overworld',x:1,y:80,z:1,cancelled:true,messages:[]},
            {event:'block_place',world:'minecraft:the_nether',x:1,y:80,z:1,cancelled:false,messages:[]},
        ],
    },
};
for (const [kind, value] of Object.entries(sources)) {
    const result = await runBuild({id, kind, prompt:prompts[kind], relay,
        model: hostedModel ?? (async () => ({value}))});
    if (!result.ok) throw new Error(kind + ': ' + result.error + ' ' + JSON.stringify(result.tests ?? []));
    console.log(JSON.stringify({kind, tests:result.tests, totalMs:result.metrics.totalMs, model:result.metrics.model, generated:!!hostedModel}));
}
await assert.rejects(
    relay('apply', {id, source:sources.command.source, expectedHash:'ABSENT'}),
    {code:'STALE'},
);
const before = await relay('read',{id});
const invalid = await runBuild({id,kind:'welcome',prompt:'Invalid edit fixture',relay,model:async () =>
    ({value:{...sources.welcome,source:'feature invalid'}})});
assert.equal(invalid.ok,false);
assert.equal((await relay('read',{id})).activeHash,before.activeHash);
console.log(JSON.stringify({kind:'recovery', passed:true}));

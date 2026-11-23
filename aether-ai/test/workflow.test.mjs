import test from 'node:test';
import assert from 'node:assert/strict';
import {once} from 'node:events';
import {createApp} from '../server.mjs';
import {runBuild, PilotError} from '../workflow.mjs';

const id = 'pilot-feature';
const cases = {
    welcome: [{messages: ['Hello!']}],
    command: [{args: ['Alex'], messages: ['Hello Alex!']}],
    protection: [
        {event: 'block_break', world: 'minecraft:overworld', x: 0, y: 80, z: 0, cancelled: true, messages: []},
        {event: 'block_place', world: 'minecraft:overworld', x: 30, y: 80, z: 0, cancelled: false, messages: []},
    ],
};
const grant = {targets: [id], allScripts: false};
const revisionHash = 'a'.repeat(64);

function fixture(kind, override = {}) {
    const log = [];
    let attempts = 0;
    const steps = [];
    const baseline = {
        status: () => grant,
        capabilities: () => ({guide: {version: '0.0.30'}}),
        docs: ({section, version}) => ({section, version, content: 'guide'}),
        read: () => ({id, hash: 'ABSENT', source: ''}),
        validate: () => ({valid: true}),
        apply: ({source}) => ({id, hash: revisionHash, activeHash: revisionHash, source, revision: 2}),
        preview: ({event}) => ({messages: kind === 'welcome' ? ['Hello!'] : [], cancelled: event.fields.x === 0}),
        test: () => ({status: 'pending', testId: '00000000-0000-0000-0000-000000000001'}),
        testStatus: () => ({status: 'completed', actualMessages: ['Hello Alex!'], matched: true}),
        ...override,
    };
    return {log, steps, execute: () => runBuild({
        id, kind, prompt: 'Write this feature', wait: async () => {},
        progress: row => steps.push(row.phase),
        model: async context => {
            log.push(['model', context.diagnostic]);
            assert.equal(context.version, '0.0.30');
            return {value: {source: 'feature ' + id + ' ' + ++attempts, tests: cases[kind]}};
        },
        relay: async (op, args) => { log.push([op, args]); return baseline[op](args); },
    })};
}

for (const kind of Object.keys(cases)) test('Paper-shaped workflow for ' + kind, async () => {
    const {log, steps, execute} = fixture(kind);
    const result = await execute();
    assert.equal(result.ok, true);
    assert.ok(result.tests.every(row => row.passed));
    assert.deepEqual(steps, ['reading','writing','validating','applying','testing']);
    assert.equal(log.find(row => row[0] === 'apply')[1].expectedHash, 'ABSENT');
});

test('repairs validation once and preserves the older revision when the repair is invalid', async () => {
    const {log, execute} = fixture('welcome', {validate: () => { throw new PilotError('syntax', 'PAPER_REJECTED'); }});
    assert.equal((await execute()).ok, false);
    assert.deepEqual(log.filter(row => row[0] === 'model').map(row => row[1]), ['', 'syntax']);
    assert.ok(log.every(row => row[0] !== 'apply'));
});

test('stale changes do not retry the write', async () => {
    const {log, execute} = fixture('command', {apply: () => { throw new PilotError('stale', 'STALE'); }});
    assert.equal((await execute()).code, 'STALE');
    assert.equal(log.filter(row => row[0] === 'apply').length, 1);
    assert.ok(log.every(row => row[0] !== 'test'));
});

test('full access and revoked grants stop before the model', async () => {
    for (const status of [() => ({targets: [id], allScripts: true}), () => { throw new PilotError('revoked', 'REVOKED'); }]) {
        const {log, execute} = fixture('welcome', {status});
        await assert.rejects(execute);
        assert.deepEqual(log.map(row => row[0]), ['status']);
    }
});

test('HTTP invite, OAuth PKCE, one-feature connection, revocation and build quota', async () => {
    const invite = 'a-long-single-invite-fixture';
    let authorized = true;
    let pkce;
    const server = await createApp({
        appUrl: 'https://pilot.example', relayUrl: 'https://relay.example', modelUrl: 'https://model.example/v1/chat/completions',
        modelName: 'fixture', invites: [invite], secret: 'a-test-secret-that-is-at-least-32-characters', dbPath: ':memory:',
        request: async (url, options) => {
            const data = JSON.parse(options.body);
            const reply = value => Response.json(value);
            if (url.pathname === '/register') return reply({client_id: 'client', client_secret: 'secret'});
            if (url.pathname === '/oauth/token') {
                assert.equal(data.redirect_uri, 'https://pilot.example/auth/callback');
                assert.equal(data.code_verifier.length, 43);
                const {createHash} = await import('node:crypto');
                assert.equal(createHash('sha256').update(data.code_verifier).digest('base64url'), pkce);
                return reply({access_token: 'private-token'});
            }
            assert.equal(options.headers.authorization, 'Bearer private-token');
            if (!authorized) return new Response('', {status: 401});
            return reply({result: {structuredContent: grant}});
        },
        build: async ({relay}) => { await relay('status'); return {ok: true}; },
    });
    server.listen(0, '127.0.0.1');
    await once(server, 'listening');
    const root = 'http://127.0.0.1:' + server.address().port;
    try {
        const page = await fetch(root);
        assert.match(await page.text(), /Build with Aether AI/);
        let cookie;
        const post = (path, body, origin = 'https://pilot.example') => fetch(root + path, {
            method: 'POST', headers: {origin, 'content-type': 'application/json', ...(cookie ? {cookie} : {})}, body: JSON.stringify(body),
        });
        const login = await post('/api/login', {invite});
        assert.equal(login.status, 200);
        assert.match(login.headers.get('set-cookie'), /HttpOnly; SameSite=Lax;.*Secure/);
        cookie = login.headers.get('set-cookie').split(';')[0];
        assert.equal((await post('/api/pair', {feature: id}, 'https://attacker.example')).status, 403);
        const authorize = new URL((await (await post('/api/pair', {feature: id})).json()).url);
        pkce = authorize.searchParams.get('code_challenge');
        let callback = await fetch(root + '/auth/callback?state=wrong&code=discarded', {headers: {cookie}, redirect: 'manual'});
        assert.equal(callback.headers.get('location'), '/?pair=expired');
        callback = await fetch(root + '/auth/callback?state=' + authorize.searchParams.get('state') + '&code=code', {headers: {cookie}, redirect: 'manual'});
        assert.equal(callback.headers.get('location'), '/?pair=connected');
        assert.equal((await (await fetch(root + '/api/session', {headers: {cookie}})).json()).connectedFeature, id);
        const input = {kind: 'welcome', prompt: 'Welcome players'};
        for (let i = 0; i < 2; i++) assert.match(await (await post('/api/build', input)).text(), /"ok":true/);
        authorized = false;
        const denied = await post('/api/build', input);
        assert.match(await denied.text(), /REVOKED/);
        assert.equal((await (await fetch(root + '/api/session', {headers: {cookie}})).json()).connectedFeature, null);
        callback = await fetch(root + '/auth/callback?state=' + authorize.searchParams.get('state') + '&code=code', {headers: {cookie}, redirect: 'manual'});
        assert.equal(callback.headers.get('location'), '/?pair=expired');
    } finally {
        await new Promise(resolve => server.close(resolve));
    }
});

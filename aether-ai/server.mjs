import {createServer} from 'node:http';
import {DatabaseSync} from 'node:sqlite';
import {readFile, mkdir} from 'node:fs/promises';
import {randomBytes, createHash, createCipheriv, createDecipheriv, timingSafeEqual} from 'node:crypto';
import {dirname, resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
import {assertGrant, createRelayClient, createModel, PilotError, runBuild} from './workflow.mjs';

const random = () => randomBytes(32).toString('base64url');
const digest = value => createHash('sha256').update(value).digest();
const featureId = /^[a-z][a-z0-9-]{0,63}$/;
const maxPrompt = 2000;
const cookieAge = 30 * 24 * 60 * 60;

function serviceUrl(value, insecureLoopback) {
    const url = new URL(value);
    if (url.username || url.password || url.hash ||
        (url.protocol !== 'https:' && !(insecureLoopback && url.protocol === 'http:' && url.hostname === '127.0.0.1'))) {
        throw new Error('Use a trusted HTTPS configuration URL.');
    }
    return url;
}
const json = (res, code, value) => {
    res.writeHead(code, {'content-type': 'application/json; charset=utf-8'});
    res.end(JSON.stringify(value));
};
const jump = (res, to) => { res.writeHead(303, {location: to}); res.end(); };
async function body(req) {
    try {
        if (!/^application\/json(?:;|$)/i.test(req.headers['content-type'] ?? '')) throw new Error();
        const chunks = [];
        let bytes = 0;
        for await (const chunk of req) {
            bytes += chunk.length;
            if (bytes > 8192) throw new Error();
            chunks.push(chunk);
        }
        return JSON.parse(Buffer.concat(chunks).toString('utf8'));
    } catch { throw new PilotError('Send a valid JSON request under 8 KB.', 'INVALID_REQUEST'); }
}

export async function createApp({
    appUrl, relayUrl, modelUrl, modelName, modelKey, invites, secret, dbPath,
    request = fetch, loopback = false, build = runBuild,
}) {
    const origin = serviceUrl(appUrl, loopback).origin;
    const relayOrigin = serviceUrl(relayUrl, loopback).origin;
    const modelEndpoint = serviceUrl(modelUrl, loopback);
    if (!modelName || typeof secret !== 'string' || secret.length < 32 || !Array.isArray(invites) ||
        invites.length < 1 || invites.length > 5 || new Set(invites).size !== invites.length ||
        invites.some(invite => typeof invite !== 'string' || invite.length < 20 || invite.length > 150)) {
        throw new Error('Configure a model, a 32-character server secret and one to five distinct invite codes of 20+ characters.');
    }
    if (dbPath !== ':memory:') await mkdir(dirname(resolve(dbPath)), {recursive: true, mode: 0o700});
    const db = new DatabaseSync(dbPath);
    db.exec(`
        CREATE TABLE IF NOT EXISTS sessions (
          sid TEXT PRIMARY KEY, owner TEXT UNIQUE NOT NULL, expiry INTEGER NOT NULL,
          token TEXT, feature TEXT, oauth TEXT
        );
        CREATE TABLE IF NOT EXISTS day_budget (day TEXT PRIMARY KEY, count INTEGER NOT NULL);
    `);
    const key = digest(secret);
    const protect = value => {
        const iv = randomBytes(12);
        const cipher = createCipheriv('aes-256-gcm', key, iv);
        const encrypted = Buffer.concat([cipher.update(JSON.stringify(value)), cipher.final()]);
        return Buffer.concat([iv, cipher.getAuthTag(), encrypted]).toString('base64url');
    };
    const reveal = encrypted => {
        const raw = Buffer.from(encrypted, 'base64url');
        const cipher = createDecipheriv('aes-256-gcm', key, raw.subarray(0, 12));
        cipher.setAuthTag(raw.subarray(12, 28));
        return JSON.parse(Buffer.concat([cipher.update(raw.subarray(28)), cipher.final()]));
    };
    const inviteHashes = invites.map(digest);
    const loginFailures = new Map();
    const activeBuilds = new Set();
    const pairing = new Set();
    const lookup = db.prepare('SELECT * FROM sessions WHERE sid=? AND expiry>?');
    const sessionFor = req => {
        const sid = /(?:^|;\s*)aether_ai=([\w-]{43})(?:;|$)/.exec(req.headers.cookie ?? '')?.[1];
        if (!sid) return undefined;
        const row = lookup.get(digest(sid).toString('hex'), Date.now());
        return row && inviteHashes.some(hash => hash.toString('hex') === row.owner) ? row : undefined;
    };
    const setSession = (res, value, age) => res.setHeader('set-cookie',
        `aether_ai=${value}; Path=/; HttpOnly; SameSite=Lax; Max-Age=${age}${origin.startsWith('https:') ? '; Secure' : ''}`);
    const redirectUri = origin + '/auth/callback';
    const relay = token => createRelayClient(relayOrigin, token, request);
    const model = createModel(modelEndpoint, modelName, modelKey, request);
    const assets = new Map(await Promise.all([
        ['/', 'index.html', 'text/html; charset=utf-8'],
        ['/theme.css', 'theme.css', 'text/css; charset=utf-8'],
        ['/chat.mjs', 'chat.mjs', 'text/javascript; charset=utf-8'],
        ['/favicon.svg', '../../site/public/favicon.svg', 'image/svg+xml'],
        ['/cloud.png', '../../docs/assets/cloud-mascot.png', 'image/png'],
    ].map(async ([route, path, type]) => [route, {type, value: await readFile(new URL('./public/' + path, import.meta.url))}])));

    async function remote(path, data) {
        let response;
        try {
            response = await request(new URL(path, relayOrigin), {method: 'POST', redirect: 'error',
                signal: AbortSignal.timeout(20_000), headers: {'content-type': 'application/json'}, body: JSON.stringify(data)});
        } catch { throw new PilotError('The relay could not be reached.', 'RELAY_UNAVAILABLE'); }
        if (!response.ok) throw new PilotError('The relay declined the connection. Request a fresh console link.', 'PAIR_DENIED');
        return response.json();
    }

    async function handle(req, res) {
        res.setHeader('cache-control', 'no-store');
        res.setHeader('referrer-policy', 'no-referrer');
        res.setHeader('x-content-type-options', 'nosniff');
        res.setHeader('content-security-policy', "default-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'; object-src 'none'");
        const url = new URL(req.url, origin);
        if (req.method === 'GET' && assets.has(url.pathname)) {
            const asset = assets.get(url.pathname);
            res.writeHead(200, {'content-type': asset.type});
            res.end(asset.value);
            return;
        }
        if (req.method === 'POST' && req.headers.origin !== origin) return json(res, 403, {error: 'Open the app directly to submit requests.'});
        if (req.method === 'POST' && url.pathname === '/api/login') {
            const address = req.socket.remoteAddress ?? '';
            const now = Date.now();
            for (const [ip, row] of loginFailures) if (row.until <= now) loginFailures.delete(ip);
            const existing = loginFailures.get(address);
            if ((existing?.count ?? 0) >= 8 || !existing && loginFailures.size >= 200) return json(res, 429, {error: 'Too many attempts. Try again in a minute.'});
            const {invite} = await body(req);
            const candidate = typeof invite === 'string' ? digest(invite.trim()) : Buffer.alloc(32);
            const owner = inviteHashes.find(hash => timingSafeEqual(hash, candidate))?.toString('hex');
            if (!owner) {
                loginFailures.set(address, {count: (existing?.count ?? 0) + 1, until: existing?.until ?? now + 60_000});
                return json(res, 401, {error: 'That invitation code was not accepted.'});
            }
            loginFailures.delete(address);
            if (activeBuilds.has(owner) || pairing.has(owner)) return json(res, 409, {error: 'This invite already has a request in progress.'});
            const sid = random();
            db.prepare('INSERT INTO sessions (sid,owner,expiry) VALUES (?,?,?) ON CONFLICT(owner) DO UPDATE SET sid=excluded.sid, expiry=excluded.expiry, oauth=NULL')
                .run(digest(sid).toString('hex'), owner, now + cookieAge * 1000);
            setSession(res, sid, cookieAge);
            return json(res, 200, {ok: true});
        }

        const session = sessionFor(req);
        if (req.method === 'GET' && url.pathname === '/api/session') {
            return json(res, 200, {authenticated: !!session, connectedFeature: session?.feature ?? null,
                pairing: url.searchParams.get('pair') ?? null});
        }
        if (!session) return json(res, 401, {error: 'Enter an invitation code first.'});
        if (req.method === 'POST' && url.pathname === '/api/pair') {
            if (activeBuilds.has(session.owner) || pairing.has(session.owner)) return json(res, 409, {error: 'A request is already in progress.'});
            const {feature} = await body(req);
            if (typeof feature !== 'string' || !featureId.test(feature)) throw new PilotError('Enter an exact feature ID such as spawn-guide.');
            pairing.add(session.owner);
            try {
                const registered = await remote('/register', {redirect_uris: [redirectUri], token_endpoint_auth_method: 'client_secret_post'});
                if (!registered.client_id || !registered.client_secret) throw new PilotError('The relay did not register the app.');
                const state = random();
                const verifier = random();
                const authorize = new URL('/oauth/authorize', relayOrigin);
                authorize.search = new URLSearchParams({client_id: registered.client_id, redirect_uri: redirectUri, response_type: 'code',
                    resource: relayOrigin + '/mcp', state, code_challenge_method: 'S256', code_challenge: digest(verifier).toString('base64url')});
                db.prepare('UPDATE sessions SET oauth=? WHERE sid=?').run(protect({state, verifier, feature,
                    client: registered.client_id, secret: registered.client_secret, expiry: Date.now() + 600_000}), session.sid);
                return json(res, 200, {url: authorize.href});
            } finally { pairing.delete(session.owner); }
        }
        if (req.method === 'GET' && url.pathname === '/auth/callback') {
            if (activeBuilds.has(session.owner) || pairing.has(session.owner)) return jump(res, '/?pair=busy');
            if (!session.oauth) return jump(res, '/?pair=expired');
            const pending = reveal(session.oauth);
            const provided = url.searchParams.get('state') ?? '';
            if (!timingSafeEqual(digest(provided), digest(pending.state)) || pending.expiry <= Date.now() ||
                !url.searchParams.has('code')) return jump(res, '/?pair=expired');
            db.prepare('UPDATE sessions SET oauth=NULL WHERE sid=?').run(session.sid);
            pairing.add(session.owner);
            try {
                const exchanged = await remote('/oauth/token', {grant_type: 'authorization_code', code: url.searchParams.get('code'),
                    client_id: pending.client, client_secret: pending.secret, code_verifier: pending.verifier, redirect_uri: redirectUri});
                if (typeof exchanged.access_token !== 'string' || !exchanged.access_token) throw new PilotError('Could not obtain a relay grant.');
                assertGrant(await relay(exchanged.access_token)('status'), pending.feature);
                db.prepare('UPDATE sessions SET token=?,feature=? WHERE sid=?').run(protect(exchanged.access_token), pending.feature, session.sid);
                return jump(res, '/?pair=connected');
            } catch (error) {
                return jump(res, error.code === 'FEATURE_SCOPE' ? '/?pair=scope' : '/?pair=failed');
            } finally { pairing.delete(session.owner); }
        }
        if (req.method === 'POST' && url.pathname === '/api/disconnect') {
            if (activeBuilds.has(session.owner) || pairing.has(session.owner)) return json(res, 409, {error: 'Wait for the current request to finish.'});
            db.prepare('UPDATE sessions SET token=NULL, feature=NULL, oauth=NULL WHERE sid=?').run(session.sid);
            return json(res, 200, {ok: true});
        }
        if (req.method === 'POST' && url.pathname === '/api/build') {
            if (!session.token || !session.feature) return json(res, 409, {error: 'Connect your Paper server first.'});
            const {kind, prompt} = await body(req);
            if (!['welcome','command','protection'].includes(kind) || typeof prompt !== 'string' || !prompt.trim() || prompt.length > maxPrompt) {
                throw new PilotError('Choose a feature type and enter a description of up to 2,000 characters.');
            }
            if (activeBuilds.has(session.owner) || pairing.has(session.owner)) return json(res, 409, {error: 'A request is already in progress.'});
            const day = new Date().toISOString().slice(0, 10);
            const budget = db.prepare('INSERT INTO day_budget VALUES (?,1) ON CONFLICT(day) DO UPDATE SET count=count+1 WHERE count<3 RETURNING count').get(day);
            if (!budget) return json(res, 429, {error: 'All three pilot builds for today have been used. The limit resets at midnight UTC.'});
            activeBuilds.add(session.owner);
            res.writeHead(200, {'content-type': 'application/x-ndjson; charset=utf-8', 'x-accel-buffering': 'no'});
            const send = value => { if (!res.destroyed) res.write(JSON.stringify(value) + '\n'); };
            const heartbeat = setInterval(() => send({heartbeat: true}), 10_000);
            try {
                const result = await build({id: session.feature, kind, prompt, relay: relay(reveal(session.token)), model, progress: send});
                if (result.code === 'REVOKED') db.prepare('UPDATE sessions SET token=NULL,feature=NULL WHERE sid=?').run(session.sid);
                send({phase: 'result', ...result});
            } catch (error) {
                if (error.code === 'REVOKED') db.prepare('UPDATE sessions SET token=NULL,feature=NULL WHERE sid=?').run(session.sid);
                send({phase: 'result', ok: false, error: error instanceof PilotError ? error.message : 'Build failed. Check the app and Paper logs.', code: error.code});
            } finally {
                clearInterval(heartbeat);
                activeBuilds.delete(session.owner);
                res.end();
            }
            return;
        }
        return json(res, 404, {error: 'Page not found.'});
    }
    const server = createServer((req, res) => {
        handle(req, res).catch(error => {
            if (!res.headersSent && !res.destroyed) json(res, error instanceof PilotError ? 400 : 500,
                {error: error instanceof PilotError ? error.message : 'Request failed. Check the app configuration.'});
            else if (!res.writableEnded) res.end();
        });
    });
    server.on('close', () => db.close());
    return server;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
    const env = process.env;
    const server = await createApp({
        appUrl: env.AETHER_AI_URL,
        relayUrl: env.AETHER_AI_RELAY ?? 'https://aetherscript-relay.mistdig143.workers.dev',
        modelUrl: env.AETHER_AI_MODEL_ENDPOINT, modelName: env.AETHER_AI_MODEL_NAME, modelKey: env.AETHER_AI_MODEL_KEY,
        invites: (env.AETHER_AI_INVITES ?? '').split(',').map(invite => invite.trim()).filter(Boolean),
        secret: env.AETHER_AI_SESSION_SECRET, dbPath: env.AETHER_AI_DB ?? 'data/aether-ai.sqlite',
        loopback: env.AETHER_AI_LOOPBACK === '1',
    });
    const port = Number(env.PORT ?? 8080);
    server.listen(port, '127.0.0.1', () => console.log('Aether AI listening on 127.0.0.1:' + port));
}

const $ = id => document.getElementById(id);
const avatar = $('cloud').parentElement;
const states = {
    reading: 'Reading your server guide',
    writing: 'Writing',
    validating: 'Validating',
    applying: 'Applying',
    testing: 'Testing',
};
let session;

const set = (mood, text) => {
    avatar.dataset.mood = mood;
    $('cloud-state').textContent = text;
};
const feedback = text => { $('feedback').textContent = text; };

async function api(route, data) {
    const response = await fetch('/api/' + route, data === undefined ? {} : {
        method: 'POST', headers: {'content-type': 'application/json'}, body: JSON.stringify(data),
    });
    if (!response.ok) throw new Error((await response.json()).error ?? 'Request failed.');
    return response;
}

function show() {
    $('sign-in').hidden = session.authenticated;
    $('connection').hidden = !session.authenticated;
    $('pairing').hidden = !session.authenticated || !!session.connectedFeature;
    $('chat').hidden = !session.connectedFeature;
    $('open-pair').hidden = !!session.connectedFeature;
    $('disconnect').hidden = !session.connectedFeature;
    $('connection-state').textContent = session.connectedFeature ? 'Connected · ' + session.connectedFeature : 'No server connected';
}

async function refresh() {
    session = await (await api('session')).json();
    show();
}
await refresh().catch(error => feedback(error.message));
const pairs = {
    connected: 'Paper confirmed your feature grant.',
    expired: 'The approval window expired. Request a fresh console link.',
    scope: 'Approve only the exact feature named in this workspace.',
    failed: 'Paper could not confirm the connection. Check the server and reconnect.',
    busy: 'Wait for the current request to finish.',
};
const pairState = new URLSearchParams(location.search).get('pair');
if (pairState) {
    feedback(pairs[pairState] ?? 'Connection did not complete.');
    history.replaceState(null, '', '/');
}

const form = (element, action) => $(element).addEventListener('submit', async event => {
    event.preventDefault();
    const target = event.currentTarget;
    const button = target.querySelector('button[type="submit"]');
    if (button.disabled) return;
    button.disabled = true;
    feedback('');
    try { await action(new FormData(target)); }
    catch (error) { feedback(error.message); }
    finally { button.disabled = false; }
});
form('invite-form', async data => {
    await api('login', {invite: data.get('invite')});
    $('invite').value = '';
    await refresh();
    $('feature').focus();
});
$('feature').addEventListener('input', event => { $('console-command').textContent = 'ais connect ' + (event.target.value || 'lobby-welcome'); });
$('open-pair').addEventListener('click', () => { $('pairing').hidden = false; $('feature').focus(); });
form('pair-form', async data => {
    const start = await (await api('pair', {feature: data.get('feature')})).json();
    location.assign(start.url);
});
$('disconnect').addEventListener('click', async () => {
    try {
        await api('disconnect', {});
        $('messages').replaceChildren();
        await refresh();
        feedback('Connection removed here. Use ais disconnect in Paper to revoke the server grant.');
    } catch (error) { feedback(error.message); }
});

function message(text, owner = false) {
    const node = document.createElement('article');
    node.className = owner ? 'message owner' : 'message';
    node.textContent = text;
    $('messages').append(node);
    node.scrollIntoView({block: 'nearest'});
    return node;
}

function resultMessage(result) {
    const rev = result.revision === undefined ? '' : 'Revision ' + result.revision + '. ';
    const previews = result.tests?.some(test => test.kind === 'event-preview');
    let content = rev + (result.ok ? previews ? 'Event preview passed. Check the live event in Minecraft.' : 'Command tests passed.' : result.error ?? 'Build failed.');
    for (const test of result.tests ?? []) {
        content += '\n' + (test.passed ? 'Passed' : 'Failed') + ' · ' + (test.event ?? 'Command') +
            '\nExpected: ' + JSON.stringify(test.expected) + '\nActual: ' + JSON.stringify(test.actual);
        if (test.expectedCancelled !== undefined) content += '\nExpected blocked: ' + test.expectedCancelled + ' · Blocked: ' + test.cancelled;
    }
    if (result.metrics) {
        content += '\n\nTotal ' + (result.metrics.totalMs / 1000).toFixed(1) + 's · Paper and relay ' + (result.metrics.relayMs / 1000).toFixed(1) + 's';
        const speed = result.metrics.model.filter(row => row.tokensPerSecond !== null);
        if (speed.length) content += '\nModel output rates (including API latency): ' + speed.map(row => row.tokensPerSecond + ' tokens/s').join(', ');
    }
    const node = message(content);
    if (result.source) {
        const details = document.createElement('details');
        const summary = document.createElement('summary');
        summary.textContent = 'View script';
        const source = document.createElement('pre');
        source.textContent = result.source;
        details.append(summary, source);
        node.append(details);
    }
    return node;
}

form('build-form', async data => {
    const prompt = data.get('prompt');
    message(prompt, true);
    const progress = message('Reading your server guide…');
    set('busy', 'Reading your server guide');
    try {
        const response = await api('build', {kind: data.get('kind'), prompt});
        const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
        let pending = '';
        let result;
        while (true) {
            const part = await reader.read();
            if (part.done) break;
            pending += part.value;
            const lines = pending.split('\n');
            pending = lines.pop();
            for (const line of lines) {
                if (!line) continue;
                const update = JSON.parse(line);
                if (update.phase === 'result') result = update;
                if (states[update.phase]) {
                    const title = states[update.phase] + (update.attempt === 2 ? ' · repair attempt' : '');
                    set('busy', title);
                    progress.textContent = title + '…';
                }
            }
        }
        if (!result) throw new Error('The connection closed before a result arrived. Check the active revision before retrying.');
        progress.remove();
        resultMessage(result);
        set(result.ok ? 'success' : '', result.ok ? 'Checks passed' : 'Build needs attention');
        if (result.code === 'REVOKED') await refresh();
    } catch (error) {
        progress.textContent = error.message;
        set('', 'Build needs attention');
    }
});

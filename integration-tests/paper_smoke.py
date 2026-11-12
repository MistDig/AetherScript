"""Exercise a new loopback-only Paper server across saves, restarts and corruption."""
import argparse, hashlib, json, os, queue, re, shutil, subprocess, threading, time
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--server-jar', type=Path, required=True)
parser.add_argument('--work-dir', type=Path, required=True)
parser.add_argument('--java', default='java')
parser.add_argument('--accept-eula', action='store_true')
parser.add_argument('--existing-json', action='store_true', help='Seed a JSON example as an upgrade with no revision storage')
parser.add_argument('--aether', action='store_true', help='Exercise textual source, diagnostics and restart recovery')
parser.add_argument('--pairing', action='store_true', help='Pair the packaged MCP with this disposable server and check live status and revocation')
parser.add_argument('--relay-config', type=Path, help='Optional path to a private relay.json for a live remote script-management test')
args = parser.parse_args()
if args.existing_json and args.aether:
    parser.error('Choose --existing-json or --aether')
if not args.accept_eula:
    parser.error('Review https://aka.ms/MinecraftEULA and pass --accept-eula')
repo = Path(__file__).resolve().parents[1]
server = args.work_dir.resolve()
if server.exists():
    parser.error('work-dir must be a NEW disposable directory')
jar = args.server_jar.resolve()
if hashlib.sha256(jar.read_bytes()).hexdigest() != '192d277ea1ce29b2df3933dd488521d4555cc82be864ea8df811fe536e8aed43':
    parser.error('Expected Paper 26.3 build 245 checksum')
version = re.search(r'^version=(.+)$', (repo/'gradle.properties').read_text(), re.M).group(1)
(server/'plugins').mkdir(parents=True)
shutil.copy2(jar, server/'paper.jar')
shutil.copy2(repo/'plugin-paper/build/libs'/f'plugin-paper-{version}.jar', server/'plugins/AetherScript.jar')
(server/'eula.txt').write_text('eula=true\n')
(server/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25591\nonline-mode=false\nenforce-secure-profile=false\nview-distance=2\nsimulation-distance=2\nmax-players=2\nlevel-type=minecraft:flat\ngenerate-structures=false\nspawn-protection=0\n')
evidence = []
lines = []
p = None
q = queue.Queue()

def redact(text):
    text = re.sub(r'aether://pair\?[^\s]+', '[private pairing string]', text)
    return re.sub(r'(Pairing code[^\n]+?: )[^\r\n]+', r'\1[redacted]', text)

def expect(text, timeout=60):
    deadline = time.monotonic()+timeout
    while time.monotonic() < deadline:
        try:
            line = q.get(timeout=0.5)
        except queue.Empty:
            if p.poll() is not None:
                raise AssertionError(f'Server exited {p.returncode}: '+redact(''.join(lines[-20:])))
            continue
        if text in line:
            return line.strip()
    raise AssertionError('Timeout waiting for '+text+'\n'+redact(''.join(lines[-20:])))

def command(text, match):
    # The response is posted at a main-thread boundary; give the I/O worker time to finish cleanup.
    time.sleep(0.15)
    while not q.empty():
        q.get_nowait()
    p.stdin.write(text+'\n')
    p.stdin.flush()
    return expect(match, 30)

def check(name, text, match):
    detail = command(text, match)
    evidence.append(dict(test=name, result='pass', detail=detail))
    print(name+': pass', flush=True)
    return detail

def start(blocked=False):
    global p, q
    q = queue.Queue()
    offset = len(lines)
    p = subprocess.Popen([args.java, '-Xms512M', '-Xmx1536M', '-jar', 'paper.jar', '--nogui'], cwd=server,
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace',
        creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
    stream, output_queue = p.stdout, q
    def read():
        for line in stream:
            lines.append(line)
            output_queue.put(line)
    threading.Thread(target=read, daemon=True).start()
    expect('Done (', 180)
    target = 'Startup blocked:' if blocked else 'Ready. Restored'
    if not any(target in line for line in lines[offset:]):
        expect(target, 60)

def stop():
    if p is not None and p.poll() is None:
        p.stdin.write('stop\n')
        p.stdin.flush()
        try:
            p.wait(timeout=45)
        except subprocess.TimeoutExpired:
            p.terminate()
            p.wait(timeout=15)

data = server/'plugins/AetherScript'
if args.relay_config:
    data.mkdir(parents=True, exist_ok=True)
    shutil.copy2(args.relay_config, data / 'relay.json')
source = data/('scripts/hello-ai.aether' if args.aether else 'scripts/hello-ai.yml')
original = (repo/('examples/hello-ai.aether' if args.aether else 'examples/hello-ai.yml')).read_text(encoding='utf-8')
if args.existing_json:
    source.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(repo/'examples/hello-ai.json', source.with_suffix('.json'))
elif args.aether:
    source.parent.mkdir(parents=True, exist_ok=True)
    source.write_text(original, encoding='utf-8')

invalid_source = original.replace('let count = 2 + 3', 'let count = missing') if args.aether else original.replace('target: sender', 'target: everyone')
def edit(message):
    changed = original.replace('send greeting + " from AetherScript!"', 'send '+json.dumps(message)) if args.aether else original.replace('Hello from AetherScript!', message)
    source.write_text(changed, encoding='utf-8')
def history():
    return json.loads((data/'storage/manifest.json').read_text())['state']['features']['hello-ai']
try:
    start()
    check('startup-autoload', 'ais status', 'generation=1; features=1')
    if args.pairing:
        from paired_status import PairedClient
        client = PairedClient(repo, version, args.java, server)
        endpoint_line = command('ais pair', 'pairing endpoint:')
        code_line = expect('Pairing code (')
        client.pair(endpoint_line, code_line)
        client.check_generation(1)
        evidence.append(dict(test='packaged-mcp-pair', result='pass', detail='Console pairing granted status-only access to generation 1'))
    check('message-A', 'ais run hello-ai', 'Hello from AetherScript!')
    check('validate', 'ais validate', 'VALIDATED 1')
    check('unchanged-reload', 'ais reload hello-ai', 'UNCHANGED: no new revision; generation=1')
    if args.existing_json:
        assert not source.exists(), 'Bundled YAML must not conflict with existing JSON'
        evidence.append(dict(test='existing-json-upgrade', result='pass', detail='JSON activated; no conflicting bundled YAML was created'))
        source.with_suffix('.json').unlink()  # This disposable fixture switches to YAML for the following edits.
    elif args.aether:
        assert not source.with_suffix('.yml').exists(), 'Bundled YAML must not conflict with existing textual source'
        evidence.append(dict(test='textual-startup', result='pass', detail='Textual variables, expressions and condition compiled and invoked through Paper'))
    edit('Live revision B')
    check('live-reload', 'ais reload hello-ai', 'ACTIVE generation=2')
    if args.pairing:
        client.check_generation(2)
        check('revoke-pairing', 'ais unpair', 'revoked')
        client.check_revoked()
        evidence.append(dict(test='live-status-and-revocation', result='pass', detail='Generation advanced to 2, then the paired client lost live access'))
    check('message-B', 'ais run hello-ai', 'Live revision B')
    source.write_text(invalid_source, encoding='utf-8')
    check('invalid-edit-rejected', 'ais reload hello-ai', 'active generation unchanged')
    check('invalid-file-status', 'ais status hello-ai', 'RUNNING SAVED VERSION - file has errors')
    check('actionable-diagnostic', 'ais status hello-ai', 'Fix that editable file' if args.aether else 'Fix: set target to sender')
    check('diff-running-vs-editable', 'ais diff hello-ai', 'let count = missing' if args.aether else 'target: everyone')
    check('last-working-message', 'ais run hello-ai', 'Live revision B')
    assert history()['active'] == 2 and len(history()['revisions']) == 2
    stop()
    start()
    check('restart-invalid-edit-recovers-B', 'ais run hello-ai', 'Live revision B')
    check('restart-active-revision', 'ais status hello-ai', 'Running: saved revision #2')
    # Each change is distinct, so this checks actual swaps and pruning rather than 100 no-ops.
    for number in range(3, 103):
        edit(f'Live revision {number}')
        command('ais reload hello-ai', f'ACTIVE generation={number-1}')
    check('100-changed-reloads', 'ais status', 'generation=101; features=1')
    check('retention-five', 'ais history hello-ai', '5/5 saved versions')
    assert [r['number'] for r in history()['revisions']] == [98, 99, 100, 101, 102]
    assert len(list((data/'storage/revisions/hello-ai').glob('*.source'))) == 5
    before = source.read_bytes()
    check('rollback', 'ais rollback hello-ai', 'ACTIVE generation=102')
    assert source.read_bytes() == before and history()['active'] == 101
    check('rollback-message', 'ais run hello-ai', 'Live revision 101')
    check('rollback-edit-status', 'ais status hello-ai', 'file has unapplied changes')
    stop()
    start()
    check('rollback-held-on-restart', 'ais run hello-ai', 'Live revision 101')
    check('rollback-held-explanation', 'ais status hello-ai', 'Rollback is held across restart')
    check('explicit-reload-releases-hold', 'ais reload hello-ai', 'ACTIVE generation=2')
    check('editable-message-restored', 'ais run hello-ai', 'Live revision 102')
    if args.relay_config:
        from cloud_workflow import test_remote_scripts
        test_remote_scripts(repo, version, args.java, server, command)
        evidence.append(dict(test='cloud-remote-script-workflow', result='pass',
            detail='Paired MCP created and edited scoped source, verified live output and rejected stale, invalid, out-of-scope and revoked requests'))
    stop()
    # Corrupt the active saved bytes AND leave an invalid editable source: an older valid revision must win.
    h = history()
    active = next(r for r in h['revisions'] if r['number'] == h['active'])
    (data/'storage/revisions/hello-ai'/f"{active['hash']}.source").write_text('damaged')
    source.write_text(invalid_source, encoding='utf-8')
    start()
    check('corrupt-active-falls-back', 'ais run hello-ai', 'Live revision 101')
    check('corrupt-active-new-pointer', 'ais status hello-ai', 'Running: saved revision #101')
    stop()
    (data/'storage/manifest.json').write_text('{"broken":true}')
    start(blocked=True)
    check('corrupt-manifest-blocks-startup', 'ais status', 'ready=false')
    check('corrupt-manifest-explanation', 'ais status', 'STORAGE_CORRUPT')
    check('corrupt-manifest-blocks-run', 'ais run hello-ai', 'Feature unavailable or permission denied')
except Exception as error:
    evidence.append(dict(test='smoke', result='fail', detail=str(error)))
    print(str(error), flush=True)
finally:
    stop()
    if args.relay_config:
        (data/'relay.json').unlink(missing_ok=True)
    console = redact(''.join(lines))
    (server/'smoke-console.log').write_text(console, encoding='utf-8')
    (server/'smoke-results.json').write_text(json.dumps(evidence, indent=2), encoding='utf-8')
if any(e['result']=='fail' for e in evidence):
    raise SystemExit(1)
print(f'PASS: {len(evidence)} Paper checks; five server boots; 100 distinct reloads', flush=True)

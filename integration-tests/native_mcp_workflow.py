"""Real Paper native MCP protocol fixture; no actual AI application is exercised.
Requires external Playwright Edge, pinned ViaProxy and homes_workflow Bot dependencies.
OAuth credentials, private bearer tokens and console approval links remain in memory.
"""
import base64, hashlib, json, os, re, secrets, socket, subprocess, time, uuid
from pathlib import Path
from http.server import BaseHTTPRequestHandler, HTTPServer
from threading import Thread
from contextlib import contextmanager
from urllib.error import HTTPError
from urllib.parse import parse_qs, urlencode, urlsplit
from urllib.request import Request, urlopen
from playwright.sync_api import sync_playwright, expect
from homes_workflow import Bot, PROXY_SHA256


@contextmanager
def serve_callback(receiver):
    thread = Thread(target=receiver.serve_forever, daemon=True)
    thread.start()
    try:
        yield
    finally:
        receiver.shutdown()
        thread.join(timeout=5)


def verify(command, start, stop, server, repo, java, evidence, client_dir):
    server, repo, client_dir = map(Path, (server, repo, client_dir))
    proxy_jar = client_dir/'ViaProxy-3.4.14.jar'
    assert hashlib.sha256(proxy_jar.read_bytes()).hexdigest() == PROXY_SHA256, 'Unexpected proxy checksum'
    assert not (server/'plugins/AetherScript/relay.json').exists(), 'Fresh shared-relay installation required'
    (client_dir/'viabedrock.yml').write_text('translate-resource-packs: false\nresource-pack-host: 127.0.0.1\n', encoding='utf-8')
    files_mode = bool(os.environ.get('AETHER_V030_PROVIDERS'))
    codex_mode = os.environ.get('AETHER_V030_CODEX')=='1'
    assert not (files_mode and codex_mode), 'Run the file and actual Codex checks separately'
    release030 = files_mode or codex_mode
    if release030:
        from release030_workflow import check_docs, prepare, stage_files, verify_installed
    if files_mode:
        prepare(server, repo)
    proxy_port=int(os.environ.get('AETHER_SMOKE_PROXY_PORT','25568'))
    paper_port=int(os.environ.get('AETHER_SMOKE_PORT','25591'))
    assert 1024 <= proxy_port <= 65535 and proxy_port != paper_port
    proxy = log = bot = None
    def passed(name, detail):
        evidence.append(dict(test='native-mcp-'+name, result='pass', detail=detail))
        print('native-mcp-'+name+': pass', flush=True)
    def settled():
        deadline = time.monotonic()+120
        while time.monotonic() < deadline:
            if 'busy=false' in command('ais status', 'AetherScript '): return
            time.sleep(.2)
        raise AssertionError('Paper activation did not settle')
    def connect_bot(first_login=False):
        result = Bot(client_dir, 'AetherHomeA', server/'native-mcp-client.jsonl', proxy_port)
        command('op AetherHomeA', 'operator')
        if first_login and (release030 or os.environ.get('AETHER_PACKED_SMOKE')=='1'):
            command('gamemode creative AetherHomeA', 'Creative Mode')
        settled()
        return result
    def tp(x, z):
        command(f'tp AetherHomeA {float(x)} 80.0 {float(z)}', 'Teleported')
        bot.wait(lambda e: e['event']=='position' and abs(e['x']-x)<.1 and abs(e['z']-z)<.1, timeout=45)
    def at(x, z):
        bot.request(op='position', id='native-snapshot')
        p = bot.wait(lambda e: e['event']=='position_snapshot', timeout=45)
        assert abs(p['x']-x)<.25 and abs(p['z']-z)<.25, 'Unexpected real player location'
    try:
        start(); settled()
        log = (server/'native-mcp-proxy.log').open('w', encoding='utf-8')
        proxy = subprocess.Popen([str(java), '-jar', str(proxy_jar.resolve()), 'cli', '--bind-address', f'127.0.0.1:{proxy_port}', '--target-address', f'127.0.0.1:{paper_port}', '--target-version', '26.3', '--proxy-online-mode', 'false', '--auth-method', 'NONE', '--chat-signing', 'false'], cwd=client_dir, stdout=log, stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
        deadline = time.monotonic()+120
        while time.monotonic()<deadline:
            if proxy.poll() is not None: raise AssertionError('Proxy exited')
            try:
                with socket.create_connection(('127.0.0.1',proxy_port), timeout=.2): pass
                break
            except OSError: time.sleep(.25)
        else: raise AssertionError('Proxy readiness timeout')
        bot = connect_bot(first_login=True)
        actor = str(uuid.UUID(bytes=hashlib.md5(b'OfflinePlayer:AetherHomeA').digest(), version=3))
        enroll = 'ais connect --files' if files_mode else 'ais connect visitor-desk' if codex_mode else 'ais connect'
        line = command(enroll, '/connect#code=')
        link = re.search(r'https://[^\s]+/connect#code=[^\s]+', line).group()
        origin = 'https://'+urlsplit(link).netloc
        def http(path, value=None, token=None, form=False):
            headers = {'Accept':'application/json, text/event-stream', 'User-Agent':'AetherScript-Native-MCP-Fixture'}
            body = None
            if value is not None:
                headers['Content-Type'] = 'application/x-www-form-urlencoded' if form else 'application/json'
                body = urlencode(value).encode() if form else json.dumps(value).encode()
            if token: headers['Authorization'] = 'Bearer '+token
            with urlopen(Request(origin+path, data=body, headers=headers), timeout=50) as response: return json.load(response)
        def call(token, name, arguments, success=True):
            answer = http('/mcp', {'jsonrpc':'2.0','id':1,'method':'tools/call','params':{'name':name,'arguments':arguments}}, token)
            result = answer.get('result', {})
            ok = 'error' not in answer and not result.get('isError', True)
            assert ok == success, 'Unexpected result for '+name
            return result.get('structuredContent', {})
        def live(token):
            deadline = time.monotonic()+120
            while time.monotonic()<deadline:
                try:
                    result = call(token, 'aether_server_status', {})
                    local = re.search(r'generation=(\d+)', command('ais status', 'AetherScript '))
                    assert local and str(result.get('generation')) == local.group(1), 'Status differs from intended Paper instance'
                    return result
                except (HTTPError, AssertionError): time.sleep(1)
            raise AssertionError('Approved connection did not reach intended Paper')
        callback = {}
        class Receiver(BaseHTTPRequestHandler):
            def do_GET(self):
                callback.update(parse_qs(urlsplit(self.path).query)); self.send_response(200); self.end_headers(); self.wfile.write(b'Fixture callback received.')
            def log_message(self, *args): pass
        with HTTPServer(('127.0.0.1',0), Receiver) as receiver, serve_callback(receiver), sync_playwright() as pw:
            browser = pw.chromium.launch(channel=os.environ.get('AETHER_BROWSER_CHANNEL','msedge'), headless=True)
            def approve(native, player, fresh_link, file_scope=files_mode, all_scripts=True):
                redirect = f'http://127.0.0.1:{receiver.server_port}/callback'
                client = http('/register', {'redirect_uris':[redirect], 'token_endpoint_auth_method':'none'})
                verifier = secrets.token_urlsafe(48)
                challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip('=')
                state = secrets.token_hex(16)
                fields = dict(client_id=client['client_id'], redirect_uri=redirect, resource=origin+'/mcp', response_type='code', code_challenge_method='S256', code_challenge=challenge, state=state)
                context = browser.new_context(); page = context.new_page()
                try:
                    page.goto(fresh_link); expect(page.locator('#server')).to_have_text(re.compile(r'\S'))
                    page.locator('#kind').select_option('codex'); page.wait_for_timeout(700)
                    page.goto(origin+'/oauth/authorize?'+urlencode(fields))
                    expect(page.locator('#server')).to_have_text(re.compile(r'\S'))
                    page.locator('#name').fill('Native protocol fixture' if native else 'Restricted protocol fixture')
                    page.locator('#all' if all_scripts else '#selected').check()
                    page.locator('#native').set_checked(native)
                    page.locator('#test-player').select_option(actor if player else '')
                    if file_scope: page.locator('#approve-files').check()
                    page.locator('#approve').click(); page.wait_for_url(redirect+'**', timeout=45000)
                    returned = parse_qs(urlsplit(page.url).query); assert returned['state']==[state]
                    token = http('/oauth/token', dict(grant_type='authorization_code', code=returned['code'][0], client_id=client['client_id'], redirect_uri=redirect, code_verifier=verifier, resource=origin+'/mcp'), form=True)['access_token']
                    return token
                finally: context.close()
            token = approve(True, True, link, all_scripts=not codex_mode); live(token)
            if release030:
                check_docs(call, http, token, passed)
                if codex_mode:
                    from codex_blind_build import blind_build
                    blind_build(origin+'/mcp', token, server, passed)
                    settled()
                    bot.command('visitordesk MistDig true'); bot.message('Staff desk for MistDig.',timeout=30)
                    bot.command('visitordesk Alex false'); bot.message('Visitor desk for Alex.',timeout=30)
                    bot.close(); bot=connect_bot()
                    welcome = 'Welcome to the visitor desk.'
                    if not any(event.get('event') == 'message' and welcome in json.dumps(event) for event in bot.events):
                        bot.message(welcome,timeout=30)
                    passed('actual-codex-paper','A translated client observed both command branches and a real join message from the feature authored by Codex.')
                    command('ais disconnect','revocation saved'); settled()
                    browser.close(); receiver.shutdown()
                    return
                changes = stage_files(server, command, settled, call, token, passed)
                bot.close(); bot=None; stop(); start(); settled(); bot=connect_bot()
                live(token)
                verify_installed(server, command, settled, bot, changes, passed)
                view=call(token,'aether_files_config_read',{'target':'gui-settings'})['config']
                edit=call(token,'aether_files_config_preview',{'target':'gui-settings','expectedHash':view['hash'],'replacements':{'click-sound':False}})['change']
                command('ais disconnect','revocation saved'); settled()
                command('ais files approve '+edit['token'],'CONNECTION_REVOKED'); settled()
                assert (server/'plugins/MCGUI/config.yml').read_bytes()==changes[2]
                passed('file-revocation','Revocation invalidated an already-staged proposal before console approval.')
                browser.close()
                return

            caps = call(token, 'aether_capabilities', {})
            assert 'player.teleport.self' in json.dumps(caps) and actor in json.dumps(caps), 'Native capabilities or approved actor absent'
            passed('browser-grant', 'Fresh owner browser grant reached intended Paper with explicit native capabilities and actor')
            source = (repo/'examples/homes.aether').read_text(encoding='utf-8').replace('feature homes ', 'feature nativehomes ').replace('command home ', 'command nativehome ')
            assert call(token,'aether_scripts_read',{'id':'nativehomes'})['hash']=='ABSENT'
            assert call(token,'aether_scripts_validate',{'id':'nativehomes','source':source})['valid']
            applied = call(token,'aether_scripts_apply',{'id':'nativehomes','source':source,'expectedHash':'ABSENT'})
            settled()
            call(token,'aether_scripts_apply',{'id':'nativehomes','source':source,'expectedHash':'ABSENT'},False)
            passed('authoring', 'AI validated and applied native source without a console permit; stale apply rejected')
            def begin(action, expected):
                result = call(token,'aether_scripts_test',{'id':'nativehomes','arguments':[action,'main'],'expectedMessages':expected})
                assert result['status']=='pending' and result['testId']; return result['testId']
            def poll(test_id, expected_status='completed'):
                deadline = time.monotonic()+150
                while time.monotonic()<deadline:
                    result = call(token,'aether_scripts_test_status',{'id':'nativehomes','testId':test_id})
                    if result['status']!='pending':
                        if expected_status: assert result['status']==expected_status, 'Unexpected native test terminal status'
                        return result
                    time.sleep(.25)
                raise AssertionError('Native test did not terminate')

            def exercise_packed():
                format_args = {'id':'quests','format':'aetherpack'}
                files = {path.name: path.read_text(encoding='utf-8') for path in (repo/'examples/quests.aetherpack').glob('*.aether')}
                source = json.dumps(files, separators=(',', ':'))
                assert call(token,'aether_scripts_read',format_args)['hash']=='ABSENT'
                assert call(token,'aether_scripts_validate',dict(format_args,source=source))['valid']
                edited = call(token,'aether_scripts_apply',dict(format_args,source=source,expectedHash='ABSENT'))
                settled()
                call(token,'aether_scripts_apply',dict(format_args,source=source,expectedHash='ABSENT'),False)
                def run(expected):
                    result = call(token,'aether_scripts_test',{'id':'quests','arguments':['show'],'expectedMessages':expected})
                    deadline=time.monotonic()+120
                    while result['status']=='pending' and time.monotonic()<deadline:
                        time.sleep(.25)
                        result=call(token,'aether_scripts_test_status',{'id':'quests','testId':result['testId']})
                    assert result['status']=='completed'
                    return result
                failed=run(['Intentional mismatch'])
                assert not failed['matched'] and any('Reward preview: 0 bread' in line for line in failed['actualMessages'])
                files['rewards.aether']=files['rewards.aether'].replace('progress.stage * 2','progress.stage * 2 + 1')
                fixed=json.dumps(files, separators=(',', ':'))
                assert call(token,'aether_scripts_validate',dict(format_args,source=fixed))['valid']
                assert json.loads(call(token,'aether_scripts_apply',dict(format_args,source=fixed,expectedHash=edited['hash']))['source'])==files
                settled()
                expected=[line.replace('Reward preview: 0 bread','Reward preview: 1 bread') for line in failed['actualMessages']]
                assert run(expected)['matched']
                passed('packed-quest-repair','Applied four files, rejected a stale write, observed a failed assertion, repaired one module and passed the retest.')
            if os.environ.get('AETHER_PACKED_SMOKE')=='1':
                exercise_packed()
                protection=(repo/'examples/starter-protection.aether').read_text(encoding='utf-8')
                call(token,'aether_scripts_apply',{'id':'starter-protection','source':protection,'expectedHash':'ABSENT'})
                settled()
                event={'kind':'block_break','fields':{'player_name':'AetherHomeA','player_uuid':actor,
                    'world':'minecraft:overworld','block_type':'STONE','x':10,'y':80,'z':10}}
                preview=call(token,'aether_events_preview',{'id':'starter-protection','event':event})
                assert preview['simulated'] and preview['cancelled'] and preview['handlersEvaluated']==1
                event['fields']['world']='minecraft:the_nether'
                assert not call(token,'aether_events_preview',{'id':'starter-protection','event':event})['cancelled']
                passed('event-preview','Approved event snapshots simulated cancellation only in the overworld, with no live block edits.')
            tp(11,13)
            saved_id = begin('set',['Home saved.']); saved = poll(saved_id)
            assert saved['matched'] and saved['actualMessages']==['Home saved.']
            assert any(r.get('operation')=='state.own.put_location' and r.get('acknowledged') for r in saved['receipts'])
            tp(71,73)
            arrived = poll(begin('go',['Teleporting in one second...','Home reached.']))
            assert arrived['matched'] and any(r.get('operation')=='player.teleport.self' and 'actualLocation' in r for r in arrived['receipts']); at(11,13)
            passed('real-effects', 'Asynchronous tests captured actual messages, durable state acknowledgement and post-teleport location')
            line = command('ais connect','/connect#code='); restricted_link = re.search(r'https://[^\s]+/connect#code=[^\s]+',line).group()
            restricted = approve(False, False, restricted_link); live(restricted)
            call(restricted,'aether_scripts_apply',{'id':'nativehomes','source':source,'expectedHash':applied['hash']},False)
            call(restricted,'aether_scripts_test_status',{'id':'nativehomes','testId':saved_id},False)
            call(token,'aether_scripts_test_status',{'id':'hello-ai','testId':saved_id},False)
            passed('scope-isolation', 'Fresh message-only connection rejected native apply and foreign test; feature mismatch rejected')
            delayed = source.replace('delay(20)','delay(600)')
            changed = call(token,'aether_scripts_apply',{'id':'nativehomes','source':delayed,'expectedHash':applied['hash']}); settled()
            tp(91,93); pending = begin('go',['Teleporting in one second...','Home reached.'])
            bot.message('Teleporting in one second...',timeout=45)
            command('deop AetherHomeA','operator')
            retired = poll(pending, None); assert retired['status']!='completed' and not retired['matched']; at(91,93)
            command('op AetherHomeA','operator')
            applied = call(token,'aether_scripts_apply',{'id':'nativehomes','source':source,'expectedHash':changed['hash']}); settled()
            passed('permission-retirement', 'Removing real actor permission stopped delayed test before teleport')
            bot.close(); bot=None; stop(); start(); settled(); bot=connect_bot(); live(token)
            assert call(token,'aether_scripts_read',{'id':'nativehomes'})['hash']==applied['hash']
            tp(101,103); restored = poll(begin('go',['Teleporting in one second...','Home reached.']))
            assert restored['matched']; at(11,13)
            passed('restart', 'Original OAuth token, native grant, active source and own-player state survived restart')
            command('ais disconnect','revocation saved'); settled()
            try:
                call(token,'aether_scripts_read',{'id':'nativehomes'})
                raise AssertionError('Revoked connection retained AI access')
            except HTTPError as failure: assert failure.code in (401,403,409)
            except AssertionError as failure:
                if str(failure)=='Revoked connection retained AI access': raise
            tp(111,113); bot.command('nativehome go main'); bot.message('Home reached.',timeout=45); at(11,13)
            passed('disconnect', 'AI access revoked while locally approved native script still executed')
            browser.close()
    finally:
        if bot: bot.close()
        if proxy and proxy.poll() is None:
            proxy.terminate()
            try: proxy.wait(timeout=10)
            except subprocess.TimeoutExpired: proxy.kill(); proxy.wait(timeout=5)
        if log: log.close()

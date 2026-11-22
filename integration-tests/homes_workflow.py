"""Loopback-only translated-client Paper homes checks; test dependencies stay external."""
import hashlib, json, os, queue, socket, subprocess, threading, time
from pathlib import Path

PROXY_SHA256 = '2894bbfb2f4342f8fde887af6c689313efd5b0e46a8006775eea0e21e2948ab5'
CAPS = 'player.location.read,state.own,scheduler.delay,player.teleport.self'

class Bot:
    def __init__(self, client_dir, name, output):
        self.events, self.q, self.name = [], queue.Queue(), name
        script = Path(__file__).resolve().parent/'client/home_client.cjs'
        self.p = subprocess.Popen(['node', str(script.resolve()), '25568', name], cwd=client_dir,
            env=dict(os.environ, NODE_PATH=str(Path(client_dir).resolve()/'node_modules')),
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding='utf-8', creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
        self.errors = []
        def errors():
            for line in self.p.stderr:
                self.errors.append(line)
                self.errors[:] = self.errors[-30:]
        threading.Thread(target=errors, daemon=True).start()
        def read():
            for line in self.p.stdout:
                try: event = json.loads(line)
                except ValueError: continue
                self.events.append(event); self.q.put(event)
                with output.open('a', encoding='utf-8') as stream: stream.write(json.dumps(dict(bot=name, **event))+'\n')
        threading.Thread(target=read, daemon=True).start()
        try:
            self.wait(lambda e:e['event']=='ready', timeout=45)
            self.wait(lambda e:e['event']=='position', timeout=45)
        except Exception:
            self.close()
            raise
    def wait(self, predicate, timeout=12):
        deadline=time.monotonic()+timeout
        while time.monotonic()<deadline:
            try: event=self.q.get(timeout=.2)
            except queue.Empty:
                if self.p.poll() is not None: raise AssertionError('Bot exited: '+''.join(self.errors)[-1000:])
                continue
            if predicate(event): return event
        raise AssertionError('Bot timeout '+self.name+': '+json.dumps(self.events[-8:]))
    def request(self, **request):
        self.p.stdin.write(json.dumps(request)+'\n'); self.p.stdin.flush()
    def command(self, text):
        while not self.q.empty(): self.q.get_nowait()
        self.request(op='command', command=text)
    def message(self, text, timeout=12):
        return self.wait(lambda e:e['event']=='message' and text in json.dumps(e), timeout)
    def position(self):
        self.request(op='position', id='snapshot')
        return self.wait(lambda e:e['event']=='position_snapshot')
    def close(self):
        if self.p.poll() is None:
            try: self.request(op='quit'); self.p.wait(timeout=5)
            except (OSError,subprocess.TimeoutExpired): self.p.kill(); self.p.wait(timeout=5)

def verify(command, start, stop, server, repo, java, evidence, client_dir, events=False, kits=False, packages=False, economy=False, menus=False, arenas=False, addon=None, bridge=False):
    server,repo,client_dir=map(Path,(server,repo,client_dir))
    proxy_jar=client_dir/'ViaProxy-3.4.14.jar'
    if hashlib.sha256(proxy_jar.read_bytes()).hexdigest()!=PROXY_SHA256: raise AssertionError('Unexpected ViaProxy checksum')
    (client_dir/'viabedrock.yml').write_text('translate-resource-packs: false\nresource-pack-host: 127.0.0.1\n',encoding='utf-8')
    scripts=server/'plugins/AetherScript/scripts'; scripts.mkdir(parents=True,exist_ok=True)
    source=scripts/'homes.aether'; hello=scripts/'hello-ai.aether'
    original=(repo/'examples/homes.aether').read_text(encoding='utf-8').replace('Teleporting in one second...', 'Preparing teleport...')
    if bridge:
        original=original.replace('command home ', 'command aetherhome ')
    source.write_text(original,encoding='utf-8')
    bots=[]; proxy=None; proxy_log=None
    def passed(name, detail):
        evidence.append(dict(test=name,result='pass',detail=str(detail))); print(name+': pass',flush=True)
    def check(name,text,match):
        detail=command(text,match)
        if text.startswith(('ais reload','ais permit','ais restrict')):
            # ACTIVE acknowledges activation before the bounded I/O worker releases its busy
            # flag. Observe the public status instead of racing a player command against it.
            deadline=time.monotonic()+30
            while True:
                if 'busy=false' in command('ais status','AetherScript '): break
                if time.monotonic() >= deadline: raise AssertionError('Activation did not settle: '+text)
                time.sleep(.1)
        passed(name,detail)
    def connect(name):
        bot=Bot(client_dir,name,server/'homes-client.jsonl'); bots.append(bot); return bot
    def tp(bot,x,z):
        teleport_command = 'minecraft:tp' if bridge else 'tp'
        command(f'{teleport_command} {bot.name} {float(x)} 80.0 {float(z)}', 'Teleported')
        bot.wait(lambda e:e['event']=='position' and abs(e['x']-x)<.1 and abs(e['z']-z)<.1)
    def at(bot,x,z):
        pos=bot.position(); assert abs(pos['x']-x)<.2 and abs(pos['z']-z)<.2, pos
        return pos
    def travel(bot):
        bot.command('home go main'); bot.message('Preparing teleport...')
    try:
        start()
        check('homes-startup-native-denied','ais status','features=1')
        check('homes-denial-keeps-pure-feature','helloai','Hello from AetherScript!')
        check('homes-owner-permit','ais permit homes '+CAPS,'CAPABILITIES_SAVED homes:')
        check('homes-activate','ais reload homes','ACTIVE generation=')
        proxy_log=(server/'homes-proxy.log').open('w',encoding='utf-8')
        proxy=subprocess.Popen([str(java),'-jar',str(proxy_jar.resolve()),'cli','--bind-address','127.0.0.1:25568','--target-address','127.0.0.1:25591','--target-version','26.3','--proxy-online-mode','false','--auth-method','NONE','--chat-signing','false'],cwd=client_dir,stdout=proxy_log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
        deadline=time.monotonic()+45
        while time.monotonic()<deadline:
            proxy_log.flush()
            if proxy.poll() is not None: raise AssertionError('Proxy exited; see homes-proxy.log')
            try:
                with socket.create_connection(('127.0.0.1',25568),timeout=.2): pass
                break
            except OSError: pass
            time.sleep(.25)
        else: raise AssertionError('Proxy readiness timeout: '+(server/'homes-proxy.log').read_text()[-1500:])
        if events:
            from event_workflow import verify_protection
            verify_protection(command, check, connect, tp, passed, repo, scripts)
            return
        if kits:
            from kit_workflow import verify_kit
            verify_kit(command, check, connect, start, stop, passed, repo, scripts)
            return
        if packages:
            from quest_workflow import verify_quest
            verify_quest(command, check, connect, start, stop, passed, repo, scripts)
            return
        if economy:
            from economy_workflow import verify_economy
            verify_economy(command, check, connect, start, stop, passed, repo, scripts)
            return
        if menus:
            from menu_workflow import verify_menu
            verify_menu(command, check, connect, passed, repo, scripts)
            return
        if arenas:
            from arena_workflow import verify_arenas
            verify_arenas(command, check, connect, tp, passed, repo, scripts)
            return
        if addon:
            from addon_workflow import verify_addon
            verify_addon(command, check, connect, start, stop, passed, repo, scripts, addon)
            return
        if bridge:
            from bridge_workflow import verify_bridge
            verify_bridge(command, check, connect, tp, passed, repo, scripts)
            return
        a,b=connect('AetherHomeA'),connect('AetherHomeB')
        command('op AetherHomeA','operator'); command('op AetherHomeB','operator')
        progress = 'player-progress'
        (scripts/(progress+'.aether')).write_bytes((repo/'examples'/(progress+'.aether')).read_bytes())
        check('progress-owner-permit', 'ais permit player-progress state.own', 'CAPABILITIES_SAVED player-progress:')
        check('progress-activate', 'ais reload player-progress', 'ACTIVE generation=')
        a.command('progress show'); a.message('No saved progress.')
        a.command('progress save'); a.message('Progress saved: score=7, quest_stage=2.')
        b.command('progress show'); b.message('No saved progress.')
        passed('progress-player-isolation', 'Second player received the missing-state fallback after first player saved.')
        tp(a,10,10); a.command('home set main'); a.message('Home saved.'); passed('homes-player-save','durable acknowledgement received')
        b.command('home go main'); b.message('No home saved with that name.'); passed('homes-player-isolation','second UUID has no home')
        tp(a,40,40); travel(a); a.message('Home reached.'); passed('homes-delay-teleport',at(a,10,10))
        check('homes-unchanged-reload','ais reload homes','UNCHANGED:')
        slow=original.replace('delay(20)','delay(60)'); source.write_text(slow,encoding='utf-8')
        check('homes-delay-edit','ais reload homes','ACTIVE generation=')
        tp(a,40,40); travel(a)
        hello_original=hello.read_text(encoding='utf-8'); hello.write_text(hello_original+'\n// unrelated homes lifecycle check\n',encoding='utf-8')
        check('homes-unrelated-feature-edit','ais reload hello-ai','ACTIVE generation=')
        a.message('Home reached.'); passed('homes-unrelated-edit-retains-invocation',at(a,10,10))
        tp(a,40,40); travel(a); source.write_text(slow+'\n// replace active homes revision\n',encoding='utf-8')
        check('homes-own-edit-replaces-revision','ais reload homes','ACTIVE generation=')
        time.sleep(3.5); passed('homes-own-edit-cancels-pending',at(a,40,40))
        travel(a); command('deop AetherHomeA','operator'); time.sleep(3.5); passed('homes-permission-revocation-cancels-pending',at(a,40,40)); command('op AetherHomeA','operator')
        travel(a); check('homes-restrict','ais restrict homes','CAPABILITIES_SAVED homes:'); time.sleep(3.5); passed('homes-restrict-cancels-pending',at(a,40,40))
        check('homes-repermit','ais permit homes '+CAPS,'CAPABILITIES_SAVED homes:'); check('homes-reactivate','ais reload homes','ACTIVE generation=')
        # Give the disconnect/reconnect fixture ample time even on a loaded host. A graceful
        # protocol-client shutdown may flush for several seconds, so kick on Paper's thread.
        source.write_text(slow.replace('delay(60)', 'delay(600)'), encoding='utf-8')
        check('homes-disconnect-delay-edit','ais reload homes','ACTIVE generation=')
        travel(a); old_uuid=next(e['uuid'] for e in a.events if e['event']=='ready')
        command('kick AetherHomeA lifecycle-test', 'Kicked AetherHomeA'); a.close(); a=connect('AetherHomeA')
        new_uuid=next(e['uuid'] for e in a.events if e['event']=='ready'); assert new_uuid==old_uuid
        time.sleep(31); passed('homes-disconnect-same-uuid-cancels-pending',at(a,40,40))
        source.write_text(original, encoding='utf-8')
        check('homes-restore-normal-delay','ais reload homes','ACTIVE generation=')
        for bot in bots: bot.close()
        bots.clear(); stop(); start()
        a=connect('AetherHomeA'); travel(a); a.message('Home reached.'); passed('homes-restart-restores-state-and-owner-caps',at(a,10,10))
        a.command('progress show'); a.message('Score: 7'); a.message('Quest stage: 2')
        passed('progress-restart-restores-map', 'Both integer entries restored for the original player.')
        passed('homes-client-protocol','Offline26.1(protocol775) translated by verifiedViaProxy3.4.14 to Paper26.3(protocol777); not native26.3 client')
    finally:
        for bot in bots: bot.close()
        if proxy is not None and proxy.poll() is None:
            proxy.terminate()
            try: proxy.wait(timeout=10)
            except subprocess.TimeoutExpired: proxy.kill(); proxy.wait(timeout=5)
        if proxy_log: proxy_log.close()

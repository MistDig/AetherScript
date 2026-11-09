import subprocess, threading, queue, time, shutil, json, sys, argparse, hashlib, os
from pathlib import Path
parser=argparse.ArgumentParser(description='Disposable loopback-only Paper 26.3 smoke test')
parser.add_argument('--server-jar',type=Path,required=True)
parser.add_argument('--work-dir',type=Path,required=True,help='New disposable directory; must not already exist')
parser.add_argument('--java',default='java')
parser.add_argument('--accept-eula',action='store_true',help='Confirm acceptance of the Minecraft EULA for this disposable server')
args=parser.parse_args()
if not args.accept_eula: parser.error('Review https://aka.ms/MinecraftEULA and pass --accept-eula to run the server')
repo=Path(__file__).resolve().parents[1];server=args.work_dir.resolve()
if server.exists(): parser.error('work-dir already exists; choose a new disposable directory')
jar=args.server_jar.resolve()
expected='192d277ea1ce29b2df3933dd488521d4555cc82be864ea8df811fe536e8aed43'
if hashlib.sha256(jar.read_bytes()).hexdigest()!=expected: parser.error('Paper server checksum mismatch; expected 26.3 build 245')
(server/'plugins').mkdir(parents=True)
shutil.copy2(jar,server/'paper.jar')
shutil.copy2(repo/'plugin-paper/build/libs/plugin-paper-0.1.0-dev.jar',server/'plugins/AetherScript.jar')
(server/'eula.txt').write_text('eula=true\n')
(server/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25591\nonline-mode=false\nenforce-secure-profile=false\nview-distance=2\nsimulation-distance=2\nmax-players=2\nlevel-type=minecraft:flat\ngenerate-structures=false\nspawn-protection=0\n')
lines=[]; q=queue.Queue(); evidence=[]
p=subprocess.Popen([args.java,'-Xms512M','-Xmx1536M','-jar','paper.jar','--nogui'],cwd=server,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace',creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
def read():
 for line in p.stdout:
  lines.append(line); q.put(line)
threading.Thread(target=read,daemon=True).start()
def expect(text,timeout=120):
 deadline=time.monotonic()+timeout
 while time.monotonic()<deadline:
  try: line=q.get(timeout=1)
  except queue.Empty:
   if p.poll() is not None: raise AssertionError(f'server exited {p.returncode}: '+''.join(lines[-20:]))
   continue
  if text in line: return line.strip()
 raise AssertionError('Timeout waiting for '+text+'\n'+''.join(lines[-15:]))
def command(text,match):
 while not q.empty(): q.get_nowait()
 p.stdin.write(text+'\n');p.stdin.flush()
 return expect(match,30)
try:
 expect('Done (',180)
 if not any('Ready. AI deployment absent' in line for line in lines): expect('Ready. AI deployment absent',30)
 evidence.append(dict(test='startup',result='pass',detail=command('ais status','AI deployment=ABSENT')))
 source=server/'plugins/AetherScript/scripts/hello-ai.yml'
 original=(repo/'examples/hello-ai.yml').read_text();source.write_text(original)
 evidence.append(dict(test='validate',result='pass',detail=command('ais validate','VALIDATED')))
 evidence.append(dict(test='initial-reload',result='pass',detail=command('ais reload','ACTIVE generation=1')))
 evidence.append(dict(test='message-A',result='pass',detail=command('ais run hello-ai','Hello from AetherScript!')))
 source.write_text(original.replace('Hello from AetherScript!','Live revision B'))
 evidence.append(dict(test='live-reload',result='pass',detail=command('ais reload hello-ai','ACTIVE generation=2')))
 evidence.append(dict(test='message-B',result='pass',detail=command('ais run hello-ai','Live revision B')))
 source.write_text(original.replace('message.send','console.run'))
 evidence.append(dict(test='invalid-reload',result='pass',detail=command('ais reload hello-ai','active generation unchanged')))
 evidence.append(dict(test='preserved-generation',result='pass',detail=command('ais status','generation=2')))
 evidence.append(dict(test='preserved-message',result='pass',detail=command('ais run hello-ai','Live revision B')))
 source.write_text(original.replace('Hello from AetherScript!','Live revision B'))
 for i in range(100): command('ais reload hello-ai',f'ACTIVE generation={i+3}')
 evidence.append(dict(test='100-reloads',result='pass',detail=command('ais status','generation=102')))
 evidence.append(dict(test='one-active-feature',result='pass',detail=command('ais list','Active: hello-ai')))
 evidence.append(dict(test='message-after-100',result='pass',detail=command('ais run hello-ai','Live revision B')))
 print(json.dumps(evidence,indent=2),flush=True)
except Exception as exc:
 evidence.append(dict(test='smoke',result='fail',detail=str(exc)));print(str(exc),flush=True)
finally:
 if p.poll() is None:
  p.stdin.write('stop\n');p.stdin.flush()
  try:p.wait(timeout=60)
  except subprocess.TimeoutExpired:p.terminate();p.wait(timeout=15)
 (server/'smoke-console.log').write_text(''.join(lines),encoding='utf-8')
 (server/'smoke-results.json').write_text(json.dumps(evidence,indent=2),encoding='utf-8')
if any(e['result']=='fail' for e in evidence): sys.exit(1)

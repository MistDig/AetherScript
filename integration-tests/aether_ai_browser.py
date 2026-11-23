import json,os,subprocess,time,urllib.request
from pathlib import Path
from playwright.sync_api import sync_playwright
root='http://127.0.0.1:25640'
repo=Path(__file__).resolve().parent.parent
env=dict(os.environ,PORT='25640',AETHER_AI_LOOPBACK='1',AETHER_AI_URL=root,AETHER_AI_MODEL_ENDPOINT=root+'/model',
 AETHER_AI_MODEL_NAME='fixture',AETHER_AI_SESSION_SECRET='test-only-secret-000000000000000000',AETHER_AI_INVITES='test-only-invitation-0000000000000',
 AETHER_AI_DB=':memory:')
server=subprocess.Popen(['node','aether-ai/server.mjs'],env=env,cwd=repo,stdout=subprocess.DEVNULL)
try:
 for _ in range(100):
  try:
   with urllib.request.urlopen(root,timeout=.3) as response: assert response.status==200
   break
  except OSError: time.sleep(.1)
 else: raise AssertionError('Preview did not start')
 with sync_playwright() as playwright:
  browser=playwright.chromium.launch(channel='msedge',headless=True)
  for width,reduced in [(1440,'no-preference'),(375,'reduce')]:
   page=browser.new_page(viewport={'width':width,'height':900},reduced_motion=reduced)
   page.route('**/api/session',lambda route: route.fulfill(json={'authenticated':True,'connectedFeature':'lobby-welcome'}))
   updates=[{'phase':'writing'},{'phase':'validating'},{'phase':'testing'},{'phase':'result','ok':True,'revision':1,'tests':[{'kind':'event-preview','event':'join','actual':['Welcome!'],'expected':['Welcome!'],'passed':True}]}]
   page.route('**/api/build',lambda route: route.fulfill(status=200,content_type='application/x-ndjson',body=''.join(json.dumps(row)+'\n' for row in updates)))
   page.goto(root)
   assert page.get_by_role('link',name='Connect my AI').is_visible()
   page.keyboard.press('Tab')
   assert page.evaluate("document.activeElement.textContent") == 'Skip to workspace'
   page.get_by_label('Message Aether').fill('Welcome new players.')
   page.get_by_role('button',name='Build').focus()
   page.keyboard.press('Enter')
   page.get_by_text('Event preview passed.',exact=False).wait_for()
   assert page.locator('#cloud-state').inner_text()=='Checks passed'
   assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
   assert (page.locator('#cloud').evaluate('(img) => getComputedStyle(img).animationName')=='none') == (reduced=='reduce')
   page.close()
  browser.close()
 print('Desktop and mobile: keyboard workflow, result and mascot motion preference passed')
finally:
 server.terminate()
 server.wait(timeout=10)

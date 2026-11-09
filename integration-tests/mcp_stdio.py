"""Exercise the actual gateway JAR through stdio, not an in-process mock."""
import argparse, json, subprocess, re, zipfile
from pathlib import Path
parser=argparse.ArgumentParser()
parser.add_argument('--java',default='java')
args=parser.parse_args()
repo=Path(__file__).resolve().parents[1]
source=(repo/'examples/hello-ai.json').read_text()
version=re.search(r'^version=(.+)$', (repo/'gradle.properties').read_text(), re.M).group(1)
with zipfile.ZipFile(repo/'plugin-paper/build/libs'/f'plugin-paper-{version}.jar') as plugin:
 assert f'version: {version}' in plugin.read('plugin.yml').decode()
 assert plugin.read('aetherscript-build.properties').decode().strip() == f'version={version}'
requests=[
 dict(jsonrpc='2.0',id=1,method='initialize',params=dict(protocolVersion='2025-06-18',capabilities={},clientInfo=dict(name='process-test',version='1'))),
 dict(jsonrpc='2.0',method='notifications/initialized'),
 dict(jsonrpc='2.0',id=2,method='tools/list',params={}),
 dict(jsonrpc='2.0',id=3,method='tools/call',params=dict(name='aether_validate',arguments=dict(source=source,format='json'))),
 dict(jsonrpc='2.0',id=4,method='tools/call',params=dict(name='aether_deploy',arguments={})),
 dict(jsonrpc='2.0',id=5,method='tools/call',params=dict(name='aether_server_status',arguments={}))]
result=subprocess.run([args.java,'-jar',str(repo/'gateway-mcp/build/libs'/f'gateway-mcp-{version}.jar')],input=''.join(json.dumps(r)+'\n' for r in requests),capture_output=True,text=True,timeout=30,check=True)
responses=[json.loads(line) for line in result.stdout.splitlines()]
assert len(responses)==5,responses
assert responses[0]['result']['protocolVersion']=='2025-06-18'
assert responses[0]['result']['serverInfo']['version']==version
assert len(responses[1]['result']['tools'])==3
assert responses[2]['result']['structuredContent']['valid'] is True
assert responses[3]['error']['code']==-32602
assert responses[4]['result']['structuredContent']['connected'] is False
assert responses[4]['result']['structuredContent']['deploymentEnabled'] is False
print(json.dumps(dict(result='pass',transport='stdio',responses=responses),indent=2))

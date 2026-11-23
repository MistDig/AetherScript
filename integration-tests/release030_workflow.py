"""Paper checks for versioned docs, file approval and an RP Engine model reference."""

import hashlib
import json
import os
from pathlib import Path
import shutil
import struct
import zipfile
import zlib


def png():
    def chunk(kind, body):
        return struct.pack('>I', len(body)) + kind + body + struct.pack('>I', zlib.crc32(kind + body))
    rows = b''.join(b'\0' + bytes(pixel for x in range(16) for pixel in
                             ([180, 229, 255, 255] if 2 <= x <= 13 and 4 <= y <= 11 else [0, 0, 0, 0]))
                    for y in range(16))
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>2I5B', 16, 16, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(rows)) + chunk(b'IEND', b'')


def prepare(server, repo):
    provider_dir = Path(os.environ['AETHER_V030_PROVIDERS'])
    plugins = server/'plugins'
    gui = provider_dir/'MCGUI-0.8.6.jar'
    shutil.copy2(gui, plugins/gui.name)
    gui_dir = plugins/'MCGUI'
    gui_dir.mkdir()
    (gui_dir/'config.yml').write_text('telemetry: false\nupdate-check: false\nallow-web: false\nclick-sound: true\nresource-pack:\n  mode: "off"\n', encoding='utf-8')
    pages=gui_dir/'pages'
    pages.mkdir()
    (pages/'cloud.html').write_text('<html><body><button data-command="cloudtoken">Cloud token</button></body></html>', encoding='utf-8')
    rp_dir = plugins/'RPEngine'
    rp_dir.mkdir()
    (rp_dir/'config.yml').write_text(
        'config-version: 9\nhost:\n  mode: none\nsend:\n  on-join: false\n  before-join: false\n  on-reload: false\n'
        'edit:\n  enabled: false\ntelemetry:\n  enabled: false\nupdate-check: false\n', encoding='utf-8')
    pack=rp_dir/'content/aetherdemo'
    (pack/'items').mkdir(parents=True)
    (pack/'assets/textures/item').mkdir(parents=True)
    (pack/'pack.yml').write_text('name: AetherScript cloud model fixture\n', encoding='utf-8')
    (pack/'items/cloud.yml').write_text('cloud_token:\n  material: PAPER\n  name: Cloud token\n', encoding='utf-8')
    (pack/'assets/textures/item/cloud_token.png').write_bytes(png())
    inbox=plugins/'AetherScript/file-management/inbox'
    inbox.mkdir(parents=True)
    shutil.copy2(provider_dir/'RPEngine.jar', inbox/'RPEngine.jar')
    source = repo/'examples/cloud-token.aether'
    shutil.copy2(source, plugins/'AetherScript/scripts'/source.name)


def check_docs(call, http, token, passed):
    from pathlib import Path
    version = next(line.partition('=')[2] for line in Path('gradle.properties').read_text().splitlines() if line.startswith('version='))
    caps=call(token,'aether_capabilities',{})
    assert caps['guide']['version']==version
    assert 'authoring' not in caps and any(route['id']=='resourcepacks' for route in caps['guide']['routes'])
    def resource(method, params):
        return http('/mcp', {'jsonrpc':'2.0','id':3,'method':method,'params':params}, token)['result']
    assert any(row['uri']=='aetherscript://docs/quickstart' for row in resource('resources/list',{})['resources'])
    contents=resource('resources/read',{'uri':'aetherscript://docs/quickstart'})['contents']
    quick=call(token,'aether_docs_read',{'section':'quickstart','version':version})
    assert quick==json.loads(contents[0]['text'])
    call(token,'aether_docs_read',{'section':'quickstart','version':'0.0.0'},False)
    focused=call(token,'aether_docs_read',{'section':'resourcepacks'})
    assert focused['version']==version and 'resourcePackIntegration' in focused['content']
    passed('versioned-docs','Hosted resources and tools matched the server version and returned identical quickstart content; rejected mismatched version.')


def stage_files(server, command, settled, call, token, passed):
    plugins=server/'plugins'
    gui_config=plugins/'MCGUI/config.yml'
    original=gui_config.read_bytes()
    command('ais files allow-config gui-settings MCGUI/config.yml yaml click-sound','FILE_TARGET_SAVED'); settled()
    command('ais files allow-jar rp-engine RPEngine.jar RPEngine','FILE_TARGET_SAVED'); settled()
    assert sorted(call(token,'aether_files_list',{})['targets'])==['gui-settings','rp-engine']
    view=call(token,'aether_files_config_read',{'target':'gui-settings'})['config']
    assert view['values']=={'click-sound':True}
    call(token,'aether_files_config_preview',{'target':'gui-settings','expectedHash':'0'*64,'replacements':{'click-sound':False}},False)
    call(token,'aether_files_config_preview',{'target':'gui-settings','expectedHash':view['hash'],'replacements':{'allow-private-hosts':True}},False)
    edit=call(token,'aether_files_config_preview',{'target':'gui-settings','expectedHash':view['hash'],'replacements':{'click-sound':False}})['change']
    assert gui_config.read_bytes()==original
    change=command('ais files approve '+edit['token'],'FILE_APPLIED gui-settings'); settled()
    updated=call(token,'aether_files_config_read',{'target':'gui-settings'})['config']
    assert updated['values']=={'click-sound':False}
    passed('scoped-config','Unlisted key and stale digest denied; pending review left the original unchanged; console approval changed the selected key.')
    source=plugins/'AetherScript/file-management/inbox/RPEngine.jar'
    digest=hashlib.sha256(source.read_bytes()).hexdigest()
    desired={'target':'rp-engine','filename':source.name,'sha256':digest}
    call(token,'aether_files_jar_stage',dict(desired,sha256='0'*64),False)
    stage=call(token,'aether_files_jar_stage',desired)['change']
    assert stage['details']['name']=='RPEngine' and stage['afterHash']==digest
    assert not (plugins/'RPEngine.jar').exists()
    install=command('ais files approve '+stage['token'],'FILE_APPLIED rp-engine'); settled()
    assert hashlib.sha256((plugins/'RPEngine.jar').read_bytes()).hexdigest()==digest
    passed('jar-staging','The real RPEngine JAR stayed in the inbox until exact console approval; an incorrect digest was rejected.')
    return (change.split('rollback=',1)[1].split()[0], install.split('rollback=',1)[1].split()[0], original)


def verify_installed(server, command, settled, bot, change, passed):
    plugins=server/'plugins'
    command('version RPEngine','0.2.91')
    command('version MCGUI','0.8.6')
    command('ais permit cloud-token player.inventory.write','CAPABILITIES_SAVED'); settled()
    command('ais reload cloud-token','ACTIVE generation='); settled()
    bot.command('cloudtoken'); bot.message('Cloud token delivered.',timeout=40)
    item=command('data get entity AetherHomeA Inventory','aetherdemo:cloud_token')
    assert 'item_model' in item and 'aetherscript:custom_item' in item
    built=None
    for path in (plugins/'RPEngine/output').rglob('*.zip'):
        with zipfile.ZipFile(path) as pack:
            if 'assets/aetherdemo/items/cloud_token.json' in pack.namelist():
                definition=json.loads(pack.read('assets/aetherdemo/items/cloud_token.json'))
                assert 'aetherdemo:' in json.dumps(definition)
                assert pack.read('assets/aetherdemo/textures/item/cloud_token.png')==png()
                built=path; break
    assert built is not None, 'RP Engine did not build the expected item definition'
    passed('resourcepack-model','Paper loaded RP Engine 0.2.91 and MC GUI 0.8.6. RP Engine built the item definition and its texture. AetherScript delivered the matching Paper model and ownership metadata. Visual rendering was not exercised.')
    command('ais files rollback '+change[0],'FILE_ROLLED_BACK gui-settings'); settled()
    assert (plugins/'MCGUI/config.yml').read_bytes()==change[2]
    expected='JAR_ROLLBACK_REQUIRES_STOP' if os.name=='nt' else 'FILE_ROLLED_BACK rp-engine'
    jar=plugins/'RPEngine.jar'
    before=hashlib.sha256(jar.read_bytes()).hexdigest()
    command('ais files rollback '+change[1],expected); settled()
    if os.name=='nt':
        assert hashlib.sha256(jar.read_bytes()).hexdigest()==before
        passed('persistent-file-rollback','Config restored byte-for-byte after restart. Windows blocked removal of the loaded JAR and preserved the reviewed file and receipt.')
    else:
        assert not jar.exists()
        passed('persistent-file-rollback','Config and new-JAR rollback restored their previous filesystem state after restart.')

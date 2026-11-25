"""Observe actual block changes on Paper after sending player break/place packets."""
import json
import time


def verify_protection(command, check, connect, teleport, passed, repo, scripts):
    target = scripts/'starter-protection.aether'
    packaged = scripts.parent/'examples'/target.name
    assert packaged.read_bytes() == (repo/'examples'/target.name).read_bytes()
    original = packaged.read_text(encoding='utf-8')
    target.write_text(original, encoding='utf-8')
    caps = 'runtime.event.block_break,runtime.event.block_place,runtime.event.cancel'
    check('events-owner-permit', 'ais permit starter-protection '+caps, 'CAPABILITIES_SAVED starter-protection:')
    check('events-activate', 'ais reload starter-protection', 'ACTIVE generation=')
    (scripts/'event-welcome.aether').write_text(
        'feature event-welcome version "1"\non join permission "*" {\n'
        '  send "Join world: " + world\n}\n', encoding='utf-8')
    check('join-owner-permit', 'ais permit event-welcome runtime.event.join', 'CAPABILITIES_SAVED event-welcome:')
    check('join-activate', 'ais reload event-welcome', 'ACTIVE generation=')
    actor = connect('AetherHomeA')
    actor.message('Join world: minecraft:overworld')
    passed('events-join', 'The player received the namespaced world key from the join handler.')
    command('gamemode creative AetherHomeA', 'Creative')
    teleport(actor, 10, 10)
    command('setblock 12 79 10 minecraft:stone', 'Changed')
    command('setblock 12 80 10 minecraft:stone', 'Changed')
    command('item replace entity AetherHomeA weapon.mainhand with minecraft:dirt', 'Replaced')

    def check_block(material):
        command(f'execute if block 12 80 10 minecraft:{material} run say event-state-{material}', 'event-state-'+material)

    def interact(op, message=None):
        before = len(actor.events)
        actor.request(op=op, x=12, y=80 if op == 'break' else 79, z=10)
        if message:
            actor.message(message)
        time.sleep(.4)
        if message:
            seen = [e for e in actor.events[before:] if e['event']=='message' and message in json.dumps(e)]
            assert len(seen) == 1, f'{op} produced {len(seen)} messages'

    interact('break', 'Block breaking is protected here.')
    check_block('stone')
    passed('events-guest-break', 'Guest received one denial and stone remained in the world.')
    changed = original.replace('Block breaking is protected here.', 'New break denial.')
    target.write_text(changed, encoding='utf-8')
    check('events-replace', 'ais reload starter-protection', 'ACTIVE generation=')
    offset = len(actor.events)
    interact('break', 'New break denial.')
    assert not any('Block breaking is protected here.' in json.dumps(e) for e in actor.events[offset:])
    check_block('stone')
    passed('events-reload-single-delivery', 'New denial delivered once, old denial absent, block preserved.')
    command('setblock 12 80 10 air', 'Changed')
    interact('place', 'Block placing is protected here.')
    check_block('air')
    passed('events-guest-place', 'Guest received one denial and target remained air.')
    command('op AetherHomeA', 'operator')
    interact('place')
    check_block('dirt')
    interact('break')
    check_block('air')
    passed('events-builder-bypass', 'Same actor with the bypass permission placed dirt and broke it.')

    heart_file = repo / 'examples/ten-heart-blocks.aether'
    (scripts / heart_file.name).write_bytes(heart_file.read_bytes())
    check('hearts-owner-permit', 'ais permit ten-heart-blocks runtime.event.block_break,runtime.event.block_hearts',
          'CAPABILITIES_SAVED ten-heart-blocks:')
    check('hearts-activate', 'ais reload ten-heart-blocks', 'ACTIVE generation=')
    command('deop AetherHomeA', 'operator')
    command('setblock 12 80 10 minecraft:stone', 'Changed')
    interact('break', 'New break denial.')
    check_block('stone')
    selector = '@e[type=minecraft:text_display,distance=..3]'

    def display(exists):
        condition = 'if' if exists else 'unless'
        command(f'execute positioned 12 80 10 {condition} entity {selector} run say hearts-display-{exists}',
                f'hearts-display-{exists}')

    display(False)
    passed('hearts-respects-protection', 'A denied break produced no heart display or block change.')
    check('hearts-restrict-protection-fixture', 'ais restrict starter-protection', 'CAPABILITIES_SAVED starter-protection:')
    interact('break')
    display(True)
    (scripts / heart_file.name).write_text(heart_file.read_text(encoding='utf-8') + '\n// Replace revision\n', encoding='utf-8')
    check('hearts-reload', 'ais reload ten-heart-blocks', 'ACTIVE generation=')
    display(False)
    for _ in range(9):
        interact('break')
        check_block('stone')
    display(True)
    interact('break')
    check_block('air')
    display(False)
    passed('hearts-tenth-break', 'After reload, nine hits retained stone and the tenth broke it and removed the display.')

    dashboard = repo / 'examples/world-display.aether'
    path = scripts / dashboard.name
    source = dashboard.read_text(encoding='utf-8').replace(
        '  send "Weather:', '  let target = block_at(world, 12, 80, 10)\n'
        '  block_set(target, "minecraft:glass")\n'
        '  send block_material(target)\n'
        '  title("World dashboard", "Ready", 0, 20, 0)\n'
        '  sound("minecraft:entity.experience_orb.pickup", 100, 100)\n'
        '  send "Weather:')
    path.write_text(source, encoding='utf-8')
    caps = 'player.location.read,world.read,world.block.write,player.display,player.sound,scheduler.repeat'
    check('dashboard-owner-permit', 'ais permit world-display '+caps, 'CAPABILITIES_SAVED world-display:')
    check('dashboard-activate', 'ais reload world-display', 'ACTIVE generation=')
    command('op AetherHomeA', 'operator')
    offset = len(actor.events)
    actor.command('world-display')
    actor.message('minecraft:glass')
    check_block('glass')
    actor.wait(lambda e: e['event']=='display' and e['kind']=='action_bar')
    actor.wait(lambda e: e['event']=='display' and e['kind']=='action_bar')
    packets = [e['kind'] for e in actor.events[offset:] if e['event']=='display']
    assert {'action_bar', 'set_title_text', 'boss_bar', 'scoreboard_objective', 'sound_effect'} <= set(packets), packets
    assert packets.count('action_bar') == 2, packets
    passed('dashboard-displays-and-timer', 'World write/read succeeded and the client received title, sound, boss bar, scoreboard and two timed action bars.')
    check('dashboard-restrict', 'ais restrict world-display', 'CAPABILITIES_SAVED world-display:')
    actor.wait(lambda e: e['event']=='display' and e['kind']=='boss_bar')
    actor.wait(lambda e: e['event']=='display' and e['kind']=='scoreboard_objective')
    passed('dashboard-cleared', 'Revocation removed persistent displays.')

    sample = repo / 'examples/gathering-station.aether'
    (scripts / sample.name).write_bytes(sample.read_bytes())
    caps = 'runtime.event.block_drop,runtime.event.replace_drop,runtime.event.block_interact,runtime.event.run_self,player.menu'
    check('gathering-owner-permit', 'ais permit gathering-station '+caps, 'CAPABILITIES_SAVED gathering-station:')
    check('gathering-activate', 'ais reload gathering-station', 'ACTIVE generation=')
    command('gamemode survival AetherHomeA', 'Survival')
    command('item replace entity AetherHomeA weapon.mainhand with minecraft:air', 'Replaced')
    teleport(actor, 10, 10)
    command('setblock 12 79 10 minecraft:cobblestone', 'Changed')
    command('setblock 12 80 10 minecraft:torch', 'Changed')
    guard = scripts / 'gathering-guard.aether'
    guard.write_text('feature gathering-guard version "1"\non block_break permission "*" {\n'
                     '  if block_type == "TORCH" { cancel }\n}\n', encoding='utf-8')
    check('gathering-protection-owner-permit', 'ais permit gathering-guard runtime.event.block_break,runtime.event.cancel',
          'CAPABILITIES_SAVED gathering-guard:')
    check('gathering-protection-activate', 'ais reload gathering-guard', 'ACTIVE generation=')
    interact('break')
    check_block('torch')
    no_crystal = 'execute positioned 12 80 10 unless entity @e[type=minecraft:item,distance=..3,nbt={Item:{id:"minecraft:amethyst_shard"}}] run say no-protected-drop'
    command(no_crystal, 'no-protected-drop')
    passed('gathering-protection', 'Cancellation kept the exact resource node in place and emitted no custom loot.')
    check('gathering-protection-restrict', 'ais restrict gathering-guard', 'CAPABILITIES_SAVED gathering-guard:')
    interact('break')
    check_block('air')
    crystal = '@e[type=minecraft:item,nbt={Item:{id:"minecraft:amethyst_shard",count:1}},limit=1]'
    command(f'data get entity {crystal} Item', 'Raw Crystal')
    command('scoreboard objectives add gather_smoke dummy', 'Created')
    command('execute store result score #drops gather_smoke if entity @e[type=minecraft:item,nbt={Item:{id:"minecraft:amethyst_shard"}}]', 'Count: 1')
    command('execute positioned 12 80 10 unless entity @e[type=minecraft:item,distance=..3,nbt={Item:{id:"minecraft:torch"}}] run say no-vanilla-drop', 'no-vanilla-drop')
    passed('gathering-custom-drop', 'A survival break of the exact node produced one entity with one named custom shard and no vanilla torch.')
    command('setblock 12 80 10 minecraft:barrel', 'Changed')
    actor.request(op='place', x=12, y=80, z=10)
    actor.wait(lambda event: event['event']=='menu_open' and 'Gathering station' in json.dumps(event))
    actor.request(op='menu_click', slot=0, button=0, mode=0)
    actor.message('Bring Raw Crystals here.')
    passed('gathering-station-menu', 'Main-hand right-click opened the owned selection menu and the real menu click ran its action.')
    from menu_workflow import verify_processing
    verify_processing(command, check, actor, passed, repo, scripts)


def verify_hooks(command, check, actor, passed, repo, scripts):
    command('op AetherHomeA', 'operator')
    sample = repo / 'examples/interaction-hooks.aether'
    (scripts / sample.name).write_bytes(sample.read_bytes())
    caps = ('runtime.event.item_interact,runtime.event.entity_interact,runtime.event.run_self,'
            'runtime.event.player_teleport,runtime.event.player_respawn,runtime.event.item_consume,'
            'runtime.event.projectile_hit,runtime.event.player_command,runtime.event.cancel')
    check('hooks-owner-permit', 'ais permit interaction-hooks '+caps, 'CAPABILITIES_SAVED interaction-hooks:')
    check('hooks-activate', 'ais reload interaction-hooks', 'ACTIVE generation=')
    (scripts / 'hook-key.aether').write_text(
        'feature hook-key version "1"\ncommand hookkey permission "aetherscript.test.issue" sender player {\n'
        '  if inventory_give(custom_item("station_key", "minecraft:paper", "Station Key", 1)) { send "Key issued." }\n'
        '}\n', encoding='utf-8')
    check('hook-key-owner-permit', 'ais permit hook-key player.inventory.write', 'CAPABILITIES_SAVED hook-key:')
    check('hook-key-activate', 'ais reload hook-key', 'ACTIVE generation=')
    command('setblock 10 79 10 minecraft:stone', 'Changed')
    command('setblock 12 79 10 minecraft:stone', 'Changed')
    command('tp AetherHomeA 10.5 80 10.5 -90 0', 'Teleported')
    actor.message('Teleport destination: minecraft:overworld')
    passed('hooks-teleport', 'A player teleport reached the new destination snapshot handler.')
    actor.command('hookkey')
    actor.message('Key issued.')
    command('data get entity AetherHomeA SelectedItem', 'hook-key:station_key')
    actor.request(op='use_item')
    actor.message('Interaction accepted.')
    command('setblock 12 80 11 minecraft:barrel', 'Changed')
    actor.request(op='place', x=12, y=80, z=11)
    actor.message('Interaction accepted.')
    actor.request(op='menu_close')
    passed('hooks-key', 'Main-hand use of a custom key in air and against a block queued the interaction command.')
    command('summon minecraft:villager 12.5 80 10.5 {NoAI:1b,Silent:1b,Tags:["hook_npc"]}', 'Summoned')
    npc = actor.wait(lambda event: event['event']=='villager' and abs(event['x'] - 12.5) < .2 and abs(event['y'] - 80) < .2)
    actor.request(op='interact_entity', id=npc['id'])
    actor.message('Interaction accepted.')
    passed('hooks-npc', 'A real right-click packet on the configured villager queued the interaction command.')
    command('kill @e[type=minecraft:villager,tag=hook_npc]', 'Killed')
    command('setblock 12 81 10 minecraft:stone', 'Changed')
    command('item replace entity AetherHomeA weapon.mainhand with minecraft:snowball', 'Replaced')
    actor.request(op='use_item')
    actor.message('Snowball hit the target.')
    passed('hooks-projectile', 'A thrown snowball struck a stone block and reached the shooter-scoped event.')
    command('item replace entity AetherHomeA weapon.mainhand with minecraft:golden_apple', 'Replaced')
    actor.request(op='use_item')
    actor.message('Golden apple consumed.')
    passed('hooks-consume', 'A real item-use packet completed a golden apple and reported its material.')
    actor.command('version hidden_fixture_argument')
    actor.message('Version command blocked by the fixture.')
    passed('hooks-command', 'The player command hook matched the command label and cancelled command execution.')
    command('kill AetherHomeA', 'Killed')
    time.sleep(.5)
    actor.request(op='respawn')
    actor.message('Respawn destination: minecraft:overworld')
    passed('hooks-respawn', 'A real player respawn reached the destination snapshot handler.')

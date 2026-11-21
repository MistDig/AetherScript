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

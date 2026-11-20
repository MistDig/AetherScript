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
    actor = connect('AetherHomeA')
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

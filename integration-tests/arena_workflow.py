"""Exercise two arena instances and combat cancellation on a disposable Paper world."""
import time


def verify_arenas(command, check, connect, teleport, passed, repo, scripts):
    source = repo / 'examples/two-arenas.aether'
    (scripts / source.name).write_bytes(source.read_bytes())
    caps = ('player.location.read,player.team,entity.own,state.shared.read,state.shared.write,'
            'runtime.event.entity_damage,runtime.event.entity_death,runtime.event.cancel')
    check('arenas-permit', 'ais permit two-arenas '+caps, 'CAPABILITIES_SAVED two-arenas:')
    check('arenas-activate', 'ais reload two-arenas', 'ACTIVE generation=')
    red, blue = [connect(name) for name in ('AetherHomeA', 'AetherHomeB')]
    for who in (red, blue):
        command(f'op {who.name}', 'operator')
    teleport(red, 8, 8)
    teleport(blue, 40, 8)
    command('fill 5 79 5 11 79 11 minecraft:stone', 'Successfully filled')
    command('fill 37 79 5 43 79 11 minecraft:stone', 'Successfully filled')
    for who, arena in [(red, 'red'), (blue, 'blue')]:
        who.command(f'arenas {arena} join')
        who.message('Joined '+arena)
        who.command(f'arenas {arena} start')
        who.message('Started '+arena+' boss')
        command(f'execute if entity @e[type=minecraft:zombie,name="{arena} arena boss"] run say boss-{arena}',
                'boss-'+arena)
    red.command('arenas red score')
    red.message('red score 1')
    blue.command('arenas blue show')
    blue.message('blue score 0')
    blue.message('Member: true')
    blue.message('Inside: true')
    blue.command('arenas blue target')
    blue.message('Boss targeted blue')
    passed('arenas-isolation', 'Two real bosses and separate scores/teams existed concurrently.')
    for arena in ('red', 'blue'):
        command(f'data merge entity @e[type=minecraft:zombie,name="{arena} arena boss",limit=1] {{NoAI:1b}}',
                'Modified entity data')
    time.sleep(1.5)
    command('damage AetherHomeA 2', 'Target is invulnerable')
    red.message('Damage blocked in red.')
    command('damage AetherHomeB 2', 'Applied')
    blue.message('Damage:')
    passed('arenas-combat-regions', 'Red cancelled damage while blue took damage.')
    red.command('arenas red cleanup')
    red.message('Cleaned red')
    command('execute unless entity @e[type=minecraft:zombie,name="red arena boss"] run say no-red', 'no-red')
    command('execute if entity @e[type=minecraft:zombie,name="blue arena boss"] run say still-blue', 'still-blue')
    check('arenas-restrict', 'ais restrict two-arenas', 'CAPABILITIES_SAVED two-arenas:')
    command('execute unless entity @e[type=minecraft:zombie,name="blue arena boss"] run say no-blue', 'no-blue')
    passed('arenas-cleanup', 'Explicit cleanup kept the other arena active. Revocation removed the remaining owned boss.')

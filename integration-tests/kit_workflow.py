"""Verify a kit's real inventory changes and its persisted cooldown on Paper."""
import re


def verify_kit(command, check, connect, start, stop, passed, repo, scripts):
    source = repo / 'examples/daily-kit.aether'
    (scripts / source.name).write_bytes(source.read_bytes())
    check('kit-owner-permit', 'ais permit daily-kit player.kit.claim',
          'CAPABILITIES_SAVED daily-kit:')
    check('kit-activate', 'ais reload daily-kit', 'ACTIVE generation=')
    first = connect('AetherHomeA')
    second = connect('AetherHomeB')
    for player in (first, second):
        command(f'op {player.name}', 'operator')

    def counts(player):
        for item, expected in (('bread', 16), ('iron_ingot', 4)):
            line = command(f'clear {player.name} minecraft:{item} 0', 'matching item')
            assert re.search(rf'\b{expected} matching item', line, re.I), line

    command('give AetherHomeA minecraft:stone 2304', '2304')
    first.command('dailykit')
    first.message('Not enough inventory space; your daily kit remains available.')
    passed('kit-full-denial', 'A full storage inventory blocked delivery.')
    command('clear AetherHomeA minecraft:stone', 'Removed 2304')
    first.command('dailykit')
    first.message('Daily kit claimed.')
    counts(first)
    passed('kit-claim-after-full', 'Clearing space allowed the same player to get 16 bread and 4 iron ingots.')
    first.command('dailykit')
    first.message('Your daily kit is on cooldown.')
    counts(first)
    passed('kit-repeat-denial', 'A repeat command left the inventory counts unchanged.')
    second.command('dailykit')
    second.message('Daily kit claimed.')
    counts(second)
    passed('kit-player-isolation', 'A different player could claim the same kit independently.')

    (scripts / 'inventory-check.aether').write_text(
        'feature inventory-check version "1"\n'
        'command inventory-check permission "aetherscript.example.dailykit" sender player {\n'
        '  if inventory_has(item("minecraft:iron_ingot", 4)) {\n'
        '    if inventory_remove(item("minecraft:iron_ingot", 4)) {\n'
        '      if inventory_give(item("minecraft:iron_ingot", 4)) {\n'
        '        send "Inventory calls worked."\n'
        '      }\n'
        '    }\n'
        '  }\n'
        '}\n', encoding='utf-8')
    check('inventory-owner-permit', 'ais permit inventory-check player.inventory.read,player.inventory.write',
          'CAPABILITIES_SAVED inventory-check:')
    check('inventory-activate', 'ais reload inventory-check', 'ACTIVE generation=')
    second.command('inventory-check')
    second.message('Inventory calls worked.')
    counts(second)
    passed('inventory-check-remove-give', 'The second player checked, removed and restored the same four items.')

    first.close()
    second.close()
    stop()
    start()
    first = connect('AetherHomeA')
    first.command('dailykit')
    first.message('Your daily kit is on cooldown.')
    counts(first)
    passed('kit-restart-cooldown', 'The original player still had the rewards and cooldown after restarting Paper.')

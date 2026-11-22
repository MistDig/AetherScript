"""Observe one shop exchange and rejected transfer gestures on Paper."""
import json
import time


def verify_menu(command, check, connect, passed, repo, scripts):
    source = repo / 'examples/menu-shop.aether'
    (scripts / source.name).write_bytes(source.read_bytes())
    check('shop-permit', 'ais permit menu-shop player.menu,player.inventory.exchange', 'CAPABILITIES_SAVED menu-shop:')
    check('shop-activate', 'ais reload menu-shop', 'ACTIVE generation=')
    actor = connect('AetherHomeA')
    command('op AetherHomeA', 'operator')
    command('give AetherHomeA minecraft:emerald 1', 'Gave')

    def shop():
        actor.command('menushop')
        menu = actor.wait(lambda event: event['event'] == 'menu_open')
        assert 'Cloud shop' in json.dumps(menu), menu
        time.sleep(.25)

    shop()
    for mode, slot, button in [(1, 0, 0), (6, 0, 0), (5, -999, 0), (5, 0, 1), (5, -999, 2)]:
        actor.request(op='menu_click', slot=slot, button=button, mode=mode)
        time.sleep(.12)
    command('clear AetherHomeA minecraft:emerald 0', '1 matching item')
    command('clear AetherHomeA minecraft:paper 0', 'No items')
    passed('shop-transfer-gestures', 'Shift, double-click and drag packets left payment and reward counts unchanged.')
    actor.request(op='menu_click', slot=0, button=0, mode=0)
    actor.message('Purchased one Cloud token.')
    command('clear AetherHomeA minecraft:emerald 0', 'No items')
    command('clear AetherHomeA minecraft:paper 0', '1 matching item')
    command('data get entity AetherHomeA Inventory', 'aetherscript:custom_item')
    passed('shop-exchange', 'One emerald became one tagged Cloud token.')
    shop()
    actor.request(op='menu_click', slot=0, button=0, mode=0)
    actor.message('You need one plain emerald and space for a Cloud token.')
    command('clear AetherHomeA minecraft:paper 0', '1 matching item')
    shop()
    actor.request(op='menu_close')
    actor.message('Shop closed.')
    shop()
    check('shop-restrict', 'ais restrict menu-shop', 'CAPABILITIES_SAVED menu-shop:')
    actor.wait(lambda event: event['event'] == 'menu_close')
    command('clear AetherHomeA minecraft:paper 0', '1 matching item')
    passed('shop-close-and-restrict', 'Closing and revoking a live menu each left the owned item count unchanged.')

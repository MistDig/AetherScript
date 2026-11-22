"""Verify a guarded purchase with installed provider JARs on disposable Paper."""


def verify_bridge(command, check, connect, teleport, passed, repo, scripts):
    source = repo / 'examples/guarded-shop.aether'
    (scripts / source.name).write_bytes(source.read_bytes())
    caps = ('player.location.read,player.inventory.write,integration.inspect,integration.permission,'
            'integration.economy.read,integration.economy.write,integration.worldguard.read')
    check('guarded-shop-permit', 'ais permit guarded-shop '+caps, 'CAPABILITIES_SAVED guarded-shop:')
    check('guarded-shop-activate', 'ais reload guarded-shop', 'ACTIVE generation=')
    player = connect('AetherHomeA')
    command('op AetherHomeA', 'operator')
    command('eco give AetherHomeA 10', 'AetherHomeA')

    def quote(balance):
        player.command('guardedshop quote')
        player.message('Quote: 4 bread costs 250 minor units')
        player.message(f'Balance in minor units: {balance}')

    teleport(player, 20, 20)
    quote(1000)
    player.command('guardedshop buy')
    player.message('Purchased 4 bread for 250 minor units.')
    command('minecraft:clear AetherHomeA minecraft:bread 0', '4 matching item')
    quote(750)
    passed('guarded-shop-provider-purchase', 'Vault and the installed economy debited 250 minor units and Paper delivered exactly four bread.')
    player.command('//pos1 8,78,8')
    player.message('First position')
    player.command('//pos2 12,82,12')
    player.message('Second position')
    player.command('region define protectedshop')
    player.message('A new region has been made named')
    player.command('region flag protectedshop build deny')
    player.message('build')
    teleport(player, 10, 10)
    player.command('guardedshop buy')
    player.message('Shop requires WorldGuard build access here.')
    command('minecraft:clear AetherHomeA minecraft:bread 0', '4 matching item')
    teleport(player, 20, 20)
    quote(750)
    passed('guarded-shop-worldguard', 'The WorldGuard denial prevented both payment and delivery.')

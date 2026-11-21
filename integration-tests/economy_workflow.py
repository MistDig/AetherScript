"""Verify contested stock, atomic balances and scoped shared state on real Paper."""
from concurrent.futures import ThreadPoolExecutor
import json


def verify_economy(command, check, connect, start, stop, passed, repo, scripts):
    path = scripts / 'economy.aether'
    source = (repo / 'examples' / path.name).read_text(encoding='utf-8')
    # Let both invocations finish their reads before either tries to write.
    path.write_text(source.replace('        if shared_commit', '        delay(40)\n        if shared_commit'), encoding='utf-8')
    caps = 'state.shared.read,state.shared.write,player.identity.self,scheduler.delay'
    check('economy-permit', 'ais permit economy '+caps, 'CAPABILITIES_SAVED economy:')
    check('economy-activate', 'ais reload economy', 'ACTIVE generation=')
    names = ['AetherHomeA', 'AetherHomeB']
    buyers = [connect(name) for name in names]
    for name in names:
        command(f'op {name}', 'operator')

    outcomes = ('Purchase committed.', 'Sold out.', 'Purchase conflict; try again.')

    def buy(player):
        player.command('ais run economy buy')
        message = player.wait(lambda event: event['event']=='message' and any(
            text in json.dumps(event) for text in outcomes))
        return next(i for i, text in enumerate(outcomes) if text in json.dumps(message))

    def show(player, won):
        player.command('ais run economy show')
        player.message('Stock: 0')
        player.message('Balance: 5' if won else 'Balance: 10')
        player.message('Owned: 1' if won else 'Owned: 0')

    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(buy, buyers))
    assert results.count(0) == 1 and results.count(2) == 1, results
    for i, player in enumerate(buyers):
        show(player, results[i] == 0)
    passed('economy-concurrent-last-item', 'Exactly one transaction committed. The stale buyer kept the entire balance.')

    world = scripts / 'world-state.aether'
    world.write_text('''feature world-state version "1"
command world-state permission "aetherscript.example.worldstate" sender player {
  arg action: text = "show"
  let saved = shared_read("world", "counter", 0)
  if action == "set" {
    if shared_commit("world", {"counter": 11}, list<text>()) {
      send "World value saved."
    }
  } else {
    if action == "delete" {
      if shared_commit("world", map<text,integer>(), ["counter"]) {
        send "World value deleted."
      }
    } else {
      send "World value: " + text(saved)
    }
  }
}
''', encoding='utf-8')
    check('world-state-permit', 'ais permit world-state state.shared.read,state.shared.write', 'CAPABILITIES_SAVED world-state:')
    check('world-state-activate', 'ais reload world-state', 'ACTIVE generation=')
    a, b = buyers
    a.command('ais run world-state set')
    a.message('World value saved.')
    b.command('ais run world-state show')
    b.message('World value: 11')
    command('execute in minecraft:the_nether run tp AetherHomeB 8 80 8', 'Teleported')
    b.wait(lambda event: event['event']=='position' and event['y']==80)
    b.command('ais run world-state show')
    b.message('World value: 0')
    a.command('ais run world-state delete')
    a.message('World value deleted.')
    a.command('ais run world-state show')
    a.message('World value: 0')
    passed('economy-world-scope-and-delete', 'Players shared a value in one world, another world saw the fallback, and deletion restored the fallback.')

    for player in buyers:
        player.close()
    stop()
    start()
    winner = results.index(0)
    player = connect(names[winner])
    show(player, True)
    player.command('ais run world-state show')
    player.message('World value: 0')
    passed('economy-restart', 'Stock, buyer balance, purchase count and world deletion survived restart.')

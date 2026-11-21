"""Exercise a multi-file feature with real invocation, rejection and restart recovery."""
import shutil


def verify_quest(command, check, connect, start, stop, passed, repo, scripts):
    package = scripts / 'quests.aetherpack'
    shutil.copytree(repo / 'examples' / package.name, package)
    check('quests-permit', 'ais permit quests state.own', 'CAPABILITIES_SAVED quests:')
    check('quests-activate', 'ais reload quests', 'ACTIVE generation=')
    player = connect('AetherHomeA')
    command('op AetherHomeA', 'operator')

    def invoke(action, stage, quest):
        player.command('ais run quests ' + action)
        player.message(f'Explorer: stage {stage}')
        player.message(f'Reward preview: {stage * 2} bread')
        player.message(quest)

    invoke('show', 0, 'Second quest: gather stone.')
    invoke('advance', 1, 'Second quest: gather stone.')
    passed('quests-record-progress', 'Imported code read, updated and saved a typed progress record.')
    module = package / 'second-quest.aether'
    original = module.read_text(encoding='utf-8')
    changed = original.replace('gather stone', 'gather wood')
    assert changed != original
    module.write_text(changed, encoding='utf-8')
    check('quests-module-reload', 'ais reload quests', 'ACTIVE generation=')
    invoke('show', 1, 'Second quest: gather wood.')
    module.write_text(changed.replace('"Second quest: gather wood."', 'missing_name'), encoding='utf-8')
    check('quests-module-location', 'ais validate quests', 'second-quest.aether')
    invoke('show', 1, 'Second quest: gather wood.')
    passed('quests-invalid-edit-isolated', 'Invalid module left the complete prior revision active.')
    player.close()
    stop()
    start()
    player = connect('AetherHomeA')
    invoke('show', 1, 'Second quest: gather wood.')
    passed('quests-package-restart', 'Recovered the saved package and persisted record with an invalid editable module still present.')

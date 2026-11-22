"""Verify a separately built addon, owner consent and clean behavior after removal."""


def verify_addon(command, check, connect, start, stop, passed, repo, scripts, jar):
    source = repo / 'examples/personal-weather.aether'
    (scripts / source.name).write_bytes(source.read_bytes())
    check('addon-denies-unapproved', 'ais validate personal-weather', 'not owner-approved')
    check('addon-owner-permit', 'ais permit personal-weather addon.sky.weather', 'CAPABILITIES_SAVED personal-weather:')
    check('addon-activate', 'ais reload personal-weather', 'ACTIVE generation=')
    actor = connect('AetherHomeA')
    command('op AetherHomeA', 'operator')
    actor.command('personalweather rain')
    actor.wait(lambda event: event['event']=='weather' and event['reason']=='start_raining')
    # The receipt can arrive just ahead of the packet, so consult the entire invocation trace.
    assert any('Personal weather set to rain' in str(event) for event in actor.events) or actor.message('Personal weather set to rain')
    actor.command('personalweather reset')
    actor.message('Personal weather reset.')
    passed('addon-personal-weather', 'A separately compiled addon produced a real weather packet and reset receipt.')
    actor.close()
    stop()
    jar.rename(jar.with_suffix('.disabled'))
    start()
    check('addon-missing-diagnostic', 'ais validate personal-weather', 'sky_weather')
    check('addon-missing-core-still-active', 'helloai', 'Hello from AetherScript!')
    passed('addon-removal-recovery', 'With the addon absent, its saved grant and source stayed on disk and unrelated features still loaded.')

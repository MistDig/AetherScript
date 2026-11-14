"""Real Paper console checks for bounded runtime programs and owned command names."""

def verify(command, start, stop, source, original, evidence):
    def check(name, text, expected):
        result = command(text, expected)
        evidence.append(dict(test=name, result='pass', detail=result))

    runtime = '''feature hello-ai version "runtime-1"
fn triangular(n: integer): integer {
    var total = 0
    var index = 0
    while index < n {
        set index = index + 1
        set total = total + index
    }
    return total
}
command helloai permission "aetherscript.example.hello" sender any {
    arg name: text
    arg count: integer = 3
    send "Hello " + name + "! total=" + text(triangular(count))
}
'''
    start()
    check('legacy-real-command', 'helloai', 'Hello from AetherScript!')
    source.write_text(runtime, encoding='utf-8')
    check('activate-runtime', 'ais reload hello-ai', 'ACTIVE generation=2')
    check('direct-command-MistDig', 'helloai MistDig 3', 'Hello MistDig! total=6')
    check('same-revision-Alex', 'helloai Alex 2', 'Hello Alex! total=3')
    check('unchanged-functions-retain-generation', 'ais reload hello-ai', 'UNCHANGED: no new revision; generation=2')
    check('quoted-name-and-default', 'helloai "Alex Smith"', 'Hello Alex Smith! total=6')
    source.write_text(runtime.replace('while index < n', 'while "bad"'), encoding='utf-8')
    check('invalid-loop-preserves-active', 'ais reload hello-ai', 'active generation unchanged')
    check('previous-command-still-runs', 'helloai Alex 2', 'Hello Alex! total=3')
    source.write_text(runtime.replace('command helloai ', 'command stop '), encoding='utf-8')
    check('foreign-stop-collision', 'ais reload hello-ai', 'command already registered')
    check('collision-retains-old-command', 'helloai MistDig 3', 'Hello MistDig! total=6')
    source.write_text(runtime.replace('command helloai ', 'command greetcount '), encoding='utf-8')
    check('rename-registers-new-command', 'ais reload hello-ai', 'ACTIVE generation=3')
    check('renamed-command-runs', 'greetcount Alex 2', 'Hello Alex! total=3')
    check('renamed-old-command-removed', 'helloai', 'Unknown')
    stop()
    start()
    check('restart-restores-command-and-program', 'greetcount MistDig 4', 'Hello MistDig! total=10')
    source.unlink()
    check('full-reload-retires-feature', 'ais reload', 'features=0')
    check('retired-command-removed', 'greetcount Alex', 'Unknown')
    source.write_text(original, encoding='utf-8')
    check('restore-legacy-source', 'ais reload', 'features=1')
    check('legacy-command-works-after-replacement', 'helloai', 'Hello from AetherScript!')

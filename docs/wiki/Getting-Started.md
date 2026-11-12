[☁ Home](https://github.com/MistDig/AetherScript/wiki/Home) · [Start](https://github.com/MistDig/AetherScript/wiki/Getting-Started) · [Commands](https://github.com/MistDig/AetherScript/wiki/Commands) · [Download](https://github.com/MistDig/AetherScript/releases/tag/v0.0.6)

---

## Your first little script

**You need:** Paper 26.3 build 245, Java 25, and console access or `aetherscript.admin`.

### 1 · Install

Download **AetherScript-0.0.6.jar** from the [release](https://github.com/MistDig/AetherScript/releases/tag/v0.0.6). Stop Paper, place it in `plugins/`, and start the server. Keep only one AetherScript plugin JAR installed. The separate MCP JAR does not belong in `plugins/`.

Wait for `Ready. Restored ...`. A fresh install creates and activates `plugins/AetherScript/scripts/hello-ai.yml` automatically.

```text
/ais status
/ais run hello-ai
```

You should receive `Hello from AetherScript!`. Console commands omit the leading slash.

### 2 · Make it yours

Open `plugins/AetherScript/scripts/hello-ai.yml` and change the final quoted `text` value. Save, then run:

```text
/ais validate hello-ai
/ais reload hello-ai
/ais run hello-ai
```

Validation checks without activating or saving. Reload activates a valid change and saves its revision. There is no automatic file watcher.

For a complete native-language example with variables and a conditional, follow [Writing scripts with AI](https://github.com/MistDig/AetherScript/wiki/AI-Authoring).

### 3 · See what is running

```text
/ais status hello-ai
/ais history hello-ai
/ais diff hello-ai
```

`UP TO DATE` means your editable bytes match the running revision. `RUNNING SAVED VERSION` means the file is missing, invalid or different. Keep editing the file shown by status; the engine manages internal saved copies.

### Permissions

Administration requires `aetherscript.admin`. The example requires `aetherscript.example.hello` to run and defaults to operators. Grant the exact script permission through your permissions system for other players. Invocation is `/ais run hello-ai`; the metadata `name: helloai` does not register `/helloai`.

### Upgrade from 0.0.1

Stop Paper and back up `plugins/AetherScript/`. Replace the old JAR and restart. **Valid editable scripts now activate automatically at startup.** The first activation creates the initial saved revision; 0.0.1 has no saved history to recover.

A missing configuration is generated. If you previously created a custom config, compare it with [the supported configuration](https://github.com/MistDig/AetherScript/wiki/Revisions-and-Recovery#configuration); unknown keys are rejected. Follow the [upgrade checklist](https://github.com/MistDig/AetherScript/wiki/Testing-0.0.2).

---

[Back to the field guide](https://github.com/MistDig/AetherScript/wiki/Home) · AetherScript 0.0.6


## Connect Codex on Windows

For scoped remote authoring, the owner first configures a personal HTTPS relay and private `plugins/AetherScript/relay.json`, then runs `ais remote cloud-note` from the server console. Extract `AetherScript-Codex-Windows-0.0.6.zip` and double-click `Connect-AetherScript.cmd` on the Codex computer. Paste the private pairing URI into the helper prompt, then open a new Codex chat. Keep the URI out of chat/logs and use `ais remote-off` to revoke access. Follow the [complete connection guide](https://github.com/MistDig/AetherScript/wiki/Security-and-MCP) for grants, privacy and setup requirements.

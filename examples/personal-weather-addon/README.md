# 0.0.27 staged addon guide and standalone example

These drafts stay outside the repository until the prior release is committed. `personal-weather-addon/` is an ordinary Java Bukkit plugin, with compile-only references to AetherScript and the exact Paper target. `personal-weather.aether` invokes its typed callable. No AetherScript implementation or Paper classes are bundled into the addon JAR.

## Build

Use Java 25 and the pinned AetherScript Gradle 9.1.0 wrapper, or Gradle 9.1.0. From PowerShell, replace the three paths below with the matching checkout, example directory and built/released 0.0.27 plugin JAR:

```powershell
& 'C:\path\AetherScript\gradlew.bat' -p 'C:\path\personal-weather-addon' build '-PaetherScriptJar=C:\path\AetherScript-0.0.27.jar'
```

The output is `personal-weather-addon/build/libs/PersonalWeatherAddon-1.0.0.jar`. The build resolves `io.papermc.paper:paper-api:26.3.build.245-stable`, including its compile-time dependencies, and requires network access or an existing Gradle cache. AetherScript remains a compile-only local JAR. This example intentionally uses the new public SDK from 0.0.27; an older AetherScript JAR will fail compilation.

## Install and authorize

Copy the addon JAR and the matching AetherScript plugin JAR into the Paper server's `plugins/` directory, then restart. `plugin.yml` declares `depend: [AetherScript]` so the service is available before registration. Copy `personal-weather.aether` into the AetherScript scripts directory. In the server console:

```text
ais permit personal-weather addon.sky.weather
ais validate personal-weather
ais reload personal-weather
```

Grant the player the explicit command permission `aetherscript.example.personalweather`. As that player run `/ais run personal-weather rain`, `/ais run personal-weather clear`, or `/ais run personal-weather reset`. The default argument is rain. Success messages are `Personal weather set to rain for 200 ticks.`, `Personal weather set to clear for 200 ticks.` and `Personal weather reset.`

The callable descriptor is `sky_weather(text) -> text`, player required, capability/ID `addon.sky.weather`. It exposes Bukkit's invoking-player `setPlayerWeather` and `resetPlayerWeather`, rather than global world weather. One capability covers rain, clear and reset; the script's command permission controls who can invoke the example. This is trusted Java code installed by the owner; the script grant does not sandbox its Java implementation.

## Observable verification

1. Use two online players in the same world and record global world weather.
2. Player one calls rain. Observe their personal rain while player two remains unaffected and global world weather remains unchanged.
3. Call clear and verify the first player's personal weather changes. Wait 200 server ticks and verify the previous personal-weather state is restored. Tick duration depends on server scheduling.
4. Call rain then reset; verify immediate restoration. Call rain then quit/rejoin; verify no stale override. Stop/restart the server during an override and check cleanup on normal shutdown.
5. Deny `addon.sky.weather`, validate/run and verify the call cannot execute. Unregister/disable the addon and verify the callable becomes unavailable and active overrides are restored.
6. Validate `sky_weather(1)` and verify a type diagnostic, and validate without the addon installed to verify unavailable-callable diagnostics.

Registration/unregistration and compiler checks are automated evidence only when run. A compiled JAR does not establish personal-weather behavior on a real Paper server. Actual AI-client discovery remains a separate acceptance test.

## Cleanup boundaries

The addon tracks at most 128 active overrides. Each lasts at most 200 server ticks from the latest call and is restored on explicit reset, quit or addon disable. It preserves a previously configured personal weather value. A later differing change by another plugin is left intact; the Bukkit weather API cannot identify ownership of a later identical value. Script reload or grant revocation prevents future calls but does not invoke addon cleanup: existing overrides expire through the addon's own timer. Normal plugin shutdown cleans up; abrupt process termination cannot run Java cleanup hooks.

## Apply

Copy the standalone addon sources into an examples addon directory and the script into `examples/`. Merge `contract-fragment.json`, append `reference-section.md` to the maintained human/AI guides, add a dated 0.0.27 changelog entry with actual results, and update the 0.0.27 tracker row with implemented scope and remaining gaps. Increment the repository version only after the previous release is committed. Rebuild the SDK and addon, validate scripts with the installed catalog, regenerate the site exports and record real Paper checks separately.

## Staged compilation evidence


The separate addon compiled with Java 25 against the packaged 0.0.27 SDK and passed the Paper personal-weather fixture.

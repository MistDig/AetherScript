[Home](https://github.com/MistDig/AetherScript/wiki/Home)

---

## The plugin did not start

On the Minecraft computer, use the server console and type `ais status`. Confirm the installed version, `ready=true`, and the startup log. Use Java 25 and Paper 26.3. Keep one AetherScript plugin JAR in plugins. Keep the existing AetherScript folder when upgrading.

If storage is corrupt, preserve the files and consult the recovery guide before editing internal storage. Local script errors identify the editable source path and preserve the saved working version.

## Permission denied in Minecraft

Administrative commands require `aetherscript.admin`. It defaults to operators. A permissions plugin can override the default. Use the server console to check status and grant the intended node through your permissions system.

The feature's own permission is checked separately when running it. Browser approval belongs in the actual server console through `ais connect`.

## My pairing link expired

On the Minecraft computer, type `ais connect` again. Open the fresh link on the AI computer. Use the same browser for the AI app's authorization. Each console link authorizes one connection and expires after ten minutes.

Do not share the link or paste it into an AI conversation. A previously consumed link cannot be reused.

## The page says waiting

Approve the exact feature IDs and finish the AI app's sign-in. Ask the app to check AetherScript server status. Connected appears after that app's request reaches Paper and Paper answers.

If Paper is offline, start it and allow the connection to retry. Check the server console for a relay-unavailable message. Daily free-service quotas can temporarily interrupt access. Local scripts still run.

## My AI cannot see a script

Use `ais connections` in the Minecraft console and check its exact IDs. Creating a new feature requires full access or approving that ID. A connection cannot enlarge its own grant. Full access is available only through a fresh owner approval. Use a fresh `ais connect` approval for a new grant.

The native file's ID must match its filename. Keep one source format per feature ID. A remotely managed native feature cannot overwrite an existing YAML feature of the same ID.

## A test failed

Read the actual message list. Compare spelling, case, punctuation and order. A synthetic sender cannot run player-only features. Run player behavior in Minecraft with the correct permission.

## I want to remove access

On the Minecraft computer, type `ais disconnect` for all connections, or include one ID from `ais connections`. Access blocks immediately. Wait for the console to confirm saved revocation before restarting. Your scripts keep running locally.

---

[Recovery guide](https://github.com/MistDig/AetherScript/wiki/Revisions-and-Recovery)

## Repeated rejected-grant messages on 0.0.7

Update the Paper plugin to 0.0.8. The connector logs each rejected grant state once, clears superseded pending links and informs the service of terminal rejection. Use a fresh `ais connect` and sign in again when the AI app reports invalid_token.

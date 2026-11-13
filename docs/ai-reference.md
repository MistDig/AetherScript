# AetherScript AI reference

## Current capabilities

This document describes the shipped engine. It supports native .aether message commands, typed positional arguments, immutable locals, arithmetic, boolean conditions, explicit reload, revisions and rollback. Events, inventories, item grants, economy, quests, functions and persistent player variables are planned. Do not invent syntax or promise unsupported gameplay.

Use tool discovery from the connected app as the authority for available tools. Hosted and local gateways expose different tools. A hosted service version may differ from the plugin release version.

## Tools by connection mode

Hosted OAuth MCP exposes exactly these six tools:
- aether_server_status {}
- aether_scripts_list {}
- aether_scripts_read {id}
- aether_scripts_validate {id, source}
- aether_scripts_apply {id, source, expectedHash}
- aether_scripts_test {id, expectedMessages, arguments?}

The local stdio gateway additionally has aether_capabilities {}, aether_validate {source, format}, and aether_test {source, format, expectedMessages, arguments?}. Offline and loopback status modes have four tools total, including aether_server_status, and cannot deploy. A local gateway using a management relay profile has nine tools. Hosted clients cannot call aether_capabilities, aether_validate or aether_test. Use the hosted script tools instead.

format is aether, yaml or json for local validation/testing. Remote management uses native .aether files. arguments is an optional list of strings, including strings for integer and boolean inputs. expectedMessages is an ordered list of exact message strings.

## Permissions and autonomous edits

Read aether_server_status and aether_scripts_list. The live status includes allScripts and targets.
When allScripts is true, you may create any valid native feature ID and edit existing native scripts within the user's request. An empty list of existing scripts does not remove permission to create one.
When allScripts is false, use only the granted IDs. Explain the need for fresh owner approval if the requested ID is outside that grant.
Choose a sensible ID when the user authorizes creation but supplies no name. Do not ask for an additional approval already granted by the user and server. Never claim a denied operation succeeded.
Feature IDs are lowercase, start with a letter, and contain only lowercase letters, digits and hyphens, up to 64 characters. The source filename must match the ID. Full native access does not overwrite existing YAML/JSON sources or bypass source-format collisions.

## Safe editing workflow

1. Read the target with aether_scripts_read.
2. For creation, confirm exists=false and use expectedHash "ABSENT". For edits use the returned hash.
3. Write a complete valid native source. Keep explicit permission and sender rules.
4. Validate with aether_scripts_validate, then apply with aether_scripts_apply.
5. Read back source, hash and activeHash. Confirm the intended source is active.
6. Test using aether_scripts_test with explicit expectedMessages and arguments.
7. Report actualMessages, matched and the active revision. A synthetic test does not certify player rendering or player-only behavior.

On a stale source hash, read again before merging. After a timeout or unknown network outcome, read back before retrying because the change may already be committed. Invalid edits preserve the working revision. A test mismatch is not success.

## Complete runnable example

Save as greet.aether. The command header is metadata. Invocation uses /ais run greet, not a directly registered /greet command.

```aether
feature greet version "1.0.0"
command greet permission "aetherscript.example.greet" sender any {
  arg name: text
  arg excited: boolean = true
  if excited {
    send "Hello " + name + "!"
  } else {
    send "Hello " + name
  }
}
```

Test the active feature with:
{"id":"greet","arguments":["MistDig"],"expectedMessages":["Hello MistDig!"]}
Then test:
{"id":"greet","arguments":["Alex","false"],"expectedMessages":["Hello Alex"]}
A player invokes /ais run greet "Alex Smith". Administrator console commands omit the leading slash.

## Syntax and limits

Declare arg name: text, arg count: integer = 1 or arg enabled: boolean = true at the start of a command. Required arguments precede defaults. At most 16 arguments, each at most 2048 UTF-16 units. Integer inputs are signed 64-bit decimal strings; booleans are exactly true or false. Quoted console arguments can contain spaces.

let creates an immutable inferred text, integer or boolean value. if requires a boolean. send requires text. Use text(value) for explicit conversion. Operators include +, -, *, /, %, comparisons, equality, !, && and ||. Braces delimit blocks; put statements on separate lines. Comments begin //.
Commands with arguments evaluate fresh values at invocation. Scripts without arg declarations preserve preparation-time evaluation. Both branches are type-checked. Arithmetic errors and invalid arguments fail before messages are emitted.
Source limit is 64 KiB locally. The hosted tool's source schema allows at most 16000 characters and its serialized request has a smaller transport envelope than the compiler's source limit. Keep remote scripts comfortably within both limits.
Messages are plain text, at most 2048 UTF-16 units each and 1–32 messages. No arbitrary host objects, Java calls or general console execution.

## Errors and connection recovery

Read isError and the response body. Validation diagnostics include a location in the message; local validation also returns line, column, path and reason. Correct the source and validate again.
TEXT_SOURCE_REQUIRED means the owner must resolve an existing legacy/duplicate source format. TARGET_DENIED means the requested ID is outside selected permissions.
HTTP 401 invalid_token requires reconnecting with a fresh ais connect browser approval. Superseded, expired, rejected and revoked sessions terminate. HTTP 503 temporarily_unavailable with Retry-After means a valid new connection is waiting for Paper; retry after the indicated delay.
The owner can inspect ais connections and revoke with ais disconnect. Approval does not imply the connection has reached Paper; Connected is confirmed after an actual status response.

## Reference priority

Use this current reference, discovered tool schemas and the bundled authoring contract for generation. The following full-guide appendix includes human tutorials, historical test records and explicitly planned work. Historical release examples do not add current capabilities.

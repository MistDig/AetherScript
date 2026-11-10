# Security status

This development release exposes no remote listener and no AI deployment path. MCP only accepts source supplied in the request; it does not read server directories, files, credentials, logs, or live state. Tool annotations are descriptive, not an authorization mechanism.

Local `/ais` administration requires `aetherscript.admin`; runtime messages require the script's explicit permission. YAML tags/anchors/aliases, duplicate keys, unknown fields/actions, arbitrary code, non-sender targets, oversized input, and unsafe logical IDs are rejected. Syntax failures avoid dumping source; bounded field diagnostics can show the invalid value. Source files must be regular files under the owner-managed scripts directory. Dependencies are shaded/relocated in the plugin.

Before enabling any AI deployment: independently authenticated gateway and plugin identities, default-deny server policy, per-agent/server/environment/feature/action scopes, capability inference, runtime/player permission intersection, token rotation/revocation, request limits, approval binding, stale-plan detection, durable commits, and direct bridge-bypass tests are mandatory. No self-approval or policy-editing agent tools.

Security reporting: the repository is https://github.com/MistDig/AetherScript. Report privately to the repository owner through an agreed private channel; no dedicated security contact is currently configured. Do not include real tokens or private server logs in public issues.

Installed Java plugins and host administrators remain trusted. Registry rollback does not undo world changes. The local audit directory needs owner-only OS permissions and monitoring; ACL provisioning, log rotation and remote audit integrity are not implemented. Local revision recovery is implemented in 0.0.2; checksums are not authentication against privileged edits, and process-crash tests do not establish power-loss durability.

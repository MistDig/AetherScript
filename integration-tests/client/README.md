# Offline homes integration client

Original test harness using pinned `minecraft-protocol@1.68.0`. Install dependencies using `npm ci --ignore-scripts` into an external working directory. No credentials or Microsoft account are used. Dependencies and ViaProxy JARs must stay outside this repository.

Copy `home_client.cjs` and the manifests to that working directory. Obtain official ViaProxy3.4.14 (SHA256 `2894bbfb2f4342f8fde887af6c689313efd5b0e46a8006775eea0e21e2948ab5`) there. The Python workflow verifies this digest and binds the proxy only to127.0.0.1:25568, targeting the offline test Paper endpoint127.0.0.1:25591. Disable optional ViaBedrock resource-pack HTTP translation.

Run `node /absolute/path/home_client.cjs 25568 AetherHomeA`. JSON stdin requests: `{"op":"command","command":"home set main"}`, `{"op":"position"}`, `{"op":"quit"}`. Stdout contains JSON events; keepalive, teleport confirmation, ping, chunk acknowledgements and tick-end packets are automatic.

This is a26.1 protocol775 client translated to26.3 protocol777, not a native26.3 client. Primary references: [ViaProxy release](https://github.com/ViaVersion/ViaProxy/releases/tag/v3.4.14), [26.3 support release](https://github.com/ViaVersion/ViaProxy/releases/tag/v3.4.13), [protocol client API](https://github.com/PrismarineJS/node-minecraft-protocol/blob/master/docs/API.md).

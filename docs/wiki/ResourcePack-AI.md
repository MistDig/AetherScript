[Home](https://github.com/MistDig/AetherScript/wiki/Home)

---

## Use a model from a resource pack

Install the Paper plugin and get the client resource pack working first. Paste the exact item definition key into the fifth argument of `custom_item`. Paper sends it in Minecraft's `item_model` component. The client uses the matching item definition and textures from its active packs. AetherScript owns the gameplay and inventory metadata.

```aether
feature cloud-token version "1.0.0"
command cloudtoken permission "aetherscript.example.cloudtoken" sender player {
  let token = custom_item("cloud_token", "minecraft:paper", "Cloud token", 1, "aetherdemo:cloud_token")
  if inventory_give(token) {
    send "Cloud token delivered."
  } else {
    send "Free an inventory slot."
  }
}
```

On the **Paper PC**, save that script as `plugins/AetherScript/scripts/cloud-token.aether`. In the server console, enter `ais permit cloud-token player.inventory.write` and then `ais reload cloud-token`. Look for `ACTIVE`. On the **player PC**, join with the pack enabled and run `/cloudtoken` as a player who has the declared permission. Inspect the item in the inventory. An item definition at `assets/aetherdemo/items/cloud_token.json` has the key `aetherdemo:cloud_token`. Check that key and the client pack if the appearance is wrong.

## Choose the ResourcePack AI tool for the job

| Job | Where it happens | What to carry into AetherScript |
| --- | --- | --- |
| Generate or upload textures, models, sounds, GUI art | ResourcePack AI Studio or its separate MCP connection. Export Java for Java players. | The exported item model or sound key. Inspect the actual export before writing the script. Studio generation uses the account's AI Credits. |
| Install configurable custom items and other pack content | RP Engine on the Paper PC. Its documented pack content folder holds item YAML and textures. | The namespaced ID listed by RP Engine. Studio art becomes an RP Engine registered item only after you define its gameplay metadata there. |
| Build a full-screen or in-world HTML interface | MC GUI on the Paper PC. Use its editor or the ResourcePack AI MCP custom-GUI tools, then import on the server with the vendor's command. | A player command defined by your script. Configure a supported GUI button action to invoke it as the player and verify the script permission for the intended audience. |
| Open a native Minecraft dialog | Dialog Engine with its YAML and bundled guide on the Paper PC. | A supported player command or a separately installed Java addon that bridges its documented events. |
| Import animated GLB/OBJ models | 3D Engine on the Paper PC. Follow the plugin's import and setup instructions. | A vendor-managed model or a Java addon for typed operations. |

Start with the formats and versions in the vendor's guides. [ResourcePack AI API](https://resourcepack.ai/docs/api-reference) · [Java exports](https://resourcepack.ai/docs/exporting) · [RP Engine model keys](https://resourcepack.ai/docs/rp-engine/ids) · [RP Engine items](https://resourcepack.ai/docs/rp-engine/items) · [MC GUI](https://resourcepack.ai/docs/mcgui) · [Dialog Engine](https://resourcepack.ai/docs/dialog-engine) · [3D Engine](https://resourcepack.ai/docs/3d-engine)

AetherScript's MCP connector manages AetherScript features and approved file targets. Use the vendor's own editor, MCP connector, and operator commands for installation, asset publishing, GUI imports, dialogs, and 3D model operations. The active Minecraft client and its pack determine the rendered result. Paper can verify the item-model component before someone visually checks it in game.

## Tested on Paper 26.3 build 245

RP Engine 0.2.91 built the cloud token's item definition and PNG. Paper reported the matching model key on the item issued by AetherScript. MC GUI 0.8.6 loaded alongside AetherScript and its approved config setting was changed and restored. In a separate run 3D Engine 0.1.6 loaded an original OBJ and built pack assets. Client rendering and live GUI clicks still need a player test. Dialog Engine remains unverified. If two packs supply the same shader, check the vendor's pack ordering guidance. [3D Engine pack and shader documentation](https://resourcepack.ai/docs/3d-engine)

---

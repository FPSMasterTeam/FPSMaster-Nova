# ViaFabric compatibility

Nova can be installed next to [ViaFabric](https://modrinth.com/mod/viafabric). Nova does not vendor ViaFabric and does not hard-depend on Fabric API.

## Root cause

ViaFabric translates packets by injecting into `Connection` / handshake types and, when Fabric API is present, into `RegistrySyncManager.configureClient` (`remap = false`, Minecraft types in the injector descriptor).

Nova previously declared `depends.fabric-api` even though no Nova code calls Fabric API. That forced `fabric-registry-sync-v0` onto every install. ViaFabric's own README warns that registry synchronization breaks protocol translation, and the debug mixin that tries to cancel `configureClient` fails mixin apply in named/Loom environments (`class_8610` vs `ServerConfigurationPacketListenerImpl`).

The fix is the smallest correct change: stop requiring Fabric API. Players who want ViaFabric still install Fabric API themselves (ViaFabric needs `fabric-resource-loader-v0`). Nova no longer forces registry-sync onto installs that do not need it.

## Install (player)

Drop the ViaFabric jar that lists your Minecraft version into `mods/`, together with Fabric API if ViaFabric asks for it. Official ViaFabric jars already nest ViaVersion as a JiJ (`fabric.mod.json` `jars`).

## Dev (`runClient`)

```bash
./gradlew :<mc>:runClient -PwithViaFabric
```

Loom remapping of the published ViaFabric jar drops the `jars` field; the build restores it before `runClient`. `-PwithViaFabric` also excludes `fabric-registry-sync-v0` from the dev Fabric API umbrella so ViaFabric's `MixinRegistrySyncManager` is not applied against named mappings.

## Version matrix

Current ViaFabric (Modrinth project `YlKdE5VK`, checked 2026-08-21) vs Nova nodes:

| Nova | Current ViaFabric artifact | Notes |
|---|---|---|
| 1.21.11 | `0.4.21+181-1.14-1.21` | Supported by current ViaFabric |
| 26.2 | `0.4.21+182-26.x` | Supported by current ViaFabric |
| 1.21.8 | last listing: `0.4.19+118-main` | Current `1.14-1.21` line no longer lists 1.21.8 |
| 1.21.1 | last listing: `0.4.15+84-main` | Current line does not list 1.21.1 |
| 1.20.1 | last listing: `0.4.18+109-main` | Current line lists 1.20.6, not 1.20.1 |
| 1.19.2 | last listing: `0.4.9+21-main` | Current line lists 1.19.4, not 1.19.2 |

Do not install ViaFabricPlus alongside ViaFabric (`viafabricplus` `breaks: viafabric`).

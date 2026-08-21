# ViaFabric compatibility

Nova can be installed next to [ViaFabric](https://modrinth.com/mod/viafabric). Nova does not vendor ViaFabric and does not hard-depend on Fabric API.

## Root cause

ViaFabric translates packets by injecting into `Connection` / handshake types and, when Fabric API is present, into `RegistrySyncManager.configureClient` (`remap = false`, Minecraft types in the injector descriptor).

Nova previously declared `depends.fabric-api` even though no Nova code calls Fabric API. That forced `fabric-registry-sync-v0` onto every install. ViaFabric's own README warns that registry synchronization breaks protocol translation, and the debug mixin that tries to cancel `configureClient` fails mixin apply in named/Loom environments (`class_8610` vs `ServerConfigurationPacketListenerImpl`).

The fix is the smallest correct change: stop requiring Fabric API. Players who want ViaFabric still install `fabric-resource-loader-v0` (or the Fabric API umbrella) themselves. Nova no longer forces registry-sync onto installs that do not need it.

On a production (intermediary) install with the full Fabric API, ViaFabric's registry-sync mixin can apply because `class_8610` matches. Named/Loom `runClient` is the environment that turns that mixin into a fatal `InvalidInjectionException`. ViaFabric's README still warns that registry synchronization breaks protocol translation.

## Install (player)

Drop the ViaFabric jar that lists your Minecraft version into `mods/`, together with `fabric-resource-loader-v0` (or Fabric API) if ViaFabric asks for it.

ViaFabric 0.4.15+ nests a Java-8-downgraded ViaVersion. That JiJ crashes on Java 21 (`NoSuchMethodError` on `Runtime.Version.feature` / jvmdg stubs). ViaVersion's own install docs: put the current [ViaVersion](https://modrinth.com/plugin/viaversion) jar into `mods/` to override the included copy. `-PwithViaFabric` does that automatically (except 1.19.2, whose ViaFabric 0.4.9 still nests ViaVersion 4.x).

## Dev (`runClient`)

```bash
./gradlew :<mc>:runClient -PwithViaFabric
```

Loom's `mod*` configurations remap artifacts and **strip nested jars**. That drops ViaVersion and leaves `viafabric-mc*` in intermediary, so named `runClient` dies with `ClassNotFoundException: net.minecraft.class_1132`. `-PwithViaFabric` instead copies the official ViaFabric jar into `run/mods/` (player install) and puts only `fabric-resource-loader-v0` on the classpath — ViaFabric's real depend — not the Fabric API umbrella. That keeps `RegistrySyncManager` off the named/Loom classpath so `MixinRegistrySyncManager` is skipped instead of failing mixin apply. Do not exclude `fabric-registry-sync-v0` from the umbrella while leaving the rest of Fabric API — other API modules hard-depend on it and loader resolution then fails.

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

## Tested (2026-08-21)

`xvfb-run ./gradlew :1.21.11:runClient -PwithViaFabric` loaded `viafabric 0.4.21+181-1.14-1.21`, `viafabric-mc12111`, and `viaversion 5.12.0-SNAPSHOT`. ViaVersion finished mapping loading; FPSMaster initialized; LWJGL started; resource reload included viafabric + viaversion. The previous `MixinRegistrySyncManager` apply crash, `class_1132` classload crash, and jvmdg `Runtime.Version.feature` crash did not recur. Headless OpenAL/ALSA failed in this environment (no sound device) and is unrelated. 26.2 was not launched here (needs JDK 25).

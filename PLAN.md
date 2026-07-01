# Schildblase — Implementation Plan

**A Minecraft Fabric 1.20.1 mod that adds a placeable "Schildblase" (shield bubble) block which projects an animated, shader-driven energy dome into the world.**

This document is fully self-contained. It is written so that execution subagents with **no other context** can implement the mod milestone-by-milestone. Every version, file path, class name, command, and shader is specified. Follow milestones **in order** (M0 → M7); each has its own verification step.

---

## 1. Overview & scope

### What the mod does

- Adds one block: the **Schildblase-Projektor** (`schildblase:schildblase_block`). Crafted/obtained from the creative inventory.
- When placed, the block projects a **translucent spherical energy bubble** (default radius 6 blocks) centered on the block.
- The bubble is rendered with a **custom GLSL shader** featuring:
  - **Fresnel rim glow** (bright edges, near-transparent center — classic force-field look)
  - **Animated hexagonal energy pattern** (procedural, no texture) with per-cell pulsing
  - **Scrolling scanlines / vertical energy waves**
  - **Global pulse** (slow breathing of intensity)
  - Configurable **color** (default cyan `#33CCFF`)
- **Ambient particles**: glowing sparks drift along the bubble surface.
- **Sound**: beacon-style activation hum on placement, ambient hum while active, deactivate sound on break (all vanilla sound events — no custom audio assets).
- **Optional gameplay** (stretch, M6): projectiles crossing the shell from outside are deflected, with a particle burst at the impact point.
- **Config**: JSON file (`config/schildblase.json`) controlling default radius, color, particles, projectile deflection.

### Non-goals (out of scope)

- No multiblock structures, no energy/fuel system, no shield durability/HP.
- No server-side world protection (explosions etc.) — visual + projectile deflection only.
- No support for MC versions other than 1.20.1; no Forge/NeoForge port.

### Identifiers (use these everywhere, exactly)

| Thing | Value |
|---|---|
| Mod ID | `schildblase` |
| Mod name | `Schildblase` |
| Maven group / root package | `de.schildblase` |
| Archive base name | `schildblase` |
| Git repo root | `/workspace` |

---

## 2. Tech stack & toolchain (exact versions)

All versions below were verified against live Maven metadata / the Fabric meta API on 2026-07-01.

| Component | Version | Where it comes from |
|---|---|---|
| Minecraft | `1.20.1` | Mojang (fetched by Loom) |
| Java (compile target) | **17** (`options.release = 17`) | see note below |
| Gradle (wrapper) | `8.7` | `services.gradle.org` |
| Fabric Loom (Gradle plugin) | `1.6.12` | `https://maven.fabricmc.net` (plugin repo) |
| Yarn mappings | `1.20.1+build.10` (`:v2` classifier) | latest build for 1.20.1 per `meta.fabricmc.net` |
| Fabric Loader | `0.16.14` | version-independent; any newer stable (e.g. 0.19.3) also works. Must be **≥ 0.15.11** (Veil requirement) |
| Fabric API | `0.92.9+1.20.1` | latest 1.20.1 build on `maven.fabricmc.net` |
| Veil (rendering lib) | `foundry.veil:Veil-fabric-1.20.1:1.0.0.296` | `https://maven.blamejared.com` (latest 1.20.1 build; fallback pin: `1.0.0.285`, the version the official Veil example mod uses) |

**Why Loom 1.6.12 + Gradle 8.7:** this exact pair is what the official Veil example mod (`FoundryMC/veil-example-mod`, branch `1.20`) builds with (`fabric-loom 1.6-SNAPSHOT`, Gradle 8.7). We pin the fixed release `1.6.12` instead of the snapshot for reproducibility.

**Java note (important for this VM):** the cloud VM has **only JDK 21** installed (`/usr/lib/jvm/java-21-openjdk-amd64`). This is fine:
- Gradle 8.7 runs on Java 21.
- We compile with `options.release = 17`, producing Java-17 bytecode.
- Minecraft 1.20.1 requires Java ≥ 17 and runs correctly on 21.
- If (and only if) a toolchain error appears, install JDK 17: `sudo apt-get install -y openjdk-17-jdk` and set `org.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64` in `gradle.properties`.

**How to re-verify versions at execution time** (all of these must succeed from the VM):

```bash
# Yarn + Loader for 1.20.1:
curl -s "https://meta.fabricmc.net/v2/versions/yarn/1.20.1" | head -c 300
curl -s "https://meta.fabricmc.net/v2/versions/loader/1.20.1" | head -c 300
# Fabric API builds for 1.20.1:
curl -s "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml" | grep -o '<version>0.92.[0-9]*+1.20.1</version>' | tail -3
# Loom releases:
curl -s "https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml" | grep -o '<version>1.6.[0-9]*</version>' | tail -3
# Veil 1.20.1 fabric builds:
curl -s "https://maven.blamejared.com/foundry/veil/Veil-fabric-1.20.1/maven-metadata.xml" | grep -o '<version>[^<]*</version>' | tail -3
```

---

## 3. Library choice: Veil (over Lodestone)

### Decision: **Veil** (`foundry.veil:Veil-fabric-1.20.1:1.0.0.296`, repo `https://maven.blamejared.com`)

| Criterion | Veil | Lodestone |
|---|---|---|
| Purpose | Purpose-built **advanced rendering** library: JSON-defined shader programs, framebuffers, post-processing pipeline, shader includes, shader hot-reload, in-game ImGui shader editor (dev) | General-purpose backend lib for the Lodestar team's mods (Malum, Embers); rendering helpers geared to **their** particle/trail/screen VFX |
| Custom shader story | First-class: `assets/<modid>/pinwheel/shaders/program/*.json/.vsh/.fsh`, bridged into vanilla `RenderLayer`s via `VeilRenderBridge.shaderState(id)` | No general "load my GLSL program and use it in a RenderLayer" API; shaders are mostly internal |
| Fabric 1.20.1 support | Dedicated artifact `Veil-fabric-1.20.1` (builds up to `1.0.0.296`, Dec 2024); requires only Fabric API + loader ≥ 0.15.11 (verified from its `fabric.mod.json`) | Fabric port exists (`team.lodestar.lodestone:lodestone:1.20.1-1.6.2.3-fabric`, latest fabric build `1.20.1-1.6.2.3g-fabric`) but the actively-maintained 1.6.4.x line is **Forge-only**; docs for Fabric are sparse |
| Reference material | **Official example mod** `FoundryMC/veil-example-mod` (branch `1.20`) contains exactly our use case: a block + block entity + BER using a custom Veil shader program | Setup guides exist, but no official example with custom GLSL program in a BER |

Veil wins decisively for "custom shaders on a block entity renderer".

### How Veil is used here

1. Dependency (Loom remaps the published intermediary jar to our Yarn dev names automatically):
   ```gradle
   modImplementation "foundry.veil:Veil-fabric-1.20.1:${project.veil_version}"
   ```
2. Shader program declared as JSON + GLSL under `assets/schildblase/pinwheel/shaders/program/shield.{json,vsh,fsh}`.
3. Wired into a custom `RenderLayer` via `VeilRenderBridge.shaderState(new Identifier("schildblase", "shield"))`, which returns a `RenderPhase.ShaderProgram` (Yarn name; Mojmap name is `ShaderStateShard`) usable in `RenderLayer.of(...)`.
4. Dev perks: F3+T reloads shaders live; Veil's editor overlay helps debug (dev only).

**Mappings caveat:** Veil's docs/example use **Mojang mappings**; this project uses **Yarn** (as required). Loom remaps the Veil jar, so in our dev environment Veil API signatures show Yarn names. Translation cheat sheet for reading Veil examples:

| Mojmap (Veil docs) | Yarn (this project) |
|---|---|
| `ResourceLocation` | `Identifier` |
| `RenderType` / `RenderType.create` | `RenderLayer` / `RenderLayer.of` |
| `RenderStateShard.ShaderStateShard` | `RenderPhase.ShaderProgram` |
| `RenderType.CompositeState` | `RenderLayer.MultiPhaseParameters` |
| `PoseStack` | `MatrixStack` |
| `MultiBufferSource` | `VertexConsumerProvider` |
| `DefaultVertexFormat.NEW_ENTITY` | `VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL` |
| `VertexFormat.Mode.TRIANGLES` | `VertexFormat.DrawMode.TRIANGLES` |
| `Minecraft` | `MinecraftClient` |
| `Level` | `World` |
| `ShaderInstance` | `net.minecraft.client.gl.ShaderProgram` |

### Fallback plan (execution must never block on Veil)

The shader wiring is isolated behind one small abstraction (`ShieldRenderLayers`, see §5/M4). Two backends:

- **Backend A (primary): Veil** — as above.
- **Backend B (fallback): vanilla core shaders via Fabric API** — zero extra dependencies:
  1. Put the same GLSL in `assets/schildblase/shaders/core/shield_bubble.{json,vsh,fsh}` (vanilla core-shader format).
  2. Register in the client entrypoint:
     ```java
     CoreShaderRegistrationCallback.EVENT.register(ctx -> ctx.register(
         new Identifier("schildblase", "shield_bubble"),
         VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
         program -> ShieldRenderLayers.shieldProgram = program));
     ```
     (`net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback`, present in Fabric API 0.92.x.)
  3. Use `new RenderPhase.ShaderProgram(() -> ShieldRenderLayers.shieldProgram)` in the `RenderLayer` instead of the Veil bridge.

  Switch trigger: if the Veil dependency fails to resolve, crashes at boot (mixin errors), or its shader fails to compile and can't be fixed within one milestone attempt — flip the flag `ShieldRenderLayers.USE_VEIL = false` (see M4), which selects Backend B. Backend B is fully specified in this plan (M3 actually builds it first), so the dome effect ships either way. If Veil `1.0.0.296` specifically misbehaves, first try `1.0.0.285` (the build pinned by the official example mod) before falling back.

---

## 4. Project structure

Final repository layout (files marked ⚙ are generated by Gradle, do not hand-write):

```
/workspace
├── README.md                          (exists; extend in M7)
├── PLAN.md                            (this file)
├── .gitignore
├── gradle.properties
├── settings.gradle
├── build.gradle
├── gradlew            ⚙
├── gradlew.bat        ⚙
├── gradle/wrapper/gradle-wrapper.jar         ⚙
├── gradle/wrapper/gradle-wrapper.properties  ⚙
└── src/main/
    ├── java/de/schildblase/
    │   ├── Schildblase.java                     (main entrypoint: registries, config load)
    │   ├── SchildblaseConfig.java               (JSON config: radius, color, toggles)
    │   ├── block/
    │   │   ├── SchildblaseBlock.java            (BlockWithEntity)
    │   │   └── SchildblaseBlockEntity.java      (radius/color state, ticker)
    │   ├── registry/
    │   │   ├── ModBlocks.java
    │   │   ├── ModItems.java
    │   │   ├── ModBlockEntities.java
    │   │   ├── ModParticles.java
    │   │   └── ModItemGroups.java
    │   └── client/
    │       ├── SchildblaseClient.java           (client entrypoint: BER + particle factory + shader registration)
    │       ├── render/
    │       │   ├── ShieldRenderLayers.java      (RenderLayer + Veil/vanilla shader backends)
    │       │   ├── SphereMesh.java              (UV-sphere tessellation)
    │       │   └── SchildblaseBlockEntityRenderer.java
    │       └── particle/
    │           └── ShieldSparkParticle.java
    └── resources/
        ├── fabric.mod.json
        └── assets/schildblase/
            ├── icon.png
            ├── lang/en_us.json
            ├── lang/de_de.json
            ├── blockstates/schildblase_block.json
            ├── models/block/schildblase_block.json
            ├── models/item/schildblase_block.json
            ├── textures/block/schildblase_block.png
            ├── textures/particle/shield_spark.png
            ├── particles/shield_spark.json
            ├── pinwheel/shaders/program/shield.json     (Veil backend)
            ├── pinwheel/shaders/program/shield.vsh
            ├── pinwheel/shaders/program/shield.fsh
            ├── shaders/core/shield_bubble.json          (vanilla fallback backend)
            ├── shaders/core/shield_bubble.vsh
            └── shaders/core/shield_bubble.fsh
```

**No mixins are needed** (neither backend requires one). Do not create a mixins JSON; do not reference one in `fabric.mod.json`.

### Exact contents of build files

`gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx2G
org.gradle.parallel=true

minecraft_version=1.20.1
yarn_mappings=1.20.1+build.10
loader_version=0.16.14
fabric_version=0.92.9+1.20.1
veil_version=1.0.0.296

mod_version=0.1.0
maven_group=de.schildblase
archives_base_name=schildblase
```

`settings.gradle`:
```gradle
pluginManagement {
    repositories {
        maven { name = 'Fabric'; url = 'https://maven.fabricmc.net/' }
        mavenCentral()
        gradlePluginPortal()
    }
}
```

`build.gradle`:
```gradle
plugins {
    id 'fabric-loom' version '1.6.12'
}

version = project.mod_version
group = project.maven_group

base { archivesName = project.archives_base_name }

repositories {
    maven { name = 'BlameJared'; url = 'https://maven.blamejared.com' }
}

dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    mappings "net.fabricmc:yarn:${project.yarn_mappings}:v2"
    modImplementation "net.fabricmc:fabric-loader:${project.loader_version}"
    modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"
    modImplementation("foundry.veil:Veil-fabric-${project.minecraft_version}:${project.veil_version}") {
        exclude group: "maven.modrinth"
    }
}

processResources {
    inputs.property "version", project.version
    filesMatching("fabric.mod.json") { expand "version": project.version }
}

tasks.withType(JavaCompile).configureEach { it.options.release = 17 }

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
```

`fabric.mod.json`:
```json
{
  "schemaVersion": 1,
  "id": "schildblase",
  "version": "${version}",
  "name": "Schildblase",
  "description": "Platziere Schildblasen-Projektoren, die dramatische Energie-Schutzkuppeln in die Welt projizieren.",
  "authors": ["Schildblase Team"],
  "license": "MIT",
  "icon": "assets/schildblase/icon.png",
  "environment": "*",
  "entrypoints": {
    "main": ["de.schildblase.Schildblase"],
    "client": ["de.schildblase.client.SchildblaseClient"]
  },
  "depends": {
    "fabricloader": ">=0.15.11",
    "fabric-api": "*",
    "minecraft": "~1.20.1",
    "java": ">=17",
    "veil": "*"
  }
}
```
(In M4, if the Veil fallback is triggered, remove the `"veil": "*"` depends entry and the gradle dependency.)

`.gitignore`:
```
.gradle/
build/
run/
out/
.idea/
*.iml
.vscode/
bin/
```

---

## 5. Detailed component design

### 5.1 Registration (main entrypoint)

`Schildblase.java` implements `net.fabricmc.api.ModInitializer`. `onInitialize()` calls, in order: `SchildblaseConfig.load()`, `ModBlocks.register()`, `ModBlockEntities.register()`, `ModItems.register()`, `ModItemGroups.register()`, `ModParticles.register()`.

- `ModBlocks`: `SCHILDBLASE_BLOCK = Registry.register(Registries.BLOCK, new Identifier("schildblase","schildblase_block"), new SchildblaseBlock(FabricBlockSettings.create().strength(3.0f, 1200.0f).luminance(state -> 15).nonOpaque().requiresTool()));`
  (1.20.1 has no `Material`; `FabricBlockSettings.create()` from `net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings`.)
- `ModItems`: `BlockItem` registered as `schildblase:schildblase_block` in `Registries.ITEM`.
- `ModBlockEntities`: `SCHILDBLASE = Registry.register(Registries.BLOCK_ENTITY_TYPE, new Identifier("schildblase","schildblase"), FabricBlockEntityTypeBuilder.create(SchildblaseBlockEntity::new, ModBlocks.SCHILDBLASE_BLOCK).build());`
- `ModItemGroups`: own creative tab: `Registry.register(Registries.ITEM_GROUP, new Identifier("schildblase","main"), FabricItemGroup.builder().displayName(Text.translatable("itemGroup.schildblase.main")).icon(() -> new ItemStack(ModItems.SCHILDBLASE_BLOCK_ITEM)).entries((ctx, e) -> e.add(ModItems.SCHILDBLASE_BLOCK_ITEM)).build());`
- `ModParticles`: `SHIELD_SPARK = Registry.register(Registries.PARTICLE_TYPE, new Identifier("schildblase","shield_spark"), FabricParticleTypes.simple());` (type `DefaultParticleType`).

### 5.2 Block & block entity

`SchildblaseBlock extends BlockWithEntity`:
- `createBlockEntity` → new `SchildblaseBlockEntity`.
- `getRenderType(state)` → `BlockRenderType.MODEL` (BlockWithEntity defaults to INVISIBLE — must override or the projector cube won't render).
- `getTicker(world, state, type)` → `BlockWithEntity.checkType(type, ModBlockEntities.SCHILDBLASE, SchildblaseBlockEntity::tick)` (server and client; the ticker branches on `world.isClient`).
- `onPlaced` → `world.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 1.0f, 1.0f)`.
- `onStateReplaced` (when block removed) → play `SoundEvents.BLOCK_BEACON_DEACTIVATE`.

`SchildblaseBlockEntity extends BlockEntity`:
- Fields: `float radius` (default `SchildblaseConfig.get().radius`, clamp 2..16), `int color` (0xRRGGBB, default config color), `int ageTicks`.
- `readNbt`/`writeNbt` persist `Radius` (float) and `Color` (int). Override `toInitialChunkDataNbt()` returning `createNbt()` and `toUpdatePacket()` returning `BlockEntityUpdateS2CPacket.create(this)` so clients receive radius/color.
- Static `tick(World world, BlockPos pos, BlockState state, SchildblaseBlockEntity be)`:
  - `be.ageTicks++`.
  - Client side: if particles enabled, every 2 ticks spawn 1–3 `ModParticles.SHIELD_SPARK` at random points **on** the sphere surface: pick random unit vector `v`, position = center + `v * radius`, velocity = small tangential drift (`0.02`). Use `world.addParticle`.
  - Server side: every 80 ticks play `SoundEvents.BLOCK_BEACON_AMBIENT` at pos, volume 0.6. If deflection enabled (M6): scan `world.getOtherEntities(null, Box.of(center, 2r, 2r, 2r), e -> e instanceof ProjectileEntity)`; for each projectile whose distance `d` to center satisfies `radius - 1.0 < d < radius + 0.5` **and** whose velocity points inward (`velocity.dotProduct(center.subtract(entityPos)) > 0`), reflect: `v' = v - 2(v·n)n` with `n = normalize(entityPos - center)`, then `setVelocity(v' * 0.8)`, `velocityModified = true` (Yarn field `velocityDirty` setter: set `entity.velocityModified = true` — in Yarn 1.20.1 the field is `velocityModified` on `Entity`), and spawn 20 `SHIELD_SPARK` particles at the impact point via `ServerWorld.spawnParticles`.
- `markRemoved`: nothing special (sound handled in block).

### 5.3 Sphere mesh (`SphereMesh.java`)

Pure static helper that emits a UV sphere into a `VertexConsumer`:

- Signature: `static void render(MatrixStack.Entry entry, VertexConsumer vc, float radius, int stacks, int sectors, float r, float g, float b, float a, int light)`.
- Defaults used by the BER: `stacks = 32`, `sectors = 48`.
- For stack `i in [0, stacks]`, latitude `phi = PI * i / stacks`; for sector `j in [0, sectors]`, longitude `theta = 2PI * j / sectors`. Local position `p = (sin phi * cos theta, cos phi, sin phi * sin theta) * radius`; **normal = normalize(p)**; UV = `(j / sectors, i / stacks)` — the UV drives the procedural hex pattern in the shader.
- Emit two triangles per quad cell, draw mode `TRIANGLES` (avoids QUADS winding headaches with two-sided rendering). Vertex layout per vertex (must match `POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL`):
  `vc.vertex(entry.getPositionMatrix(), x, y, z).color(r,g,b,a).texture(u,v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry.getNormalMatrix(), nx, ny, nz).next();`
- Total vertices: 32×48×6 ≈ 9216 per bubble per frame — fine for immediate mode.

### 5.4 Block entity renderer (`SchildblaseBlockEntityRenderer`)

Implements `BlockEntityRenderer<SchildblaseBlockEntity>`; registered in the client entrypoint via `BlockEntityRendererFactories.register(ModBlockEntities.SCHILDBLASE, SchildblaseBlockEntityRenderer::new)`.

- `render(be, tickDelta, matrices, vertexConsumers, light, overlay)`:
  1. `matrices.push(); matrices.translate(0.5, 0.5, 0.5);` (center bubble on block center).
  2. Extract color: `r,g,b` from `be.getColor()`; alpha `1.0` (transparency lives in the shader).
  3. `VertexConsumer vc = vertexConsumers.getBuffer(ShieldRenderLayers.shieldBubble());`
  4. `SphereMesh.render(matrices.peek(), vc, be.getRadius(), 32, 48, r, g, b, 1.0f, LightmapTextureManager.MAX_LIGHT_COORDINATE);`
  5. `matrices.pop();`
- Override `getRenderDistance()` → `128`.
- **Culling caveat (known vanilla behavior):** vanilla renders BEs from visible chunk sections; a 6-block dome may pop out when the projector block leaves the frustum while the dome edge is still visible. Acceptable for v0.1. Mitigation (only if it looks bad during M5 verification): move dome drawing to a `WorldRenderEvents.AFTER_TRANSLUCENT` handler (Fabric API `net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents`) that iterates a client-side registry of active shield BEs and draws with the same mesh/layer; keep the BER for dev fallback.

### 5.5 Custom shader pipeline (`ShieldRenderLayers` + GLSL)

`ShieldRenderLayers` (client-only class):

```java
public final class ShieldRenderLayers {
    /** Flip to false to use the vanilla core-shader fallback instead of Veil. */
    public static final boolean USE_VEIL = true;
    /** Populated by CoreShaderRegistrationCallback (fallback backend). */
    public static net.minecraft.client.gl.ShaderProgram shieldProgram;

    private static RenderLayer SHIELD;

    public static RenderLayer shieldBubble() {
        if (SHIELD == null) {
            RenderPhase.ShaderProgram shader = USE_VEIL
                ? VeilRenderBridge.shaderState(new Identifier("schildblase", "shield"))
                : new RenderPhase.ShaderProgram(() -> shieldProgram);
            SHIELD = RenderLayer.of(
                "schildblase:shield_bubble",
                VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
                VertexFormat.DrawMode.TRIANGLES,
                4096, false, true,
                RenderLayer.MultiPhaseParameters.builder()
                    .program(shader)
                    .transparency(RenderPhase.LIGHTNING_TRANSPARENCY)   // additive: dramatic glow
                    .cull(RenderPhase.DISABLE_CULLING)                  // see bubble from inside too
                    .writeMaskState(RenderPhase.COLOR_MASK)             // no depth write: no sorting artifacts
                    .layering(RenderPhase.POLYGON_OFFSET_LAYERING)
                    .build(false));
        }
        return SHIELD;
    }
}
```

Notes for implementers:
- `RenderLayer.of(...)` is the Yarn name of Mojmap `RenderType.create(...)`; `MultiPhaseParameters.builder()` methods in Yarn 1.20.1: `.program(...)`, `.transparency(...)`, `.cull(...)`, `.writeMaskState(...)`, `.layering(...)`.
- `LIGHTNING_TRANSPARENCY` = additive blending (`SRC_ALPHA, ONE`) — brightest, most "energy field". If too blown out over bright skies, switch to `TRANSLUCENT_TRANSPARENCY`.
- `VeilRenderBridge` is `foundry.veil.api.client.render.VeilRenderBridge` (same package name under Yarn remap). Its `shaderState(Identifier)` returns the shader render phase; the Veil shader manager loads `assets/schildblase/pinwheel/shaders/program/shield.json`.
- The lazy init (`shieldBubble()` called first from the BER, i.e. after shader load) avoids class-load-order problems.

**Veil program JSON** — `assets/schildblase/pinwheel/shaders/program/shield.json`:
```json
{
  "vertex": "schildblase:shield",
  "fragment": "schildblase:shield"
}
```

**Vertex shader** (identical logic for both backends; Veil copy at `pinwheel/shaders/program/shield.vsh`, fallback copy at `shaders/core/shield_bubble.vsh` — fallback must **not** use `#include` and needs `#version 150` at top; Veil ignores/injects its own version header, keep `#version 150` only in the core-shader copy):

```glsl
// core-shader copy starts with: #version 150
layout(location = 0) in vec3 Position;   // core-shader copy: "in vec3 Position;" (no layout qualifiers)
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float GameTime;

out vec4 vertexColor;
out vec2 texCoord0;
out vec3 viewPos;
out vec3 viewNormal;

void main() {
    vec4 vp = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * vp;
    viewPos = vp.xyz;
    viewNormal = Normal;   // already view-space (baked via MatrixStack normal matrix)
    vertexColor = Color;
    texCoord0 = UV0;
}
```
(BER vertices are pre-transformed to camera-relative view space by the `MatrixStack`; the global `ModelViewMat` is effectively identity there, so `viewPos ≈ Position`. Keeping the multiply is still correct and matches vanilla conventions.)

**Fragment shader** — the "ultra dramatic" effect (`pinwheel/shaders/program/shield.fsh` and `shaders/core/shield_bubble.fsh`, core copy prefixed with `#version 150`):

```glsl
uniform float GameTime;        // vanilla-style uniform: (worldTime % 24000) / 24000
uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec2 texCoord0;
in vec3 viewPos;
in vec3 viewNormal;

out vec4 fragColor;

// --- hex grid helpers (procedural, no texture) ---
vec4 hexCoords(vec2 uv) {
    vec2 r = vec2(1.0, 1.7320508);
    vec2 h = r * 0.5;
    vec2 a = mod(uv, r) - h;
    vec2 b = mod(uv - h, r) - h;
    vec2 gv = dot(a, a) < dot(b, b) ? a : b;
    // x = distance to hex edge, yz = cell id
    float edist = 0.5 - max(abs(gv.x) * 0.8660254 + abs(gv.y) * 0.5, abs(gv.y));
    vec2 id = uv - gv;
    return vec4(edist, id, 0.0);
}
float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

void main() {
    float time = GameTime * 24000.0;                  // ticks; wraps daily, all uses are periodic

    // 1) Fresnel rim glow: transparent center, blazing edges
    vec3 nrm = normalize(viewNormal);
    vec3 vdir = normalize(-viewPos);
    float fresnel = pow(1.0 - abs(dot(nrm, vdir)), 2.5);

    // 2) Animated hex energy pattern on spherical UV
    vec2 huv = vec2(texCoord0.x * 48.0, texCoord0.y * 24.0);  // match sector/stack density
    vec4 hex = hexCoords(huv);
    float edge = smoothstep(0.06, 0.0, hex.x);                 // thin hex borders
    float cellPulse = 0.5 + 0.5 * sin(time * 0.35 + hash21(hex.yz) * 6.2831);
    float cells = edge * (0.35 + 0.65 * cellPulse);

    // 3) Rising scanline band
    float scan = smoothstep(0.12, 0.0, abs(fract(texCoord0.y * 3.0 - time * 0.02) - 0.5) - 0.35);

    // 4) Global breathing pulse
    float pulse = 0.85 + 0.15 * sin(time * 0.1);

    float intensity = (0.15 + 1.6 * fresnel + 0.9 * cells + 0.5 * scan) * pulse;
    vec3 base = vertexColor.rgb;
    vec3 col = base * intensity + vec3(1.0) * fresnel * fresnel * 0.6;  // white-hot rim core

    float alpha = clamp(0.05 + 0.85 * fresnel + 0.55 * cells + 0.25 * scan, 0.0, 1.0) * vertexColor.a;
    fragColor = vec4(col, alpha) * ColorModulator;
}
```

**Fallback core-shader JSON** — `assets/schildblase/shaders/core/shield_bubble.json` (vanilla `ShaderProgram` format for 1.20.1, entity vertex format):
```json
{
  "vertex": "schildblase:shield_bubble",
  "fragment": "schildblase:shield_bubble",
  "attributes": ["Position", "Color", "UV0", "UV1", "UV2", "Normal"],
  "samplers": [
    { "name": "Sampler0" },
    { "name": "Sampler1" },
    { "name": "Sampler2" }
  ],
  "uniforms": [
    { "name": "ModelViewMat", "type": "matrix4x4", "count": 16, "values": [1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1] },
    { "name": "ProjMat",      "type": "matrix4x4", "count": 16, "values": [1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1] },
    { "name": "ColorModulator", "type": "float", "count": 4, "values": [1,1,1,1] },
    { "name": "GameTime",     "type": "float", "count": 1, "values": [0.0] }
  ]
}
```
Vanilla sets `GameTime` and `ColorModulator` automatically for any core shader declaring them (`RenderSystem.setupShaderLights`/`ShaderProgram.initializeUniforms` bind standard uniforms by name). Veil does the same for its wrapped programs — if `GameTime` turns out unbound under Veil at runtime (visible as a static pattern), set it manually each frame: in the BER call `VeilRenderSystem.setShader(...)`-independent path: `foundry.veil.api.client.render.VeilRenderSystem.renderer().getShaderManager().getShader(new Identifier("schildblase","shield"))` and `.setFloat("GameTime", world.getTime() % 24000 / 24000f)` — do this only if needed.

### 5.6 Particles

`ShieldSparkParticle extends SpriteBillboardParticle` (client):
- Constructor sets: `scale(0.15f)`, `maxAge = 20 + random.nextInt(20)`, no gravity (`gravityStrength = 0`), full-bright (`getBrightness` override → `LightmapTextureManager.MAX_LIGHT_COORDINATE`), color tinted to config color.
- `getType()` → `ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT`.
- Fade out: `getSize`/alpha scales with `1 - age/maxAge`.
- `Factory implements ParticleFactory<DefaultParticleType>` storing the `SpriteProvider`; registered client-side: `ParticleFactoryRegistry.getInstance().register(ModParticles.SHIELD_SPARK, ShieldSparkParticle.Factory::new);`
- Asset: `assets/schildblase/particles/shield_spark.json` → `{ "textures": ["schildblase:shield_spark"] }`; texture at `assets/schildblase/textures/particle/shield_spark.png`.

### 5.7 Config (`SchildblaseConfig`)

Minimal Gson-based JSON config, no library:
- Path: `FabricLoader.getInstance().getConfigDir().resolve("schildblase.json")`.
- Fields + defaults: `radius = 6.0`, `color = "#33CCFF"`, `ambientParticles = true`, `deflectProjectiles = true`.
- `load()`: read if exists, else write defaults. Parse color hex → int. Expose via static `get()`.
- Applied when a new `SchildblaseBlockEntity` is created (per-BE values persist in NBT afterwards).

### 5.8 Client entrypoint (`SchildblaseClient`)

`onInitializeClient()`:
1. `BlockEntityRendererFactories.register(ModBlockEntities.SCHILDBLASE, SchildblaseBlockEntityRenderer::new);`
2. `ParticleFactoryRegistry.getInstance().register(ModParticles.SHIELD_SPARK, ShieldSparkParticle.Factory::new);`
3. `BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.SCHILDBLASE_BLOCK, RenderLayer.getCutout());`
4. Fallback backend only: the `CoreShaderRegistrationCallback` registration from §3 (register it unconditionally — it is harmless when Veil is active and makes the `USE_VEIL` flag a one-line switch).

---

## 6. Assets (complete list with exact paths)

| # | Path (under `src/main/resources/`) | Content |
|---|---|---|
| 1 | `fabric.mod.json` | exact content in §4 |
| 2 | `assets/schildblase/icon.png` | any PNG ≥ 64x64; scale up texture #10 (`python3` + the script below, or copy it — Fabric accepts any size) |
| 3 | `assets/schildblase/lang/en_us.json` | `{"block.schildblase.schildblase_block":"Shield Bubble Projector","itemGroup.schildblase.main":"Schildblase"}` |
| 4 | `assets/schildblase/lang/de_de.json` | `{"block.schildblase.schildblase_block":"Schildblasen-Projektor","itemGroup.schildblase.main":"Schildblase"}` |
| 5 | `assets/schildblase/blockstates/schildblase_block.json` | `{"variants":{"":{"model":"schildblase:block/schildblase_block"}}}` |
| 6 | `assets/schildblase/models/block/schildblase_block.json` | `{"parent":"minecraft:block/cube_all","textures":{"all":"schildblase:block/schildblase_block"}}` |
| 7 | `assets/schildblase/models/item/schildblase_block.json` | `{"parent":"schildblase:block/schildblase_block"}` |
| 8 | `assets/schildblase/textures/block/schildblase_block.png` | 16x16 RGBA — base64 below |
| 9 | `assets/schildblase/textures/particle/shield_spark.png` | 8x8 RGBA — base64 below |
| 10 | `assets/schildblase/particles/shield_spark.json` | `{"textures":["schildblase:shield_spark"]}` |
| 11 | `assets/schildblase/pinwheel/shaders/program/shield.json` | §5.5 |
| 12 | `assets/schildblase/pinwheel/shaders/program/shield.vsh` | §5.5 vertex shader (no `#version` line) |
| 13 | `assets/schildblase/pinwheel/shaders/program/shield.fsh` | §5.5 fragment shader (no `#version` line) |
| 14 | `assets/schildblase/shaders/core/shield_bubble.json` | §5.5 fallback JSON |
| 15 | `assets/schildblase/shaders/core/shield_bubble.vsh` | §5.5 vertex shader with `#version 150` first line, plain `in` declarations (strip `layout(location = N)`) |
| 16 | `assets/schildblase/shaders/core/shield_bubble.fsh` | §5.5 fragment shader with `#version 150` first line |

**Pre-generated placeholder textures** (valid, tested PNGs — decode verbatim):

```bash
mkdir -p src/main/resources/assets/schildblase/textures/block src/main/resources/assets/schildblase/textures/particle
# 16x16 projector block texture (dark frame, glowing cyan core):
base64 -d > src/main/resources/assets/schildblase/textures/block/schildblase_block.png << 'EOF'
iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAUElEQVR42mMQklL7TwlmABEaNmFkYRQDZLRsSMJYDVCO6iEKE2VAwIn/KJgkA9A1oxuC1wCY4ku/UDGyIcPdAIoDkSrRSHZCoigpU5SZKMEAouj4BN6+ZE8AAAAASUVORK5CYII=
EOF
# 8x8 soft spark particle:
base64 -d > src/main/resources/assets/schildblase/textures/particle/shield_spark.png << 'EOF'
iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAVUlEQVR42mPY8uw/AwxvffafGYSRxeASO5//F9r78r8iCIPYMIVgBSCBI6//W5x//z8WhEFskBhYAUglSBdI4tm3/2tAGMQGiYHkCCsgaAVRjsTnTQDKh7GNYLAKgAAAAABJRU5ErkJggg==
EOF
file src/main/resources/assets/schildblase/textures/block/schildblase_block.png   # must say: PNG image data, 16 x 16
file src/main/resources/assets/schildblase/textures/particle/shield_spark.png     # must say: PNG image data, 8 x 8
```

> NOTE: the second base64 block above must decode to `PNG image data, 8 x 8`. If `base64 -d` fails or `file` reports a size other than 16x16 / 8x8, regenerate both PNGs with this stdlib-only script instead (no PIL needed): write any 16x16 and 8x8 RGBA PNG via Python `zlib`+`struct` (IHDR bit depth 8, color type 6, one `\x00` filter byte per row). Keep colors: dark blue frame + cyan core for the block; white→transparent radial falloff for the spark. Icon (#2): reuse the block PNG bytes as `icon.png`.

---

## 7. Build & run instructions

### One-time bootstrap (M0)

The repo has no Gradle wrapper yet. Bootstrap it deterministically:

```bash
cd /workspace
curl -fL -o /tmp/gradle-8.7-bin.zip https://services.gradle.org/distributions/gradle-8.7-bin.zip
unzip -q /tmp/gradle-8.7-bin.zip -d /tmp
# (write settings.gradle/build.gradle/gradle.properties FIRST — see §4 — then:)
/tmp/gradle-8.7/bin/gradle wrapper --gradle-version 8.7
./gradlew --version   # must print "Gradle 8.7"
```

### Build

```bash
cd /workspace
./gradlew build          # compiles, remaps, produces build/libs/schildblase-0.1.0.jar
```
First run downloads Minecraft, mappings, and dependencies (several minutes; needs network to `maven.fabricmc.net`, `libraries.minecraft.net`, `piston-data.mojang.com`, `maven.blamejared.com`).

### Run the client (headless VM specifics)

This is a **headless Linux cloud VM with a virtual X display already running at `DISPLAY=:1`** (Xvfb; `xvfb-run` also available). Mesa/llvmpipe provides software OpenGL (supports GL ≥ 3.2 required by MC, and GL 4.x features Veil may touch). Caveats:

- Rendering is **software-rasterized**: expect low FPS (5–20). That is fine for verification/screenshots.
- Run with the display env var set (it usually already is): `DISPLAY=:1 ./gradlew runClient`.
- The client opens a real window on the virtual display; interact with it via the computer-use/VNC desktop, or automate world entry with quick-play (below).
- First launch downloads assets (~300 MB) into `run/`; allow 5–15 min. Keep `run/` out of git (`.gitignore` covers it).
- If the JVM crashes with GL errors, verify `glxinfo -B | grep "core profile"` reports ≥ 3.2 (install `mesa-utils` if `glxinfo` is missing).

**Auto-join a world (recommended for verification):** 1.20.1 supports quick play. Add to `build.gradle`:
```gradle
loom {
    runs {
        client {
            programArgs "--quickPlaySingleplayer", "schildtest", "--width", "1280", "--height", "720"
        }
    }
}
```
The world `schildtest` must exist first (create once via GUI: Singleplayer → Create New World, name `schildtest`, Creative, Superflat). After that, `runClient` boots straight into it.

### Verifying the mod end-to-end

1. `DISPLAY=:1 ./gradlew runClient` — watch the log for `Loading 3 mods:` (or more) including `schildblase 0.1.0` and (Veil backend) `veil`.
2. In-world (creative): open inventory → search "Schild" → the **Schildblase** tab/item exists.
3. Place the block on flat ground. Expected within 1–2 seconds:
   - Beacon-activation sound.
   - A glowing cyan bubble (r=6) around the block: bright fresnel rim, animated hex cells, rising scanline, slow pulsing.
   - Sparks drifting on the bubble surface.
4. Walk inside the bubble — it must be visible from inside too (culling disabled).
5. Shoot an arrow at the bubble from outside (M6 built): arrow bounces off, spark burst at impact.
6. Break the block: bubble + particles disappear, deactivate sound plays.
7. Shader iteration: press **F3+T** to hot-reload shader edits (works for both Veil pinwheel programs and vanilla core shaders).
8. Screenshot for evidence: F2 in-game (saved to `run/screenshots/`) or grab the X display.

### Lint / test posture

There is no linter or unit-test suite in scope; the enforced quality gates are `./gradlew build` (includes `validateAccessWidener`, remap checks, resource processing — JSON syntax errors in models/blockstates fail at runtime, so runtime verification per milestone is mandatory) and the in-game verification steps above. `./gradlew check` runs whatever verification tasks exist; keep it green.

---

## 8. Milestone roadmap (ordered, independently verifiable)

Execute strictly in order. Each milestone ends with its **Verify** step passing.

- [ ] **M0 — Buildable skeleton mod.**
  Create `.gitignore`, `gradle.properties`, `settings.gradle`, `build.gradle` (§4 exact contents), bootstrap the Gradle 8.7 wrapper (§7), create `fabric.mod.json`, `Schildblase.java` + `SchildblaseClient.java` (empty log-line entrypoints), `icon.png`, lang files.
  *Verify:* `./gradlew build` succeeds; `DISPLAY=:1 ./gradlew runClient` reaches the title screen and the log shows `schildblase` and `veil` in the mod list. Create the `schildtest` superflat creative world now and add the quick-play args.

- [ ] **M1 — Block, item, creative tab, block entity, config.**
  Implement §5.1, §5.2 (without ticker particle/deflection logic — just NBT + sync), §5.7. Add assets #3–#8 (blockstate, models, block texture, lang).
  *Verify:* `runClient` → block appears in its creative tab, places with correct texture, plays activation sound, survives world reload (radius NBT persists — check with F3 or log line).

- [ ] **M2 — Dome geometry with a vanilla layer (no custom shader yet).**
  Implement `SphereMesh` (§5.3) and the BER (§5.4) but temporarily use `RenderLayer.getEntityTranslucentEmissive(new Identifier("textures/misc/white.png"))` — wait, that identifier does not exist in vanilla; instead use `RenderLayer.getEntityTranslucent(new Identifier("textures/block/white_stained_glass.png"))` if it resolves, otherwise `RenderLayer.getLightning()` (needs only position+color; for this milestone emit `vertex(...).color(...)` only, matching `POSITION_COLOR` format) — the goal is purely to see a translucent sphere in the right place at the right size. Register the BER in the client entrypoint.
  *Verify:* placing the block shows a visible translucent sphere of radius 6 centered on the block, visible from outside and inside.

- [ ] **M3 — Vanilla core-shader backend (fallback path) end-to-end.**
  Build Backend B first — it derisks everything: create `ShieldRenderLayers` with `USE_VEIL = false`, assets #14–#16, the `CoreShaderRegistrationCallback` registration, switch `SphereMesh` to the full entity vertex format (§5.3), and swap the BER to `ShieldRenderLayers.shieldBubble()`.
  *Verify:* bubble renders with fresnel rim + animated hexes + scanlines + pulse (motion proves `GameTime` binds). F3+T reload works. Screenshot saved.

- [ ] **M4 — Veil backend.**
  Add assets #11–#13, flip `USE_VEIL = true`.
  *Verify:* identical (or better) visuals through the Veil pipeline; log shows Veil loading the `schildblase:shield` program without errors. If Veil fails irrecoverably (dependency resolution, mixin crash, shader compile), first retry with `veil_version=1.0.0.285`; if still broken, revert flag to `false`, remove the gradle dep + `"veil"` from `fabric.mod.json` depends, document in README — the mod still fully works via M3.

- [ ] **M5 — Particles, ambient sound, polish.**
  Implement §5.6 (particle + factory + assets #9–#10) and the BE ticker's client particle emission + server ambient hum (§5.2). Tune shader constants if the bubble is too bright/dim over a superflat sky.
  *Verify:* sparks visibly drift on the bubble surface; ambient hum audible every ~4s (check log or listen via recording); no client log spam/errors.

- [ ] **M6 — (Stretch) Projectile deflection.**
  Server-side deflection logic in the ticker (§5.2) + impact particle burst.
  *Verify:* in-game, shoot a bow from outside the bubble: the arrow visibly bounces back and sparks burst at the impact point.

- [ ] **M7 — Final verification + docs.**
  Full clean run: `./gradlew clean build`, then `runClient`; walk through §7 verification list; capture screenshots/video of the bubble; update `README.md` (features, screenshots, build/run instructions, config documentation, Veil credit + license note: Veil is LGPL-3.0 — we only link against it as a dependency, no code copied).
  *Verify:* all §7 steps pass; `build/libs/schildblase-0.1.0.jar` exists; README accurate.

---

## 9. Risks & unknowns (and how to resolve at execution time)

| # | Risk | Likelihood | Mitigation |
|---|---|---|---|
| 1 | **Veil version incompatibility** (mixin crash at boot, API drift between 1.0.0.285 → .296) | Medium | Pinned `1.0.0.296`; first fallback pin `1.0.0.285` (proven by official example mod); ultimate fallback = Backend B (M3), already built before Veil is introduced. The `USE_VEIL` flag makes the switch one line. |
| 2 | **`VeilRenderBridge.shaderState` name/location differs in the 1.20.1 artifact** | Low-Med | The 1.20 example mod uses exactly `foundry.veil.api.client.render.VeilRenderBridge.shaderState(ResourceLocation)`. If missing, decompile check: `javap -classpath ~/.gradle/caches/... foundry.veil.api.client.render.VeilRenderBridge`. If truly absent, use Backend B. |
| 3 | **`GameTime` uniform not bound under Veil** (static pattern, no animation) | Medium | §5.5 gives the manual per-frame uniform set via Veil's `ShaderProgramm` API; or simply rely on Backend B where vanilla binds it guaranteed. |
| 4 | **Yarn vs Mojmap confusion when reading Veil docs** | High (but harmless) | Cheat-sheet table in §3. Loom remaps Veil's jar to Yarn automatically; IDE autocomplete shows correct Yarn signatures. |
| 5 | **Headless GL limits** (llvmpipe missing GL feature Veil wants, e.g. compute/DSA) | Low-Med | llvmpipe advertises GL 4.5. If Veil still fails GL checks at runtime: Backend B needs only GL 3.2 core. Verify with `glxinfo -B`. |
| 6 | **BER frustum pop-in for large domes** | Medium | Documented in §5.4 with the `WorldRenderEvents.AFTER_TRANSLUCENT` mitigation; only implement if M5 verification shows it matters at r=6. |
| 7 | **Vanilla core-shader JSON format details** (attribute order, sampler count for `POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL`) | Medium | The format mirrors vanilla `rendertype_entity_translucent_emissive.json`. On failure, MC logs the exact missing attribute/uniform name at resource load — copy vanilla's JSON from the loom-cache MC jar (`~/.gradle/caches/fabric-loom/1.20.1/.../assets/minecraft/shaders/core/`) and adapt. |
| 8 | **First-run downloads** (MC jar, assets ~300 MB) slow or blocked | Low | All hosts are public CDNs; retry with `--refresh-dependencies`. Allow long timeouts on first `build`/`runClient`. |
| 9 | **JDK 21-only VM** | Low | `options.release = 17` compiles fine; MC 1.20.1 runs on 21. If a tool insists on 17: `apt-get install openjdk-17-jdk`, set `org.gradle.java.home`. |
| 10 | **Sound spam / particle perf** | Low | Ambient hum every 80 ticks at volume 0.6; particles ≤ ~30/s per bubble. Tune in M5. |
| 11 | **Additive blending washes out in bright skies** | Medium (aesthetic) | One-line switch to `TRANSLUCENT_TRANSPARENCY` in `ShieldRenderLayers` (§5.5). Judge from M3 screenshots. |

---

## Appendix A — Quick command reference

```bash
cd /workspace
./gradlew build                         # full build → build/libs/schildblase-0.1.0.jar
DISPLAY=:1 ./gradlew runClient          # dev client on the VM's virtual display
./gradlew --stop                        # kill stuck Gradle daemons
./gradlew build --refresh-dependencies  # if dependency resolution flakes
# In-game: F3+T = reload shaders/resources, F2 = screenshot (run/screenshots/)
```

## Appendix B — Sources for pinned versions (checked 2026-07-01)

- Yarn `1.20.1+build.10`, Loader `0.16.14+` : https://meta.fabricmc.net/v2/versions/yarn/1.20.1 , https://meta.fabricmc.net/v2/versions/loader/1.20.1
- Fabric API `0.92.9+1.20.1` : https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml
- Loom `1.6.12` : https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml
- Veil `1.0.0.296` (fabric 1.20.1; requires loader ≥ 0.15.11, fabric-api, java ≥ 17 per its `fabric.mod.json`) : https://maven.blamejared.com/foundry/veil/Veil-fabric-1.20.1/maven-metadata.xml
- Veil example mod (1.20 branch: Loom 1.6 + Gradle 8.7 + Veil 1.0.0.285) : https://github.com/FoundryMC/veil-example-mod/tree/1.20
- Lodestone fabric 1.20.1 (rejected alternative) : https://maven.blamejared.com/team/lodestar/lodestone/lodestone/ (`1.20.1-1.6.2.3g-fabric` latest fabric build)

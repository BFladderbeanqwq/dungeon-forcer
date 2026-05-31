# Dungeon Forcer v2

Dungeon Forcer is a client-side Fabric mod for searching and visualizing controllable monster-room dungeon generation.

v2 adds loot forcing: it simulates the exact RNG advancement inside `MonsterRoomFeature.place`, enumerates block-layout edits, and searches for dungeon chest `LootTableSeed` values that generate target loot such as `minecraft:enchanted_golden_apple`.

## Core Idea

Minecraft monster rooms use one worldgen RNG instance during placement. The relevant vanilla order is:

1. Pick dungeon radii: `xr = nextInt(2) + 2`, `zr = nextInt(2) + 2`.
2. Place shell blocks. Every solid floor block at `dy == -1` consumes one `nextInt(4)`.
3. Try up to two chests. Each chest gets up to three attempts, and every attempt consumes:
   - `nextInt(xr * 2 + 1)`
   - `nextInt(zr * 2 + 1)`
4. When a chest is successfully placed, vanilla immediately calls `nextLong()` for its loot table seed.
5. Spawner mob type is selected after chest generation.

v2 manipulates the RNG offset by:

- breaking selected floor blocks before generation, which skips corresponding `nextInt(4)` calls;
- pre-placing selected blocker blocks near chest candidate locations, which changes whether chest attempts fail or succeed;
- evaluating the resulting chest loot seed against `chests/simple_dungeon`.

## Build

This project requires JDK 25.

```powershell
.\gradlew.bat build
```

The built mod jar is written to:

```text
build/libs/dungeonforcer-2.0.0.jar
```

## Mineshaft-Forcer Subproject

This repository also contains the standalone `mineshaft-forcer` Fabric client mod. It targets Minecraft `26.1.1`, Fabric Loader `0.19.2`, Fabric API `0.145.4+26.1.1`, Mojmap, and Java release `25`.

Build only Mineshaft-Forcer with:

```powershell
.\gradlew.bat :mineshaft-forcer:build
```

The built mod jar is written to:

```text
mineshaft-forcer/build/libs/mineshaft-forcer-0.1.0.jar
```

The module is intentionally separate from Dungeon Forcer. Its mod id is `mineshaftforcer`, and its main command is:

```text
/mineshaftforcer
```

### Mineshaft Quick Start

If you want the shortest path that actually works in-game, use this flow:

1. Build `mineshaft-forcer` and install `mineshaft-forcer/build/libs/mineshaft-forcer-0.1.0.jar` into your Fabric client `mods` folder.
2. Launch Minecraft `26.1.1` with Fabric Loader `0.19.2` and Fabric API `0.145.4+26.1.1`.
3. Open an integrated singleplayer world. `eval` and every `search` command require the integrated server; they do not work from a pure multiplayer client.
4. Run `/mineshaftforcer` to confirm the client command is loaded.
5. Run `/mineshaftforcer debug render_test` once. If you can see the three debug boxes, the preview renderer is working.
6. Travel into unexplored terrain so abandoned mineshaft corridors generate. Capture happens when vanilla `MineShaftCorridor.postProcess` runs, so the recommended workflow is to trigger fresh corridor generation, not to stand in an old area.
7. List captured corridors with `/mineshaftforcer capture list` and pick a capture id.
8. Search that capture and render the first matching blueprint with `/mineshaftforcer search corridor_preview_capture <captureId> <maxMutations> <itemId>`.
9. Apply the shown block changes in-world, or clear the preview with `/mineshaftforcer render clear`.

Concrete example:

```text
/mineshaftforcer capture list
/mineshaftforcer search corridor_preview_capture 3 2 minecraft:golden_apple
```

That is the main intended usage path. If you only want one command to remember, it is `capture list` followed by `search corridor_preview_capture`.

### Mineshaft Runtime Checks

Use these commands in an integrated singleplayer world:

```text
/mineshaftforcer debug render_test
/mineshaftforcer render status
/mineshaftforcer render clear
```

`debug render_test` queues three nearby Gizmo boxes so you can confirm the blueprint renderer is visible before searching.

### Mineshaft Corridor Capture

Mineshaft-Forcer injects into vanilla `MineShaftCorridor.postProcess` and records the corridor bounding box, orientation, section count, corridor flags, current `XoroshiroRandomSource` state, and a pre-generation snapshot of the corridor's local block matrix plus `OCEAN_FLOOR_WG` heights. Generate or approach new chunks in singleplayer, then run:

```text
/mineshaftforcer capture list
/mineshaftforcer capture latest
/mineshaftforcer capture clear
```

`capture list` prints entries like `#12 origin=(x, y, z) ... orientation=EAST sections=4 rails=false spider=false seedLo=... seedHi=...`. The `#12` part is the `captureId` you pass into `search corridor_preview_capture`.

### Mineshaft Loot Evaluation And Preview

Evaluate a raw abandoned-mineshaft loot seed through the vanilla loot table engine:

```text
/mineshaftforcer eval abandoned_mineshaft <lootSeed> <itemId>
```

Use this when you already have a loot seed and only want to check whether vanilla abandoned-mineshaft chest loot contains an item.

Search manually from known corridor parameters:

```text
/mineshaftforcer search corridor <sections> <seedLo> <seedHi> <maxMutations> <maxResults> <itemId>
```

This is an advanced manual search. It does not use captured world geometry; it searches a synthetic corridor model with the given section count.

Search and render a blueprint at a known corridor bounding-box origin:

```text
/mineshaftforcer search corridor_preview <originX> <originY> <originZ> <orientation> <sections> <seedLo> <seedHi> <maxMutations> <itemId>
```

`orientation` must be one of `NORTH`, `SOUTH`, `WEST`, or `EAST`.

Search and render from a captured corridor id:

```text
/mineshaftforcer search corridor_preview_capture <captureId> <maxMutations> <itemId>
```

If you are using the mod in a real world, this is the command you usually want.

The captured preview path uses that pre-generation snapshot, then searches floor-removal, support-roof, and cobweb-heightmap short-circuit mutations. Matching results are evaluated with vanilla `BuiltInLootTables.ABANDONED_MINESHAFT` and rendered as place/break/target blueprint boxes.

## Basic Setup

Install the generated jar as a Fabric client mod. Join the target world, stand in or near the chunk you want to search, then run commands from chat.

If the mod cannot read the world seed directly, set it manually:

```text
/dungeonforcer seed <worldSeed>
```

Check detected feature indexes:

```text
/dungeonforcer featureindex
```

## v2 Loot Commands

### Search For An Enchanted Golden Apple

```text
/dungeonforcer loot
```

This searches the current player chunk for `minecraft:enchanted_golden_apple` using default limits:

- `maxResults = 10`
- `maxFloorBreaks = 1`
- `maxWallOpenings = 5`

### Search For A Custom Item

```text
/dungeonforcer loot <targetItem>
```

Example:

```text
/dungeonforcer loot minecraft:golden_apple
```

### Search With Custom Limits

```text
/dungeonforcer loot <targetItem> <maxResults> <maxSupportBreaks> <maxWallOpenings>
```

Example:

```text
/dungeonforcer loot minecraft:enchanted_golden_apple 20 2 5
```

Parameter meanings:

- `targetItem`: item id to search for in the generated simple dungeon loot.
- `maxResults`: maximum number of blueprints to return.
- `maxSupportBreaks`: maximum number of support blocks at `dy == -2` to pre-break. The floor at `dy == -1` must stay solid for the vanilla pre-check.
- `maxWallOpenings`: maximum number of wall openings to enumerate. Vanilla requires `1..5` openings.

Higher `maxSupportBreaks` and `maxWallOpenings` explore a larger search space and can be much slower.

The loot search only returns controllable cross-chunk blueprints:

- the dungeon shell must cross the spawner chunk boundary;
- floor and ceiling blocks inside the spawner chunk must already be solid;
- support breaks and wall openings are only selected outside the spawner chunk.

### Show Next Loot Result

```text
/dungeonforcer lootnext
```

Cycles through the loot blueprints found by the last `/dungeonforcer loot` search.

## Reading Loot Output

A successful v2 result prints:

- dungeon origin and size;
- whether it is from `MONSTER_ROOM` or `MONSTER_ROOM_DEEP`;
- first and second chest loot seeds;
- which chest hit the target item;
- required shell coordinates;
- support break and wall opening coordinates;
- generated chest coordinates.

The renderer highlights:

- gray blocks: dungeon floor, ceiling, and wall-ring reference;
- blue blocks: required shell blocks to pre-place;
- orange blocks: support blocks or wall opening blocks to break;
- dungeon bounds and spawner position.

Apply the printed blueprint before regenerating the chunk or triggering dungeon placement.

## Legacy Spawner Search

The original spawner-combination search is still available:

```text
/dungeonforcer run <searchType>
/dungeonforcer run <searchType> <spawnerType>
/dungeonforcer next
/dungeonforcer reset
```

Available search types are suggested by command completion from `SearchType`.

Spawner types:

```text
SKELETON
ZOMBIE
SPIDER
```

## Good Chunk Finder

Search nearby chunks for promising dungeon attempts:

```text
/dungeonforcer goodchunkfinder run <radius>
/dungeonforcer goodchunkfinder reset
```

## Notes

- v2 loot simulation is specialized for `minecraft:chests/simple_dungeon`.
- Loot RNG uses vanilla `RandomSource.create(seed)`, which is the legacy Java-style RNG used by `LootContext.Builder.withOptionalRandomSeed`.
- The search is chunk-local: stand in the chunk you want to analyze before running commands.
- For large search limits, expect the client to pause while combinations are enumerated.

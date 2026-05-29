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
- `maxChestBlockers = 2`

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
/dungeonforcer loot <targetItem> <maxResults> <maxFloorBreaks> <maxChestBlockers>
```

Example:

```text
/dungeonforcer loot minecraft:enchanted_golden_apple 20 2 3
```

Parameter meanings:

- `targetItem`: item id to search for in the generated simple dungeon loot.
- `maxResults`: maximum number of blueprints to return.
- `maxFloorBreaks`: maximum number of dungeon floor blocks at `dy == -1` to pre-break.
- `maxChestBlockers`: maximum number of chest-level blocker blocks to pre-place.

Higher `maxFloorBreaks` and `maxChestBlockers` explore a larger search space and can be much slower.

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
- wall block coordinates;
- floor break coordinates;
- chest blocker coordinates;
- generated chest coordinates.

The renderer highlights:

- blue blocks: blocks to place;
- orange blocks: blocks to break;
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

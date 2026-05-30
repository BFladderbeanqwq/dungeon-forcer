package hackerrouter.dungeonforcer.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import hackerrouter.dungeonforcer.*;
import hackerrouter.dungeonforcer.render.RenderQueue;
import hackerrouter.dungeonforcer.util.Chat;
import hackerrouter.dungeonforcer.util.FeatureIndexHelper;
import hackerrouter.dungeonforcer.util.World;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;

import java.util.List;

public class ClientCommands {

    private static long worldSeed = 0;
    private static boolean seedOverride = false;
    private static List<SpawnerCombination> currentResults;
    private static int currentResultIndex = 0;
    private static List<DungeonLootBlueprint> currentLootResults;
    private static int currentLootResultIndex = 0;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
                literal("dungeonforcer")
                        .then(literal("run")
                                .then(argument("searchType", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (SearchType st : SearchType.values()) {
                                                builder.suggest(st.name());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> runSearch(ctx.getSource(),
                                                SearchType.valueOf(StringArgumentType.getString(ctx, "searchType")),
                                                null))
                                        .then(argument("spawnerType", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    for (SpawnerType st : SpawnerType.values()) {
                                                        builder.suggest(st.name());
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> runSearch(ctx.getSource(),
                                                        SearchType.valueOf(StringArgumentType.getString(ctx, "searchType")),
                                                        SpawnerType.valueOf(StringArgumentType.getString(ctx, "spawnerType")))))))
                        .then(literal("next")
                                .executes(ctx -> nextResult(ctx.getSource())))
                        .then(literal("reset")
                                .executes(ctx -> reset(ctx.getSource())))
                        .then(literal("seed")
                                .then(argument("seed", LongArgumentType.longArg())
                                        .executes(ctx -> setSeed(ctx.getSource(),
                                                LongArgumentType.getLong(ctx, "seed")))))
                        .then(literal("featureindex")
                                .executes(ctx -> showFeatureIndex(ctx.getSource())))
                        .then(literal("loot")
                                .executes(ctx -> runLootSearch(ctx.getSource(),
                                        "minecraft:enchanted_golden_apple", 10, 1, 5))
                                .then(argument("targetItem", StringArgumentType.word())
                                        .executes(ctx -> runLootSearch(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "targetItem"), 10, 1, 5))
                                        .then(argument("maxResults", IntegerArgumentType.integer(1, 50))
                                                .executes(ctx -> runLootSearch(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "targetItem"),
                                                        IntegerArgumentType.getInteger(ctx, "maxResults"), 1, 5))
                                                .then(argument("maxFloorBreaks", IntegerArgumentType.integer(0, 8))
                                                        .executes(ctx -> runLootSearch(ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "targetItem"),
                                                                IntegerArgumentType.getInteger(ctx, "maxResults"),
                                                                IntegerArgumentType.getInteger(ctx, "maxFloorBreaks"), 2))
                                                        .then(argument("maxWallOpenings", IntegerArgumentType.integer(1, 5))
                                                                .executes(ctx -> runLootSearch(ctx.getSource(),
                                                                        StringArgumentType.getString(ctx, "targetItem"),
                                                                        IntegerArgumentType.getInteger(ctx, "maxResults"),
                                                                        IntegerArgumentType.getInteger(ctx, "maxFloorBreaks"),
                                                                        IntegerArgumentType.getInteger(ctx, "maxWallOpenings"))))))))
                        .then(literal("lootnext")
                                .executes(ctx -> nextLootResult(ctx.getSource())))
                        .then(literal("goodchunkfinder")
                                .then(literal("run")
                                        .then(argument("radius", IntegerArgumentType.integer(1, 32))
                                                .executes(ctx -> runGoodChunkFinder(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "radius")))))
                                .then(literal("reset")
                                        .executes(ctx -> resetGoodChunkFinder(ctx.getSource()))))
        );
    }

    private static int runSearch(FabricClientCommandSource source,
                                 SearchType searchType, SpawnerType preferredType) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return 0;

        long seed = seedOverride ? worldSeed : World.getWorldSeed();

        ChunkPos chunkPos = ChunkPos.containing(client.player.blockPosition());

        int normalIndex = FeatureIndexHelper.getNormalFeatureIndex();
        int deepIndex = FeatureIndexHelper.getDeepFeatureIndex();

        Chat.send("§6[DungeonForcer] §fSearching chunk (" + chunkPos.x() + ", " + chunkPos.z() + ")...");
        Chat.send("§7featureIndex: normal=" + normalIndex + ", deep=" + deepIndex);

        DungeonFinder finder = new DungeonFinder();
        currentResults = finder.runForChunk(
                chunkPos.x(), chunkPos.z(), seed,
                normalIndex, deepIndex,
                searchType, preferredType,
                (x, y, z) -> World.getBlockState(x, y, z));

        currentResultIndex = 0;

        if (searchType.hasType && preferredType != null) {
            currentResults.removeIf(combo -> {
                for (int i = 0; i < combo.spawnerCount; i++) {
                    if (combo.spawners[i].type == preferredType) return false;
                }
                return true;
            });
        }

        if (currentResults.isEmpty()) {
            Chat.send("§c[DungeonForcer] §fNo Combinations Found.");
        } else {
            showCurrentResult();
        }

        return 1;
    }

    private static int nextResult(FabricClientCommandSource source) {
        if (currentResults == null || currentResults.isEmpty()) {
            Chat.send("§c[DungeonForcer] §fNo search result. Try /dungeonforcer run first.");
            return 0;
        }
        currentResultIndex = (currentResultIndex + 1) % currentResults.size();
        showCurrentResult();
        return 1;
    }

    private static void showCurrentResult() {
        SpawnerCombination combo = currentResults.get(currentResultIndex);
        Chat.send(String.format("§6[DungeonForcer] §fResult %d/%d (score: %d, spawners: %d)",
                currentResultIndex + 1, currentResults.size(),
                combo.points, combo.spawnerCount));

        for (int i = 0; i < combo.spawnerCount; i++) {
            Spawner s = combo.spawners[i];
            String color = getSpawnerColor(s.type);
            Chat.send(String.format("  %s%s §f@ (%d, %d, %d) size=%dx%d exits=%d floor=%d%s",
                    color, s.type.name(), s.x, s.y, s.z,
                    s.sizeX, s.sizeZ, s.exitsNeeded, s.floorBlocksNeeded,
                    s.isDeep ? " §8[DEEP]" : ""));
        }

        RenderQueue.clear();
        for (int i = 0; i < combo.spawnerCount; i++) {
            RenderQueue.addSpawnerHighlight(combo.spawners[i]);
        }
    }

    private static int runLootSearch(FabricClientCommandSource source, String targetItem, int maxResults,
                                     int maxFloorBreaks, int maxChestBlockers) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return 0;

        long seed = seedOverride ? worldSeed : World.getWorldSeed();
        ChunkPos chunkPos = ChunkPos.containing(client.player.blockPosition());
        int normalIndex = FeatureIndexHelper.getNormalFeatureIndex();
        int deepIndex = FeatureIndexHelper.getDeepFeatureIndex();

        Chat.send("搂6[DungeonForcer-v2] 搂fSearching loot seed in chunk (" + chunkPos.x() + ", " + chunkPos.z() + ")...");
        Chat.send(String.format("搂7target=%s maxResults=%d supportBreaks<=%d wallOpenings<=%d",
                targetItem, maxResults, maxFloorBreaks, maxChestBlockers));

        DungeonLootForcer forcer = new DungeonLootForcer();
        currentLootResults = forcer.runForChunk(
                chunkPos.x(), chunkPos.z(), seed,
                normalIndex, deepIndex,
                targetItem, maxResults, maxFloorBreaks, maxChestBlockers,
                (x, y, z) -> World.getBlockState(x, y, z));
        currentLootResultIndex = 0;

        if (currentLootResults.isEmpty()) {
            RenderQueue.clear();
            Chat.send("搂c[DungeonForcer-v2] 搂fNo loot blueprint found with current limits.");
        } else {
            showCurrentLootResult();
        }

        return 1;
    }

    private static int nextLootResult(FabricClientCommandSource source) {
        if (currentLootResults == null || currentLootResults.isEmpty()) {
            Chat.send("搂c[DungeonForcer-v2] 搂fNo loot result. Try /dungeonforcer loot first.");
            return 0;
        }
        currentLootResultIndex = (currentLootResultIndex + 1) % currentLootResults.size();
        showCurrentLootResult();
        return 1;
    }

    private static void showCurrentLootResult() {
        DungeonLootBlueprint b = currentLootResults.get(currentLootResultIndex);
        Chat.send(String.format("搂6[DungeonForcer-v2] 搂fLoot result %d/%d @ (%d, %d, %d) size=%dx%d hitChest=%d%s",
                currentLootResultIndex + 1, currentLootResults.size(),
                b.originX, b.originY, b.originZ, b.sizeX, b.sizeZ, b.hitChestIndex,
                b.isDeep ? " 搂8[DEEP]" : ""));
        Chat.send(String.format("  搂7lootSeed[1]=%d lootSeed[2]=%d spawner=%s",
                b.firstChestLootSeed, b.secondChestLootSeed, b.spawnerType));
        Chat.send(String.format("  搂7requiredPlace=%d breaks=%d chestPos=%d",
                b.requiredPlaceBlocks.size() + b.chestBlockers.size(),
                b.floorBreaks.size(), b.chestPositions.size()));
        sendPositions("required shell", b.requiredPlaceBlocks);
        sendPositions("break support/openings", b.floorBreaks);
        sendPositions("chests", b.chestPositions);

        RenderQueue.clear();
        RenderQueue.addSpawnerHighlight(b.toSpawnerHighlight());
    }

    private static void sendPositions(String label, List<int[]> positions) {
        if (positions.isEmpty()) {
            Chat.send("  搂8" + label + ": none");
            return;
        }
        int perLine = 6;
        for (int start = 0; start < positions.size(); start += perLine) {
            StringBuilder sb = new StringBuilder("  搂7").append(label);
            if (positions.size() > perLine) {
                sb.append(" ").append(start + 1).append("-").append(Math.min(start + perLine, positions.size()));
            }
            sb.append(": ");
            for (int i = start; i < Math.min(start + perLine, positions.size()); i++) {
                int[] p = positions.get(i);
                if (i > start) sb.append(" ");
                sb.append("(").append(p[0]).append(",").append(p[1]).append(",").append(p[2]).append(")");
            }
            Chat.send(sb.toString());
        }
    }

    private static int reset(FabricClientCommandSource source) {
        currentResults = null;
        currentResultIndex = 0;
        RenderQueue.clear();
        Chat.send("§6[DungeonForcer] §fSuccessfully reset.");
        return 1;
    }

    private static int setSeed(FabricClientCommandSource source, long seed) {
        worldSeed = seed;
        seedOverride = true;
        Chat.send("§6[DungeonForcer] §fSuccessfully set the seed to " + seed);
        return 1;
    }

    private static int showFeatureIndex(FabricClientCommandSource source) {
        try {
            int normalIndex = FeatureIndexHelper.getNormalFeatureIndex();
            int deepIndex = FeatureIndexHelper.getDeepFeatureIndex();
            Chat.send("§6[DungeonForcer] §ffeatureIndex:");
            Chat.send("  §7MONSTER_ROOM: " + normalIndex);
            Chat.send("  §7MONSTER_ROOM_DEEP: " + deepIndex);
        } catch (Exception e) {
            Chat.send("§c[DungeonForcer] §fUnable to obtain featureIndex: " + e.getMessage());
        }
        return 1;
    }

    private static int runGoodChunkFinder(FabricClientCommandSource source, int radius) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return 0;

        long seed = seedOverride ? worldSeed : World.getWorldSeed();
        ChunkPos chunkPos = ChunkPos.containing(client.player.blockPosition());
        int normalIndex = FeatureIndexHelper.getNormalFeatureIndex();
        int deepIndex = FeatureIndexHelper.getDeepFeatureIndex();

        Chat.send("§6[DungeonForcer] §fScanning " + ((2*radius+1)*(2*radius+1)) + " chunks...");

        List<GoodChunkFinder.ChunkResult> results = GoodChunkFinder.findGoodChunks(
                chunkPos.x(), chunkPos.z(), radius, seed, normalIndex, deepIndex, 20);

        if (results.isEmpty()) {
            Chat.send("§c[DungeonForcer] §fNo good chunk found yet.");
        } else {
            Chat.send("§6[DungeonForcer] §fFound " + results.size() + " good chunks:");
            for (int i = 0; i < results.size(); i++) {
                GoodChunkFinder.ChunkResult r = results.get(i);
                Chat.send(String.format("  §e%d. §f(%d, %d) rate=%.0f potential=%d",
                        i + 1, r.chunkX, r.chunkZ, r.score, r.potentialSpawners));
                RenderQueue.addChunkCross(r.chunkX, r.chunkZ);
            }
        }
        return 1;
    }

    private static int resetGoodChunkFinder(FabricClientCommandSource source) {
        RenderQueue.clearChunkCrosses();
        Chat.send("§6[DungeonForcer] §fCleared the good chunk mark.");
        return 1;
    }

    private static String getSpawnerColor(SpawnerType type) {
        switch (type) {
            case SKELETON: return "§b";
            case ZOMBIE: return "§a";
            case SPIDER: return "§c";
            default: return "§f";
        }
    }
}

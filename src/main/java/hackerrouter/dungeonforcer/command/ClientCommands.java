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
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;

import java.util.List;

public class ClientCommands {

    private static long worldSeed = 0;
    private static boolean seedOverride = false;
    private static List<SpawnerCombination> currentResults;
    private static int currentResultIndex = 0;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
                ClientCommandManager.literal("dungeonforcer")
                        .then(ClientCommandManager.literal("run")
                                .then(ClientCommandManager.argument("searchType", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (SearchType st : SearchType.values()) {
                                                builder.suggest(st.name());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> runSearch(ctx.getSource(),
                                                SearchType.valueOf(StringArgumentType.getString(ctx, "searchType")),
                                                null))
                                        .then(ClientCommandManager.argument("spawnerType", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    for (SpawnerType st : SpawnerType.values()) {
                                                        builder.suggest(st.name());
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> runSearch(ctx.getSource(),
                                                        SearchType.valueOf(StringArgumentType.getString(ctx, "searchType")),
                                                        SpawnerType.valueOf(StringArgumentType.getString(ctx, "spawnerType")))))))
                        .then(ClientCommandManager.literal("next")
                                .executes(ctx -> nextResult(ctx.getSource())))
                        .then(ClientCommandManager.literal("reset")
                                .executes(ctx -> reset(ctx.getSource())))
                        .then(ClientCommandManager.literal("seed")
                                .then(ClientCommandManager.argument("seed", LongArgumentType.longArg())
                                        .executes(ctx -> setSeed(ctx.getSource(),
                                                LongArgumentType.getLong(ctx, "seed")))))
                        .then(ClientCommandManager.literal("featureindex")
                                .executes(ctx -> showFeatureIndex(ctx.getSource())))
                        .then(ClientCommandManager.literal("goodchunkfinder")
                                .then(ClientCommandManager.literal("run")
                                        .then(ClientCommandManager.argument("radius", IntegerArgumentType.integer(1, 32))
                                                .executes(ctx -> runGoodChunkFinder(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "radius")))))
                                .then(ClientCommandManager.literal("reset")
                                        .executes(ctx -> resetGoodChunkFinder(ctx.getSource()))))
        );
    }

    private static int runSearch(FabricClientCommandSource source,
                                 SearchType searchType, SpawnerType preferredType) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return 0;

        long seed = seedOverride ? worldSeed : World.getWorldSeed();

        ChunkPos chunkPos = new ChunkPos(client.player.blockPosition());

        int normalIndex = FeatureIndexHelper.getNormalFeatureIndex();
        int deepIndex = FeatureIndexHelper.getDeepFeatureIndex();

        Chat.send("§6[DungeonForcer] §fSearching chunk (" + chunkPos.x + ", " + chunkPos.z + ")...");
        Chat.send("§7featureIndex: normal=" + normalIndex + ", deep=" + deepIndex);

        DungeonFinder finder = new DungeonFinder();
        currentResults = finder.runForChunk(
                chunkPos.x, chunkPos.z, seed,
                normalIndex, deepIndex,
                searchType, preferredType,
                (x, y, z) -> World.getBlockState(x, y, z));

        currentResultIndex = 0;

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
        Chat.send(String.format("§6[DungeonForcer] §f结果 %d/%d (评分: %d, 刷怪笼: %d)",
                currentResultIndex + 1, currentResults.size(),
                combo.points, combo.spawnerCount));

        for (int i = 0; i < combo.spawnerCount; i++) {
            Spawner s = combo.spawners[i];
            String color = getSpawnerColor(s.type);
            Chat.send(String.format("  %s%s §f@ (%d, %d, %d) size=%dx%d exits=%d %s",
                    color, s.type.name(), s.x, s.y, s.z,
                    s.sizeX, s.sizeZ, s.exitsNeeded,
                    s.isDeep ? "§8[DEEP]" : ""));
        }

        RenderQueue.clear();
        for (int i = 0; i < combo.spawnerCount; i++) {
            Spawner s = combo.spawners[i];
            RenderQueue.addSpawnerHighlight(s);
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
            Chat.send("  MONSTER_ROOM: " + normalIndex);
            Chat.send("  MONSTER_ROOM_DEEP: " + deepIndex);
        } catch (Exception e) {
            Chat.send("§c[DungeonForcer] §fUnable to obtain featureIndex: " + e.getMessage());
        }
        return 1;
    }

    private static int runGoodChunkFinder(FabricClientCommandSource source, int radius) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return 0;

        long seed = seedOverride ? worldSeed : World.getWorldSeed();
        ChunkPos chunkPos = new ChunkPos(client.player.blockPosition());
        int normalIndex = FeatureIndexHelper.getNormalFeatureIndex();
        int deepIndex = FeatureIndexHelper.getDeepFeatureIndex();

        Chat.send("§6[DungeonForcer] §fScanning " + ((2*radius+1)*(2*radius+1)) + " chunks...");

        List<GoodChunkFinder.ChunkResult> results = GoodChunkFinder.findGoodChunks(
                chunkPos.x, chunkPos.z, radius, seed, normalIndex, deepIndex, 20);

        if (results.isEmpty()) {
            Chat.send("§c[DungeonForcer] §fNo good chunk found yet.");
        } else {
            Chat.send("§6[DungeonForcer] §fFound " + results.size() + " good chunks:");
            for (int i = 0; i < Math.min(10, results.size()); i++) {
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
            case SKELETON: return "§b"; // 青色
            case ZOMBIE: return "§a";   // 绿色
            case SPIDER: return "§c";   // 红色
            default: return "§f";
        }
    }
}

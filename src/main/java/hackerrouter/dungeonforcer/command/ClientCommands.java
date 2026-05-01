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

        Chat.send("[DungeonForcer] Searching chunk (" + chunkPos.x() + ", " + chunkPos.z() + ")...");
        Chat.send("featureIndex: normal=" + normalIndex + ", deep=" + deepIndex);

        DungeonFinder finder = new DungeonFinder();
        currentResults = finder.runForChunk(
                chunkPos.x(), chunkPos.z(), seed,
                normalIndex, deepIndex,
                searchType, preferredType,
                (x, y, z) -> World.getBlockState(x, y, z));

        currentResultIndex = 0;

        if (currentResults.isEmpty()) {
            Chat.send("[DungeonForcer] No Combinations Found.");
        } else {
            showCurrentResult();
        }

        return 1;
    }

    private static int nextResult(FabricClientCommandSource source) {
        if (currentResults == null || currentResults.isEmpty()) {
            Chat.send("[DungeonForcer] No search result. Try /dungeonforcer run first.");
            return 0;
        }
        currentResultIndex = (currentResultIndex + 1) % currentResults.size();
        showCurrentResult();
        return 1;
    }

    private static void showCurrentResult() {
        SpawnerCombination combo = currentResults.get(currentResultIndex);
        Chat.send(String.format("[DungeonForcer] Result %d/%d (score: %d, spawners: %d)",
                currentResultIndex + 1, currentResults.size(),
                combo.points, combo.spawnerCount));

        for (int i = 0; i < combo.spawnerCount; i++) {
            Spawner s = combo.spawners[i];
            String color = getSpawnerColor(s.type);
            Chat.send(String.format("  %s%s @ (%d, %d, %d) size=%dx%d exits=%d floor=%d%s",
                    color, s.type.name(), s.x, s.y, s.z,
                    s.sizeX, s.sizeZ, s.exitsNeeded, s.floorBlocksNeeded,
                    s.isDeep ? " [DEEP]" : ""));
        }

        RenderQueue.clear();
        for (int i = 0; i < combo.spawnerCount; i++) {
            RenderQueue.addSpawnerHighlight(combo.spawners[i]);
        }
    }

    private static int reset(FabricClientCommandSource source) {
        currentResults = null;
        currentResultIndex = 0;
        RenderQueue.clear();
        Chat.send("[DungeonForcer] Successfully reset.");
        return 1;
    }

    private static int setSeed(FabricClientCommandSource source, long seed) {
        worldSeed = seed;
        seedOverride = true;
        Chat.send("[DungeonForcer] Successfully set the seed to " + seed);
        return 1;
    }

    private static int showFeatureIndex(FabricClientCommandSource source) {
        try {
            int normalIndex = FeatureIndexHelper.getNormalFeatureIndex();
            int deepIndex = FeatureIndexHelper.getDeepFeatureIndex();
            Chat.send("[DungeonForcer] featureIndex:");
            Chat.send("  MONSTER_ROOM: " + normalIndex);
            Chat.send("  MONSTER_ROOM_DEEP: " + deepIndex);
        } catch (Exception e) {
            Chat.send("[DungeonForcer] Unable to obtain featureIndex: " + e.getMessage());
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

        Chat.send("[DungeonForcer] Scanning " + ((2*radius+1)*(2*radius+1)) + " chunks...");

        List<GoodChunkFinder.ChunkResult> results = GoodChunkFinder.findGoodChunks(
                chunkPos.x(), chunkPos.z(), radius, seed, normalIndex, deepIndex, 20);

        if (results.isEmpty()) {
            Chat.send("[DungeonForcer] No good chunk found yet.");
        } else {
            Chat.send("[DungeonForcer] Found " + results.size() + " good chunks:");
            for (int i = 0; i < Math.min(10, results.size()); i++) {
                GoodChunkFinder.ChunkResult r = results.get(i);
                Chat.send(String.format("  %d. (%d, %d) rate=%.0f potential=%d",
                        i + 1, r.chunkX, r.chunkZ, r.score, r.potentialSpawners));
                RenderQueue.addChunkCross(r.chunkX, r.chunkZ);
            }
        }
        return 1;
    }

    private static int resetGoodChunkFinder(FabricClientCommandSource source) {
        RenderQueue.clearChunkCrosses();
        Chat.send("[DungeonForcer] Cleared the good chunk mark.");
        return 1;
    }

    private static String getSpawnerColor(SpawnerType type) {
        switch (type) {
            case SKELETON: return "\u00A7b";
            case ZOMBIE: return "\u00A7a";
            case SPIDER: return "\u00A7c";
            default: return "\u00A7f";
        }
    }
}

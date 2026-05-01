package hackerrouter.dungeonforcer.util;

import hackerrouter.dungeonforcer.mixin.ChunkGeneratorAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.placement.CavePlacements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.List;
import java.util.Optional;

public class FeatureIndexHelper {

    private static int cachedNormalIndex = -1;
    private static int cachedDeepIndex = -1;
    private static long cachedWorldSeed = Long.MIN_VALUE;

    // 获取 MONSTER_ROOM 的 featureIndex
    public static int getNormalFeatureIndex() {
        ensureCached();
        return cachedNormalIndex;
    }

    // 获取 MONSTER_ROOM_DEEP 的 featureIndex
    public static int getDeepFeatureIndex() {
        ensureCached();
        return cachedDeepIndex;
    }

    public static void invalidateCache() {
        cachedWorldSeed = Long.MIN_VALUE;
    }

    private static void ensureCached() {
        Minecraft client = Minecraft.getInstance();
        if (client.getSingleplayerServer() == null) {
            throw new IllegalStateException("Not in a world");
        }

        ServerLevel overworld = client.getSingleplayerServer().overworld();
        long worldSeed = overworld.getSeed();

        if (worldSeed == cachedWorldSeed) return;

        ChunkGenerator generator = overworld.getChunkSource().getGenerator();
        ChunkGeneratorAccessor accessor = (ChunkGeneratorAccessor) generator;
        List<FeatureSorter.StepFeatureData> featuresPerStep =
                accessor.getFeaturesPerStep().get();

        // UNDERGROUND_STRUCTURES = ordinal 3
        int stepIndex = GenerationStep.Decoration.UNDERGROUND_STRUCTURES.ordinal();
        if (stepIndex >= featuresPerStep.size()) {
            throw new IllegalStateException(
                    "UNDERGROUND_STRUCTURES step not found in featuresPerStep");
        }

        FeatureSorter.StepFeatureData stepData = featuresPerStep.get(stepIndex);

        Registry<PlacedFeature> registry =
                overworld.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);

        Optional<PlacedFeature> monsterRoom = registry.getOptional(CavePlacements.MONSTER_ROOM);
        Optional<PlacedFeature> monsterRoomDeep = registry.getOptional(CavePlacements.MONSTER_ROOM_DEEP);

        cachedNormalIndex = monsterRoom.map(placedFeature -> stepData.indexMapping().applyAsInt(placedFeature)).orElse(-1);

        cachedDeepIndex = monsterRoomDeep.map(placedFeature -> stepData.indexMapping().applyAsInt(placedFeature)).orElse(-1);

        cachedWorldSeed = worldSeed;
    }
}
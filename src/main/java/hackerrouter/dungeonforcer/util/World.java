package hackerrouter.dungeonforcer.util;

import hackerrouter.dungeonforcer.DungeonFinder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.placement.CavePlacements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public class World {

    private static long hashedSeed = 0;

    public static long getWorldSeed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() != null) {
            return mc.getSingleplayerServer().overworld().getSeed();
        }
        throw new IllegalStateException(
                "Cannot obtain the world seed on a remote server. Use /dungeonforcer seed <value>");
    }

    public static void setHashedSeed(long seed) {
        hashedSeed = seed;
    }

    public static long getHashedSeed() {
        return hashedSeed;
    }

    public static boolean hasIntegratedServer() {
        return Minecraft.getInstance().getSingleplayerServer() != null;
    }

    public static byte getBlockState(int worldX, int worldY, int worldZ) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel world = mc.level;
        if (world == null) return DungeonFinder.UNKNOWN;

        if (worldY < world.getMinY() || worldY > world.getMaxY()) {
            return DungeonFinder.UNKNOWN;
        }

        BlockPos pos = new BlockPos(worldX, worldY, worldZ);

        if (!world.hasChunkAt(pos)) {
            return DungeonFinder.UNKNOWN;
        }

        BlockState state = world.getBlockState(pos);

        if (state.is(Blocks.CHEST)) {
            return DungeonFinder.BLOCK_CHEST;
        }
        if (state.is(Blocks.SPAWNER)) {
            return DungeonFinder.BLOCK_SPAWNER;
        }
        if (state.isAir()) {
            return DungeonFinder.AIR;
        }
        if (state.isSolid()) {
            return DungeonFinder.SOLID;
        }

        return DungeonFinder.AIR;
    }

    public static boolean canPlaceMonsterRoom(int worldX, int worldY, int worldZ, boolean deep) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() == null) {
            return true;
        }
        ServerLevel level = mc.getSingleplayerServer().overworld();
        PlacedFeature feature = level.registryAccess()
                .lookupOrThrow(Registries.PLACED_FEATURE)
                .getOrThrow(deep ? CavePlacements.MONSTER_ROOM_DEEP : CavePlacements.MONSTER_ROOM)
                .value();
        return level.getChunkSource().getGenerator()
                .getBiomeGenerationSettings(level.getBiome(new BlockPos(worldX, worldY, worldZ)))
                .hasFeature(feature);
    }

    public static String getDimension() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            return mc.level.dimension().identifier().toString();
        }
        return "unknown";
    }

    public static boolean isOverworld() {
        return "minecraft:overworld".equals(getDimension());
    }
}

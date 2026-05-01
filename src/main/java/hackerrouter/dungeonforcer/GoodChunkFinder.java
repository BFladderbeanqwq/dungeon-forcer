package hackerrouter.dungeonforcer;

import hackerrouter.dungeonforcer.rng.WorldgenRandom;

import java.util.ArrayList;
import java.util.List;

public class GoodChunkFinder {

    public static class ChunkResult implements Comparable<ChunkResult> {
        public final int chunkX;
        public final int chunkZ;
        public final int potentialSpawners;
        public final double score;
        public final List<DungeonFinder.DungeonAttempt> attempts;

        public ChunkResult(int chunkX, int chunkZ, int potentialSpawners,
                           double score, List<DungeonFinder.DungeonAttempt> attempts) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.potentialSpawners = potentialSpawners;
            this.score = score;
            this.attempts = attempts;
        }

        @Override
        public int compareTo(ChunkResult other) {
            return Double.compare(other.score, this.score);
        }

        @Override
        public String toString() {
            return String.format("Chunk(%d, %d): score=%.1f, potential=%d",
                    chunkX, chunkZ, score, potentialSpawners);
        }
    }

    public static List<ChunkResult> findGoodChunks(
            int centerChunkX, int centerChunkZ, int radius,
            long worldSeed, int featureIndexNormal, int featureIndexDeep,
            int maxResults) {

        List<ChunkResult> results = new ArrayList<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = centerChunkX + dx;
                int cz = centerChunkZ + dz;

                ChunkResult result = evaluateChunk(cx, cz, worldSeed,
                        featureIndexNormal, featureIndexDeep);
                if (result.potentialSpawners > 0) {
                    results.add(result);
                }
            }
        }

        results.sort(null);
        if (results.size() > maxResults) {
            return results.subList(0, maxResults);
        }
        return results;
    }

    private static ChunkResult evaluateChunk(int chunkX, int chunkZ, long worldSeed,
                                             int featureIndexNormal, int featureIndexDeep) {
        List<DungeonFinder.DungeonAttempt> allAttempts = new ArrayList<>();
        double score = 0;
        int potentialSpawners = 0;

        WorldgenRandom random = new WorldgenRandom(0L);
        long decorationSeed = random.setDecorationSeed(worldSeed, chunkX * 16, chunkZ * 16);
        random.setFeatureSeed(decorationSeed, featureIndexNormal, DungeonFinder.STEP_ORDINAL);

        List<DungeonFinder.DungeonAttempt> normalAttempts = new ArrayList<>();
        for (int i = 0; i < DungeonFinder.NORMAL_ATTEMPTS; i++) {
            int px = random.nextInt(16);
            int pz = random.nextInt(16);
            int py = random.nextInt(320);
            int sx = random.nextInt(2) + 2;
            int sz = random.nextInt(2) + 2;

            DungeonFinder.DungeonAttempt attempt = new DungeonFinder.DungeonAttempt(
                    chunkX * 16 + px, py, chunkZ * 16 + pz,
                    sx, sz, px, pz, false, i, 0, 0);
            normalAttempts.add(attempt);

            boolean crossChunk = px < sx + 1 || px > 15 - (sx + 1)
                    || pz < sz + 1 || pz > 15 - (sz + 1);

            if (py >= 10 && py <= 50) {
                score += crossChunk ? 10 : 3;
                potentialSpawners++;
            } else if (py >= 0 && py <= 70) {
                score += crossChunk ? 5 : 1;
                potentialSpawners++;
            }

            if (sx == 3 && sz == 3) score += 2;
            else if (sx == 3 || sz == 3) score += 1;
        }

        if (featureIndexDeep >= 0) {
            random.setFeatureSeed(decorationSeed, featureIndexDeep, DungeonFinder.STEP_ORDINAL);
            for (int i = 0; i < DungeonFinder.DEEP_ATTEMPTS; i++) {
                int px = random.nextInt(16);
                int pz = random.nextInt(16);
                int py = random.nextInt(58) + (-58);
                int sx = random.nextInt(2) + 2;
                int sz = random.nextInt(2) + 2;

                DungeonFinder.DungeonAttempt attempt = new DungeonFinder.DungeonAttempt(
                        chunkX * 16 + px, py, chunkZ * 16 + pz,
                        sx, sz, px, pz, true, i, 0, 0);
                normalAttempts.add(attempt);

                score += 8;
                potentialSpawners++;
            }
        }

        for (int i = 0; i < normalAttempts.size(); i++) {
            for (int j = i + 1; j < normalAttempts.size(); j++) {
                int dy = Math.abs(normalAttempts.get(i).originY - normalAttempts.get(j).originY);
                if (dy <= 16) {
                    score += 15;
                } else if (dy <= 32) {
                    score += 5;
                }
            }
        }

        allAttempts.addAll(normalAttempts);
        return new ChunkResult(chunkX, chunkZ, potentialSpawners, score, allAttempts);
    }
}

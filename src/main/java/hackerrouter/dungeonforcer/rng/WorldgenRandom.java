package hackerrouter.dungeonforcer.rng;

/**
 * Simulates vanilla WorldgenRandom behavior.
 *
 * Key: vanilla WorldgenRandom extends LegacyRandomSource → BitRandomSource,
 * all nextInt/nextLong/nextFloat methods dispatch through next(int bits),
 * and next(bits) is overridden by WorldgenRandom as randomSource.nextLong() >>> (64 - bits).
 *
 * This means nextInt(bound) uses BitRandomSource's default impl (based on next(31)),
 * not XoroshiroRandomSource's own nextInt(bound). The two algorithms are completely different!
 */
public class WorldgenRandom {
    private final XoroshiroRandomSource randomSource;
    private int count;

    public WorldgenRandom(XoroshiroRandomSource randomSource) {
        this.randomSource = randomSource;
        this.count = 0;
    }

    public WorldgenRandom(long seed) {
        this(new XoroshiroRandomSource(seed));
    }

    /**
     * Core method: simulates vanilla WorldgenRandom.next(bits).
     * vanilla: (int)(this.randomSource.nextLong() >>> (64 - bits))
     */
    public int next(int bits) {
        this.count++;
        return (int) (this.randomSource.nextLong() >>> (64 - bits));
    }

    public void setSeed(long seed) {
        this.randomSource.setSeed(seed);
    }

    public long setDecorationSeed(long worldSeed, int blockX, int blockZ) {
        this.setSeed(worldSeed);
        long m = this.nextLong() | 1L;
        long n = this.nextLong() | 1L;
        long decorationSeed = (long) blockX * m + (long) blockZ * n ^ worldSeed;
        this.setSeed(decorationSeed);
        return decorationSeed;
    }

    public void setFeatureSeed(long decorationSeed, int featureIndex, int stepOrdinal) {
        long featureSeed = decorationSeed + (long) featureIndex + 10000L * (long) stepOrdinal;
        this.setSeed(featureSeed);
    }

    // ========== BitRandomSource default implementations ==========
    // The following methods are exact copies of BitRandomSource's default impl,
    // dispatching through next(bits) to ensure vanilla-consistent behavior.

    public int nextInt() {
        return this.next(32);
    }

    /**
     * Exact copy of BitRandomSource.nextInt(bound).
     * Note: this differs from XoroshiroRandomSource.nextInt(bound) algorithm!
     */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        }

        if ((bound & (bound - 1)) == 0) {
            // bound is a power of 2
            return (int) ((long) bound * (long) this.next(31) >> 31);
        }

        int sample;
        int modulo;
        do {
            sample = this.next(31);
            modulo = sample % bound;
        } while (sample - modulo + (bound - 1) < 0);

        return modulo;
    }

    /**
     * Exact copy of BitRandomSource.nextLong().
     * Note: this consumes 2 next() calls (= 2 Xoroshiro nextLong calls).
     */
    public long nextLong() {
        int upper = this.next(32);
        int lower = this.next(32);
        long shifted = (long) upper << 32;
        return shifted + (long) lower;
    }

    public boolean nextBoolean() {
        return this.next(1) != 0;
    }

    public float nextFloat() {
        return (float) this.next(24) * 5.9604645E-8F;
    }

    public double nextDouble() {
        int upper = this.next(26);
        int lower = this.next(27);
        long combined = ((long) upper << 27) + (long) lower;
        return (double) combined * 1.110223E-16;
    }

    public int getCount() {
        return this.count;
    }

    public long getSeedLo() { return randomSource.getSeedLo(); }
    public long getSeedHi() { return randomSource.getSeedHi(); }
}

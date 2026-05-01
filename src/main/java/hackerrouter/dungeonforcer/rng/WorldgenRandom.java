package hackerrouter.dungeonforcer.rng;

/**
 * 模拟 vanilla WorldgenRandom 的行为。
 *
 * 关键：vanilla WorldgenRandom 继承 LegacyRandomSource → BitRandomSource，
 * 所有 nextInt/nextLong/nextFloat 等方法都通过 next(int bits) 分发，
 * 而 next(bits) 被 WorldgenRandom 重写为 randomSource.nextLong() >>> (64 - bits)。
 *
 * 这意味着 nextInt(bound) 使用的是 BitRandomSource 的默认实现（基于 next(31)），
 * 而不是 XoroshiroRandomSource 自己的 nextInt(bound)。两者算法完全不同！
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
     * 核心方法：模拟 vanilla WorldgenRandom.next(bits)。
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

    // ========== BitRandomSource 默认实现 ==========
    // 以下方法完全复制 BitRandomSource 的默认实现，
    // 通过 next(bits) 分发，确保与 vanilla 行为一致。

    public int nextInt() {
        return this.next(32);
    }

    /**
     * BitRandomSource.nextInt(bound) 的精确复制。
     * 注意：这与 XoroshiroRandomSource.nextInt(bound) 算法不同！
     */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        }

        if ((bound & (bound - 1)) == 0) {
            // bound 是 2 的幂
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
     * BitRandomSource.nextLong() 的精确复制。
     * 注意：这消耗 2 次 next() 调用（= 2 次 Xoroshiro nextLong）。
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

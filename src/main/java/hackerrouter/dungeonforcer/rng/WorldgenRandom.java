package hackerrouter.dungeonforcer.rng;

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

    public int nextInt() {
        return this.next(32);
    }

    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        }

        if ((bound & (bound - 1)) == 0) {
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

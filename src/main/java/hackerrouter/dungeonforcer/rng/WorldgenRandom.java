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
        return this.randomSource.nextInt();
    }

    public int nextInt(int bound) {
        return this.randomSource.nextInt(bound);
    }

    public long nextLong() {
        return this.randomSource.nextLong();
    }

    public boolean nextBoolean() {
        return this.randomSource.nextBoolean();
    }

    public float nextFloat() {
        return this.randomSource.nextFloat();
    }

    public double nextDouble() {
        return this.randomSource.nextDouble();
    }

    public int getCount() {
        return this.count;
    }

    public long getSeedLo() { return randomSource.getSeedLo(); }
    public long getSeedHi() { return randomSource.getSeedHi(); }
}
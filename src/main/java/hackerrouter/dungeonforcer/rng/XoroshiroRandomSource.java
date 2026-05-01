package hackerrouter.dungeonforcer.rng;

public class XoroshiroRandomSource {
    private Xoroshiro128PlusPlus rng;

    public XoroshiroRandomSource(long seed) {
        long[] upgraded = RandomSupport.upgradeSeedTo128bit(seed);
        this.rng = new Xoroshiro128PlusPlus(upgraded[0], upgraded[1]);
    }

    public XoroshiroRandomSource(long seedLo, long seedHi) {
        this.rng = new Xoroshiro128PlusPlus(seedLo, seedHi);
    }

    public void setSeed(long seed) {
        long[] upgraded = RandomSupport.upgradeSeedTo128bit(seed);
        this.rng = new Xoroshiro128PlusPlus(upgraded[0], upgraded[1]);
    }

    public int nextInt() {
        return (int) this.rng.nextLong();
    }

    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        }

        long randomBits = Integer.toUnsignedLong(this.nextInt());
        long multipliedRandomBits = randomBits * (long) bound;
        long fractionalPart = multipliedRandomBits & 0xFFFFFFFFL;

        if (fractionalPart < (long) bound) {
            int threshold = Integer.remainderUnsigned(~bound + 1, bound);
            while (fractionalPart < (long) threshold) {
                randomBits = Integer.toUnsignedLong(this.nextInt());
                multipliedRandomBits = randomBits * (long) bound;
                fractionalPart = multipliedRandomBits & 0xFFFFFFFFL;
            }
        }

        return (int) (multipliedRandomBits >> 32);
    }

    public long nextLong() {
        return this.rng.nextLong();
    }

    public boolean nextBoolean() {
        return (this.rng.nextLong() & 1L) != 0L;
    }

    public float nextFloat() {
        return (float) this.nextBits(24) * 5.9604645E-8F;
    }

    public double nextDouble() {
        return (double) this.nextBits(53) * 1.110223E-16;
    }

    private long nextBits(int bits) {
        return this.rng.nextLong() >>> (64 - bits);
    }

    public long getSeedLo() { return rng.getSeedLo(); }
    public long getSeedHi() { return rng.getSeedHi(); }
}
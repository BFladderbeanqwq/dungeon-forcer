package hackerrouter.dungeonforcer.rng;

public class LegacyRandomSource {
    private static final long MODULUS_MASK = (1L << 48) - 1L;
    private static final long MULTIPLIER = 25214903917L;
    private static final long INCREMENT = 11L;

    private long seed;

    public LegacyRandomSource(long seed) {
        setSeed(seed);
    }

    public void setSeed(long seed) {
        this.seed = (seed ^ MULTIPLIER) & MODULUS_MASK;
    }

    public int next(int bits) {
        seed = (seed * MULTIPLIER + INCREMENT) & MODULUS_MASK;
        return (int) (seed >>> (48 - bits));
    }

    public int nextInt() {
        return next(32);
    }

    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        }

        if ((bound & (bound - 1)) == 0) {
            return (int) ((long) bound * (long) next(31) >> 31);
        }

        int sample;
        int modulo;
        do {
            sample = next(31);
            modulo = sample % bound;
        } while (sample - modulo + (bound - 1) < 0);

        return modulo;
    }

    public long nextLong() {
        int upper = next(32);
        int lower = next(32);
        return ((long) upper << 32) + (long) lower;
    }
}

package hackerrouter.dungeonforcer.rng;

public class Xoroshiro128PlusPlus {
    private long seedLo;
    private long seedHi;

    public Xoroshiro128PlusPlus(long seedLo, long seedHi) {
        this.seedLo = seedLo;
        this.seedHi = seedHi;
        if ((this.seedLo | this.seedHi) == 0L) {
            this.seedLo = -7046029254386353131L; // GOLDEN_RATIO_64
            this.seedHi = 7640891576956012809L;  // SILVER_RATIO_64
        }
    }

    public long nextLong() {
        long s0 = this.seedLo;
        long s1 = this.seedHi;
        long result = Long.rotateLeft(s0 + s1, 17) + s0;
        s1 ^= s0;
        this.seedLo = Long.rotateLeft(s0, 49) ^ s1 ^ (s1 << 21);
        this.seedHi = Long.rotateLeft(s1, 28);
        return result;
    }

    public long getSeedLo() { return seedLo; }
    public long getSeedHi() { return seedHi; }
    public void setState(long lo, long hi) {
        this.seedLo = lo;
        this.seedHi = hi;
        if ((this.seedLo | this.seedHi) == 0L) {
            this.seedLo = -7046029254386353131L;
            this.seedHi = 7640891576956012809L;
        }
    }
}


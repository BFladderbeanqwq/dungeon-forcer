package hackerrouter.dungeonforcer;

public class SpawnerCombination implements Comparable<SpawnerCombination> {
    public static final int MAX_SPAWNERS = 14;

    public Spawner[] spawners;
    public int spawnerCount;
    public int points;

    public SpawnerCombination() {
        this.spawners = new Spawner[MAX_SPAWNERS];
        this.spawnerCount = 0;
        this.points = 0;
    }

    public boolean countPoints(int minPoints, SearchType searchType, SpawnerType preferredType) {
        int points = -1;
        int typeCount = 0;
        for (int i = 0; i < spawnerCount; i++) {
            if (spawners[i].type == preferredType) typeCount++;
        }

        switch (searchType) {
            case MAX_TOTAL:
                points = spawnerCount;
                break;
            case MAX_TOTAL_PREFER_TYPE:
                points = (spawnerCount << 3) + typeCount;
                break;
            case MAX_TYPE:
                points = typeCount;
                break;
            case MAX_TYPE_PREFER_MORE:
                points = (typeCount << 3) + spawnerCount;
                break;
            case MAX_ACTIVATED_TOTAL:
                points = calcActivatedTotal(false, preferredType);
                break;
            case MAX_ACTIVATED_TOTAL_PREFER_TYPE:
                points = calcActivatedTotalPreferType(preferredType);
                break;
            case MAX_ACTIVATED_TYPE:
                points = calcActivatedType(preferredType);
                break;
            case MAX_ACTIVATED_TYPE_PREFER_MORE:
                points = calcActivatedTypePreferMore(preferredType);
                break;
        }

        if (points < minPoints) return false;
        this.points = points;
        return true;
    }

    private static int distanceSq(Spawner a, Spawner b) {
        int dx = a.x - b.x;
        int dy = a.y - b.y;
        int dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static int distanceSq(Spawner a, int x, int y, int z) {
        int dx = a.x - x;
        int dy = a.y - y;
        int dz = a.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private Spawner findDensestCenter(SpawnerType typeFilter) {
        Spawner best = null;
        int bestCount = 0;
        for (int i = 0; i < spawnerCount; i++) {
            Spawner s = spawners[i];
            if (typeFilter != null && s.type != typeFilter) continue;
            int count = 0;
            for (int j = 0; j < spawnerCount; j++) {
                if (typeFilter != null && spawners[j].type != typeFilter) continue;
                if (distanceSq(s, spawners[j]) < 1024) count++;
            }
            if (count > bestCount) { bestCount = count; best = s; }
        }
        return best;
    }

    private int scanBox(Spawner center, SpawnerType typeFilter) {
        int best = 0;
        for (int sx = 4; sx < 12; sx++) {
            for (int sz = 4; sz < 12; sz++) {
                for (int dy = -16; dy <= 16; dy++) {
                    int cy = center.y + dy;
                    int count = 0;
                    for (int k = 0; k < spawnerCount; k++) {
                        if (typeFilter != null && spawners[k].type != typeFilter) continue;
                        if (distanceSq(spawners[k], sx, cy, sz) < 256) count++;
                    }
                    if (count > best) best = count;
                }
            }
        }
        return best;
    }

    private int scanBoxWithType(Spawner center, SpawnerType preferredType) {
        int best = 0;
        for (int sx = 4; sx < 12; sx++) {
            for (int sz = 4; sz < 12; sz++) {
                for (int dy = -16; dy <= 16; dy++) {
                    int cy = center.y + dy;
                    int total = 0, typed = 0;
                    for (int k = 0; k < spawnerCount; k++) {
                        if (distanceSq(spawners[k], sx, cy, sz) < 256) {
                            total++;
                            if (spawners[k].type == preferredType) typed++;
                        }
                    }
                    int score = (total << 3) + typed;
                    if (score > best) best = score;
                }
            }
        }
        return best;
    }

    private int calcActivatedTotal(boolean typeOnly, SpawnerType preferredType) {
        if (spawnerCount < 1) return 0;
        Spawner center = findDensestCenter(typeOnly ? preferredType : null);
        if (center == null) return 0;
        return scanBox(center, typeOnly ? preferredType : null);
    }

    private int calcActivatedTotalPreferType(SpawnerType preferredType) {
        if (spawnerCount < 1) return 0;
        Spawner center = findDensestCenter(null);
        if (center == null) return 0;
        return scanBoxWithType(center, preferredType);
    }

    private int calcActivatedType(SpawnerType preferredType) {
        return calcActivatedTotal(true, preferredType);
    }

    private int calcActivatedTypePreferMore(SpawnerType preferredType) {
        if (spawnerCount < 1) return 0;
        Spawner center = findDensestCenter(preferredType);
        if (center == null) return 0;
        int best = 0;
        for (int sx = 4; sx < 12; sx++) {
            for (int sz = 4; sz < 12; sz++) {
                for (int dy = -16; dy <= 16; dy++) {
                    int cy = center.y + dy;
                    int total = 0, typed = 0;
                    for (int k = 0; k < spawnerCount; k++) {
                        if (spawners[k].type != preferredType) continue;
                        if (distanceSq(spawners[k], sx, cy, sz) < 256) {
                            typed++;
                            total++;
                        }
                    }
                    int score = (typed << 3) + total;
                    if (score > best) best = score;
                }
            }
        }
        return best;
    }

    public SpawnerCombination copy() {
        SpawnerCombination c = new SpawnerCombination();
        c.spawnerCount = this.spawnerCount;
        c.points = this.points;
        for (int i = 0; i < spawnerCount; i++) {
            c.spawners[i] = this.spawners[i].copy();
        }
        return c;
    }

    @Override
    public int compareTo(SpawnerCombination other) {
        return Integer.compare(other.points, this.points);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("SpawnerCombination{points=%d, count=%d, spawners=[\n", points, spawnerCount));
        for (int i = 0; i < spawnerCount; i++) {
            sb.append("  ").append(spawners[i]).append("\n");
        }
        sb.append("]}");
        return sb.toString();
    }
}

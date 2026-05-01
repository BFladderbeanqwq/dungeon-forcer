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

    public void countPoints(int activationRange, SearchType searchType, SpawnerType preferredType) {
        int totalCount = spawnerCount;
        int typeCount = 0;
        int activatedTotal = 0;
        int activatedType = 0;

        for (int i = 0; i < spawnerCount; i++) {
            Spawner s = spawners[i];
            if (s.type == preferredType) {
                typeCount++;
            }
        }

        if (searchType == SearchType.MAX_ACTIVATED_TOTAL ||
                searchType == SearchType.MAX_ACTIVATED_TOTAL_PREFER_TYPE ||
                searchType == SearchType.MAX_ACTIVATED_TYPE ||
                searchType == SearchType.MAX_ACTIVATED_TYPE_PREFER_MORE) {

            int bestActivated = 0;
            int bestActivatedType = 0;
            for (int i = 0; i < spawnerCount; i++) {
                int activated = 0;
                int activatedT = 0;
                for (int j = 0; j < spawnerCount; j++) {
                    if (distanceSq(spawners[i], spawners[j]) <= activationRange * activationRange) {
                        activated++;
                        if (spawners[j].type == preferredType) {
                            activatedT++;
                        }
                    }
                }
                if (activated > bestActivated ||
                        (activated == bestActivated && activatedT > bestActivatedType)) {
                    bestActivated = activated;
                    bestActivatedType = activatedT;
                }
            }
            activatedTotal = bestActivated;
            activatedType = bestActivatedType;
        }

        switch (searchType) {
            case MAX_TOTAL:
                points = totalCount * 1000;
                break;
            case MAX_TOTAL_PREFER_TYPE:
                points = totalCount * 1000 + typeCount;
                break;
            case MAX_TYPE:
                points = typeCount * 1000;
                break;
            case MAX_TYPE_PREFER_MORE:
                points = typeCount * 1000 + totalCount;
                break;
            case MAX_ACTIVATED_TOTAL:
                points = activatedTotal * 1000;
                break;
            case MAX_ACTIVATED_TOTAL_PREFER_TYPE:
                points = activatedTotal * 1000 + activatedType;
                break;
            case MAX_ACTIVATED_TYPE:
                points = activatedType * 1000;
                break;
            case MAX_ACTIVATED_TYPE_PREFER_MORE:
                points = activatedType * 1000 + activatedTotal;
                break;
        }
    }

    private static int distanceSq(Spawner a, Spawner b) {
        int dx = a.x - b.x;
        int dy = a.y - b.y;
        int dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
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
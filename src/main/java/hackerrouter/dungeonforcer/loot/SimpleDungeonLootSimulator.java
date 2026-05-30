package hackerrouter.dungeonforcer.loot;

import hackerrouter.dungeonforcer.rng.LegacyRandomSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SimpleDungeonLootSimulator {
    public static final String DEFAULT_TARGET = "minecraft:enchanted_golden_apple";

    private static final Entry[] POOL_1 = {
            new Entry("minecraft:leather", 20, 1, 5),
            new Entry("minecraft:golden_apple", 15),
            new Entry("minecraft:enchanted_golden_apple", 2),
            new Entry("minecraft:music_disc_otherside", 2),
            new Entry("minecraft:music_disc_13", 15),
            new Entry("minecraft:music_disc_cat", 15),
            new Entry("minecraft:name_tag", 20),
            new Entry("minecraft:golden_horse_armor", 10),
            new Entry("minecraft:copper_horse_armor", 15),
            new Entry("minecraft:iron_horse_armor", 15),
            new Entry("minecraft:diamond_horse_armor", 5),
            new Entry("minecraft:enchanted_book", 10, true)
    };

    private static final Entry[] POOL_2 = {
            new Entry("minecraft:iron_ingot", 10, 1, 4),
            new Entry("minecraft:gold_ingot", 5, 1, 4),
            new Entry("minecraft:bread", 20),
            new Entry("minecraft:wheat", 20, 1, 4),
            new Entry("minecraft:bucket", 10),
            new Entry("minecraft:redstone", 15, 1, 4),
            new Entry("minecraft:coal", 15, 1, 4),
            new Entry("minecraft:melon_seeds", 10, 2, 4),
            new Entry("minecraft:pumpkin_seeds", 10, 2, 4),
            new Entry("minecraft:beetroot_seeds", 10, 2, 4)
    };

    private static final Entry[] POOL_3 = {
            new Entry("minecraft:bone", 10, 1, 8),
            new Entry("minecraft:gunpowder", 10, 1, 8),
            new Entry("minecraft:rotten_flesh", 10, 1, 8),
            new Entry("minecraft:string", 10, 1, 8)
    };

    private static final int[] ON_RANDOM_LOOT_MAX_LEVELS = {
            4, 4, 4, 4, 4, 3, 1, 3, 3, 5, 5, 5,
            2, 2, 3, 3, 5, 1, 3, 3, 5, 2, 1, 1,
            3, 3, 3, 5, 3, 1, 1, 3, 4, 5, 4, 3,
            1, 1, 2, 1
    };

    private SimpleDungeonLootSimulator() {
    }

    public static Result simulate(long lootTableSeed) {
        LegacyRandomSource random = new LegacyRandomSource(lootTableSeed);
        List<ItemRoll> items = new ArrayList<>();

        rollPool(random, POOL_1, nextInclusive(random, 1, 3), items);
        rollPool(random, POOL_2, nextInclusive(random, 1, 4), items);
        rollPool(random, POOL_3, 3, items);

        return new Result(lootTableSeed, items);
    }

    public static boolean contains(long lootTableSeed, String itemId) {
        if (DEFAULT_TARGET.equals(itemId)) {
            return containsEnchantedGoldenAppleReliably(lootTableSeed);
        }
        return simulate(lootTableSeed).contains(itemId);
    }

    private static boolean containsEnchantedGoldenAppleReliably(long lootTableSeed) {
        LegacyRandomSource random = new LegacyRandomSource(lootTableSeed);
        int rolls = nextInclusive(random, 1, 3);
        int totalWeight = totalWeight(POOL_1);

        for (int i = 0; i < rolls; i++) {
            Entry selected = selectEntry(random, POOL_1, totalWeight);
            if (DEFAULT_TARGET.equals(selected.itemId)) {
                return true;
            }
            if (selected.hasCountRange) {
                nextInclusive(random, selected.minCount, selected.maxCount);
            }
            if (selected.enchantRandomly) {
                return false;
            }
        }
        return false;
    }

    private static void rollPool(LegacyRandomSource random, Entry[] entries, int rolls, List<ItemRoll> items) {
        int totalWeight = totalWeight(entries);

        for (int i = 0; i < rolls; i++) {
            Entry selected = selectEntry(random, entries, totalWeight);

            int count = selected.hasCountRange ? nextInclusive(random, selected.minCount, selected.maxCount) : 1;
            if (selected.enchantRandomly) {
                consumeEnchantRandomly(random);
            }
            items.add(new ItemRoll(selected.itemId, count));
        }
    }

    private static int totalWeight(Entry[] entries) {
        int totalWeight = 0;
        for (Entry entry : entries) {
            totalWeight += entry.weight;
        }
        return totalWeight;
    }

    private static Entry selectEntry(LegacyRandomSource random, Entry[] entries, int totalWeight) {
        int choice = random.nextInt(totalWeight);
        Entry selected = entries[entries.length - 1];
        for (Entry entry : entries) {
            choice -= entry.weight;
            if (choice < 0) {
                selected = entry;
                break;
            }
        }
        return selected;
    }

    private static int nextInclusive(LegacyRandomSource random, int minInclusive, int maxInclusive) {
        return minInclusive >= maxInclusive ? minInclusive : random.nextInt(maxInclusive - minInclusive + 1) + minInclusive;
    }

    private static void consumeEnchantRandomly(LegacyRandomSource random) {
        int enchantment = random.nextInt(ON_RANDOM_LOOT_MAX_LEVELS.length);
        int maxLevel = ON_RANDOM_LOOT_MAX_LEVELS[enchantment];
        if (maxLevel > 1) {
            random.nextInt(maxLevel);
        }
    }

    private static final class Entry {
        final String itemId;
        final int weight;
        final int minCount;
        final int maxCount;
        final boolean hasCountRange;
        final boolean enchantRandomly;

        Entry(String itemId, int weight) {
            this(itemId, weight, 1, 1, false);
        }

        Entry(String itemId, int weight, boolean enchantRandomly) {
            this(itemId, weight, 1, 1, enchantRandomly);
        }

        Entry(String itemId, int weight, int minCount, int maxCount) {
            this(itemId, weight, minCount, maxCount, false);
        }

        Entry(String itemId, int weight, int minCount, int maxCount, boolean enchantRandomly) {
            this.itemId = itemId;
            this.weight = weight;
            this.minCount = minCount;
            this.maxCount = maxCount;
            this.hasCountRange = minCount != maxCount;
            this.enchantRandomly = enchantRandomly;
        }
    }

    public static final class Result {
        public final long seed;
        public final List<ItemRoll> items;

        Result(long seed, List<ItemRoll> items) {
            this.seed = seed;
            this.items = Collections.unmodifiableList(items);
        }

        public boolean contains(String itemId) {
            for (ItemRoll item : items) {
                if (item.itemId.equals(itemId)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static final class ItemRoll {
        public final String itemId;
        public final int count;

        ItemRoll(String itemId, int count) {
            this.itemId = itemId;
            this.count = count;
        }
    }
}

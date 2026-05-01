# dungeonforcer-26.1.1


Yes. Dungeon Forcer mod for 26.1.1.

I don't play 26.1.1 survival myself, but still, it is time to remind people of cool stuff we have.

The basic concept is from andrew_10_'s video: [https://www.youtube.com/watch?v=OnsBTC5MNW8](https://www.youtube.com/watch?v=OnsBTC5MNW8)

## How does it work?

[PLACEHOLDER HERE]

[YEAH JUST CHECK THE CODE YOURSELF.
I WILL FINISH THE README ONCE I HAVE TIME TO ENJOY WRITING ANALYSIS]

## If you wonder what's changed from the original mod:

1. Mojank changed their RNG of feature world gen from LCG to Xoroshiro128++ in 1.18-pre7.

2. There are `MONSTER_ROOM_DEEP` and `MONSTER_ROOM` since the change of world height.

3. The `featureIndex` and its salt are not fixed now. They depend on `FeatureSorter`.

## How to regenerate chunks in 26.1.1 in vanilla:

See [OOME method](https://mcdf.wiki.gg/wiki/Java_Edition:Chunk_Regeneration#Out_Of_Memory_Save_Error).
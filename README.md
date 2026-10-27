# Block Void (Minecraft Java 26.3, Fabric)

**Every block you pick up is erased from the world forever.**

Pick up a block and that block type stops existing: every single one of them, in every loaded chunk,
and in every chunk that loads or generates from then on. The mod tells everyone what just got erased
(chat message, big title, deep rumble). The world slowly disappears as you play.

* Works in singleplayer, on LAN and on servers. It is server-side, so **friends do not need to install anything**.
* Erasing spreads outward from you like a wave, and is time-limited per tick so it never freezes the game.
* Blocks that nature or players try to bring back (placing it, lava + water making stone, growing trees...) vanish again.
* The list is saved per world in `blockvoid.tsv` inside the world folder.
* `/blockvoid` shows everything that has been erased and who picked it up.

## Requirements to PLAY

* Minecraft Java 26.3
* Fabric Loader 0.19.5 or newer
* Fabric API for 26.3 (0.161.0 or newer)
* Java 25 (the Minecraft launcher already includes it)

Put `blockvoid-1.0.0.jar` and the Fabric API jar into your `.minecraft/mods` folder.

## How to BUILD the .jar

**Option A, nothing to install (GitHub):**
1. Make a free GitHub repository and upload everything in this folder (including the hidden `.github` folder).
2. Open the **Actions** tab, wait for **Build mod jar** to go green, open the run and download the `blockvoid-jar` artifact.
3. Unzip it. `blockvoid-1.0.0.jar` is your mod.

**Option B, on your own PC:**
1. Install JDK 25 (e.g. Eclipse Temurin 25) and Gradle 9.7 or newer.
2. In this folder run: `gradle build`
3. Your jar is at `build/libs/blockvoid-1.0.0.jar`.

## Tweaking

Everything adjustable is at the top of `src/main/java/com/blockvoid/Eraser.java`:
`SCAN_RADIUS` (how many chunks around each player are cleaned), `ROLLING_CHUNKS_PER_TICK` and
`TIME_BUDGET_NANOS` (how fast erasing happens vs. how much lag it may cause).

## How "picking up" is detected

Each tick the mod compares each player's inventory with the previous tick. If the count of a block item went up
and that item was lying on the ground right next to the player, it was picked up. Crafting, chests and the creative
menu do not count. Items that only *place* a block (seeds, carrots, redstone dust, ...) do not erase their crop or wire,
only the block's own item does.

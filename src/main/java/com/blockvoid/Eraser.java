package com.blockvoid;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Wipes erased blocks out of the world.
 *
 * Every tick, for every player, we sweep the loaded chunks around them (nearest first, so the erasing
 * spreads outwards from you like a wave). Chunks that load later, including brand-new chunks being
 * generated, are cleaned the same way the moment they appear. That is how the block disappears from
 * "everywhere", not just the chunk you are standing in. It also means blocks that nature or players
 * try to bring back (lava + water making stone, planting a banned block...) vanish again straight away.
 *
 * Work is time-boxed per tick so erasing a huge amount of terrain never freezes the server.
 */
final class Eraser {

    /** How many chunks around each player are swept (radius, in chunks). */
    private static final int SCAN_RADIUS = 12;
    /** Chunks checked per player per tick in the rolling sweep. Checking a chunk with nothing to erase is very cheap. */
    private static final int ROLLING_CHUNKS_PER_TICK = 32;
    /** Maximum time spent erasing per server tick. */
    private static final long TIME_BUDGET_NANOS = 12_000_000L;
    /**
     * setBlock flags: 2 = update clients, 16 = skip neighbour shape updates. Skipping the shape updates is
     * important, otherwise erasing blocks at the edge of the loaded area could force-load more chunks.
     */
    private static final int SET_BLOCK_FLAGS = 2 | 16;

    private static final Predicate<BlockState> IS_ERASED = state -> VoidLedger.isErased(state.getBlock());

    /** Chunk offsets inside the scan circle, sorted nearest first. */
    private static final int[][] OFFSETS = buildOffsets(SCAN_RADIUS);

    private static final class Cursor {
        int epoch = -1;
        int index = 0;
    }

    private static final Map<UUID, Cursor> CURSORS = new HashMap<>();
    private static int epoch = 0;
    private static int tickCounter = 0;

    private Eraser() {
    }

    /** Called when a new block gets erased: every player's sweep starts over from their own position. */
    static void restartSweep() {
        epoch++;
    }

    static void reset() {
        CURSORS.clear();
        epoch++;
    }

    static void tick(MinecraftServer server) {
        if (VoidLedger.isEmpty()) {
            return;
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        int count = players.size();
        if (count == 0) {
            return;
        }
        tickCounter++;
        long deadline = System.nanoTime() + TIME_BUDGET_NANOS;

        // Start with a different player each tick so nobody gets starved when a lot is being erased.
        for (int i = 0; i < count; i++) {
            if (System.nanoTime() >= deadline) {
                return;
            }
            sweepAround(players.get((i + tickCounter) % count), deadline);
        }
    }

    private static void sweepAround(ServerPlayer player, long deadline) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos position = player.blockPosition();
        int centerX = position.getX() >> 4;
        int centerZ = position.getZ() >> 4;

        // The 3x3 chunks right around the player are checked every other tick,
        // so a banned block someone tries to place disappears almost instantly.
        if ((tickCounter & 1) == 0) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (!eraseChunk(level, centerX + dx, centerZ + dz, deadline)) {
                        return;
                    }
                }
            }
        }

        // Rolling sweep over the whole area, nearest chunks first.
        Cursor cursor = CURSORS.computeIfAbsent(player.getUUID(), id -> new Cursor());
        if (cursor.epoch != epoch) {
            cursor.epoch = epoch;
            cursor.index = 0;
        }
        for (int i = 0; i < ROLLING_CHUNKS_PER_TICK; i++) {
            if (cursor.index >= OFFSETS.length) {
                cursor.index = 0;
            }
            int[] offset = OFFSETS[cursor.index];
            if (!eraseChunk(level, centerX + offset[0], centerZ + offset[1], deadline)) {
                return; // out of time, pick this same chunk up again next tick
            }
            cursor.index++;
        }
    }

    /**
     * Erases every erased block in one chunk. Returns false if the time budget ran out before the
     * chunk was finished (the remainder is handled on a later tick).
     */
    private static boolean eraseChunk(ServerLevel level, int chunkX, int chunkZ, long deadline) {
        if (!level.hasChunk(chunkX, chunkZ)) {
            return true; // not loaded: it gets cleaned the moment it loads
        }
        LevelChunk chunk = level.getChunk(chunkX, chunkZ);
        LevelChunkSection[] sections = chunk.getSections();
        int minY = level.getMinY();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int removed = 0;

        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            // maybeHas only looks at the section's palette, so sections without any erased block are skipped instantly.
            if (section == null || !section.maybeHas(IS_ERASED)) {
                continue;
            }
            int baseX = chunkX << 4;
            int baseY = minY + (i << 4);
            int baseZ = chunkZ << 4;

            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!VoidLedger.isErased(state.getBlock())) {
                            continue;
                        }
                        pos.set(baseX + x, baseY + y, baseZ + z);
                        // Leaves water behind if the block was waterlogged, otherwise air.
                        level.setBlock(pos, state.getFluidState().createLegacyBlock(), SET_BLOCK_FLAGS);

                        if ((++removed & 63) == 0 && System.nanoTime() >= deadline) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private static int[][] buildOffsets(int radius) {
        List<int[]> list = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    list.add(new int[]{dx, dz});
                }
            }
        }
        list.sort(Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
        return list.toArray(new int[0][]);
    }
}

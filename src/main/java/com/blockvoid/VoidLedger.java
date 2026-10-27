package com.blockvoid;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The permanent list of erased blocks for the current world.
 *
 * "Primary" entries are the blocks a player actually picked up (these are what get saved and listed).
 * Some blocks come with companion blocks that must vanish with them, for example a torch and its
 * wall-mounted version, so the full erased set can be a little bigger than the primary list.
 */
final class VoidLedger {

    record Entry(String blockId, String playerName, long timeMillis) {
    }

    private static final List<Entry> PRIMARY = new ArrayList<>();
    private static final Set<Block> ERASED = new HashSet<>();
    private static Map<String, Block> blocksById;
    private static Path file;

    private VoidLedger() {
    }

    // ------------------------------------------------------------------ queries

    static boolean isErased(Block block) {
        return !ERASED.isEmpty() && ERASED.contains(block);
    }

    static boolean isEmpty() {
        return ERASED.isEmpty();
    }

    static List<Entry> entries() {
        return new ArrayList<>(PRIMARY);
    }

    static Block blockFor(String blockId) {
        return blocksById().get(blockId);
    }

    /**
     * Only count items that are "the block itself" (stone, a torch, a bed...). Items that merely place
     * a block, like seeds, carrots or redstone dust, do not erase their crop or wire.
     */
    static boolean isEligible(Item item, Block block) {
        if (block == Blocks.AIR) {
            return false;
        }
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        return itemId.equals(idOf(block));
    }

    // ------------------------------------------------------------------ changes

    /** Returns false if the block was already erased. */
    static boolean eraseForever(Block block, String playerName) {
        if (ERASED.contains(block)) {
            return false;
        }
        String id = idOf(block);
        PRIMARY.add(new Entry(id, playerName, System.currentTimeMillis()));
        addWithCompanions(block, id);
        Eraser.restartSweep();
        return true;
    }

    private static void addWithCompanions(Block block, String id) {
        ERASED.add(block);
        for (String companionId : companionIds(id)) {
            Block companion = blocksById().get(companionId);
            if (companion != null) {
                ERASED.add(companion);
            }
        }
    }

    private static List<String> companionIds(String id) {
        int colon = id.indexOf(':');
        String ns = id.substring(0, colon + 1);
        String path = id.substring(colon + 1);
        List<String> out = new ArrayList<>();

        if (path.equals("torch")) {
            out.add(ns + "wall_torch");
        } else if (path.endsWith("_torch")) {
            out.add(ns + cut(path, "_torch") + "_wall_torch");
        }
        if (path.endsWith("_hanging_sign")) {
            out.add(ns + cut(path, "_hanging_sign") + "_wall_hanging_sign");
        } else if (path.endsWith("_sign")) {
            out.add(ns + cut(path, "_sign") + "_wall_sign");
        }
        if (path.endsWith("_banner")) {
            out.add(ns + cut(path, "_banner") + "_wall_banner");
        }
        if (path.endsWith("_skull")) {
            out.add(ns + cut(path, "_skull") + "_wall_skull");
        }
        if (path.endsWith("_head")) {
            out.add(ns + cut(path, "_head") + "_wall_head");
        }

        switch (path) {
            case "kelp" -> out.add(ns + "kelp_plant");
            case "weeping_vines" -> out.add(ns + "weeping_vines_plant");
            case "twisting_vines" -> out.add(ns + "twisting_vines_plant");
            case "big_dripleaf" -> out.add(ns + "big_dripleaf_stem");
            case "bamboo" -> out.add(ns + "bamboo_sapling");
            case "piston", "sticky_piston" -> {
                out.add(ns + "piston_head");
                out.add(ns + "moving_piston");
            }
            case "cauldron" -> {
                out.add(ns + "water_cauldron");
                out.add(ns + "lava_cauldron");
                out.add(ns + "powder_snow_cauldron");
            }
            default -> {
            }
        }
        return out;
    }

    private static String cut(String path, String suffix) {
        return path.substring(0, path.length() - suffix.length());
    }

    // ------------------------------------------------------------------ registry helpers

    static String idOf(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    private static Map<String, Block> blocksById() {
        if (blocksById == null) {
            Map<String, Block> map = new HashMap<>();
            for (Block block : BuiltInRegistries.BLOCK) {
                map.put(idOf(block), block);
            }
            blocksById = map;
        }
        return blocksById;
    }

    // ------------------------------------------------------------------ saving / loading

    static void load(MinecraftServer server) {
        PRIMARY.clear();
        ERASED.clear();
        Eraser.reset();
        PickupDetector.reset();

        file = server.getWorldPath(LevelResource.ROOT).resolve("blockvoid.tsv");
        if (!Files.exists(file)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\t");
                Block block = blocksById().get(parts[0]);
                if (block == null) {
                    BlockVoidMod.LOGGER.warn("Block Void: unknown block '{}' in save file, skipping", parts[0]);
                    continue;
                }
                String player = parts.length > 1 ? parts[1] : "unknown";
                long time = 0L;
                if (parts.length > 2) {
                    try {
                        time = Long.parseLong(parts[2].trim());
                    } catch (NumberFormatException ignored) {
                        // keep 0
                    }
                }
                PRIMARY.add(new Entry(parts[0], player, time));
                addWithCompanions(block, parts[0]);
            }
            BlockVoidMod.LOGGER.info("Block Void: {} blocks are already erased in this world", PRIMARY.size());
        } catch (IOException | RuntimeException e) {
            BlockVoidMod.LOGGER.error("Block Void: could not read {}", file, e);
        }
    }

    static void save(MinecraftServer server) {
        saveNow();
    }

    static void saveNow() {
        if (file == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add("# Block Void: blocks erased from this world. Format: block id <TAB> player <TAB> time (ms)");
        for (Entry entry : PRIMARY) {
            lines.add(entry.blockId() + "\t" + entry.playerName() + "\t" + entry.timeMillis());
        }
        try {
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            BlockVoidMod.LOGGER.error("Block Void: could not write {}", file, e);
        }
    }
}

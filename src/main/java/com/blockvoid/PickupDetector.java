package com.blockvoid;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Works out when a player picks a block item up off the ground.
 *
 * Every tick we remember (1) how many of each block item a player carries and (2) which block items
 * were lying on the ground right next to them. If the next tick shows more of an item in the
 * inventory AND that item was on the ground beside the player, it was picked up. Crafting,
 * chests and the creative menu therefore do not count, only real pick-ups.
 */
final class PickupDetector {

    private static final class Snapshot {
        Map<Item, Integer> held = new HashMap<>();
        Set<Item> ground = new HashSet<>();
    }

    private static final Map<UUID, Snapshot> SNAPSHOTS = new HashMap<>();
    private static int tickCounter = 0;

    private PickupDetector() {
    }

    static void reset() {
        SNAPSHOTS.clear();
    }

    static void tick(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();

        for (ServerPlayer player : players) {
            Snapshot snapshot = SNAPSHOTS.get(player.getUUID());
            Map<Item, Integer> held = countBlockItems(player);

            if (snapshot == null) {
                // First time we see this player: just remember what they have, don't trigger anything.
                snapshot = new Snapshot();
                SNAPSHOTS.put(player.getUUID(), snapshot);
            } else {
                for (Map.Entry<Item, Integer> entry : held.entrySet()) {
                    Item item = entry.getKey();
                    int before = snapshot.held.getOrDefault(item, 0);
                    if (entry.getValue() > before && snapshot.ground.contains(item)) {
                        handlePickup(server, player, item);
                    }
                }
            }

            snapshot.held = held;
            snapshot.ground = nearbyGroundBlockItems(player);
        }

        if (++tickCounter % 600 == 0) {
            SNAPSHOTS.keySet().removeIf(id -> players.stream().noneMatch(p -> p.getUUID().equals(id)));
        }
    }

    private static void handlePickup(MinecraftServer server, ServerPlayer player, Item item) {
        if (!(item instanceof BlockItem blockItem)) {
            return;
        }
        Block block = blockItem.getBlock();
        if (!VoidLedger.isEligible(item, block) || VoidLedger.isErased(block)) {
            return;
        }
        if (VoidLedger.eraseForever(block, player.getName().getString())) {
            VoidLedger.saveNow();
            Announcer.announce(server, player, block);
        }
    }

    private static Map<Item, Integer> countBlockItems(ServerPlayer player) {
        Map<Item, Integer> counts = new HashMap<>();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    private static Set<Item> nearbyGroundBlockItems(ServerPlayer player) {
        Set<Item> found = new HashSet<>();
        AABB area = player.getBoundingBox().inflate(3.0, 2.0, 3.0);
        for (ItemEntity itemEntity : player.level().getEntitiesOfClass(ItemEntity.class, area)) {
            ItemStack stack = itemEntity.getItem();
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                found.add(stack.getItem());
            }
        }
        return found;
    }
}

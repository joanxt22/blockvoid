package com.blockvoid;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Block Void: every block you pick up is erased from the world forever.
 *
 * This is a server-side mod. In singleplayer it runs in the built-in server, and on LAN or a
 * dedicated server only the host needs it. Friends can join with a completely vanilla client.
 */
public class BlockVoidMod implements ModInitializer {
    public static final String MOD_ID = "blockvoid";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // The list of erased blocks is saved inside each world's folder (blockvoid.tsv).
        ServerLifecycleEvents.SERVER_STARTED.register(VoidLedger::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(VoidLedger::save);

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            PickupDetector.tick(server); // did anyone just pick up a block?
            Eraser.tick(server);         // wipe erased blocks out of every loaded chunk near players
        });

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> VoidCommands.register(dispatcher));

        LOGGER.info("Block Void loaded. Be careful what you pick up.");
    }
}

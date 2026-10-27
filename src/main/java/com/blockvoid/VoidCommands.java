package com.blockvoid;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** /blockvoid (or /blockvoid list): shows everything that has been erased so far, and who did it. */
final class VoidCommands {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private VoidCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("blockvoid")
                .executes(context -> list(context.getSource()))
                .then(Commands.literal("list").executes(context -> list(context.getSource()))));
    }

    private static int list(CommandSourceStack source) {
        List<VoidLedger.Entry> entries = VoidLedger.entries();

        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Nothing has been erased yet. Go pick something up!")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        Component header = Component.literal("Erased from the world forever (" + entries.size() + "):")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD);
        source.sendSuccess(() -> header, false);

        for (VoidLedger.Entry entry : entries) {
            Block block = VoidLedger.blockFor(entry.blockId());
            Component name = block != null ? block.getName() : Component.literal(entry.blockId());
            String when = entry.timeMillis() > 0 ? " on " + TIME_FORMAT.format(Instant.ofEpochMilli(entry.timeMillis())) : "";

            Component line = Component.empty()
                    .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.empty().append(name).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" (picked up by " + entry.playerName() + when + ")").withStyle(ChatFormatting.GRAY));
            source.sendSuccess(() -> line, false);
        }
        return entries.size();
    }
}

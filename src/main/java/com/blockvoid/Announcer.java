package com.blockvoid;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;

/** Tells everyone which block just got erased: chat message, big title and a deep rumble. */
final class Announcer {

    private Announcer() {
    }

    static void announce(MinecraftServer server, ServerPlayer picker, Block block) {
        Component blockName = styled(block.getName(), ChatFormatting.GOLD, ChatFormatting.BOLD);

        Component chat = Component.empty()
                .append(styled(Component.literal("[Block Void] "), ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD))
                .append(picker.getName())
                .append(Component.literal(" picked up ").withStyle(ChatFormatting.GRAY))
                .append(blockName)
                .append(Component.literal(". It is now erased from the world forever!").withStyle(ChatFormatting.RED));
        server.getPlayerList().broadcastSystemMessage(chat, false);

        Component title = styled(block.getName(), ChatFormatting.RED, ChatFormatting.BOLD);
        Component subtitle = Component.literal("has been erased from the world forever").withStyle(ChatFormatting.GRAY);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 60, 20));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
        }

        picker.level().playSound(null, picker.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.5F, 1.3F);
    }

    private static Component styled(Component base, ChatFormatting... formats) {
        return Component.empty().append(base).withStyle(formats);
    }
}

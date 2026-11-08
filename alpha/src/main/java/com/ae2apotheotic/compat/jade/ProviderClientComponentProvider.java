package com.ae2apotheotic.compat.jade;

import com.ae2apotheotic.AE2Apotheotic;
import com.ae2apotheotic.blockentity.InfusionProviderBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum ProviderClientComponentProvider implements IBlockComponentProvider {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(AE2Apotheotic.MODID, "provider_component");

    @Override
    public Identifier getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains("type")) {
            return;
        }

        boolean online = data.getBooleanOr("online", false);
        tooltip.add(Component.translatable("gui.ae2apotheotic.network")
            .append(": ")
            .append(online 
                ? Component.translatable("gui.ae2apotheotic.online").withStyle(ChatFormatting.GREEN)
                : Component.translatable("gui.ae2apotheotic.offline").withStyle(ChatFormatting.RED)));

        String type = data.getStringOr("type", "");
        if ("gem_provider".equals(type)) {
            boolean hasCase = data.getBooleanOr("has_case", false);
            tooltip.add(Component.literal("Gem Case: ")
                .append(hasCase
                    ? Component.literal("Attached").withStyle(ChatFormatting.GREEN)
                    : Component.literal("Not detected!").withStyle(ChatFormatting.RED)));
        } else if ("infusion_provider".equals(type)) {
            int flags = data.getIntOr("flags", 0);
            boolean hasTable = (flags & InfusionProviderBlockEntity.FLAG_TABLE) != 0;
            boolean isRaven = (flags & InfusionProviderBlockEntity.FLAG_RAVEN) != 0;
            if (!hasTable) {
                tooltip.add(Component.literal("Table: ").append(Component.literal("Not detected!").withStyle(ChatFormatting.RED)));
            } else if (isRaven) {
                tooltip.add(Component.literal("Table: ").append(Component.literal("Raven Enchanting Table").withStyle(ChatFormatting.LIGHT_PURPLE)));
            } else {
                tooltip.add(Component.literal("Table: ").append(Component.literal("Standard Table").withStyle(ChatFormatting.AQUA)));
            }

            int storedXp = data.getIntOr("stored_xp", 0);
            long networkXp = data.getLongOr("network_xp", 0L);
            tooltip.add(Component.literal("XP: ")
                .append(Component.literal(storedXp + " pts").withStyle(ChatFormatting.GREEN))
                .append(" | ME: ")
                .append(Component.literal(networkXp + " mB").withStyle(ChatFormatting.DARK_GREEN)));
        }

        int patterns = data.getIntOr("patterns", 0);
        tooltip.add(Component.literal("Patterns: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(String.valueOf(patterns)).withStyle(ChatFormatting.WHITE)));

        String problem = data.getStringOr("problem", "NONE");
        if (!problem.isEmpty() && !"NONE".equals(problem)) {
            tooltip.add(Component.literal("Problem: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(problem).withStyle(ChatFormatting.RED)));
        }
    }
}

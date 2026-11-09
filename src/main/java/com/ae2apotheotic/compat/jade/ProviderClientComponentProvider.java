package com.ae2apotheotic.compat.jade;

import com.ae2apotheotic.AE2Apotheotic;
import com.ae2apotheotic.blockentity.InfusionProviderBlockEntity;
import com.ae2apotheotic.blockentity.ProviderProblem;
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
        tooltip.add(online
            ? Component.translatable("gui.ae2apotheotic.online").withStyle(ChatFormatting.GREEN)
            : Component.translatable("gui.ae2apotheotic.offline").withStyle(ChatFormatting.RED));

        String type = data.getStringOr("type", "");
        if ("gem_provider".equals(type)) {
            boolean hasCase = data.getBooleanOr("has_case", false);
            tooltip.add(Component.translatable(hasCase
                ? "gui.ae2apotheotic.gem_case.found"
                : "gui.ae2apotheotic.gem_case.missing").withStyle(hasCase ? ChatFormatting.GREEN : ChatFormatting.RED));
        } else if ("infusion_provider".equals(type)) {
            int flags = data.getIntOr("flags", 0);
            boolean hasTable = (flags & InfusionProviderBlockEntity.FLAG_TABLE) != 0;
            boolean isRaven = (flags & InfusionProviderBlockEntity.FLAG_RAVEN) != 0;
            if (!hasTable) {
                tooltip.add(Component.translatable("gui.ae2apotheotic.table.missing").withStyle(ChatFormatting.RED));
            } else if (isRaven) {
                tooltip.add(Component.translatable("gui.ae2apotheotic.table.raven").withStyle(ChatFormatting.LIGHT_PURPLE));
            } else {
                tooltip.add(Component.translatable("gui.ae2apotheotic.table.shelves").withStyle(ChatFormatting.AQUA));
            }

            int storedXp = data.getIntOr("stored_xp", 0);
            long networkXp = data.getLongOr("network_xp", 0L);
            tooltip.add(Component.translatable("gui.ae2apotheotic.xp.jade", storedXp, networkXp)
                .withStyle(ChatFormatting.GREEN));
        }

        int patterns = data.getIntOr("patterns", 0);
        tooltip.add(Component.translatable("gui.ae2apotheotic.patterns", patterns).withStyle(ChatFormatting.GRAY));

        ProviderProblem problem = ProviderProblem.byId(data.getIntOr("problem", 0));
        if (problem != ProviderProblem.NONE) {
            tooltip.add(Component.translatable("gui.ae2apotheotic.problem_label", problem.text())
                .withStyle(ChatFormatting.GOLD));
        }
    }
}

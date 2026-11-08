package com.ae2apotheotic.compat.jade;

import com.ae2apotheotic.AE2Apotheotic;
import com.ae2apotheotic.blockentity.EncoderBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum EncoderClientComponentProvider implements IBlockComponentProvider {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(AE2Apotheotic.MODID, "encoder_component");

    @Override
    public Identifier getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        int mode = data.getIntOr("mode", 0);
        String modeName = switch (mode) {
            case EncoderBlockEntity.MODE_CRUSH -> "Crushing";
            case EncoderBlockEntity.MODE_INFUSION -> "Infusion";
            default -> "Upgrade";
        };
        tooltip.add(Component.literal("Mode: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(modeName).withStyle(ChatFormatting.AQUA)));

        boolean hasSample = data.getBooleanOr("has_sample", false);
        if (hasSample && data.contains("sample_name")) {
            String sample = data.getStringOr("sample_name", "");
            tooltip.add(Component.literal("Sample: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(sample).withStyle(ChatFormatting.WHITE)));
        } else {
            tooltip.add(Component.literal("Sample: (empty)").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}

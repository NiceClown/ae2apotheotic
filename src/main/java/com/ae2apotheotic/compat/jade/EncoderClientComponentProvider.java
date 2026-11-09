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
        String modeKey = switch (mode) {
            case EncoderBlockEntity.MODE_CRUSH -> "gui.ae2apotheotic.mode.crush";
            case EncoderBlockEntity.MODE_INFUSION -> "gui.ae2apotheotic.mode.infusion";
            default -> "gui.ae2apotheotic.mode.upgrade";
        };
        tooltip.add(Component.translatable("gui.ae2apotheotic.mode_label", Component.translatable(modeKey))
            .withStyle(ChatFormatting.GRAY));

        boolean hasSample = data.getBooleanOr("has_sample", false);
        if (hasSample && data.contains("sample_name")) {
            String sample = data.getStringOr("sample_name", "");
            tooltip.add(Component.translatable("gui.ae2apotheotic.sample_label", Component.literal(sample))
                .withStyle(ChatFormatting.WHITE));
        } else {
            tooltip.add(Component.translatable("gui.ae2apotheotic.sample_empty").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}

package com.ae2apotheotic.encoder;

import java.util.Locale;
import net.minecraft.network.chat.Component;

public enum EncoderStatus {
    OK,
    NO_SAMPLE,
    NOT_A_GEM,
    GEM_ALREADY_PERFECT,
    NO_UPGRADE_RECIPE,
    NO_GEMS,
    NO_INFUSION_RECIPE,
    STATS_OUT_OF_RANGE,
    STATS_OTHER_RECIPE,
    ETERNA_ABOVE_MAX;

    public boolean isOk() {
        return this == OK;
    }

    public Component text() {
        return Component.translatable("gui.ae2apotheotic.status." + name().toLowerCase(Locale.ROOT));
    }

    public static EncoderStatus byId(int id) {
        EncoderStatus[] values = values();
        return id >= 0 && id < values.length ? values[id] : NO_SAMPLE;
    }
}

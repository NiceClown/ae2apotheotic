package com.ae2apotheotic.blockentity;

import java.util.Locale;
import net.minecraft.network.chat.Component;

/** Why the last attempt of a provider to run a pattern failed. Shown in the provider GUI. */
public enum ProviderProblem {
    NONE,
    INVALID_INPUTS,
    NO_GEM_CASE,
    GEM_CASE_FULL,
    NO_ROOM_FOR_MATERIALS,
    UPGRADE_REJECTED,
    NO_TABLE,
    STATS_MISMATCH,
    NOT_ENOUGH_XP;

    public Component text() {
        return Component.translatable("gui.ae2apotheotic.problem." + name().toLowerCase(Locale.ROOT));
    }

    public static ProviderProblem byId(int id) {
        ProviderProblem[] values = values();
        return id >= 0 && id < values.length ? values[id] : NONE;
    }
}

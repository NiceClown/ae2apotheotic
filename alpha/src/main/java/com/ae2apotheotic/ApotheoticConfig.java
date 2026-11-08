package com.ae2apotheotic;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ApotheoticConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue INFUSION_REQUIRES_XP;
    public static final ModConfigSpec.DoubleValue INFUSION_XP_MULTIPLIER;
    public static final ModConfigSpec.IntValue XP_BUFFER_CAPACITY;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("infusion");
        INFUSION_REQUIRES_XP = builder
            .comment("If true, the Infusion Pattern Provider charges experience for every infusion, like the enchanting table does.")
            .define("requiresExperience", true);
        INFUSION_XP_MULTIPLIER = builder
            .comment("Multiplier applied to the experience cost of an infusion (the base cost matches the enchanting table).")
            .defineInRange("experienceMultiplier", 1.0D, 0.0D, 100.0D);
        XP_BUFFER_CAPACITY = builder
            .comment("How many experience points the Infusion Pattern Provider can keep in its internal buffer.")
            .defineInRange("bufferCapacity", 1_000_000, 0, 100_000_000);
        builder.pop();
        SPEC = builder.build();
    }

    private ApotheoticConfig() {}

    /** Safe to call on the client too, where the server config is not loaded. */
    public static int xpBufferCapacity() {
        try {
            return XP_BUFFER_CAPACITY.get();
        } catch (IllegalStateException e) {
            return XP_BUFFER_CAPACITY.getDefault();
        }
    }
}

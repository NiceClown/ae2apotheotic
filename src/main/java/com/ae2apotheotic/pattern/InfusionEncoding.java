package com.ae2apotheotic.pattern;

import appeng.api.stacks.AEItemKey;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * Infusion craft on an enchanting table. The eterna/quanta/arcana values are what the Enchanting Table of the Raven
 * is set to while crafting; {@link #xpCost} is informative only, the provider recalculates it from the matched recipe.
 */
public record InfusionEncoding(AEItemKey input, AEItemKey output, int outputCount, Identifier recipe, int eterna, int quanta, int arcana, int xpCost) {
    public static final Codec<InfusionEncoding> CODEC = RecordCodecBuilder.create(i -> i.group(
            AEItemKey.CODEC.fieldOf("input").forGetter(InfusionEncoding::input),
            AEItemKey.CODEC.fieldOf("output").forGetter(InfusionEncoding::output),
            Codec.INT.optionalFieldOf("output_count", 1).forGetter(InfusionEncoding::outputCount),
            Identifier.CODEC.fieldOf("recipe").forGetter(InfusionEncoding::recipe),
            Codec.intRange(0, 100).fieldOf("eterna").forGetter(InfusionEncoding::eterna),
            Codec.intRange(0, 100).fieldOf("quanta").forGetter(InfusionEncoding::quanta),
            Codec.intRange(0, 100).fieldOf("arcana").forGetter(InfusionEncoding::arcana),
            Codec.INT.optionalFieldOf("xp_cost", 0).forGetter(InfusionEncoding::xpCost))
        .apply(i, InfusionEncoding::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, InfusionEncoding> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    /** Lapis consumed by the third (infusion) slot of the enchanting table. */
    public static final int LAPIS_COST = 3;
}

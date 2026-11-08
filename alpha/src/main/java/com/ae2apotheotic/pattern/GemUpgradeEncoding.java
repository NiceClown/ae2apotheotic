package com.ae2apotheotic.pattern;

import appeng.api.stacks.AEItemKey;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Gem purity upgrade performed by a Gem Case: 2x {@link #input} + left material + right material -> 1x {@link #output}.
 */
public record GemUpgradeEncoding(AEItemKey input, AEItemKey output, MaterialChoice left, MaterialChoice right) {
    public static final Codec<GemUpgradeEncoding> CODEC = RecordCodecBuilder.create(i -> i.group(
            AEItemKey.CODEC.fieldOf("input").forGetter(GemUpgradeEncoding::input),
            AEItemKey.CODEC.fieldOf("output").forGetter(GemUpgradeEncoding::output),
            MaterialChoice.CODEC.fieldOf("left").forGetter(GemUpgradeEncoding::left),
            MaterialChoice.CODEC.fieldOf("right").forGetter(GemUpgradeEncoding::right))
        .apply(i, GemUpgradeEncoding::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, GemUpgradeEncoding> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    /** The Gem Case always merges two gems of the same purity into one. */
    public static final int GEMS_PER_UPGRADE = 2;
}

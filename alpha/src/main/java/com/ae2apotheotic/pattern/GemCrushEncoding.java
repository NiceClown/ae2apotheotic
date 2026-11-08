package com.ae2apotheotic.pattern;

import appeng.api.stacks.AEItemKey;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * Crushing gems into gem dust (what a falling anvil does in Apotheosis). One gem always gives one dust.
 */
public record GemCrushEncoding(CrushMode mode, Optional<AEItemKey> gem, Purity purity) {
    public static final Codec<GemCrushEncoding> CODEC = RecordCodecBuilder.create(i -> i.group(
            CrushMode.CODEC.fieldOf("mode").forGetter(GemCrushEncoding::mode),
            AEItemKey.CODEC.optionalFieldOf("gem").forGetter(GemCrushEncoding::gem),
            Purity.CODEC.optionalFieldOf("purity", Purity.CRACKED).forGetter(GemCrushEncoding::purity))
        .apply(i, GemCrushEncoding::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, GemCrushEncoding> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public static int dustYield(Purity purity) {
        if (purity == null) {
            return 1;
        }
        return switch (purity) {
            case CRACKED -> 1;
            case CHIPPED -> 1;
            case FLAWED -> 2;
            case NORMAL -> 3;
            case FLAWLESS -> 4;
            case PERFECT -> 5;
        };
    }

    public enum CrushMode implements StringRepresentable {
        /** Only the exact gem (type and purity) from the sample slot. */
        EXACT("exact"),
        /** Any gem type, but only of the selected purity. */
        SAME_PURITY("same_purity"),
        /** Any gem of any purity. */
        ANY("any");

        public static final Codec<CrushMode> CODEC = StringRepresentable.fromEnum(CrushMode::values);

        private final String name;

        CrushMode(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public CrushMode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public static CrushMode byId(int id) {
            CrushMode[] values = values();
            return values[Math.floorMod(id, values.length)];
        }
    }
}

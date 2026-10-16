package com.ae2apotheotic.pattern;

import appeng.api.crafting.PatternDetailsTooltip;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.ae2apotheotic.registry.ModRegistry;
import dev.shadowsoffire.apotheosis.socket.gem.UnsocketedGem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/** Any matching gem -> 1 gem dust. The candidate list is built from the gem registry when the pattern is decoded. */
public final class GemCrushPattern extends ApotheoticPattern {
    private final GemCrushEncoding encoding;
    private final int candidateCount;

    public GemCrushPattern(AEItemKey definition, Level level) {
        super(definition);
        GemCrushEncoding enc = definition.get(ModRegistry.GEM_CRUSH.get());
        if (enc == null) {
            throw new IllegalArgumentException("Item does not contain a gem crushing pattern: " + definition);
        }
        List<AEItemKey> candidates = switch (enc.mode()) {
            case EXACT -> {
                AEItemKey gem = enc.gem().orElseThrow(() -> new IllegalArgumentException("Exact crushing pattern without a gem"));
                if (!UnsocketedGem.of(gem.toStack()).isValid()) {
                    throw new IllegalArgumentException("Crushing pattern references an unknown gem");
                }
                yield List.of(gem);
            }
            case SAME_PURITY -> GemKeys.allGems(enc.purity());
            case ANY -> GemKeys.allGems(null);
        };
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("No gems match the crushing pattern");
        }
        this.encoding = enc;
        this.candidateCount = candidates.size();
        this.inputs = new IInput[] { SimpleInput.of(candidates, 1) };
        this.outputs = List.of(new GenericStack(GemKeys.dust(), GemCrushEncoding.dustYield(enc.purity())));
    }

    public GemCrushEncoding encoding() {
        return encoding;
    }

    @Override
    protected Component machineName() {
        return Component.translatable("block.ae2apotheotic.gem_pattern_provider");
    }

    @Override
    protected void addProperties(PatternDetailsTooltip tooltip) {
        Component mode = switch (encoding.mode()) {
            case EXACT -> Component.translatable("gui.ae2apotheotic.crush_mode.exact");
            case SAME_PURITY -> Component.translatable("gui.ae2apotheotic.crush_mode.same_purity", encoding.purity().toComponent());
            case ANY -> Component.translatable("gui.ae2apotheotic.crush_mode.any");
        };
        tooltip.addProperty(Component.translatable("tooltip.ae2apotheotic.crush_mode"), mode.copy().withStyle(ChatFormatting.YELLOW));
        if (candidateCount > 1) {
            tooltip.addProperty(Component.translatable("tooltip.ae2apotheotic.candidates", candidateCount));
        }
    }
}

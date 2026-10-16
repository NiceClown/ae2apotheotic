package com.ae2apotheotic.pattern;

import appeng.api.crafting.PatternDetailsTooltip;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.ae2apotheotic.registry.ModRegistry;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import dev.shadowsoffire.apotheosis.socket.gem.UnsocketedGem;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/** 2 gems of one purity + upgrade materials -> 1 gem of the next purity, crafted inside a Gem Case. */
public final class GemUpgradePattern extends ApotheoticPattern {
    private final GemUpgradeEncoding encoding;
    private final Purity fromPurity;

    public GemUpgradePattern(AEItemKey definition, Level level) {
        super(definition);
        GemUpgradeEncoding enc = definition.get(ModRegistry.GEM_UPGRADE.get());
        if (enc == null) {
            throw new IllegalArgumentException("Item does not contain a gem upgrade pattern: " + definition);
        }
        UnsocketedGem gem = UnsocketedGem.of(enc.input().toStack());
        if (!gem.isValid() || gem.isPerfect()) {
            throw new IllegalArgumentException("Gem upgrade pattern references an unknown or perfect gem");
        }
        if (enc.left().isEmpty() || enc.right().isEmpty()) {
            throw new IllegalArgumentException("Gem upgrade pattern has no materials");
        }
        this.encoding = enc;
        this.fromPurity = gem.purity();
        this.inputs = new IInput[] {
            SimpleInput.of(enc.input(), GemUpgradeEncoding.GEMS_PER_UPGRADE),
            SimpleInput.of(enc.left()),
            SimpleInput.of(enc.right())
        };
        this.outputs = List.of(new GenericStack(enc.output(), 1));
    }

    public GemUpgradeEncoding encoding() {
        return encoding;
    }

    public Purity fromPurity() {
        return fromPurity;
    }

    @Override
    protected Component machineName() {
        return Component.translatable("block.apotheosis.gem_case");
    }

    @Override
    protected void addProperties(PatternDetailsTooltip tooltip) {
        tooltip.addProperty(Component.translatable("tooltip.ae2apotheotic.purity_upgrade"),
            fromPurity.toComponent().append(Component.literal(" → ")).append(fromPurity.next().toComponent()));
        if (encoding.left().items().size() > 1 || encoding.right().items().size() > 1) {
            tooltip.addProperty(Component.translatable("tooltip.ae2apotheotic.accepts_alternatives"));
        }
    }
}

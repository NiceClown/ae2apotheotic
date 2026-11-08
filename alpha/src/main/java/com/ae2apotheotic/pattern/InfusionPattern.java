package com.ae2apotheotic.pattern;

import appeng.api.crafting.PatternDetailsTooltip;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.ae2apotheotic.registry.ModRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.Tags;

/** Infusion on an enchanting table: input + 3 lapis (+ experience taken by the provider) -> infusion result. */
public final class InfusionPattern extends ApotheoticPattern {
    private final InfusionEncoding encoding;

    public InfusionPattern(AEItemKey definition, Level level) {
        super(definition);
        InfusionEncoding enc = definition.get(ModRegistry.INFUSION.get());
        if (enc == null) {
            throw new IllegalArgumentException("Item does not contain an infusion pattern: " + definition);
        }
        this.encoding = enc;
        this.inputs = new IInput[] {
            SimpleInput.of(enc.input(), 1),
            SimpleInput.of(enchantingFuels(), InfusionEncoding.LAPIS_COST)
        };
        this.outputs = List.of(new GenericStack(enc.output(), Math.max(1, enc.outputCount())));
    }

    public InfusionEncoding encoding() {
        return encoding;
    }

    public static List<AEItemKey> enchantingFuels() {
        List<AEItemKey> fuels = new ArrayList<>();
        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(Tags.Items.ENCHANTING_FUELS)) {
            AEItemKey key = AEItemKey.of(holder.value());
            if (key != null && !fuels.contains(key)) {
                fuels.add(key);
            }
        }
        if (fuels.isEmpty()) {
            fuels.add(AEItemKey.of(Items.LAPIS_LAZULI));
        }
        return fuels;
    }

    @Override
    protected Component machineName() {
        return Component.translatable("block.apothic_enchanting.raven_enchanting_table");
    }

    @Override
    protected void addProperties(PatternDetailsTooltip tooltip) {
        tooltip.addProperty(Component.translatable("gui.ae2apotheotic.eterna"), Component.literal(String.valueOf(encoding.eterna())).withStyle(ChatFormatting.GREEN));
        tooltip.addProperty(Component.translatable("gui.ae2apotheotic.quanta"), Component.literal(String.valueOf(encoding.quanta())).withStyle(ChatFormatting.RED));
        tooltip.addProperty(Component.translatable("gui.ae2apotheotic.arcana"), Component.literal(String.valueOf(encoding.arcana())).withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.addProperty(Component.translatable("tooltip.ae2apotheotic.xp_cost"), Component.translatable("gui.ae2apotheotic.xp_points", encoding.xpCost()).withStyle(ChatFormatting.YELLOW));
        tooltip.addProperty(Component.translatable("tooltip.ae2apotheotic.recipe"), Component.literal(encoding.recipe().toString()).withStyle(ChatFormatting.DARK_GRAY));
    }
}

package com.ae2apotheotic.pattern;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsTooltip;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Base for patterns that can only be executed by the Apotheotic pattern providers. They never push their inputs
 * into an adjacent inventory, so a regular ME Pattern Provider holding one of them simply ignores it.
 */
public abstract class ApotheoticPattern implements IPatternDetails {
    protected final AEItemKey definition;
    protected IInput[] inputs = new IInput[0];
    protected List<GenericStack> outputs = List.of();

    protected ApotheoticPattern(AEItemKey definition) {
        this.definition = definition;
    }

    /** Translation key of the machine that has to be next to the provider. */
    protected abstract Component machineName();

    protected abstract void addProperties(PatternDetailsTooltip tooltip);

    @Override
    public final AEItemKey getDefinition() {
        return definition;
    }

    @Override
    public final IInput[] getInputs() {
        return inputs;
    }

    @Override
    public final List<GenericStack> getOutputs() {
        return outputs;
    }

    @Override
    public final boolean supportsPushInputsToExternalInventory() {
        return false;
    }

    @Override
    public PatternDetailsTooltip getTooltip(Level level, TooltipFlag flags) {
        PatternDetailsTooltip tooltip = new PatternDetailsTooltip(PatternDetailsTooltip.OUTPUT_TEXT_PRODUCES);
        tooltip.addInputsAndOutputs(this);
        tooltip.addProperty(Component.translatable("tooltip.ae2apotheotic.machine"), machineName().copy().withStyle(ChatFormatting.AQUA));
        addProperties(tooltip);
        return tooltip;
    }

    @Override
    public int hashCode() {
        return definition.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj.getClass() == getClass() && ((ApotheoticPattern) obj).definition.equals(definition);
    }
}

package com.ae2apotheotic.util;

import dev.shadowsoffire.apothic_enchanting.table.infusion.InfusionRecipe;
import dev.shadowsoffire.apothic_enchanting.util.MiscUtil;
import dev.shadowsoffire.placebo.util.EnchantmentUtils;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

public final class XpHelper {
    /** Common conversion rate of liquid experience (used by Just Dire Things and most other mods). */
    public static final int MB_PER_XP = 20;

    private XpHelper() {}

    public static boolean isXpFluid(FluidResource resource) {
        return !resource.isEmpty() && resource.typeHolder().is(Tags.Fluids.EXPERIENCE);
    }

    /** Enchanting table level that an infusion is performed at (the table caps the third slot at this level). */
    public static int infusionLevel(InfusionRecipe recipe) {
        return Math.max((int) Math.ceil(recipe.getRequirements().eterna()), 3);
    }

    /** Experience points the enchanting table charges for an infusion (third slot). */
    public static int infusionXpCost(InfusionRecipe recipe) {
        return Math.max(0, MiscUtil.getExpCostForSlot(infusionLevel(recipe), 2));
    }

    public static int totalExperience(Player player) {
        return EnchantmentUtils.getExperience(player);
    }

    /** Points needed to drop the player by (at most) one level: the current progress, or one full level. */
    public static int pointsForOneLevel(Player player) {
        int progress = (int) (player.experienceProgress * player.getXpNeededForNextLevel());
        if (progress > 0) {
            return progress;
        }
        return player.experienceLevel > 0 ? EnchantmentUtils.getExperienceForLevel(player.experienceLevel) : 0;
    }
}

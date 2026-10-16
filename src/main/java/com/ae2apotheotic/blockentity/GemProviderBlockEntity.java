package com.ae2apotheotic.blockentity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.ae2apotheotic.pattern.GemCrushEncoding;
import com.ae2apotheotic.pattern.GemCrushPattern;
import com.ae2apotheotic.pattern.GemKeys;
import com.ae2apotheotic.pattern.GemUpgradeEncoding;
import com.ae2apotheotic.pattern.GemUpgradePattern;
import com.ae2apotheotic.registry.ModRegistry;
import dev.shadowsoffire.apotheosis.Apoth;
import dev.shadowsoffire.apotheosis.socket.gem.Gem;
import dev.shadowsoffire.apotheosis.socket.gem.GemItem;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import dev.shadowsoffire.apotheosis.socket.gem.UnsocketedGem;
import dev.shadowsoffire.apotheosis.socket.gem.cutting.GemCuttingMenu;
import dev.shadowsoffire.apotheosis.socket.gem.cutting.GemCuttingRecipe;
import dev.shadowsoffire.apotheosis.socket.gem.cutting.PurityUpgradeRecipe;
import dev.shadowsoffire.apotheosis.socket.gem.storage.GemCaseTile;
import dev.shadowsoffire.placebo.cap.InternalItemHandler;
import dev.shadowsoffire.placebo.dynreg.DynamicHolder;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Pattern provider that works with an adjacent Apotheosis Gem Case.
 * <ul>
 * <li>Gem upgrade patterns: the two gems and the materials are put into the Gem Case, the Gem Case performs its own
 * purity upgrade, and the upgraded gem is taken out again and sent to the ME network.</li>
 * <li>Gem crushing patterns: gems are smashed into gem dust (one dust per gem, like under a falling anvil). This does
 * not need the Gem Case.</li>
 * </ul>
 */
public class GemProviderBlockEntity extends AbstractProviderBlockEntity {
    public static final int FLAG_GEM_CASE = 1;
    public static final int PATTERN_SLOTS = 27;

    public GemProviderBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.GEM_PROVIDER_BE.get(), pos, state, PATTERN_SLOTS);
    }

    @Override
    public boolean acceptsPatternItem(ItemStack stack) {
        return stack.is(ModRegistry.GEM_UPGRADE_PATTERN.get()) || stack.is(ModRegistry.GEM_CRUSH_PATTERN.get());
    }

    @Override
    protected boolean acceptsPattern(IPatternDetails details) {
        return details instanceof GemUpgradePattern || details instanceof GemCrushPattern;
    }

    @Override
    public int machineFlags() {
        return findGemCase() != null ? FLAG_GEM_CASE : 0;
    }

    public @Nullable GemCaseTile findGemCase() {
        if (level == null) {
            return null;
        }
        for (Direction dir : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(dir)) instanceof GemCaseTile gemCase && !gemCase.isRemoved()) {
                return gemCase;
            }
        }
        return null;
    }

    @Override
    protected @Nullable List<GenericStack> process(ServerLevel level, IPatternDetails details, KeyCounter[] inputs) {
        if (details instanceof GemCrushPattern) {
            return crush(inputs);
        }
        if (details instanceof GemUpgradePattern upgrade) {
            return upgrade(level, upgrade, inputs);
        }
        return fail(ProviderProblem.INVALID_INPUTS);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Crushing

    private @Nullable List<GenericStack> crush(KeyCounter[] inputs) {
        long totalDust = 0;
        for (KeyCounter counter : inputs) {
            for (Object2LongMap.Entry<AEKey> entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey key) || key.getItem() != Apoth.Items.GEM.value()) {
                    return fail(ProviderProblem.INVALID_INPUTS);
                }
                UnsocketedGem gem = UnsocketedGem.of(key.toStack());
                if (!gem.isValid()) {
                    return fail(ProviderProblem.INVALID_INPUTS);
                }
                int yield = GemCrushEncoding.dustYield(gem.purity());
                totalDust += entry.getLongValue() * yield;
            }
        }
        if (totalDust <= 0) {
            return fail(ProviderProblem.INVALID_INPUTS);
        }
        playSoundThrottled(worldPosition, SoundEvents.ANVIL_LAND, 0.25F, 1.4F);
        return List.of(new GenericStack(GemKeys.dust(), totalDust));
    }

    // ------------------------------------------------------------------------------------------------------------
    // Purity upgrade through the Gem Case

    private record Material(ItemResource resource, int amount) {
        ItemStack stack() {
            return resource.toStack(amount);
        }
    }

    private @Nullable List<GenericStack> upgrade(ServerLevel level, GemUpgradePattern pattern, KeyCounter[] inputs) {
        GemCaseTile gemCase = findGemCase();
        if (gemCase == null) {
            return fail(ProviderProblem.NO_GEM_CASE);
        }
        GemUpgradeEncoding enc = pattern.encoding();
        UnsocketedGem gem = UnsocketedGem.of(enc.input().toStack());
        if (!gem.isValid() || gem.isPerfect() || inputs.length < 3) {
            return fail(ProviderProblem.INVALID_INPUTS);
        }
        DynamicHolder<Gem> holder = gem.gem();
        Purity from = gem.purity();
        Purity to = from.next();
        if (inputs[0].get(enc.input()) < GemUpgradeEncoding.GEMS_PER_UPGRADE) {
            return fail(ProviderProblem.INVALID_INPUTS);
        }
        Material left = singleMaterial(inputs[1]);
        Material right = singleMaterial(inputs[2]);
        if (left == null || right == null) {
            return fail(ProviderProblem.INVALID_INPUTS);
        }

        long capacity = gemCase.getItemHandler(null).getCapacityAsLong(0, ItemResource.EMPTY);
        if ((long) gemCase.getCount(holder, from) + GemUpgradeEncoding.GEMS_PER_UPGRADE > capacity || (long) gemCase.getCount(holder, to) + 1 > capacity) {
            return fail(ProviderProblem.GEM_CASE_FULL);
        }
        if (!anyRecipeAccepts(level, from, left, right)) {
            return fail(ProviderProblem.UPGRADE_REJECTED);
        }

        // Put the materials into the Gem Case. Each material goes into one slot as a whole, because the Gem Case
        // looks for a single slot that holds the full amount required by the recipe.
        InternalItemHandler mats = gemCase.getUpgradeMaterials();
        try (Transaction tx = Transaction.openRoot()) {
            for (Material material : List.of(left, right)) {
                int slot = findSlotFor(mats, material);
                if (slot < 0 || mats.insertInternal(slot, material.resource(), material.amount(), tx) < material.amount()) {
                    return fail(ProviderProblem.NO_ROOM_FOR_MATERIALS);
                }
            }
            tx.commit();
        }

        gemCase.depositGem(GemItem.createStack(holder.get(), from, GemUpgradeEncoding.GEMS_PER_UPGRADE));
        if (!gemCase.upgradeGem(holder, to)) {
            // Should not happen because we checked the recipes, but never lose anything: undo our changes.
            gemCase.extractGem(holder, from, GemUpgradeEncoding.GEMS_PER_UPGRADE);
            removeMaterial(mats, left);
            removeMaterial(mats, right);
            return fail(ProviderProblem.UPGRADE_REJECTED);
        }
        ItemStack result = gemCase.extractGem(holder, to, 1);
        playSoundThrottled(gemCase.getBlockPos(), SoundEvents.AMETHYST_BLOCK_HIT, 1.0F, 1.5F);
        if (result.isEmpty()) {
            return List.of();
        }
        return List.of(new GenericStack(AEItemKey.of(result), result.getCount()));
    }

    private static @Nullable Material singleMaterial(KeyCounter counter) {
        Material found = null;
        for (Object2LongMap.Entry<AEKey> entry : counter) {
            if (found != null || !(entry.getKey() instanceof AEItemKey key) || entry.getLongValue() > Integer.MAX_VALUE) {
                return null;
            }
            found = new Material(ItemResource.of(key.toStack()), (int) entry.getLongValue());
        }
        return found;
    }

    private static boolean anyRecipeAccepts(ServerLevel level, Purity from, Material left, Material right) {
        ItemStack leftStack = left.stack();
        ItemStack rightStack = right.stack();
        for (RecipeHolder<GemCuttingRecipe> holder : GemCuttingMenu.getRecipes(level)) {
            if (holder.value() instanceof PurityUpgradeRecipe recipe && recipe.purity() == from
                && GemCuttingRecipe.anyMatch(leftStack, recipe.left()) && GemCuttingRecipe.anyMatch(rightStack, recipe.right())) {
                return true;
            }
        }
        return false;
    }

    /** A slot that already holds this material and has room for all of it, otherwise an empty slot. */
    private static int findSlotFor(InternalItemHandler mats, Material material) {
        int empty = -1;
        for (int i = 0; i < mats.size(); i++) {
            ItemResource present = mats.getResource(i);
            long capacity = mats.getCapacityAsLong(i, material.resource());
            if (present.isEmpty()) {
                if (empty < 0 && capacity >= material.amount()) {
                    empty = i;
                }
            } else if (present.equals(material.resource()) && capacity - mats.getAmountAsLong(i) >= material.amount()) {
                return i;
            }
        }
        return empty;
    }

    private static void removeMaterial(InternalItemHandler mats, Material material) {
        try (Transaction tx = Transaction.openRoot()) {
            int remaining = material.amount();
            for (int i = 0; i < mats.size() && remaining > 0; i++) {
                if (mats.getResource(i).equals(material.resource())) {
                    remaining -= mats.extractInternal(i, material.resource(), remaining, tx);
                }
            }
            tx.commit();
        }
    }
}

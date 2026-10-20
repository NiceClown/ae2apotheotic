package com.ae2apotheotic.blockentity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.ae2apotheotic.menu.GemProviderMenu;
import com.ae2apotheotic.pattern.GemCrushEncoding;
import com.ae2apotheotic.pattern.GemCrushPattern;
import com.ae2apotheotic.pattern.GemKeys;
import com.ae2apotheotic.pattern.GemUpgradeEncoding;
import com.ae2apotheotic.pattern.GemUpgradePattern;
import com.ae2apotheotic.registry.ModRegistry;
import dev.shadowsoffire.apotheosis.Apoth;
import dev.shadowsoffire.apotheosis.socket.gem.Gem;
import dev.shadowsoffire.apotheosis.socket.gem.GemItem;
import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import dev.shadowsoffire.apotheosis.socket.gem.UnsocketedGem;
import dev.shadowsoffire.apotheosis.socket.gem.cutting.GemCuttingMenu;
import dev.shadowsoffire.apotheosis.socket.gem.cutting.GemCuttingRecipe;
import dev.shadowsoffire.apotheosis.socket.gem.cutting.PurityUpgradeRecipe;
import dev.shadowsoffire.apotheosis.socket.gem.storage.GemCaseTile;
import dev.shadowsoffire.placebo.cap.InternalItemHandler;
import dev.shadowsoffire.placebo.dynreg.DynamicHolder;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Pattern provider that works with an adjacent Apotheosis Gem Case.
 * <p>
 * Patterns are stored in categories: one per gem (keyed by the gem id, so adding or removing gems never moves
 * patterns around) with a slot for each purity upgrade step plus one slot for crushing exactly that gem, and a
 * "bulk crushing" category with {@link #BULK_SLOTS} free slots for any crushing pattern.
 * <p>
 * Because of this layout the provider is not listed in the ME Pattern Access Terminal.
 */
public class GemProviderBlockEntity extends AbstractProviderBlockEntity {
    public static final int FLAG_GEM_CASE = 1;
    /** Tiers 0..4 are the upgrades Cracked->Chipped ... Flawless->Perfect, tier 5 crushes this gem. */
    public static final int SLOTS_PER_GEM = 6;
    public static final int CRUSH_TIER = 5;
    public static final int BULK_SLOTS = 27;
    private static final int STORAGE_VERSION = 2;

    /** Gem id -> SLOTS_PER_GEM pattern stacks. */
    private final Map<Identifier, ItemStack[]> gemPatterns = new TreeMap<>();
    /** Patterns from an older save that still have to be sorted in (done on the first server tick). */
    private final List<ItemStack> pendingMigration = new ArrayList<>();
    /** Last category opened in the GUI: a gem id, or null for bulk crushing. */
    private @Nullable Identifier lastCategory;

    public GemProviderBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.GEM_PROVIDER_BE.get(), pos, state, BULK_SLOTS);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Where a pattern belongs

    /** Gem category + tier a pattern belongs to; null if it only fits into bulk crushing (or nowhere). */
    public record Route(Identifier gem, int tier) {}

    public static @Nullable Identifier gemId(Gem gem) {
        return GemRegistry.INSTANCE.getKey(gem);
    }

    public static @Nullable Route routeOf(ItemStack stack) {
        if (stack.is(ModRegistry.GEM_UPGRADE_PATTERN.get())) {
            GemUpgradeEncoding enc = stack.get(ModRegistry.GEM_UPGRADE.get());
            if (enc == null) {
                return null;
            }
            UnsocketedGem gem = UnsocketedGem.of(enc.input().toStack());
            if (!gem.isValid() || gem.isPerfect()) {
                return null;
            }
            Identifier id = gemId(gem.gem().get());
            return id == null ? null : new Route(id, gem.purity().ordinal());
        }
        if (stack.is(ModRegistry.GEM_CRUSH_PATTERN.get())) {
            GemCrushEncoding enc = stack.get(ModRegistry.GEM_CRUSH.get());
            if (enc == null || enc.mode() != GemCrushEncoding.CrushMode.EXACT || enc.gem().isEmpty()) {
                return null;
            }
            UnsocketedGem gem = UnsocketedGem.of(enc.gem().get().toStack());
            if (!gem.isValid()) {
                return null;
            }
            Identifier id = gemId(gem.gem().get());
            return id == null ? null : new Route(id, CRUSH_TIER);
        }
        return null;
    }

    public static boolean isBulkPattern(ItemStack stack) {
        return stack.is(ModRegistry.GEM_CRUSH_PATTERN.get()) && stack.get(ModRegistry.GEM_CRUSH.get()) != null;
    }

    /** Whether the tier slot exists for this gem (gems with a minimum purity have no lower upgrade steps). */
    public static boolean tierExists(@Nullable Gem gem, int tier) {
        if (tier == CRUSH_TIER) {
            return true;
        }
        return gem == null || Purity.values()[tier].isAtLeast(gem.getMinPurity());
    }

    public static boolean fitsGemSlot(Identifier gem, int tier, ItemStack stack) {
        Route route = routeOf(stack);
        return route != null && route.gem().equals(gem) && route.tier() == tier;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Storage access

    public ItemStack getGemPattern(Identifier gem, int tier) {
        ItemStack[] stacks = gemPatterns.get(gem);
        return stacks == null ? ItemStack.EMPTY : stacks[tier];
    }

    public void setGemPattern(Identifier gem, int tier, ItemStack stack) {
        ItemStack[] stacks = gemPatterns.computeIfAbsent(gem, g -> emptyRow());
        stacks[tier] = stack;
        if (Arrays.stream(stacks).allMatch(ItemStack::isEmpty)) {
            gemPatterns.remove(gem);
        }
        markPatternsChanged();
    }

    /** Bit {@code tier} is set when that slot of the gem holds a pattern. */
    public int patternMask(Identifier gem) {
        ItemStack[] stacks = gemPatterns.get(gem);
        int mask = 0;
        if (stacks != null) {
            for (int tier = 0; tier < SLOTS_PER_GEM; tier++) {
                if (!stacks[tier].isEmpty()) {
                    mask |= 1 << tier;
                }
            }
        }
        return mask;
    }

    public InternalInventory getBulkInventory() {
        return patternInventory;
    }

    private static ItemStack[] emptyRow() {
        ItemStack[] row = new ItemStack[SLOTS_PER_GEM];
        Arrays.fill(row, ItemStack.EMPTY);
        return row;
    }

    /**
     * Puts a single pattern where it belongs: its gem tier slot, or for crushing patterns the first free bulk slot.
     * The result tells where it went: a gem id, or null for the bulk crushing category.
     */
    public record InsertResult(boolean inserted, @Nullable Identifier category) {}

    public InsertResult insertSorted(ItemStack pattern) {
        Route route = routeOf(pattern);
        if (route != null && getGemPattern(route.gem(), route.tier()).isEmpty()) {
            setGemPattern(route.gem(), route.tier(), pattern.copyWithCount(1));
            return new InsertResult(true, route.gem());
        }
        if (isBulkPattern(pattern)) {
            for (int i = 0; i < patternInventory.size(); i++) {
                if (patternInventory.getStackInSlot(i).isEmpty()) {
                    patternInventory.setItemDirect(i, pattern.copyWithCount(1));
                    return new InsertResult(true, null);
                }
            }
        }
        return new InsertResult(false, route == null ? null : route.gem());
    }

    /** Category ids shown in the GUI: every registered gem (sorted) plus gems that only exist in this provider. */
    public List<Identifier> gemCategories() {
        Set<Identifier> ids = new LinkedHashSet<>();
        for (Gem gem : GemKeys.sortedGems()) {
            Identifier id = gemId(gem);
            if (id != null) {
                ids.add(id);
            }
        }
        ids.addAll(gemPatterns.keySet());
        return List.copyOf(ids);
    }

    public @Nullable Identifier getLastCategory() {
        return lastCategory;
    }

    public void setLastCategory(@Nullable Identifier category) {
        this.lastCategory = category;
        setChanged();
    }

    // ------------------------------------------------------------------------------------------------------------
    // Provider plumbing

    @Override
    public boolean acceptsPatternItem(ItemStack stack) {
        return stack.is(ModRegistry.GEM_UPGRADE_PATTERN.get()) || stack.is(ModRegistry.GEM_CRUSH_PATTERN.get());
    }

    @Override
    public boolean acceptsPatternInSlot(int slot, ItemStack stack) {
        // patternInventory is the bulk crushing category
        return isBulkPattern(stack);
    }

    @Override
    protected boolean acceptsPattern(IPatternDetails details) {
        return details instanceof GemUpgradePattern || details instanceof GemCrushPattern;
    }

    @Override
    protected String patternInventoryKey() {
        return "bulk_crush";
    }

    @Override
    protected void collectPatternStacks(List<ItemStack> out) {
        super.collectPatternStacks(out);
        for (ItemStack[] stacks : gemPatterns.values()) {
            for (ItemStack stack : stacks) {
                if (!stack.isEmpty()) {
                    out.add(stack);
                }
            }
        }
    }

    @Override
    protected void addExtraDrops(List<ItemStack> drops) {
        for (ItemStack[] stacks : gemPatterns.values()) {
            for (ItemStack stack : stacks) {
                if (!stack.isEmpty()) {
                    drops.add(stack.copy());
                }
            }
        }
        drops.addAll(pendingMigration);
        gemPatterns.clear();
        pendingMigration.clear();
    }

    @Override
    protected void onFirstServerTick() {
        if (pendingMigration.isEmpty() || level == null) {
            return;
        }
        for (ItemStack stack : pendingMigration) {
            if (!insertSorted(stack).inserted()) {
                Block.popResource(level, worldPosition, stack);
            }
        }
        pendingMigration.clear();
        setChanged();
    }

    @Override
    public boolean isVisibleInTerminal() {
        return false;
    }

    @Override
    public void writeMenuData(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
        List<Identifier> categories = gemCategories();
        buf.writeVarInt(categories.size());
        for (Identifier id : categories) {
            buf.writeIdentifier(id);
        }
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new GemProviderMenu(containerId, inventory, this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("storage_version", STORAGE_VERSION);
        ValueOutput.ValueOutputList list = output.childrenList("gem_patterns");
        for (Map.Entry<Identifier, ItemStack[]> entry : gemPatterns.entrySet()) {
            ValueOutput child = list.addChild();
            child.putString("gem", entry.getKey().toString());
            for (int tier = 0; tier < SLOTS_PER_GEM; tier++) {
                if (!entry.getValue()[tier].isEmpty()) {
                    child.store("tier" + tier, ItemStack.CODEC, entry.getValue()[tier]);
                }
            }
        }
        if (!pendingMigration.isEmpty()) {
            output.store("pending", ItemStack.CODEC.listOf(), List.copyOf(pendingMigration));
        }
        if (lastCategory != null) {
            output.putString("last_category", lastCategory.toString());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        gemPatterns.clear();
        pendingMigration.clear();
        for (ValueInput child : input.childrenListOrEmpty("gem_patterns")) {
            Identifier id = Identifier.tryParse(child.getStringOr("gem", ""));
            if (id == null) {
                continue;
            }
            ItemStack[] row = emptyRow();
            for (int tier = 0; tier < SLOTS_PER_GEM; tier++) {
                row[tier] = child.read("tier" + tier, ItemStack.CODEC).orElse(ItemStack.EMPTY);
            }
            gemPatterns.put(id, row);
        }
        input.read("pending", ItemStack.CODEC.listOf()).ifPresent(pendingMigration::addAll);
        if (input.getIntOr("storage_version", 1) < STORAGE_VERSION) {
            // Older versions kept everything in one "patterns" slot list (9, 27 or 64*6 slots). Sort it in later.
            for (ValueInput entry : input.childrenListOrEmpty("patterns")) {
                entry.read(ItemStack.MAP_CODEC).filter(s -> !s.isEmpty()).ifPresent(pendingMigration::add);
            }
        }
        lastCategory = input.getString("last_category").map(Identifier::tryParse).orElse(null);
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

package com.ae2apotheotic.blockentity;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import com.ae2apotheotic.ApotheoticConfig;
import com.ae2apotheotic.pattern.InfusionEncoding;
import com.ae2apotheotic.pattern.InfusionPattern;
import com.ae2apotheotic.registry.ModRegistry;
import com.ae2apotheotic.util.XpHelper;
import dev.shadowsoffire.apothic_enchanting.Ench;
import dev.shadowsoffire.apothic_enchanting.table.EnchantmentTableStats;
import dev.shadowsoffire.apothic_enchanting.table.infusion.InfusionRecipe;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * Pattern provider that performs infusion crafts on an adjacent enchanting table.
 * <p>
 * With an Enchanting Table of the Raven the eterna/quanta/arcana written into the pattern are used; with any other
 * Apothic enchanting table the stats are read from the shelves around it, exactly like the table does.
 * <p>
 * Experience: the cost is computed from the matched recipe the same way the enchanting table computes it. It is paid
 * from the internal buffer and, when that is not enough, pulled as liquid experience ({@code c:experience}, 20 mB per
 * point) from adjacent tanks such as the Just Dire Things Experience Holder.
 */
public class InfusionProviderBlockEntity extends AbstractProviderBlockEntity {
    public static final int FLAG_TABLE = 1;
    public static final int FLAG_RAVEN = 2;

    private long storedXpMb;
    private final XpSink xpSink = new XpSink();

    public static final int PATTERN_SLOTS = 9;

    public InfusionProviderBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.INFUSION_PROVIDER_BE.get(), pos, state, PATTERN_SLOTS);
    }

    @Override
    public boolean acceptsPatternItem(ItemStack stack) {
        return stack.is(ModRegistry.INFUSION_PATTERN.get());
    }

    @Override
    protected boolean acceptsPattern(IPatternDetails details) {
        return details instanceof InfusionPattern;
    }

    private record Table(BlockPos pos, boolean raven) {}

    private @Nullable Table findTable() {
        if (level == null) {
            return null;
        }
        Table fallback = null;
        for (Direction dir : Direction.values()) {
            BlockPos pos = worldPosition.relative(dir);
            BlockState state = level.getBlockState(pos);
            if (state.is(Ench.Blocks.RAVEN_ENCHANTING_TABLE)) {
                return new Table(pos, true);
            }
            if (fallback == null && state.getBlock() instanceof EnchantingTableBlock) {
                fallback = new Table(pos, false);
            }
        }
        return fallback;
    }

    @Override
    public int machineFlags() {
        Table table = findTable();
        if (table == null) {
            return 0;
        }
        return FLAG_TABLE | (table.raven() ? FLAG_RAVEN : 0);
    }

    @Override
    protected @Nullable List<GenericStack> process(ServerLevel level, IPatternDetails details, KeyCounter[] inputs) {
        if (!(details instanceof InfusionPattern pattern) || inputs.length < 2) {
            return fail(ProviderProblem.INVALID_INPUTS);
        }
        Table table = findTable();
        if (table == null) {
            return fail(ProviderProblem.NO_TABLE);
        }
        InfusionEncoding enc = pattern.encoding();
        if (inputs[0].get(enc.input()) < 1 || totalAmount(inputs[1]) < InfusionEncoding.LAPIS_COST) {
            return fail(ProviderProblem.INVALID_INPUTS);
        }

        float eterna;
        float quanta;
        float arcana;
        if (table.raven()) {
            eterna = enc.eterna();
            quanta = enc.quanta();
            arcana = enc.arcana();
        } else {
            EnchantmentTableStats stats = EnchantmentTableStats.gatherStats(level, table.pos());
            eterna = stats.tableEterna();
            quanta = stats.quanta();
            arcana = stats.arcana();
        }

        ItemStack input = enc.input().toStack(1);
        InfusionRecipe recipe = InfusionRecipe.findMatch(level, input, eterna, quanta, arcana);
        if (recipe == null) {
            return fail(ProviderProblem.STATS_MISMATCH);
        }
        ItemStack result = recipe.assemble(input.copy(), eterna, quanta, arcana);
        if (result.isEmpty() || !enc.output().matches(result)) {
            return fail(ProviderProblem.STATS_MISMATCH);
        }
        int xpCost = requiredXp(recipe);
        if (xpCost > 0 && !consumeXp(xpCost)) {
            return fail(ProviderProblem.NOT_ENOUGH_XP);
        }
        playSoundThrottled(table.pos(), SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F, 1.0F);
        return List.of(new GenericStack(AEItemKey.of(result), result.getCount()));
    }

    private static long totalAmount(KeyCounter counter) {
        long total = 0;
        for (Object2LongMap.Entry<AEKey> entry : counter) {
            total += entry.getLongValue();
        }
        return total;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Experience

    public static int requiredXp(InfusionRecipe recipe) {
        if (!ApotheoticConfig.INFUSION_REQUIRES_XP.get()) {
            return 0;
        }
        return (int) Math.ceil(XpHelper.infusionXpCost(recipe) * ApotheoticConfig.INFUSION_XP_MULTIPLIER.get());
    }

    private static long capacityMb() {
        return (long) ApotheoticConfig.xpBufferCapacity() * XpHelper.MB_PER_XP;
    }

    public ResourceHandler<FluidResource> getXpSink() {
        return xpSink;
    }

    public int getStoredXp() {
        return (int) Math.min(Integer.MAX_VALUE, storedXpMb / XpHelper.MB_PER_XP);
    }

    /** Liquid experience (mB) available in the connected ME network. */
    public long getNetworkXpMb() {
        IGrid grid = mainNode.getGrid();
        if (grid == null) {
            return 0;
        }
        MEStorage storage = grid.getStorageService().getInventory();
        KeyCounter available = storage.getAvailableStacks();
        long mb = 0;
        for (Object2LongMap.Entry<AEKey> entry : available) {
            if (entry.getKey() instanceof AEFluidKey fluidKey && XpHelper.isXpFluid(fluidKey.toResource())) {
                mb += entry.getLongValue();
            }
        }
        return mb;
    }

    /** Liquid experience (mB) available in adjacent physical tanks. */
    public long getNearbyTankMb() {
        if (level == null) {
            return 0;
        }
        long mb = 0;
        for (Direction dir : Direction.values()) {
            ResourceHandler<FluidResource> handler = level.getCapability(Capabilities.Fluid.BLOCK, worldPosition.relative(dir), dir.getOpposite());
            if (handler == null || handler == xpSink) {
                continue;
            }
            for (int i = 0; i < handler.size(); i++) {
                FluidResource resource = handler.getResource(i);
                if (XpHelper.isXpFluid(resource)) {
                    mb += handler.getAmountAsLong(i);
                }
            }
        }
        return mb;
    }

    /** Total external experience points available to the provider (ME network first, then adjacent tanks). */
    public int getNearbyXp() {
        long totalMb = getNetworkXpMb() + getNearbyTankMb();
        return (int) Math.min(Integer.MAX_VALUE, totalMb / XpHelper.MB_PER_XP);
    }

    /** Extracts liquid experience directly from the ME network. */
    private long pullFromNetwork(long maxMb, boolean simulate) {
        IGrid grid = mainNode.getGrid();
        if (grid == null || maxMb <= 0) {
            return 0;
        }
        MEStorage storage = grid.getStorageService().getInventory();
        KeyCounter available = storage.getAvailableStacks();
        long pulled = 0;
        Actionable actionable = simulate ? Actionable.SIMULATE : Actionable.MODULATE;
        for (Object2LongMap.Entry<AEKey> entry : available) {
            if (pulled >= maxMb) {
                break;
            }
            if (entry.getKey() instanceof AEFluidKey fluidKey && XpHelper.isXpFluid(fluidKey.toResource())) {
                long want = maxMb - pulled;
                long extracted = StorageHelper.poweredExtraction(
                    grid.getEnergyService(), storage, fluidKey, want, actionSource, actionable
                );
                pulled += extracted;
            }
        }
        return pulled;
    }

    /**
     * Takes the required experience.
     * Order of priority:
     * 1. ME Network fluid storage (FIRST priority if present in network)
     * 2. Internal buffer (storedXpMb)
     * 3. Adjacent tanks (e.g. Just Dire Things Experience Holder)
     */
    private boolean consumeXp(int points) {
        long needMb = (long) points * XpHelper.MB_PER_XP;
        if (needMb <= 0) {
            return true;
        }

        long netSim = pullFromNetwork(needMb, true);
        long bufferAvail = storedXpMb;
        long neighborsAvail = getNearbyTankMb();

        if (netSim + bufferAvail + neighborsAvail < needMb) {
            return false;
        }

        // 1. ME Network FIRST
        if (netSim > 0) {
            long wantNet = Math.min(netSim, needMb);
            long pulled = pullFromNetwork(wantNet, false);
            needMb -= pulled;
            if (needMb <= 0) {
                return true;
            }
        }

        // 2. Next: internal buffer
        long fromBuffer = Math.min(storedXpMb, needMb);
        long deficitAfterBuffer = needMb - fromBuffer;

        if (deficitAfterBuffer <= 0) {
            storedXpMb -= fromBuffer;
            setChanged();
            return true;
        }

        // 3. Next: adjacent tanks
        long request = (deficitAfterBuffer + XpHelper.MB_PER_XP - 1) / XpHelper.MB_PER_XP * XpHelper.MB_PER_XP;
        try (Transaction tx = Transaction.openRoot()) {
            long pulled = pullFromNeighbors(request, tx);
            if (pulled < deficitAfterBuffer) {
                return false;
            }
            tx.commit();
            storedXpMb = storedXpMb - fromBuffer + pulled - deficitAfterBuffer;
        }
        setChanged();
        return true;
    }

    private long pullFromNeighbors(long maxMb, TransactionContext tx) {
        long pulled = 0;
        for (Direction dir : Direction.values()) {
            if (pulled >= maxMb) {
                break;
            }
            ResourceHandler<FluidResource> handler = level.getCapability(Capabilities.Fluid.BLOCK, worldPosition.relative(dir), dir.getOpposite());
            if (handler == null || handler == xpSink) {
                continue;
            }
            for (int i = 0; i < handler.size() && pulled < maxMb; i++) {
                FluidResource resource = handler.getResource(i);
                if (!XpHelper.isXpFluid(resource)) {
                    continue;
                }
                int want = (int) Math.min(Integer.MAX_VALUE, maxMb - pulled);
                pulled += handler.extract(i, resource, want, tx);
            }
        }
        return pulled;
    }

    @Override
    public void handleButton(Player player, int buttonId) {
        switch (buttonId) {
            case 0 -> depositFromPlayer(player, false);
            case 1 -> depositFromPlayer(player, true);
            case 2 -> withdrawToPlayer(player);
            default -> {}
        }
    }

    private void depositFromPlayer(Player player, boolean everything) {
        int points = everything ? XpHelper.totalExperience(player) : XpHelper.pointsForOneLevel(player);
        long spaceMb = Math.max(0, capacityMb() - storedXpMb);
        points = (int) Math.min(points, spaceMb / XpHelper.MB_PER_XP);
        if (points <= 0) {
            return;
        }
        player.giveExperiencePoints(-points);
        storedXpMb += (long) points * XpHelper.MB_PER_XP;
        setChanged();
    }

    private void withdrawToPlayer(Player player) {
        int points = getStoredXp();
        if (points <= 0) {
            return;
        }
        storedXpMb -= (long) points * XpHelper.MB_PER_XP;
        player.giveExperiencePoints(points);
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("stored_xp_mb", storedXpMb);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        storedXpMb = input.getLongOr("stored_xp_mb", 0L);
    }

    /** Insert-only liquid experience input, so pipes can fill the buffer. */
    private class XpSink extends SnapshotJournal<Long> implements ResourceHandler<FluidResource> {
        @Override
        public int size() {
            return 1;
        }

        @Override
        public FluidResource getResource(int index) {
            return FluidResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            return 0;
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            return Math.max(0, capacityMb() - storedXpMb);
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return resource.isEmpty() || XpHelper.isXpFluid(resource);
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (!XpHelper.isXpFluid(resource)) {
                return 0;
            }
            int accepted = (int) Math.min(amount, Math.max(0, capacityMb() - storedXpMb));
            if (accepted <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            storedXpMb += accepted;
            return accepted;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        protected Long createSnapshot() {
            return storedXpMb;
        }

        @Override
        protected void revertToSnapshot(Long snapshot) {
            storedXpMb = snapshot;
        }

        @Override
        protected void onRootCommit(Long originalState) {
            setChanged();
        }
    }
}

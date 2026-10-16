package com.ae2apotheotic.blockentity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.api.util.AECableType;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;
import com.ae2apotheotic.menu.ProviderMenu;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.ListIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Common part of the Apotheotic pattern providers: an ME network node that offers its patterns to the autocrafting
 * system, executes them against an adjacent Apotheosis machine and sends the results back into the network.
 */
public abstract class AbstractProviderBlockEntity extends BlockEntity
    implements IInWorldGridNodeHost, IActionHost, ICraftingProvider, InternalInventoryHost, PatternContainer, MenuProvider {

    public static final int DEFAULT_PATTERN_SLOTS = 9;
    public static final int GEM_PATTERN_SLOTS = 27;
    public static final int PATTERN_SLOTS = DEFAULT_PATTERN_SLOTS;
    /** Stop accepting new crafts while this many result stacks still wait to be inserted into the network. */
    private static final int MAX_BUFFERED_OUTPUTS = 64;

    private final int patternSlots;
    private final IManagedGridNode mainNode;
    private final IActionSource actionSource;
    protected final AppEngInternalInventory patternInventory;
    private final List<IPatternDetails> patterns = new ArrayList<>();
    private final List<GenericStack> outputBuffer = new ArrayList<>();
    private boolean initScheduled;
    private boolean nodeCreated;
    private ProviderProblem lastProblem = ProviderProblem.NONE;
    private long lastSoundTick = Long.MIN_VALUE / 2;

    protected AbstractProviderBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        this(type, pos, state, DEFAULT_PATTERN_SLOTS);
    }

    protected AbstractProviderBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int patternSlots) {
        super(type, pos, state);
        this.patternSlots = patternSlots;
        this.patternInventory = new AppEngInternalInventory(this, patternSlots, 1);
        this.patternInventory.setFilter(new IAEItemFilter() {
            @Override
            public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
                return acceptsPatternItem(stack);
            }
        });
        this.mainNode = GridHelper.createManagedNode(this, NodeListener.INSTANCE)
            .setFlags(GridFlags.REQUIRE_CHANNEL)
            .setIdlePowerUsage(2.0)
            .setInWorldNode(true)
            .setExposedOnSides(EnumSet.allOf(Direction.class))
            .setVisualRepresentation(state.getBlock())
            .setTagName("proxy")
            .addService(ICraftingProvider.class, this)
            .addService(IGridTickable.class, new Ticker());
        this.actionSource = IActionSource.ofMachine(this);
    }

    // ------------------------------------------------------------------------------------------------------------
    // What the concrete providers implement

    /** Whether the item may be placed into the pattern slots. */
    public abstract boolean acceptsPatternItem(ItemStack stack);

    protected abstract boolean acceptsPattern(IPatternDetails details);

    /**
     * Runs one craft. Must not change anything if it returns {@code null} (then the crafting CPU keeps the inputs
     * and retries later). On success the inputs are consumed and the returned stacks are sent to the network.
     */
    protected abstract @Nullable List<GenericStack> process(ServerLevel level, IPatternDetails details, KeyCounter[] inputs);

    /** Bit flags describing the attached machine, see {@link ProviderMenu}. */
    public abstract int machineFlags();

    protected void addExtraDrops(List<ItemStack> drops) {}

    /** Plays a machine sound, at most twice a second so big crafting jobs do not spam it. */
    protected void playSoundThrottled(BlockPos at, SoundEvent sound, float volume, float pitch) {
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        if (now - lastSoundTick < 10) {
            return;
        }
        lastSoundTick = now;
        level.playSound(null, at, sound, SoundSource.BLOCKS, volume, pitch);
    }

    protected final @Nullable List<GenericStack> fail(ProviderProblem problem) {
        this.lastProblem = problem;
        return null;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Lifecycle

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        scheduleInit();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        scheduleInit();
    }

    private void scheduleInit() {
        if (initScheduled || level == null || level.isClientSide()) {
            return;
        }
        initScheduled = true;
        GridHelper.onFirstTick(this, AbstractProviderBlockEntity::onReady);
    }

    private void onReady() {
        if (nodeCreated || level == null || level.isClientSide() || isRemoved()) {
            return;
        }
        nodeCreated = true;
        mainNode.create(level, worldPosition);
        updatePatterns();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        mainNode.destroy();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        mainNode.destroy();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        mainNode.serialize(output);
        patternInventory.writeToNBT(output, "patterns");
        output.store("output_buffer", GenericStack.CODEC.listOf(), List.copyOf(outputBuffer));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        mainNode.deserialize(input);
        patternInventory.readFromNBT(input, "patterns");
        outputBuffer.clear();
        input.read("output_buffer", GenericStack.CODEC.listOf()).ifPresent(outputBuffer::addAll);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null || level.isClientSide()) {
            return;
        }
        List<ItemStack> drops = new ArrayList<>();
        for (int i = 0; i < patternInventory.size(); i++) {
            ItemStack stack = patternInventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
        for (GenericStack stack : outputBuffer) {
            stack.what().addDrops(stack.amount(), drops, level, pos);
        }
        addExtraDrops(drops);
        outputBuffer.clear();
        for (int i = 0; i < patternInventory.size(); i++) {
            patternInventory.setItemDirect(i, ItemStack.EMPTY);
        }
        for (ItemStack drop : drops) {
            Block.popResource(level, pos, drop);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Patterns

    public AppEngInternalInventory getPatternInventory() {
        return patternInventory;
    }

    public void updatePatterns() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        patterns.clear();
        for (int i = 0; i < patternInventory.size(); i++) {
            ItemStack stack = patternInventory.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            IPatternDetails details = PatternDetailsHelper.decodePattern(stack, serverLevel);
            if (details != null && acceptsPattern(details)) {
                patterns.add(details);
            }
        }
        ICraftingProvider.requestUpdate(mainNode);
    }

    public int getPatternCount() {
        return patterns.size();
    }

    public int getPatternSlots() {
        return patternSlots;
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        setChanged();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        setChanged();
        updatePatterns();
    }

    @Override
    public boolean isClientSide() {
        return level == null || level.isClientSide();
    }

    // ------------------------------------------------------------------------------------------------------------
    // ICraftingProvider

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return patterns;
    }

    @Override
    public boolean pushPattern(IPatternDetails details, KeyCounter[] inputs) {
        if (!mainNode.isActive() || isBusy() || !patterns.contains(details) || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        List<GenericStack> results = process(serverLevel, details, inputs);
        if (results == null) {
            return false;
        }
        lastProblem = ProviderProblem.NONE;
        for (GenericStack result : results) {
            if (result != null && result.amount() > 0) {
                outputBuffer.add(result);
            }
        }
        setChanged();
        mainNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
        return true;
    }

    @Override
    public boolean isBusy() {
        return outputBuffer.size() >= MAX_BUFFERED_OUTPUTS;
    }

    private boolean flushOutputs() {
        IGrid grid = mainNode.getGrid();
        if (grid == null || outputBuffer.isEmpty()) {
            return false;
        }
        MEStorage storage = grid.getStorageService().getInventory();
        boolean didWork = false;
        ListIterator<GenericStack> it = outputBuffer.listIterator();
        while (it.hasNext()) {
            GenericStack stack = it.next();
            long inserted = StorageHelper.poweredInsert(grid.getEnergyService(), storage, stack.what(), stack.amount(), actionSource);
            if (inserted >= stack.amount()) {
                it.remove();
                didWork = true;
            } else if (inserted > 0) {
                it.set(new GenericStack(stack.what(), stack.amount() - inserted));
                didWork = true;
            }
        }
        if (didWork) {
            setChanged();
        }
        return didWork;
    }

    private class Ticker implements IGridTickable {
        @Override
        public TickingRequest getTickingRequest(IGridNode node) {
            return new TickingRequest(1, 20, outputBuffer.isEmpty());
        }

        @Override
        public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            if (!mainNode.isActive()) {
                return TickRateModulation.SLEEP;
            }
            boolean didWork = flushOutputs();
            if (outputBuffer.isEmpty()) {
                return TickRateModulation.SLEEP;
            }
            return didWork ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Grid plumbing

    public IManagedGridNode getMainNode() {
        return mainNode;
    }

    public boolean isOnline() {
        return mainNode.isActive();
    }

    public ProviderProblem getLastProblem() {
        return lastProblem;
    }

    @Override
    public @Nullable IGridNode getGridNode(Direction dir) {
        return mainNode.getNode();
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }

    @Override
    public @Nullable IGridNode getActionableNode() {
        return mainNode.getNode();
    }

    private void onNodeStateChanged() {
        if (mainNode.isActive() && !outputBuffer.isEmpty()) {
            mainNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
        }
    }

    private enum NodeListener implements IGridNodeListener<AbstractProviderBlockEntity> {
        INSTANCE;

        @Override
        public void onSaveChanges(AbstractProviderBlockEntity owner, IGridNode node) {
            owner.setChanged();
        }

        @Override
        public void onStateChanged(AbstractProviderBlockEntity owner, IGridNode node, IGridNodeListener.State state) {
            owner.onNodeStateChanged();
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Pattern access terminal

    @Override
    public @Nullable IGrid getGrid() {
        return mainNode.getGrid();
    }

    @Override
    public InternalInventory getTerminalPatternInventory() {
        return patternInventory;
    }

    @Override
    public long getTerminalSortOrder() {
        return worldPosition.asLong();
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        Block block = getBlockState().getBlock();
        return new PatternContainerGroup(AEItemKey.of(block), block.getName(), List.of());
    }

    // ------------------------------------------------------------------------------------------------------------
    // GUI

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ProviderMenu(containerId, inventory, this);
    }

    /** Called from the menu on the server when a GUI button is pressed. */
    public void handleButton(Player player, int buttonId) {}
}

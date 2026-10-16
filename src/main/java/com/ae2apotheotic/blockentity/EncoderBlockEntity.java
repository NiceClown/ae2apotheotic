package com.ae2apotheotic.blockentity;

import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEItems;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;
import com.ae2apotheotic.menu.EncoderMenu;
import com.ae2apotheotic.registry.ModRegistry;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** Holds the sample / blank pattern / encoded pattern slots and the settings of the Apotheotic Pattern Encoder. */
public class EncoderBlockEntity extends BlockEntity implements InternalInventoryHost, MenuProvider {
    public static final int SLOT_SAMPLE = 0;
    public static final int SLOT_BLANK = 1;
    public static final int SLOT_OUTPUT = 2;

    public static final int MODE_UPGRADE = 0;
    public static final int MODE_CRUSH = 1;
    public static final int MODE_INFUSION = 2;

    private final AppEngInternalInventory inventory;
    private int mode = MODE_UPGRADE;
    /** Selected purity ordinal, or -1 to use the purity of the sample gem. */
    private int purity = -1;
    private int recipeIndex;
    private int leftIndex;
    private int rightIndex;
    private int crushMode;
    private int eterna;
    private int quanta;
    private int arcana;
    private boolean statsNeedReset = true;
    private @Nullable AEItemKey lastSample;
    private int revision;

    public EncoderBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.PATTERN_ENCODER_BE.get(), pos, state);
        this.inventory = new AppEngInternalInventory(this, 3, 64);
        this.inventory.setMaxStackSize(SLOT_SAMPLE, 1);
        this.inventory.setMaxStackSize(SLOT_OUTPUT, 1);
        this.inventory.setFilter(new IAEItemFilter() {
            @Override
            public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
                return isValidForSlot(slot, stack);
            }
        });
    }

    public static boolean isOurPattern(ItemStack stack) {
        return stack.is(ModRegistry.GEM_UPGRADE_PATTERN.get()) || stack.is(ModRegistry.GEM_CRUSH_PATTERN.get())
            || stack.is(ModRegistry.INFUSION_PATTERN.get());
    }

    public static boolean isValidForSlot(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_BLANK -> AEItems.BLANK_PATTERN.is(stack);
            case SLOT_OUTPUT -> isOurPattern(stack);
            default -> !isOurPattern(stack);
        };
    }

    public AppEngInternalInventory getInventory() {
        return inventory;
    }

    public ItemStack getSample() {
        return inventory.getStackInSlot(SLOT_SAMPLE);
    }

    public int getRevision() {
        return revision;
    }

    private void changed() {
        revision++;
        setChanged();
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        changed();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        if (slot == SLOT_SAMPLE) {
            AEItemKey sample = AEItemKey.of(getSample());
            if (!Objects.equals(sample, lastSample)) {
                lastSample = sample;
                purity = -1;
                recipeIndex = 0;
                leftIndex = 0;
                rightIndex = 0;
                statsNeedReset = true;
            }
        }
        changed();
    }

    @Override
    public boolean isClientSide() {
        return level == null || level.isClientSide();
    }

    // ---- settings ----

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = Mth.clamp(mode, MODE_UPGRADE, MODE_INFUSION);
        changed();
    }

    public int getPurity() {
        return purity;
    }

    public void setPurity(int purity) {
        this.purity = purity;
        changed();
    }

    public int getRecipeIndex() {
        return recipeIndex;
    }

    public void stepRecipe(int delta) {
        recipeIndex += delta;
        leftIndex = 0;
        rightIndex = 0;
        statsNeedReset = true;
        changed();
    }

    public int getLeftIndex() {
        return leftIndex;
    }

    public void stepLeft(int delta) {
        leftIndex += delta;
        changed();
    }

    public int getRightIndex() {
        return rightIndex;
    }

    public void stepRight(int delta) {
        rightIndex += delta;
        changed();
    }

    public int getCrushMode() {
        return crushMode;
    }

    public void cycleCrushMode() {
        crushMode = (crushMode + 1) % 3;
        changed();
    }

    public int getEterna() {
        return eterna;
    }

    public int getQuanta() {
        return quanta;
    }

    public int getArcana() {
        return arcana;
    }

    public void setStats(int eterna, int quanta, int arcana) {
        this.eterna = Mth.clamp(eterna, 0, 100);
        this.quanta = Mth.clamp(quanta, 0, 100);
        this.arcana = Mth.clamp(arcana, 0, 100);
        changed();
    }

    public boolean statsNeedReset() {
        return statsNeedReset;
    }

    public void requestStatsReset() {
        statsNeedReset = true;
        changed();
    }

    public void clearStatsReset() {
        statsNeedReset = false;
    }

    // ---- persistence ----

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        inventory.writeToNBT(output, "inventory");
        output.putInt("mode", mode);
        output.putInt("purity", purity);
        output.putInt("recipe", recipeIndex);
        output.putInt("left", leftIndex);
        output.putInt("right", rightIndex);
        output.putInt("crush_mode", crushMode);
        output.putInt("eterna", eterna);
        output.putInt("quanta", quanta);
        output.putInt("arcana", arcana);
        output.putBoolean("stats_reset", statsNeedReset);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        // Inventory first: changing the sample resets the selections, the saved ones are applied afterwards.
        inventory.readFromNBT(input, "inventory");
        lastSample = AEItemKey.of(getSample());
        mode = Mth.clamp(input.getIntOr("mode", MODE_UPGRADE), MODE_UPGRADE, MODE_INFUSION);
        purity = input.getIntOr("purity", -1);
        recipeIndex = input.getIntOr("recipe", 0);
        leftIndex = input.getIntOr("left", 0);
        rightIndex = input.getIntOr("right", 0);
        crushMode = input.getIntOr("crush_mode", 0);
        eterna = input.getIntOr("eterna", 0);
        quanta = input.getIntOr("quanta", 0);
        arcana = input.getIntOr("arcana", 0);
        statsNeedReset = input.getBooleanOr("stats_reset", true);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null || level.isClientSide()) {
            return;
        }
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Block.popResource(level, pos, stack.copy());
                inventory.setItemDirect(i, ItemStack.EMPTY);
            }
        }
    }

    // ---- GUI ----

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new EncoderMenu(containerId, inventory, this);
    }
}

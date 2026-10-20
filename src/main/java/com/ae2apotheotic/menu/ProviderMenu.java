package com.ae2apotheotic.menu;

import com.ae2apotheotic.blockentity.AbstractProviderBlockEntity;
import com.ae2apotheotic.blockentity.InfusionProviderBlockEntity;
import com.ae2apotheotic.blockentity.ProviderProblem;
import com.ae2apotheotic.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public class ProviderMenu extends AbstractContainerMenu {
    public static final int BUTTON_DEPOSIT_LEVEL = 0;
    public static final int BUTTON_DEPOSIT_ALL = 1;
    public static final int BUTTON_WITHDRAW_ALL = 2;

    public static final int FLAG_ONLINE = 1 << 8;

    public static final int D_FLAGS = 0;
    public static final int D_PROBLEM = 1;
    public static final int D_PATTERNS = 2;
    private static final int D_XP_STORED = 3; // + 4
    private static final int D_XP_NEARBY = 5; // + 6
    private static final int D_SIZE = 7;

    private final @Nullable AbstractProviderBlockEntity provider;
    private final BlockPos pos;
    private final boolean infusion;
    private final int patternSlots;
    private final int playerStart;
    private final int playerEnd;
    private final ContainerData data = new SimpleContainerData(D_SIZE);
    private int refreshTimer;

    public static ProviderMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        boolean infusion = buf.readBoolean();
        int patternSlots = buf.readableBytes() >= 4 ? buf.readInt() : AbstractProviderBlockEntity.DEFAULT_PATTERN_SLOTS;
        return new ProviderMenu(containerId, inventory, pos, infusion, patternSlots, null, new SimpleContainer(patternSlots));
    }

    public ProviderMenu(int containerId, Inventory inventory, AbstractProviderBlockEntity provider) {
        this(containerId, inventory, provider.getBlockPos(), provider instanceof InfusionProviderBlockEntity,
            provider.getPatternSlots(), provider, provider.getPatternInventory().toContainer());
    }

    private ProviderMenu(int containerId, Inventory inventory, BlockPos pos, boolean infusion, int patternSlots,
        @Nullable AbstractProviderBlockEntity provider, Container patterns) {
        super(ModRegistry.PROVIDER_MENU.get(), containerId);
        this.provider = provider;
        this.pos = pos;
        this.infusion = infusion;
        this.patternSlots = patternSlots;
        this.playerStart = patternSlots;
        this.playerEnd = playerStart + 36;
        for (int i = 0; i < patternSlots; i++) {
            addSlot(new Slots.Filtered(patterns, i, 8 + (i % 3) * 18, 20 + (i / 3) * 18, this::acceptsPattern, 1));
        }
        addStandardInventorySlots(inventory, 8, 114);
        addDataSlots(data);
        if (provider != null) {
            updateData();
        }
    }

    private boolean acceptsPattern(ItemStack stack) {
        if (infusion) {
            return stack.is(ModRegistry.INFUSION_PATTERN.get());
        }
        return stack.is(ModRegistry.GEM_UPGRADE_PATTERN.get()) || stack.is(ModRegistry.GEM_CRUSH_PATTERN.get());
    }

    private void updateData() {
        if (provider == null) {
            return;
        }
        int flags = provider.machineFlags() | (provider.isOnline() ? FLAG_ONLINE : 0);
        data.set(D_FLAGS, flags);
        data.set(D_PROBLEM, provider.getLastProblem().ordinal());
        data.set(D_PATTERNS, provider.getPatternCount());
        if (provider instanceof InfusionProviderBlockEntity inf) {
            setInt(D_XP_STORED, inf.getStoredXp());
            setInt(D_XP_NEARBY, inf.getNearbyXp());
        }
    }

    // Container data is synced as 16-bit values, so bigger numbers are split in two halves.
    private void setInt(int index, int value) {
        data.set(index, value & 0xFFFF);
        data.set(index + 1, (value >>> 16) & 0xFFFF);
    }

    private int getInt(int index) {
        return (data.get(index) & 0xFFFF) | ((data.get(index + 1) & 0xFFFF) << 16);
    }

    @Override
    public void broadcastChanges() {
        if (provider != null && refreshTimer-- <= 0) {
            refreshTimer = 10;
            updateData();
        }
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (provider == null) {
            return false;
        }
        provider.handleButton(player, buttonId);
        refreshTimer = 0;
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < patternSlots) {
            if (!moveItemStackTo(stack, playerStart, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!acceptsPattern(stack) || !moveItemStackTo(stack, 0, patternSlots, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    public int getPatternSlots() {
        return patternSlots;
    }

    @Override
    public boolean stillValid(Player player) {
        if (provider == null) {
            return true;
        }
        return !provider.isRemoved() && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    // ---- client accessors ----

    public boolean isInfusion() {
        return infusion;
    }

    public boolean isOnline() {
        return (data.get(D_FLAGS) & FLAG_ONLINE) != 0;
    }

    public int machineFlags() {
        return data.get(D_FLAGS) & 0xFF;
    }

    public ProviderProblem problem() {
        return ProviderProblem.byId(data.get(D_PROBLEM));
    }

    public int patternCount() {
        return data.get(D_PATTERNS);
    }

    public int storedXp() {
        return getInt(D_XP_STORED);
    }

    public int nearbyXp() {
        return getInt(D_XP_NEARBY);
    }
}

package com.ae2apotheotic.menu;

import com.ae2apotheotic.blockentity.AbstractProviderBlockEntity;
import com.ae2apotheotic.blockentity.GemProviderBlockEntity;
import com.ae2apotheotic.blockentity.InfusionProviderBlockEntity;
import com.ae2apotheotic.blockentity.ProviderProblem;
import com.ae2apotheotic.pattern.GemCrushEncoding;
import com.ae2apotheotic.pattern.GemKeys;
import com.ae2apotheotic.pattern.GemUpgradeEncoding;
import com.ae2apotheotic.registry.ModRegistry;
import dev.shadowsoffire.apotheosis.socket.gem.Gem;
import dev.shadowsoffire.apotheosis.socket.gem.UnsocketedGem;
import java.util.List;
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
    private static final int D_CATEGORY = 7;
    private static final int D_SIZE = 8;

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
        int patternSlots = buf.readableBytes() >= 4 ? buf.readInt() : (infusion ? 9 : GemProviderBlockEntity.PATTERN_SLOTS);
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
        if (!infusion) {
            // Gem Provider: add 6 slots for each category
            for (int cat = 0; cat < GemProviderBlockEntity.MAX_CATEGORIES; cat++) {
                final int c = cat;
                for (int t = 0; t < GemProviderBlockEntity.SLOTS_PER_CATEGORY; t++) {
                    int slotIdx = c * GemProviderBlockEntity.SLOTS_PER_CATEGORY + t;
                    int x = 43 + t * 21;
                    int y = 82;
                    addSlot(new Slots.Filtered(patterns, slotIdx, x, y,
                        stack -> GemProviderBlockEntity.isPatternValidForSlot(slotIdx, stack), 1,
                        () -> getSelectedCategory() == c));
                }
            }
            addStandardInventorySlots(inventory, 24, 120);
        } else {
            // Infusion Provider: standard 9 pattern slots
            for (int i = 0; i < patternSlots; i++) {
                addSlot(new Slots.Filtered(patterns, i, 8 + (i % 3) * 18, 20 + (i / 3) * 18, this::acceptsPattern, 1));
            }
            addStandardInventorySlots(inventory, 8, 114);
        }
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
        } else if (provider instanceof GemProviderBlockEntity gemProv) {
            data.set(D_CATEGORY, gemProv.getSelectedCategory());
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
        if (provider instanceof GemProviderBlockEntity) {
            setSelectedCategory(buttonId);
        }
        refreshTimer = 0;
        return true;
    }

    public int getSelectedCategory() {
        if (provider instanceof GemProviderBlockEntity gemProv) {
            return gemProv.getSelectedCategory();
        }
        return data.get(D_CATEGORY);
    }

    public void setSelectedCategory(int category) {
        if (provider instanceof GemProviderBlockEntity gemProv) {
            gemProv.setSelectedCategory(category);
        }
        data.set(D_CATEGORY, category);
    }

    public int getCategoryPatternCount(int category) {
        int count = 0;
        int start = category * GemProviderBlockEntity.SLOTS_PER_CATEGORY;
        for (int i = 0; i < GemProviderBlockEntity.SLOTS_PER_CATEGORY && start + i < patternSlots; i++) {
            if (slots.get(start + i).hasItem()) {
                count++;
            }
        }
        return count;
    }

    public int findSlotForPattern(ItemStack stack) {
        List<Gem> gems = GemKeys.sortedGems();
        if (stack.is(ModRegistry.GEM_UPGRADE_PATTERN.get())) {
            GemUpgradeEncoding enc = stack.get(ModRegistry.GEM_UPGRADE.get());
            if (enc != null) {
                UnsocketedGem unsocketed = UnsocketedGem.of(enc.input().toStack());
                if (unsocketed.isValid()) {
                    int gemIdx = gems.indexOf(unsocketed.gem().get());
                    if (gemIdx >= 0) {
                        int category = gemIdx + 1;
                        int tier = unsocketed.purity().ordinal();
                        return category * GemProviderBlockEntity.SLOTS_PER_CATEGORY + tier;
                    }
                }
            }
        } else if (stack.is(ModRegistry.GEM_CRUSH_PATTERN.get())) {
            GemCrushEncoding enc = stack.get(ModRegistry.GEM_CRUSH.get());
            if (enc != null) {
                if (enc.mode() == GemCrushEncoding.CrushMode.EXACT && enc.gem().isPresent()) {
                    UnsocketedGem unsocketed = UnsocketedGem.of(enc.gem().get().toStack());
                    if (unsocketed.isValid()) {
                        int gemIdx = gems.indexOf(unsocketed.gem().get());
                        if (gemIdx >= 0) {
                            return (gemIdx + 1) * GemProviderBlockEntity.SLOTS_PER_CATEGORY + 5;
                        }
                    }
                } else {
                    for (int s = 0; s < GemProviderBlockEntity.SLOTS_PER_CATEGORY; s++) {
                        if (!slots.get(s).hasItem()) {
                            return s;
                        }
                    }
                    return 0;
                }
            }
        }
        return -1;
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
        } else if (infusion) {
            if (!acceptsPattern(stack) || !moveItemStackTo(stack, 0, patternSlots, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            int targetSlot = findSlotForPattern(stack);
            if (targetSlot >= 0 && targetSlot < patternSlots) {
                Slot target = slots.get(targetSlot);
                if (!target.hasItem() && target.mayPlace(stack)) {
                    target.setByPlayer(stack.split(1));
                    setSelectedCategory(targetSlot / GemProviderBlockEntity.SLOTS_PER_CATEGORY);
                } else {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }
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

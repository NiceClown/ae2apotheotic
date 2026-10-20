package com.ae2apotheotic.menu;

import com.ae2apotheotic.blockentity.GemProviderBlockEntity;
import com.ae2apotheotic.blockentity.ProviderProblem;
import com.ae2apotheotic.registry.ModRegistry;
import dev.shadowsoffire.apotheosis.socket.gem.Gem;
import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry;
import dev.shadowsoffire.placebo.dynreg.DynamicHolder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
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

/**
 * GUI of the Gem Pattern Provider. Category 0 is bulk crushing (27 real slots), every other category is a gem whose
 * six slots are a view onto the provider storage of the currently selected gem.
 */
public class GemProviderMenu extends AbstractContainerMenu {
    /** {@code BTN_SELECT + category} selects a category. */
    public static final int BTN_SELECT = 1000;
    public static final int FLAG_ONLINE = 1 << 8;
    public static final int BULK = 0;

    public static final int GEM_SLOTS = GemProviderBlockEntity.SLOTS_PER_GEM;
    public static final int BULK_SLOTS = GemProviderBlockEntity.BULK_SLOTS;
    private static final int PLAYER_START = GEM_SLOTS + BULK_SLOTS;
    private static final int PLAYER_END = PLAYER_START + 36;

    /** x of the six gem slots in the GUI, y is {@link #GEM_SLOT_Y}. */
    public static final int[] GEM_SLOT_X = { 12, 38, 64, 90, 116, 160 };
    public static final int GEM_SLOT_Y = 126;
    public static final int BULK_X = 13;
    public static final int BULK_Y = 110;

    private static final int D_FLAGS = 0;
    private static final int D_PROBLEM = 1;
    private static final int D_PATTERNS = 2;
    private static final int D_SELECTED = 3;
    private static final int D_MASKS = 4;

    private final @Nullable GemProviderBlockEntity provider;
    private final BlockPos pos;
    /** Index 0 is a placeholder for bulk crushing, the rest are gem ids. */
    private final List<@Nullable Identifier> categories;
    private final ContainerData data;
    private int lastRevision = Integer.MIN_VALUE;
    private int refreshTimer;

    public static GemProviderMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int count = buf.readVarInt();
        List<Identifier> gems = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            gems.add(buf.readIdentifier());
        }
        return new GemProviderMenu(containerId, inventory, pos, gems, null, new SimpleContainer(GEM_SLOTS), new SimpleContainer(BULK_SLOTS));
    }

    public GemProviderMenu(int containerId, Inventory inventory, GemProviderBlockEntity provider) {
        this(containerId, inventory, provider.getBlockPos(), provider.gemCategories(), provider, null,
            provider.getBulkInventory().toContainer());
    }

    private GemProviderMenu(int containerId, Inventory inventory, BlockPos pos, List<Identifier> gems,
        @Nullable GemProviderBlockEntity provider, @Nullable Container clientGemView, Container bulk) {
        super(ModRegistry.GEM_PROVIDER_MENU.get(), containerId);
        this.provider = provider;
        this.pos = pos;
        this.categories = new ArrayList<>(gems.size() + 1);
        this.categories.add(null);
        this.categories.addAll(gems);
        this.data = new SimpleContainerData(D_MASKS + categories.size());

        Container gemView = clientGemView != null ? clientGemView : new GemView();
        for (int tier = 0; tier < GEM_SLOTS; tier++) {
            addSlot(new GemSlot(gemView, tier, GEM_SLOT_X[tier], GEM_SLOT_Y));
        }
        for (int i = 0; i < BULK_SLOTS; i++) {
            addSlot(new Slots.Filtered(bulk, i, BULK_X + (i % 9) * 18, BULK_Y + (i / 9) * 18,
                GemProviderBlockEntity::isBulkPattern, 1, () -> selected() == BULK));
        }
        addStandardInventorySlots(inventory, 13, 184);
        addDataSlots(data);

        if (provider != null) {
            int initial = BULK;
            Identifier last = provider.getLastCategory();
            if (last != null && categories.contains(last)) {
                initial = categories.indexOf(last);
            } else if (categories.size() > 1) {
                initial = 1;
            }
            data.set(D_SELECTED, initial);
            refresh();
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Shared helpers (both sides)

    public int categoryCount() {
        return categories.size();
    }

    public @Nullable Identifier categoryId(int category) {
        return category > 0 && category < categories.size() ? categories.get(category) : null;
    }

    public @Nullable Gem categoryGem(int category) {
        Identifier id = categoryId(category);
        if (id == null) {
            return null;
        }
        DynamicHolder<Gem> holder = GemRegistry.INSTANCE.holder(id);
        return holder.isBound() ? holder.get() : null;
    }

    public int selected() {
        int sel = data.get(D_SELECTED);
        return sel >= 0 && sel < categories.size() ? sel : BULK;
    }

    /** Client-side prediction, the server confirms through the data slot. */
    public void selectLocally(int category) {
        data.set(D_SELECTED, category);
    }

    public int mask(int category) {
        return category > 0 && D_MASKS + category < data.getCount() ? data.get(D_MASKS + category) & 0x3F : 0;
    }

    public int bulkCount() {
        int count = 0;
        for (int i = 0; i < BULK_SLOTS; i++) {
            if (slots.get(GEM_SLOTS + i).hasItem()) {
                count++;
            }
        }
        return count;
    }

    public boolean isOnline() {
        return (data.get(D_FLAGS) & FLAG_ONLINE) != 0;
    }

    public boolean hasGemCase() {
        return (data.get(D_FLAGS) & GemProviderBlockEntity.FLAG_GEM_CASE) != 0;
    }

    public ProviderProblem problem() {
        return ProviderProblem.byId(data.get(D_PROBLEM));
    }

    public int patternCount() {
        return data.get(D_PATTERNS);
    }

    public boolean tierExists(int tier) {
        return GemProviderBlockEntity.tierExists(categoryGem(selected()), tier);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Server side

    private void refresh() {
        if (provider == null) {
            return;
        }
        lastRevision = provider.getRevision();
        data.set(D_FLAGS, provider.machineFlags() | (provider.isOnline() ? FLAG_ONLINE : 0));
        data.set(D_PROBLEM, provider.getLastProblem().ordinal());
        data.set(D_PATTERNS, provider.getPatternCount());
        for (int i = 1; i < categories.size(); i++) {
            data.set(D_MASKS + i, provider.patternMask(categories.get(i)));
        }
    }

    @Override
    public void broadcastChanges() {
        if (provider != null && (provider.getRevision() != lastRevision || refreshTimer-- <= 0)) {
            refreshTimer = 10;
            refresh();
        }
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (provider == null || id < BTN_SELECT || id >= BTN_SELECT + categories.size()) {
            return false;
        }
        select(id - BTN_SELECT);
        return true;
    }

    private void select(int category) {
        data.set(D_SELECTED, category);
        if (provider != null) {
            provider.setLastCategory(categoryId(category));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        if (index < PLAYER_START) {
            ItemStack original = stack.copy();
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            return original;
        }
        // From the player inventory: sort the pattern into its place. Only the server knows the whole storage,
        // the client simply waits for the slot update.
        if (provider == null) {
            return ItemStack.EMPTY;
        }
        GemProviderBlockEntity.InsertResult result = provider.insertSorted(stack);
        if (result.inserted()) {
            stack.shrink(1);
            slot.setChanged();
            int category = result.category() == null ? BULK : categories.indexOf(result.category());
            if (category >= 0) {
                select(category);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (provider == null) {
            return true;
        }
        return !provider.isRemoved() && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Slots

    /** One tier slot of the selected gem. */
    private final class GemSlot extends Slot {
        private final int tier;

        GemSlot(Container container, int tier, int x, int y) {
            super(container, tier, x, y);
            this.tier = tier;
        }

        @Override
        public boolean isActive() {
            return selected() != BULK && tierExists(tier);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            Identifier gem = categoryId(selected());
            return gem != null && categoryGem(selected()) != null && GemProviderBlockEntity.fitsGemSlot(gem, tier, stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }

        @Override
        public void setChanged() {
            container.setItem(getContainerSlot(), getItem());
        }
    }

    /** Server-side container that maps the six slots onto the storage of the selected gem. */
    private final class GemView implements Container {
        private @Nullable Identifier gem() {
            return categoryId(selected());
        }

        @Override
        public int getContainerSize() {
            return GEM_SLOTS;
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < GEM_SLOTS; i++) {
                if (!getItem(i).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            Identifier gem = gem();
            return gem == null || provider == null ? ItemStack.EMPTY : provider.getGemPattern(gem, slot);
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            ItemStack current = getItem(slot);
            if (current.isEmpty() || count <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack rest = current.copy();
            ItemStack taken = rest.split(count);
            setItem(slot, rest);
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return removeItem(slot, Integer.MAX_VALUE);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            Identifier gem = gem();
            if (gem != null && provider != null) {
                provider.setGemPattern(gem, slot, stack);
            }
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {}

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {}
    }
}

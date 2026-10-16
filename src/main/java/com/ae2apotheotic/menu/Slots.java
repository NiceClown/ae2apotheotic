package com.ae2apotheotic.menu;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

final class Slots {
    private Slots() {}

    /** Slot that only accepts matching items and has its own stack limit. */
    static final class Filtered extends Slot {
        private final Predicate<ItemStack> filter;
        private final int limit;

        Filtered(Container container, int index, int x, int y, Predicate<ItemStack> filter, int limit) {
            super(container, index, x, y);
            this.filter = filter;
            this.limit = limit;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return filter.test(stack);
        }

        @Override
        public int getMaxStackSize() {
            return limit;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return Math.min(limit, stack.getMaxStackSize());
        }

        @Override
        public void setChanged() {
            // Vanilla sometimes grows the stack in place and only calls setChanged(); the AE2 container adapter
            // ignores that, so write the stack back to make the block entity notice (and save) the change.
            container.setItem(getContainerSlot(), getItem());
        }
    }

    /** Read-only slot used to preview items computed on the server. */
    static final class Display extends Slot {
        private final BooleanSupplier active;

        Display(Container container, int index, int x, int y, BooleanSupplier active) {
            super(container, index, x, y);
            this.active = active;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean isActive() {
            return active.getAsBoolean();
        }

        @Override
        public boolean isFake() {
            return true;
        }
    }
}

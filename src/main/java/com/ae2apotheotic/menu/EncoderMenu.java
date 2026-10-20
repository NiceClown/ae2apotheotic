package com.ae2apotheotic.menu;

import appeng.api.inventories.InternalInventory;
import appeng.core.definitions.AEItems;
import com.ae2apotheotic.blockentity.EncoderBlockEntity;
import com.ae2apotheotic.encoder.EncoderLogic;
import com.ae2apotheotic.encoder.EncoderStatus;
import com.ae2apotheotic.registry.ModRegistry;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
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

public class EncoderMenu extends AbstractContainerMenu {
    // ---- buttons (sent with the vanilla container button packet) ----
    public static final int BTN_MODE = 0; // + mode (0..2)
    public static final int BTN_PURITY_PREV = 3;
    public static final int BTN_PURITY_NEXT = 4;
    public static final int BTN_RECIPE_PREV = 5;
    public static final int BTN_RECIPE_NEXT = 6;
    public static final int BTN_LEFT_PREV = 7;
    public static final int BTN_LEFT_NEXT = 8;
    public static final int BTN_RIGHT_PREV = 9;
    public static final int BTN_RIGHT_NEXT = 10;
    public static final int BTN_CRUSH_MODE = 11;
    /** {@code BTN_STAT + stat * 4 + op}, stat: 0 eterna, 1 quanta, 2 arcana; op: 0 -1, 1 +1, 2 -10, 3 +10. */
    public static final int BTN_STAT = 20;
    public static final int BTN_AUTO_STATS = 40;
    public static final int BTN_ENCODE = 50;
    /** Upgrade mode only: encode every purity step up to Perfect, the patterns go to the player's inventory. */
    public static final int BTN_ENCODE_CHAIN = 51;
    /** {@code BTN_PURITY_SET + ordinal} picks a purity directly. */
    public static final int BTN_PURITY_SET = 60;
    /** {@code BTN_CRUSH_SET + mode} picks a crushing mode directly. */
    public static final int BTN_CRUSH_SET = 70;
    /** {@code BTN_STAT_SET + stat * 101 + value} sets eterna (0), quanta (1) or arcana (2) to 0..100. */
    public static final int BTN_STAT_SET = 100;

    // ---- synced values ----
    public static final int D_MODE = 0;
    public static final int D_STATUS = 1;
    public static final int D_PURITY = 2;
    public static final int D_RECIPE_INDEX = 3;
    public static final int D_RECIPE_COUNT = 4;
    public static final int D_LEFT_INDEX = 5;
    public static final int D_LEFT_COUNT = 6;
    public static final int D_RIGHT_INDEX = 7;
    public static final int D_RIGHT_COUNT = 8;
    public static final int D_CRUSH_MODE = 9;
    public static final int D_ETERNA = 10;
    public static final int D_QUANTA = 11;
    public static final int D_ARCANA = 12;
    public static final int D_REQ_ETERNA = 13;
    public static final int D_REQ_QUANTA = 14;
    public static final int D_REQ_ARCANA = 15;
    public static final int D_MAX_ETERNA = 16;
    public static final int D_MAX_QUANTA = 17;
    public static final int D_MAX_ARCANA = 18;
    public static final int D_XP_COST = 19; // + 20
    public static final int D_PLAYER_MAX_ETERNA = 21;
    public static final int D_CAN_ENCODE = 22;
    public static final int D_PURITY_MIN = 23;
    public static final int D_PURITY_MAX = 24;
    private static final int D_SIZE = 25;

    public static final int SLOT_SAMPLE = 0;
    public static final int SLOT_BLANK = 1;
    public static final int SLOT_OUTPUT = 2;
    private static final int MACHINE_SLOTS = 3;
    private static final int DISPLAY_SLOTS = 6;
    private static final int PLAYER_START = MACHINE_SLOTS + DISPLAY_SLOTS;
    private static final int PLAYER_END = PLAYER_START + 36;

    public static final int PREVIEW_INPUT = 0;
    public static final int PREVIEW_LEFT = 1;
    public static final int PREVIEW_RIGHT = 2;
    public static final int PREVIEW_RESULT = 3;

    private final @Nullable EncoderBlockEntity encoder;
    private final Player player;
    private final BlockPos pos;
    private final ContainerData data = new SimpleContainerData(D_SIZE);
    private final SimpleContainer preview = new SimpleContainer(4);
    private int lastRevision = Integer.MIN_VALUE;
    private int refreshTimer;

    public static EncoderMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new EncoderMenu(containerId, inventory, buf.readBlockPos(), null, new SimpleContainer(MACHINE_SLOTS));
    }

    public EncoderMenu(int containerId, Inventory inventory, EncoderBlockEntity encoder) {
        this(containerId, inventory, encoder.getBlockPos(), encoder, encoder.getInventory().toContainer());
    }

    private EncoderMenu(int containerId, Inventory inventory, BlockPos pos, @Nullable EncoderBlockEntity encoder, Container machine) {
        super(ModRegistry.ENCODER_MENU.get(), containerId);
        this.encoder = encoder;
        this.player = inventory.player;
        this.pos = pos;

        addSlot(new Slots.Filtered(machine, EncoderBlockEntity.SLOT_SAMPLE, 14, 40,
            s -> EncoderBlockEntity.isValidForSlot(EncoderBlockEntity.SLOT_SAMPLE, s), 1));
        addSlot(new Slots.Filtered(machine, EncoderBlockEntity.SLOT_BLANK, 24, 133,
            s -> EncoderBlockEntity.isValidForSlot(EncoderBlockEntity.SLOT_BLANK, s), 64));
        addSlot(new Slots.Filtered(machine, EncoderBlockEntity.SLOT_OUTPUT, 168, 133,
            s -> EncoderBlockEntity.isValidForSlot(EncoderBlockEntity.SLOT_OUTPUT, s), 1));

        addSlot(new Slots.Display(preview, PREVIEW_INPUT, 44, 66, () -> mode() != EncoderBlockEntity.MODE_INFUSION));
        addSlot(new Slots.Display(preview, PREVIEW_LEFT, 78, 66, () -> mode() == EncoderBlockEntity.MODE_UPGRADE));
        addSlot(new Slots.Display(preview, PREVIEW_RIGHT, 112, 66, () -> mode() == EncoderBlockEntity.MODE_UPGRADE));
        addSlot(new Slots.Display(preview, PREVIEW_RIGHT, 158, 40, () -> mode() == EncoderBlockEntity.MODE_INFUSION));
        addSlot(new Slots.Display(preview, PREVIEW_RESULT, 176, 66, () -> mode() != EncoderBlockEntity.MODE_INFUSION));
        addSlot(new Slots.Display(preview, PREVIEW_RESULT, 182, 40, () -> mode() == EncoderBlockEntity.MODE_INFUSION));

        addStandardInventorySlots(inventory, 24, 166);
        addDataSlots(data);
        refresh();
    }

    // ------------------------------------------------------------------------------------------------------------
    // Server side

    private void refresh() {
        if (encoder == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        EncoderLogic.View view = EncoderLogic.compute(level, player, encoder);
        lastRevision = encoder.getRevision();
        data.set(D_MODE, encoder.getMode());
        data.set(D_STATUS, view.status.ordinal());
        data.set(D_PURITY, view.purity);
        data.set(D_RECIPE_INDEX, view.recipeIndex);
        data.set(D_RECIPE_COUNT, view.recipeCount);
        data.set(D_LEFT_INDEX, view.leftIndex);
        data.set(D_LEFT_COUNT, view.leftCount);
        data.set(D_RIGHT_INDEX, view.rightIndex);
        data.set(D_RIGHT_COUNT, view.rightCount);
        data.set(D_CRUSH_MODE, encoder.getCrushMode());
        data.set(D_ETERNA, encoder.getEterna());
        data.set(D_QUANTA, encoder.getQuanta());
        data.set(D_ARCANA, encoder.getArcana());
        data.set(D_REQ_ETERNA, view.reqEterna10);
        data.set(D_REQ_QUANTA, view.reqQuanta10);
        data.set(D_REQ_ARCANA, view.reqArcana10);
        data.set(D_MAX_ETERNA, view.maxEterna10);
        data.set(D_MAX_QUANTA, view.maxQuanta10);
        data.set(D_MAX_ARCANA, view.maxArcana10);
        data.set(D_XP_COST, view.xpCost & 0xFFFF);
        data.set(D_XP_COST + 1, (view.xpCost >>> 16) & 0xFFFF);
        data.set(D_PLAYER_MAX_ETERNA, view.playerMaxEterna);
        data.set(D_CAN_ENCODE, canEncode(view) ? 1 : 0);
        Purity[] range = EncoderLogic.purityRange(encoder);
        data.set(D_PURITY_MIN, range[0].ordinal());
        data.set(D_PURITY_MAX, range[1].ordinal());
        preview.setItem(PREVIEW_INPUT, view.previewInput);
        preview.setItem(PREVIEW_LEFT, view.previewLeft);
        preview.setItem(PREVIEW_RIGHT, view.previewRight);
        preview.setItem(PREVIEW_RESULT, view.previewResult);
    }

    private boolean canEncode(EncoderLogic.View view) {
        if (encoder == null || !view.status.isOk() || view.pattern == null) {
            return false;
        }
        InternalInventory inv = encoder.getInventory();
        ItemStack output = inv.getStackInSlot(EncoderBlockEntity.SLOT_OUTPUT);
        if (output.isEmpty()) {
            return AEItems.BLANK_PATTERN.is(inv.getStackInSlot(EncoderBlockEntity.SLOT_BLANK));
        }
        return EncoderBlockEntity.isOurPattern(output);
    }

    @Override
    public void broadcastChanges() {
        if (encoder != null && (encoder.getRevision() != lastRevision || refreshTimer-- <= 0)) {
            refreshTimer = 20;
            refresh();
        }
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (encoder == null) {
            return false;
        }
        if (id >= BTN_MODE && id < BTN_MODE + 3) {
            encoder.setMode(id - BTN_MODE);
        } else if (id == BTN_PURITY_PREV || id == BTN_PURITY_NEXT) {
            EncoderLogic.stepPurity(encoder, id == BTN_PURITY_NEXT ? 1 : -1);
        } else if (id == BTN_RECIPE_PREV || id == BTN_RECIPE_NEXT) {
            encoder.stepRecipe(id == BTN_RECIPE_NEXT ? 1 : -1);
        } else if (id == BTN_LEFT_PREV || id == BTN_LEFT_NEXT) {
            encoder.stepLeft(id == BTN_LEFT_NEXT ? 1 : -1);
        } else if (id == BTN_RIGHT_PREV || id == BTN_RIGHT_NEXT) {
            encoder.stepRight(id == BTN_RIGHT_NEXT ? 1 : -1);
        } else if (id == BTN_CRUSH_MODE) {
            encoder.cycleCrushMode();
        } else if (id >= BTN_STAT && id < BTN_STAT + 12) {
            int stat = (id - BTN_STAT) / 4;
            int delta = switch ((id - BTN_STAT) % 4) {
                case 0 -> -1;
                case 1 -> 1;
                case 2 -> -10;
                default -> 10;
            };
            int e = encoder.getEterna() + (stat == 0 ? delta : 0);
            int q = encoder.getQuanta() + (stat == 1 ? delta : 0);
            int a = encoder.getArcana() + (stat == 2 ? delta : 0);
            encoder.setStats(e, q, a);
        } else if (id == BTN_AUTO_STATS) {
            EncoderLogic.requestAutoStats(encoder);
        } else if (id == BTN_ENCODE) {
            encode();
        } else if (id == BTN_ENCODE_CHAIN) {
            encodeChain(player);
        } else if (id >= BTN_PURITY_SET && id < BTN_PURITY_SET + 6) {
            EncoderLogic.setPurity(encoder, id - BTN_PURITY_SET);
        } else if (id >= BTN_CRUSH_SET && id < BTN_CRUSH_SET + 3) {
            encoder.setCrushMode(id - BTN_CRUSH_SET);
        } else if (id >= BTN_STAT_SET && id < BTN_STAT_SET + 3 * 101) {
            int stat = (id - BTN_STAT_SET) / 101;
            int value = (id - BTN_STAT_SET) % 101;
            encoder.setStats(stat == 0 ? value : encoder.getEterna(), stat == 1 ? value : encoder.getQuanta(), stat == 2 ? value : encoder.getArcana());
        } else {
            return false;
        }
        refresh();
        return true;
    }

    private void encode() {
        if (encoder == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        EncoderLogic.View view = EncoderLogic.compute(level, player, encoder);
        if (!canEncode(view) || view.pattern == null) {
            return;
        }
        InternalInventory inv = encoder.getInventory();
        if (inv.getStackInSlot(EncoderBlockEntity.SLOT_OUTPUT).isEmpty()) {
            inv.extractItem(EncoderBlockEntity.SLOT_BLANK, 1, false);
        }
        inv.setItemDirect(EncoderBlockEntity.SLOT_OUTPUT, view.pattern.copy());
    }

    private void encodeChain(Player player) {
        if (encoder == null || encoder.getMode() != EncoderBlockEntity.MODE_UPGRADE || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        EncoderLogic.View view = EncoderLogic.compute(level, player, encoder);
        if (!view.status.isOk()) {
            return;
        }
        List<ItemStack> patterns = EncoderLogic.upgradeChain(level, encoder, view);
        InternalInventory inv = encoder.getInventory();
        ItemStack blanks = inv.getStackInSlot(EncoderBlockEntity.SLOT_BLANK);
        int available = AEItems.BLANK_PATTERN.is(blanks) ? blanks.getCount() : 0;
        int count = player.hasInfiniteMaterials() ? patterns.size() : Math.min(available, patterns.size());
        if (count <= 0) {
            return;
        }
        if (!player.hasInfiniteMaterials()) {
            inv.extractItem(EncoderBlockEntity.SLOT_BLANK, count, false);
        }
        for (int i = 0; i < count; i++) {
            player.getInventory().placeItemBackInInventory(patterns.get(i).copy());
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index >= MACHINE_SLOTS && index < PLAYER_START) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < MACHINE_SLOTS) {
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            int target;
            if (AEItems.BLANK_PATTERN.is(stack)) {
                target = SLOT_BLANK;
            } else if (EncoderBlockEntity.isOurPattern(stack)) {
                target = SLOT_OUTPUT;
            } else {
                target = SLOT_SAMPLE;
            }
            if (!moveItemStackTo(stack, target, target + 1, false)) {
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

    @Override
    public boolean stillValid(Player player) {
        if (encoder == null) {
            return true;
        }
        return !encoder.isRemoved() && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Client accessors

    public int get(int index) {
        return data.get(index);
    }

    public int mode() {
        return data.get(D_MODE);
    }

    public EncoderStatus status() {
        return EncoderStatus.byId(data.get(D_STATUS));
    }

    public int xpCost() {
        return (data.get(D_XP_COST) & 0xFFFF) | ((data.get(D_XP_COST + 1) & 0xFFFF) << 16);
    }

    public boolean canEncode() {
        return data.get(D_CAN_ENCODE) != 0;
    }
}

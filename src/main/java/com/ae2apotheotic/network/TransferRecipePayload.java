package com.ae2apotheotic.network;

import com.ae2apotheotic.AE2Apotheotic;
import com.ae2apotheotic.blockentity.EncoderBlockEntity;
import com.ae2apotheotic.encoder.EncoderLogic;
import com.ae2apotheotic.menu.EncoderMenu;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import dev.shadowsoffire.apotheosis.socket.gem.UnsocketedGem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent by the JEI recipe transfer buttons. The client is not trusted: the server only uses this payload as a
 * request to select a recipe. The sample item is validated against the current encoder mode and, outside creative
 * mode, must actually be present in the player's inventory. The previous sample is returned instead of being
 * silently discarded.
 */
public record TransferRecipePayload(
    int mode,
    ItemStack sample,
    ItemStack result,
    int purity,
    int recipeIndex,
    int eterna,
    int quanta,
    int arcana
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TransferRecipePayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(AE2Apotheotic.MODID, "transfer_recipe"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TransferRecipePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, TransferRecipePayload::mode,
        ItemStack.OPTIONAL_STREAM_CODEC, TransferRecipePayload::sample,
        ItemStack.OPTIONAL_STREAM_CODEC, TransferRecipePayload::result,
        ByteBufCodecs.VAR_INT, TransferRecipePayload::purity,
        ByteBufCodecs.VAR_INT, TransferRecipePayload::recipeIndex,
        ByteBufCodecs.VAR_INT, TransferRecipePayload::eterna,
        ByteBufCodecs.VAR_INT, TransferRecipePayload::quanta,
        ByteBufCodecs.VAR_INT, TransferRecipePayload::arcana,
        TransferRecipePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TransferRecipePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> applyTo(context.player(), payload));
    }

    public static void applyTo(Player player, TransferRecipePayload payload) {
        if (!(player.containerMenu instanceof EncoderMenu menu) || !menu.stillValid(player)) {
            return;
        }
        EncoderBlockEntity encoder = menu.getEncoder();
        if (encoder == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        int mode = Mth.clamp(payload.mode(), EncoderBlockEntity.MODE_UPGRADE, EncoderBlockEntity.MODE_INFUSION);
        ItemStack requested = payload.sample().isEmpty() ? ItemStack.EMPTY : payload.sample().copyWithCount(1);
        if (!requested.isEmpty()) {
            if (!EncoderBlockEntity.isValidForSlot(EncoderBlockEntity.SLOT_SAMPLE, requested)) {
                return;
            }
            if (mode != EncoderBlockEntity.MODE_INFUSION && !UnsocketedGem.of(requested).isValid()) {
                return;
            }
            if (!replaceSample(player, encoder, requested)) {
                return;
            }
        }

        encoder.setMode(mode);
        if (payload.purity() >= 0 && payload.purity() < Purity.values().length) {
            encoder.setPurity(payload.purity());
        }

        if (mode == EncoderBlockEntity.MODE_INFUSION) {
            ItemStack current = encoder.getSample();
            int index = current.isEmpty()
                ? -1
                : EncoderLogic.findInfusionRecipeIndex(level, current, payload.eterna(), payload.quanta(),
                    payload.arcana(), payload.result());
            if (index >= 0) {
                encoder.setRecipeIndex(index);
            } else if (payload.recipeIndex() >= 0) {
                encoder.setRecipeIndex(payload.recipeIndex());
            }
            // Stats are only UI state and are clamped by setStats; the pattern itself is validated when it is
            // encoded, so arbitrary values here cannot produce an invalid pattern.
            encoder.setStats(payload.eterna(), payload.quanta(), payload.arcana());
            encoder.clearStatsReset();
        } else if (payload.recipeIndex() >= 0) {
            encoder.setRecipeIndex(payload.recipeIndex());
        }
    }

    /**
     * Moves one real item from the player into the sample slot. In creative mode the item is not consumed, but the
     * previous sample is still returned. If the player does not own the requested item, nothing changes.
     */
    private static boolean replaceSample(Player player, EncoderBlockEntity encoder, ItemStack requested) {
        ItemStack current = encoder.getSample();
        if (ItemStack.isSameItemSameComponents(current, requested)) {
            return true;
        }
        if (!player.hasInfiniteMaterials()) {
            int slot = player.getInventory().findSlotMatchingItem(requested);
            if (slot < 0) {
                return false;
            }
            player.getInventory().removeItem(slot, 1);
        }
        if (!current.isEmpty()) {
            player.getInventory().placeItemBackInInventory(current.copy());
        }
        encoder.getInventory().setItemDirect(EncoderBlockEntity.SLOT_SAMPLE, requested.copyWithCount(1));
        return true;
    }
}

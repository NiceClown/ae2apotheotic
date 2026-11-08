package com.ae2apotheotic.network;

import com.ae2apotheotic.AE2Apotheotic;
import com.ae2apotheotic.blockentity.EncoderBlockEntity;
import com.ae2apotheotic.menu.EncoderMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TransferRecipePayload(
    int mode,
    ItemStack sample,
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
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player.containerMenu instanceof EncoderMenu menu && menu.stillValid(player)) {
                EncoderBlockEntity encoder = menu.getEncoder();
                if (encoder != null) {
                    encoder.setMode(payload.mode());
                    if (!payload.sample().isEmpty()) {
                        encoder.getInventory().setItemDirect(EncoderBlockEntity.SLOT_SAMPLE, payload.sample().copyWithCount(1));
                    }
                    if (payload.purity() >= 0) {
                        encoder.setPurity(payload.purity());
                    }
                    if (payload.recipeIndex() >= 0) {
                        encoder.setRecipeIndex(payload.recipeIndex());
                    }
                    if (payload.mode() == EncoderBlockEntity.MODE_INFUSION) {
                        encoder.setStats(payload.eterna(), payload.quanta(), payload.arcana());
                    }
                }
            }
        });
    }
}

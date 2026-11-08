package com.ae2apotheotic.compat.jei;

import com.ae2apotheotic.AE2Apotheotic;
import com.ae2apotheotic.blockentity.EncoderBlockEntity;
import com.ae2apotheotic.menu.EncoderMenu;
import com.ae2apotheotic.network.TransferRecipePayload;
import com.ae2apotheotic.registry.ModRegistry;
import dev.shadowsoffire.apotheosis.compat.jei.AdventureJEIPlugin;
import dev.shadowsoffire.apotheosis.socket.gem.cutting.GemCuttingRecipe;
import dev.shadowsoffire.apotheosis.socket.gem.cutting.PurityUpgradeRecipe;
import dev.shadowsoffire.apothic_enchanting.compat.InfusionRecipeCategory;
import dev.shadowsoffire.apothic_enchanting.table.infusion.InfusionRecipe;
import java.util.Optional;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferContext;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

@JeiPlugin
public class AE2ApotheoticJeiPlugin implements IModPlugin {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(AE2Apotheotic.MODID, "jei_plugin");

    @Override
    public Identifier getPluginUid() {
        return ID;
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new GemCuttingTransferHandler(), AdventureJEIPlugin.GEM_CUTTING);
        registration.addRecipeTransferHandler(new InfusionTransferHandler(), InfusionRecipeCategory.TYPE);
    }

    private static void sendPayload(TransferRecipePayload payload) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(payload));
        }
    }

    private static class GemCuttingTransferHandler implements IRecipeTransferHandler<EncoderMenu, GemCuttingRecipe> {
        @Override
        public Class<? extends EncoderMenu> getContainerClass() {
            return EncoderMenu.class;
        }

        @Override
        public Optional<MenuType<EncoderMenu>> getMenuType() {
            return Optional.of(ModRegistry.ENCODER_MENU.get());
        }

        @Override
        public IRecipeType<GemCuttingRecipe> getRecipeType() {
            return AdventureJEIPlugin.GEM_CUTTING;
        }

        @Override
        public @Nullable IRecipeTransferError transferRecipe(
            EncoderMenu container,
            GemCuttingRecipe recipe,
            IRecipeSlotsView recipeSlots,
            Player player,
            boolean maxTransfer,
            boolean doTransfer
        ) {
            if (!doTransfer) {
                return null;
            }
            ItemStack sample = recipeSlots.getSlotViews(RecipeIngredientRole.INPUT).stream()
                .flatMap(IRecipeSlotView::getItemStacks)
                .findFirst()
                .orElse(ItemStack.EMPTY);

            int purity = -1;
            if (recipe instanceof PurityUpgradeRecipe purityRecipe) {
                purity = purityRecipe.purity().ordinal();
            }

            sendPayload(new TransferRecipePayload(
                EncoderBlockEntity.MODE_UPGRADE,
                sample,
                purity,
                -1,
                0,
                0,
                0
            ));
            return null;
        }

        @Override
        public @Nullable IRecipeTransferError transferRecipe(IRecipeTransferContext<GemCuttingRecipe, EncoderMenu> context, boolean doTransfer) {
            return transferRecipe(
                context.getContainer(),
                context.getRecipe(),
                context.getRecipeSlots(),
                context.getPlayer(),
                context.isMaxTransfer(),
                doTransfer
            );
        }
    }

    private static class InfusionTransferHandler implements IRecipeTransferHandler<EncoderMenu, InfusionRecipe> {
        @Override
        public Class<? extends EncoderMenu> getContainerClass() {
            return EncoderMenu.class;
        }

        @Override
        public Optional<MenuType<EncoderMenu>> getMenuType() {
            return Optional.of(ModRegistry.ENCODER_MENU.get());
        }

        @Override
        public IRecipeType<InfusionRecipe> getRecipeType() {
            return InfusionRecipeCategory.TYPE;
        }

        @Override
        public @Nullable IRecipeTransferError transferRecipe(
            EncoderMenu container,
            InfusionRecipe recipe,
            IRecipeSlotsView recipeSlots,
            Player player,
            boolean maxTransfer,
            boolean doTransfer
        ) {
            if (!doTransfer) {
                return null;
            }
            ItemStack sample = recipeSlots.getSlotViews(RecipeIngredientRole.INPUT).stream()
                .flatMap(IRecipeSlotView::getItemStacks)
                .findFirst()
                .orElse(ItemStack.EMPTY);

            int eterna = (int) Math.round(recipe.getRequirements().eterna());
            int quanta = (int) Math.round(recipe.getRequirements().quanta());
            int arcana = (int) Math.round(recipe.getRequirements().arcana());

            sendPayload(new TransferRecipePayload(
                EncoderBlockEntity.MODE_INFUSION,
                sample,
                -1,
                -1,
                eterna,
                quanta,
                arcana
            ));
            return null;
        }

        @Override
        public @Nullable IRecipeTransferError transferRecipe(IRecipeTransferContext<InfusionRecipe, EncoderMenu> context, boolean doTransfer) {
            return transferRecipe(
                context.getContainer(),
                context.getRecipe(),
                context.getRecipeSlots(),
                context.getPlayer(),
                context.isMaxTransfer(),
                doTransfer
            );
        }
    }
}

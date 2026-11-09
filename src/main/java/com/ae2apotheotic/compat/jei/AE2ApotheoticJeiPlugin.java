package com.ae2apotheotic.compat.jei;

import com.ae2apotheotic.AE2Apotheotic;
import com.ae2apotheotic.blockentity.EncoderBlockEntity;
import com.ae2apotheotic.menu.EncoderMenu;
import com.ae2apotheotic.network.TransferRecipePayload;
import com.ae2apotheotic.registry.ModRegistry;
import dev.shadowsoffire.apotheosis.compat.jei.AdventureJEIPlugin;
import dev.shadowsoffire.apotheosis.socket.gem.UnsocketedGem;
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
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
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
        IRecipeTransferHandlerHelper helper = registration.getTransferHelper();
        registration.addRecipeTransferHandler(new GemCuttingTransferHandler(helper), AdventureJEIPlugin.GEM_CUTTING);
        registration.addRecipeTransferHandler(new InfusionTransferHandler(helper), InfusionRecipeCategory.TYPE);
    }

    private static void sendPayload(TransferRecipePayload payload) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(payload));
        }
    }

    /** First non-empty stack of the given role, or {@link ItemStack#EMPTY}. */
    private static ItemStack firstInput(IRecipeSlotsView recipeSlots) {
        return recipeSlots.getSlotViews(RecipeIngredientRole.INPUT).stream()
            .flatMap(IRecipeSlotView::getItemStacks)
            .filter(stack -> !stack.isEmpty())
            .findFirst()
            .orElse(ItemStack.EMPTY);
    }

    /** First gem among the inputs, used as the sample for gem cutting recipes. */
    private static ItemStack firstGem(IRecipeSlotsView recipeSlots) {
        return recipeSlots.getSlotViews(RecipeIngredientRole.INPUT).stream()
            .flatMap(IRecipeSlotView::getItemStacks)
            .filter(stack -> !stack.isEmpty() && UnsocketedGem.of(stack).isValid())
            .findFirst()
            .orElse(ItemStack.EMPTY);
    }

    private static @Nullable IRecipeTransferError checkOwned(Player player, ItemStack sample, IRecipeTransferHandlerHelper helper) {
        if (sample.isEmpty()) {
            return helper.createUserErrorWithTooltip(Component.translatable("gui.ae2apotheotic.transfer.no_sample"));
        }
        if (!player.hasInfiniteMaterials() && player.getInventory().findSlotMatchingItem(sample) < 0) {
            return helper.createUserErrorWithTooltip(Component.translatable("gui.ae2apotheotic.transfer.not_owned"));
        }
        return null;
    }

    private static class GemCuttingTransferHandler implements IRecipeTransferHandler<EncoderMenu, GemCuttingRecipe> {
        private final IRecipeTransferHandlerHelper helper;

        GemCuttingTransferHandler(IRecipeTransferHandlerHelper helper) {
            this.helper = helper;
        }

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
            if (!(recipe instanceof PurityUpgradeRecipe purityRecipe)) {
                // BasicGemCuttingRecipe has no purity step, so the encoder cannot express it.
                return helper.createUserErrorWithTooltip(Component.translatable("gui.ae2apotheotic.transfer.unsupported"));
            }
            ItemStack sample = firstGem(recipeSlots);
            if (sample.isEmpty()) {
                return helper.createUserErrorWithTooltip(Component.translatable("gui.ae2apotheotic.transfer.no_gem"));
            }
            IRecipeTransferError ownership = checkOwned(player, sample, helper);
            if (ownership != null) {
                return ownership;
            }
            if (!doTransfer) {
                return null;
            }
            sendPayload(new TransferRecipePayload(
                EncoderBlockEntity.MODE_UPGRADE,
                sample.copyWithCount(1),
                ItemStack.EMPTY,
                purityRecipe.purity().ordinal(),
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
        private final IRecipeTransferHandlerHelper helper;

        InfusionTransferHandler(IRecipeTransferHandlerHelper helper) {
            this.helper = helper;
        }

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
            ItemStack sample = firstInput(recipeSlots);
            IRecipeTransferError ownership = checkOwned(player, sample, helper);
            if (ownership != null) {
                return ownership;
            }
            if (!doTransfer) {
                return null;
            }

            // Minimum whole stat values, exactly like the encoder's "Auto" button.
            int eterna = (int) Math.ceil(Math.max(0, recipe.getRequirements().eterna()));
            int quanta = (int) Math.ceil(Math.max(0, recipe.getRequirements().quanta()));
            int arcana = (int) Math.ceil(Math.max(0, recipe.getRequirements().arcana()));
            ItemStack input = sample.copyWithCount(1);
            ItemStack result = recipe.assemble(input.copy(), eterna, quanta, arcana);

            sendPayload(new TransferRecipePayload(
                EncoderBlockEntity.MODE_INFUSION,
                input,
                result,
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

package com.ae2apotheotic.block;

import com.ae2apotheotic.blockentity.AbstractProviderBlockEntity;
import com.ae2apotheotic.blockentity.InfusionProviderBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public class ProviderBlock extends Block implements EntityBlock {
    private final Supplier<? extends BlockEntityType<? extends AbstractProviderBlockEntity>> type;

    public ProviderBlock(Properties properties, Supplier<? extends BlockEntityType<? extends AbstractProviderBlockEntity>> type) {
        super(properties);
        this.type = type;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return type.get().create(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof AbstractProviderBlockEntity provider) {
            player.openMenu(provider, buf -> {
                buf.writeBlockPos(pos);
                buf.writeBoolean(provider instanceof InfusionProviderBlockEntity);
                buf.writeInt(provider.getPatternSlots());
            });
        }
        return InteractionResult.SUCCESS;
    }
}

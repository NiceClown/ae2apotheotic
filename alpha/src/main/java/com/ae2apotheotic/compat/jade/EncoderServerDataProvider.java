package com.ae2apotheotic.compat.jade;

import com.ae2apotheotic.AE2Apotheotic;
import com.ae2apotheotic.blockentity.EncoderBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

public enum EncoderServerDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(AE2Apotheotic.MODID, "encoder_data");

    @Override
    public Identifier getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof EncoderBlockEntity encoder) {
            data.putInt("mode", encoder.getMode());
            data.putBoolean("has_sample", !encoder.getSample().isEmpty());
            if (!encoder.getSample().isEmpty()) {
                data.putString("sample_name", encoder.getSample().getHoverName().getString());
            }
        }
    }
}

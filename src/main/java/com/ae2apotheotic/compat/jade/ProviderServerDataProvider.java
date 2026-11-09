package com.ae2apotheotic.compat.jade;

import com.ae2apotheotic.AE2Apotheotic;
import com.ae2apotheotic.blockentity.AbstractProviderBlockEntity;
import com.ae2apotheotic.blockentity.GemProviderBlockEntity;
import com.ae2apotheotic.blockentity.InfusionProviderBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

public enum ProviderServerDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(AE2Apotheotic.MODID, "provider_data");

    @Override
    public Identifier getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof GemProviderBlockEntity gemProvider) {
            data.putString("type", "gem_provider");
            data.putBoolean("online", gemProvider.isOnline());
            data.putInt("patterns", gemProvider.getPatternCount());
            data.putBoolean("has_case", gemProvider.findGemCase() != null);
            data.putInt("problem", gemProvider.getLastProblem().ordinal());
        } else if (accessor.getBlockEntity() instanceof InfusionProviderBlockEntity infProvider) {
            data.putString("type", "infusion_provider");
            data.putBoolean("online", infProvider.isOnline());
            data.putInt("patterns", infProvider.getPatternCount());
            data.putInt("flags", infProvider.machineFlags());
            data.putInt("stored_xp", infProvider.getStoredXp());
            data.putLong("network_xp", infProvider.getNetworkXpMb());
            data.putInt("problem", infProvider.getLastProblem().ordinal());
        }
    }
}

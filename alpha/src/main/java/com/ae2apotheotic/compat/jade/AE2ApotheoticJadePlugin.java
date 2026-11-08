package com.ae2apotheotic.compat.jade;

import com.ae2apotheotic.block.EncoderBlock;
import com.ae2apotheotic.block.ProviderBlock;
import com.ae2apotheotic.blockentity.AbstractProviderBlockEntity;
import com.ae2apotheotic.blockentity.EncoderBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class AE2ApotheoticJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(ProviderServerDataProvider.INSTANCE, AbstractProviderBlockEntity.class);
        registration.registerBlockDataProvider(EncoderServerDataProvider.INSTANCE, EncoderBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(ProviderClientComponentProvider.INSTANCE, ProviderBlock.class);
        registration.registerBlockComponent(EncoderClientComponentProvider.INSTANCE, EncoderBlock.class);
    }
}

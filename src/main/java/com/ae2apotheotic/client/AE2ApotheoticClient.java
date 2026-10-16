package com.ae2apotheotic.client;

import com.ae2apotheotic.AE2Apotheotic;
import com.ae2apotheotic.registry.ModRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@Mod(value = AE2Apotheotic.MODID, dist = Dist.CLIENT)
public final class AE2ApotheoticClient {
    public AE2ApotheoticClient(IEventBus modBus) {
        modBus.addListener(AE2ApotheoticClient::registerScreens);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModRegistry.ENCODER_MENU.get(), EncoderScreen::new);
        event.register(ModRegistry.PROVIDER_MENU.get(), ProviderScreen::new);
    }
}

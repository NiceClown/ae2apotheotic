package com.ae2apotheotic;

import com.ae2apotheotic.network.TransferRecipePayload;
import com.ae2apotheotic.registry.ModRegistry;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

@Mod(AE2Apotheotic.MODID)
public final class AE2Apotheotic {
    public static final String MODID = "ae2apotheotic";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AE2Apotheotic(IEventBus modBus, ModContainer container) {
        ModRegistry.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, this::registerPayloads);
        container.registerConfig(ModConfig.Type.SERVER, ApotheoticConfig.SPEC);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MODID);
        registrar.playToServer(
            TransferRecipePayload.TYPE,
            TransferRecipePayload.STREAM_CODEC,
            TransferRecipePayload::handle
        );
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}

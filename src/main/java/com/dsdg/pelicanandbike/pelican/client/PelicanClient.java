package com.dsdg.pelicanandbike.pelican.client;

import com.dsdg.pelicanandbike.pelican.PelicanModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public final class PelicanClient implements ClientModInitializer {
    public void onInitializeClient(){EntityRendererRegistry.register(PelicanModule.TYPE,PelicanRenderer::new);}
}

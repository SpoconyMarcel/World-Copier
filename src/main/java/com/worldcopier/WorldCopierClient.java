package com.worldcopier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class WorldCopierClient implements ClientModInitializer {
    public static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.world_copier.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.world_copier"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.world != null && client.player != null && client.currentScreen == null) {
                    client.setScreen(new CopierScreen());
                }
            }
        });

        // Chunk dotarł od serwera -> zapisz. Chunk jest usuwany -> zapisz ponownie (najnowszy stan).
        ClientChunkEvents.CHUNK_LOAD.register(CopierManager::onChunk);
        ClientChunkEvents.CHUNK_UNLOAD.register(CopierManager::onChunk);

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                CopierManager.onDisconnect(MinecraftClient.getInstance()));
    }
}

package com.dsdg.pelicanandbike.bicycle.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** A shared physical key must still work when another vehicle registers the same binding. */
public final class BicycleInput {
    private BicycleInput() { }

    public static boolean held(MinecraftClient client, KeyBinding binding) {
        if (!client.isWindowFocused() || client.currentScreen != null) return false;
        if (binding.isPressed()) return true;
        String name = binding.getBoundKeyTranslationKey();
        InputUtil.Key key = InputUtil.fromTranslationKey(name);
        if (key.getCode() < 0) return false;
        // GLFW normally marks only one of the duplicate KeyBindings as pressed.
        for (KeyBinding alias : client.options.allKeys) {
            if (alias.isPressed() && name.equals(alias.getBoundKeyTranslationKey())) return true;
        }
        long window = client.getWindow().getHandle();
        return key.getCategory() == InputUtil.Type.MOUSE
            ? GLFW.glfwGetMouseButton(window, key.getCode()) == GLFW.GLFW_PRESS
            : key.getCategory() == InputUtil.Type.KEYSYM && InputUtil.isKeyPressed(window, key.getCode());
    }
}

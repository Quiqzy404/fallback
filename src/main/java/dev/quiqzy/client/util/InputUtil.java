package dev.quiqzy.client.util;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

public final class InputUtil {
	private InputUtil() {
	}

	/** Checks directly on mouse/keyboard whether the key of this binding is physically held down. */
	public static boolean isPhysicallyDown(Minecraft mc, KeyMapping mapping) {
		InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(mapping);
		long window = mc.getWindow().handle();
		if (key.getType() == InputConstants.Type.MOUSE) {
			return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
		}
		if (key.getType() == InputConstants.Type.KEYSYM) {
			return GLFW.glfwGetKey(window, key.getValue()) == GLFW.GLFW_PRESS;
		}
		return false;
	}
}

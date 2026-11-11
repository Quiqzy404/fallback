package dev.quiqzy.client.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;

import dev.quiqzy.client.QuiqzyClient;

/**
 * Finds Minecraft fields/methods by their stable "intermediary" names (class_..., field_..., method_...)
 * and translates them at runtime to whatever the running game uses. If something is not found,
 * it returns null and logs a warning instead of crashing.
 */
public final class Reflect {
	private Reflect() {
	}

	public static Field field(String intermediaryOwner, String intermediaryName, String descriptor) {
		try {
			MappingResolver r = FabricLoader.getInstance().getMappingResolver();
			String owner = r.mapClassName("intermediary", intermediaryOwner);
			String name = r.mapFieldName("intermediary", intermediaryOwner, intermediaryName, descriptor);
			Field f = Class.forName(owner).getDeclaredField(name);
			f.setAccessible(true);
			return f;
		} catch (Throwable t) {
			QuiqzyClient.LOGGER.warn("Field {}.{} not found", intermediaryOwner, intermediaryName, t);
			return null;
		}
	}

	public static Method method(String intermediaryOwner, String intermediaryName, String descriptor, Class<?>... params) {
		try {
			MappingResolver r = FabricLoader.getInstance().getMappingResolver();
			String owner = r.mapClassName("intermediary", intermediaryOwner);
			String name = r.mapMethodName("intermediary", intermediaryOwner, intermediaryName, descriptor);
			Method m = Class.forName(owner).getDeclaredMethod(name, params);
			m.setAccessible(true);
			return m;
		} catch (Throwable t) {
			QuiqzyClient.LOGGER.warn("Method {}.{} not found", intermediaryOwner, intermediaryName, t);
			return null;
		}
	}
}

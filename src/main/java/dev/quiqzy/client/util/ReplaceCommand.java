package dev.quiqzy.client.util;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import dev.quiqzy.client.config.QuiqzyConfig;
import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.module.misc.TextReplaceModule;

/** /zreplace add <alt> => <neu> | list | remove <nr> | clear  (nur auf deinem Bildschirm) */
public final class ReplaceCommand {
	private ReplaceCommand() {
	}

	private static void msg(FabricClientCommandSource src, String text) {
		src.sendFeedback(Component.literal("§6ZERO §7» §f" + text));
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(
				ClientCommandManager.literal("zreplace")
						.executes(c -> {
							msg(c.getSource(), "/zreplace add <old> => <new>  ·  list  ·  remove <nr>  ·  clear");
							return 1;
						})
						.then(ClientCommandManager.literal("add").then(ClientCommandManager.argument("rule", StringArgumentType.greedyString())
								.executes(c -> {
									String r = StringArgumentType.getString(c, "rule");
									int i = r.indexOf("=>");
									if (i <= 0) {
										msg(c.getSource(), "Use: /zreplace add <old> => <new>");
										return 0;
									}
									String from = r.substring(0, i).trim(), to = r.substring(i + 2).trim();
									if (from.isEmpty()) {
										msg(c.getSource(), "Old text is empty.");
										return 0;
									}
									TextReplaceModule m = ModuleManager.TEXT_REPLACE;
									m.addRule(from, to);
									if (!m.isEnabled()) m.setEnabled(true);
									QuiqzyConfig.save();
									msg(c.getSource(), "\"" + from + "\" §7→§f \"" + to + "\"  §7(only you see this)");
									return 1;
								})))
						.then(ClientCommandManager.literal("list").executes(c -> {
							var list = ModuleManager.TEXT_REPLACE.list();
							if (ModuleManager.TEXT_REPLACE.rules.get().isEmpty()) msg(c.getSource(), "No rules.");
							int n = 1;
							for (String raw : ModuleManager.TEXT_REPLACE.rules.get()) {
								int i = raw.indexOf(TextReplaceModule.SEP);
								msg(c.getSource(), n++ + ". \"" + raw.substring(0, i) + "\" §7→§f \"" + raw.substring(i + 1) + "\"");
							}
							return 1;
						}))
						.then(ClientCommandManager.literal("remove").then(ClientCommandManager.argument("nr", IntegerArgumentType.integer(1))
								.executes(c -> {
									int nr = IntegerArgumentType.getInteger(c, "nr");
									boolean ok = ModuleManager.TEXT_REPLACE.removeRule(nr - 1);
									QuiqzyConfig.save();
									msg(c.getSource(), ok ? "Removed rule " + nr + "." : "No rule " + nr + ".");
									return ok ? 1 : 0;
								})))
						.then(ClientCommandManager.literal("clear").executes(c -> {
							ModuleManager.TEXT_REPLACE.rules.set(new java.util.ArrayList<>());
							QuiqzyConfig.save();
							msg(c.getSource(), "All rules removed.");
							return 1;
						}))));
	}
}

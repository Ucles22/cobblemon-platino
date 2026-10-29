package com.platino;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.gui.summary.Summary;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class PlatinoClient implements ClientModInitializer {
	public static final String MOD_ID = "cobbleplatino";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static KeyBinding notesKey;

	@Override
	public void onInitializeClient() {
		PlatinoConfig.load();
		Notes.load();
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new ScreenRecolor());

		notesKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.cobbleplatino.notes",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, "category.cobbleplatino"));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (notesKey.wasPressed()) {
				if (client.currentScreen != null) continue;
				Pokemon p = selectedPartyPokemon();
				if (p != null) client.setScreen(new NotesScreen(null, p));
			}
		});

		// Botón "Notas" (dibujado y gestionado por nosotros) + tecla N dentro del Resumen
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof Summary summary)) return;
			int px = (w - Summary.BASE_WIDTH) / 2;
			int py = (h - Summary.BASE_HEIGHT) / 2;
			int bw = 62, bh = 14;
			int bx = px + Summary.BASE_WIDTH - bw - 4, by = py - bh - 1;

			ScreenMouseEvents.allowMouseClick(screen).register((s, mx, my, button) -> {
				if (button == 0 && mx >= bx && mx < bx + bw && my >= by && my < by + bh) {
					openNotes(summary);
					return false;
				}
				return true;
			});
			ScreenKeyboardEvents.allowKeyPress(screen).register((s, key, scancode, mods) -> {
				if (notesKey.matchesKey(key, scancode) && !(s.getFocused() instanceof TextFieldWidget)) {
					openNotes(summary);
					return false;
				}
				return true;
			});
			ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> {
				ctx.getMatrices().push();
				ctx.getMatrices().translate(0, 0, 500);
				boolean hover = mx >= bx && mx < bx + bw && my >= by && my < by + bh;
				Draw.panelGradient(ctx, bx, by, bx + bw, by + bh, Draw.OUTLINE,
						hover ? 0xFFF8E070 : 0xFFE8ECF0, hover ? Draw.GOLD : 0xFFB0B8C4);
				String label = "✎ Notas (N)";
				Draw.dsText(ctx, label, bx + (bw - Draw.width(label)) / 2, by + 3, Draw.TEXT_DARK, 0x80FFFFFF);
				ctx.getMatrices().pop();

				Pokemon p = summary.getSelectedPokemon$common();
				if (p == null || !Notes.has(p.getUuid())) return;
				drawNoteCard(ctx, Notes.get(p.getUuid()), px, py, Summary.BASE_WIDTH, Summary.BASE_HEIGHT, s.height);
			});
		});

		// /platino paleta <diamante|perla|platino|original>
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
			var root = ClientCommandManager.literal("platino");
			var pal = ClientCommandManager.literal("paleta");
			for (String name : new String[]{"diamante", "perla", "platino", "original"}) {
				pal.then(ClientCommandManager.literal(name).executes(ctx -> {
					PlatinoConfig.get().palette = name;
					PlatinoConfig.get().screensTheme = !name.equals("original");
					PlatinoConfig.save();
					ctx.getSource().sendFeedback(Text.literal("[Cobblemon Platino] Paleta: " + name + " (recargando texturas…)"));
					MinecraftClient.getInstance().reloadResources();
					return 1;
				}));
			}
			dispatcher.register(root.then(pal).then(ClientCommandManager.literal("recargar").executes(ctx -> {
				PlatinoConfig.load();
				ctx.getSource().sendFeedback(Text.literal("[Cobblemon Platino] Configuración recargada."));
				return 1;
			})));
		});

		LOGGER.info("[Cobblemon Platino] Cargado");
	}

	public static Pokemon selectedPartyPokemon() {
		try {
			var storage = CobblemonClient.INSTANCE.getStorage();
			return storage.getParty().get(storage.getSelectedSlot());
		} catch (Throwable t) {
			return null;
		}
	}

	private static void openNotes(Summary summary) {
		Pokemon p = summary.getSelectedPokemon$common();
		if (p != null) MinecraftClient.getInstance().setScreen(new NotesScreen(summary, p));
	}

	/** Tarjeta con la nota debajo del Resumen (o encima si no cabe). Máx. 3 líneas. */
	private static void drawNoteCard(DrawContext ctx, String note, int px, int py, int w, int sumH, int screenH) {
		var tr = MinecraftClient.getInstance().textRenderer;
		List<OrderedText> lines = tr.wrapLines(StringVisitable.plain(note), w - 12);
		int n = Math.min(3, lines.size());
		int h = 8 + n * 10;
		int y = py + sumH + 4;
		if (y + h > screenH - 2) y = Math.max(2, py - 18 - h);
		int x = px;
		ctx.getMatrices().push();
		ctx.getMatrices().translate(0, 0, 500);
		Draw.panelGradient(ctx, x, y, x + w, y + h, Draw.OUTLINE, 0xFFF8F0C8, 0xFFE8D898);
		for (int i = 0; i < n; i++) {
			ctx.drawText(tr, lines.get(i), x + 6, y + 5 + i * 10, Draw.TEXT_DARK, false);
		}
		ctx.getMatrices().pop();
	}
}

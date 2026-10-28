package com.platino;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.gui.summary.Summary;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
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

		notesKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.cobbleplatino.notes",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, "category.cobbleplatino"));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (notesKey.wasPressed()) {
				if (client.currentScreen != null) continue;
				Pokemon p = selectedPartyPokemon();
				if (p != null) client.setScreen(new NotesScreen(null, p));
			}
		});

		// Botón "Notas" y nota visible en el Resumen de Cobblemon
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof Summary summary)) return;
			int px = (w - Summary.BASE_WIDTH) / 2;
			int py = (h - Summary.BASE_HEIGHT) / 2;
			Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("✎ Notas"), b -> {
				Pokemon p = summary.getSelectedPokemon$common();
				if (p != null) MinecraftClient.getInstance().setScreen(new NotesScreen(summary, p));
			}).dimensions(px + Summary.BASE_WIDTH - 60, py - 22, 60, 18).build());

			ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> {
				Pokemon p = summary.getSelectedPokemon$common();
				if (p == null || !Notes.has(p.getUuid())) return;
				drawNoteCard(ctx, Notes.get(p.getUuid()), px, py + Summary.BASE_HEIGHT + 4, Summary.BASE_WIDTH);
			});
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

	/** Tarjeta con la nota debajo del Resumen (máx. 3 líneas). */
	private static void drawNoteCard(DrawContext ctx, String note, int x, int y, int w) {
		var tr = MinecraftClient.getInstance().textRenderer;
		List<OrderedText> lines = tr.wrapLines(StringVisitable.plain(note), w - 12);
		int n = Math.min(3, lines.size());
		int h = 8 + n * 10;
		ctx.getMatrices().push();
		ctx.getMatrices().translate(0, 0, 400);
		Draw.panelGradient(ctx, x, y, x + w, y + h, Draw.OUTLINE, 0xFFF8F0C8, 0xFFE8D898);
		for (int i = 0; i < n; i++) {
			ctx.drawText(tr, lines.get(i), x + 6, y + 5 + i * 10, Draw.TEXT_DARK, false);
		}
		ctx.getMatrices().pop();
	}
}

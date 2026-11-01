package com.platino.summary;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.platino.Draw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Iconos reales (texturas de Cobblemon / objetos) y flechas blancas sin marco. */
public final class Icons {
	public static final Identifier TAB_INFO = Identifier.of("cobblemon", "textures/gui/summary/summary_tab_icon_info.png");
	public static final Identifier TAB_MOVES = Identifier.of("cobblemon", "textures/gui/summary/summary_tab_icon_moves.png");
	public static final Identifier TAB_STATS = Identifier.of("cobblemon", "textures/gui/summary/summary_tab_icon_stats.png");
	public static final Identifier TAB_MARKS = Identifier.of("cobblemon", "textures/gui/summary/summary_tab_icon_marks.png");
	public static final Identifier PARTY_SWAP = Identifier.of("cobblemon", "textures/gui/summary/summary_party_swap_icon.png");

	private static final Map<Identifier, Boolean> EXISTS = new HashMap<>();

	private Icons() {}

	public static boolean exists(Identifier id) {
		return EXISTS.computeIfAbsent(id, k -> {
			try {
				return MinecraftClient.getInstance().getResourceManager().getResource(k).isPresent();
			} catch (Throwable t) {
				return false;
			}
		});
	}

	/** Dibuja una textura completa estirada al rectángulo indicado. */
	public static void tex(DrawContext c, Identifier id, int x, int y, int w, int h) {
		c.drawTexture(id, x, y, w, h, 0f, 0f, 1, 1, 1, 1);
	}

	/** La ball real en la que se capturó el Pokémon (textura del objeto de Cobblemon); si no, la ball dibujada. */
	public static void ball(DrawContext c, Pokemon p, int x, int y, int size) {
		try {
			Identifier ball = p.getCaughtBall().getName();
			Identifier t = Identifier.of(ball.getNamespace(), "textures/item/poke_balls/" + ball.getPath() + ".png");
			if (exists(t)) {
				tex(c, t, x, y, size, size);
				return;
			}
			Item item = Registries.ITEM.get(ball);
			if (item != null && item != net.minecraft.item.Items.AIR) {
				c.getMatrices().push();
				c.getMatrices().translate(x, y, 0);
				c.getMatrices().scale(size / 16f, size / 16f, 1f);
				c.drawItem(new ItemStack(item), 0, 0);
				c.getMatrices().pop();
				return;
			}
		} catch (Throwable ignored) {
		}
		Draw.tinyBall(c, x + (size - 7) / 2, y + (size - 7) / 2);
	}

	/** Objeto como icono (escalado). */
	public static void item(DrawContext c, Identifier itemId, int x, int y, int size) {
		Item item = Registries.ITEM.get(itemId);
		if (item == null || item == net.minecraft.item.Items.AIR) return;
		c.getMatrices().push();
		c.getMatrices().translate(x, y, 0);
		c.getMatrices().scale(size / 16f, size / 16f, 1f);
		c.drawItem(new ItemStack(item), 0, 0);
		c.getMatrices().pop();
	}

	/** Flecha blanca maciza sin marco, con sombra de 1 px. dir: 0 izq, 1 der, 2 arriba, 3 abajo. */
	public static void arrow(DrawContext c, int x, int y, int dir, boolean hover) {
		int col = hover ? 0xFFF8E070 : 0xFFFFFFFF, sh = 0x90000000;
		for (int pass = 0; pass < 2; pass++) {
			int ox = pass == 0 ? 1 : 0, oy = pass == 0 ? 1 : 0, cc = pass == 0 ? sh : col;
			for (int i = 0; i < 4; i++) {
				switch (dir) {
					case 0 -> c.fill(x + ox + i, y + oy + 3 - i, x + ox + i + 1, y + oy + 4 + i, cc);
					case 1 -> c.fill(x + ox + 3 - i, y + oy + 3 - i, x + ox + 4 - i, y + oy + 4 + i, cc);
					case 2 -> c.fill(x + ox + 3 - i, y + oy + i, x + ox + 4 + i, y + oy + i + 1, cc);
					default -> c.fill(x + ox + 3 - i, y + oy + 3 - i, x + ox + 4 + i, y + oy + 4 - i, cc);
				}
			}
		}
	}
}

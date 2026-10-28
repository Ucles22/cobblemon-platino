package com.platino;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

/** Fondo del inventario de Minecraft (tecla E) estilo menú de Pokémon Platino. */
public final class InventoryTheme {
	private InventoryTheme() {}

	public static void drawBackground(DrawContext c, ScreenHandler handler, int x, int y, int w, int h) {
		// Sombra y marco
		Draw.round(c, x + 3, y + 3, x + w + 3, y + h + 3, 0x70000000);
		Draw.panelGradient(c, x, y, x + w, y + h, Draw.OUTLINE, 0xFFD8E0EC, 0xFF98A8C0);
		// Líneas horizontales sutiles (textura DS)
		for (int yy = y + 3; yy < y + h - 3; yy += 3) c.fill(x + 2, yy, x + w - 2, yy + 1, 0x14FFFFFF);
		// Cabecera dorada estilo Platino
		Draw.panelGradient(c, x + 3, y + 3, x + w - 3, y + 6, Draw.GOLD_DARK, 0xFFF8E070, Draw.GOLD);

		// Ventana del jugador (zona del modelo)
		int mx1 = x + 25, my1 = y + 7, mx2 = x + 77, my2 = y + 79;
		Draw.panelGradient(c, mx1, my1, mx2, my2, Draw.OUTLINE, 0xFF4A5878, 0xFF283048);
		for (int i = 0; i < 6; i++) c.fill(mx1 + 2, my2 - 10 - i * 10, mx2 - 2, my2 - 9 - i * 10, 0x18FFFFFF);
		// Suelo (elipse simple)
		c.fill(mx1 + 10, my2 - 8, mx2 - 10, my2 - 5, 0x40000000);

		// Separador sobre el inventario principal
		int invTop = y + 80;
		c.fill(x + 6, invTop + 1, x + w - 6, invTop + 2, Draw.SILVER_DARK);
		c.fill(x + 6, invTop + 2, x + w - 6, invTop + 3, 0x80FFFFFF);

		// Ranuras
		for (int i = 0; i < handler.slots.size(); i++) {
			Slot s = handler.slots.get(i);
			if (!s.isEnabled()) continue;
			int sx = x + s.x - 1, sy = y + s.y - 1;
			if (i == 0 && handler.slots.size() >= 46) {
				// Resultado de fabricación: celda grande dorada
				Draw.panel(c, sx - 4, sy - 4, sx + 22, sy + 22, Draw.GOLD_DARK, 0xFFF8E8A8);
				cell(c, sx, sy, Draw.OUTLINE, 0xFFF8F8F0, 0xFFC8C0A0);
				continue;
			}
			boolean hotbar = i >= 36 && i <= 44;
			cell(c, sx, sy, hotbar ? Draw.GOLD_DARK : Draw.OUTLINE, 0xFFF0F4F8, 0xFFB8C4D0);
		}

		// Flecha de fabricación (entre la cuadrícula 2x2 y el resultado)
		if (handler.slots.size() >= 46) {
			Slot r = handler.slots.get(0);
			int ax = x + r.x - 22, ay = y + r.y + 4;
			c.fill(ax, ay + 3, ax + 10, ay + 6, Draw.OUTLINE);
			for (int i = 0; i < 5; i++) c.fill(ax + 10 + i, ay + i, ax + 11 + i, ay + 9 - i, Draw.OUTLINE);
			c.fill(ax + 1, ay + 4, ax + 10, ay + 5, Draw.GOLD);
		}
	}

	/** Celda de objeto 18x18: borde, sombra interior arriba-izquierda y relleno claro. */
	private static void cell(DrawContext c, int x, int y, int border, int fill, int inner) {
		Draw.round(c, x, y, x + 18, y + 18, border);
		c.fill(x + 1, y + 1, x + 17, y + 17, fill);
		c.fill(x + 1, y + 1, x + 17, y + 2, inner);
		c.fill(x + 1, y + 1, x + 2, y + 17, inner);
	}
}

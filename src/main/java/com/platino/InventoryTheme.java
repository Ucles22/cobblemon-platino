package com.platino;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.screen.slot.Slot;

/** Fondo de inventario (supervivencia y creativo) estilo menú de Pokémon Platino. */
public final class InventoryTheme {
	public enum Layout { SURVIVAL, CREATIVE_INVENTORY, CREATIVE_ITEMS, CREATIVE_SEARCH }

	private InventoryTheme() {}

	public static void drawBackground(DrawContext c, ScreenHandler handler, int x, int y, int w, int h, Layout layout) {
		Draw.batch(c, () -> drawBackgroundFills(c, handler, x, y, w, h, layout));
	}

	private static void drawBackgroundFills(DrawContext c, ScreenHandler handler, int x, int y, int w, int h, Layout layout) {
		// Sombra y marco
		Draw.round(c, x + 3, y + 3, x + w + 3, y + h + 3, 0x70000000);
		Draw.panelGradient(c, x, y, x + w, y + h, Draw.OUTLINE, 0xFFD8E0EC, 0xFF98A8C0);
		for (int yy = y + 3; yy < y + h - 3; yy += 3) c.fill(x + 2, yy, x + w - 2, yy + 1, 0x14FFFFFF);
		// Cabecera dorada
		c.fill(x + 3, y + 2, x + w - 3, y + 4, Draw.GOLD);
		c.fill(x + 3, y + 4, x + w - 3, y + 5, Draw.GOLD_DARK);

		// Ventana del jugador
		if (layout == Layout.SURVIVAL) modelWindow(c, x + 25, y + 7, x + 77, y + 79);
		else if (layout == Layout.CREATIVE_INVENTORY) modelWindow(c, x + 72, y + 5, x + 106, y + 50);

		// Separador sobre la barra/inventario
		int sep = switch (layout) {
			case SURVIVAL -> y + 80;
			case CREATIVE_INVENTORY -> y + 51;
			default -> y + 108;
		};
		c.fill(x + 6, sep + 1, x + w - 6, sep + 2, Draw.SILVER_DARK);
		c.fill(x + 6, sep + 2, x + w - 6, sep + 3, 0x80FFFFFF);

		// Buscador y barra de desplazamiento del creativo
		if (layout == Layout.CREATIVE_SEARCH) {
			Draw.panel(c, x + 79, y + 3, x + 171, y + 16, Draw.OUTLINE, 0xFFF8F8F0);
		}
		if (layout == Layout.CREATIVE_ITEMS || layout == Layout.CREATIVE_SEARCH) {
			Draw.panel(c, x + 173, y + 16, x + 189, y + 130, Draw.OUTLINE, 0xFF4A5878);
		}

		// Ranuras
		Slot result = null;
		for (Slot s : handler.slots) {
			if (!s.isEnabled() || s.x < 0 || s.y < 0 || s.x > w || s.y > h) continue;
			int sx = x + s.x - 1, sy = y + s.y - 1;
			if (s instanceof CraftingResultSlot) {
				result = s;
				Draw.panel(c, sx - 4, sy - 4, sx + 22, sy + 22, Draw.GOLD_DARK, 0xFFF8E8A8);
				cell(c, sx, sy, Draw.OUTLINE, 0xFFF8F8F0, 0xFFC8C0A0);
				continue;
			}
			if (layout == Layout.CREATIVE_INVENTORY && s.x == 173 && s.y == 112) {
				// Papelera del creativo
				cell(c, sx, sy, 0xFF682820, 0xFFE07868, 0xFFB84838);
				for (int i = 0; i < 10; i++) {
					c.fill(sx + 4 + i, sy + 4 + i, sx + 5 + i, sy + 5 + i, 0xFFFFFFFF);
					c.fill(sx + 13 - i, sy + 4 + i, sx + 14 - i, sy + 5 + i, 0xFFFFFFFF);
				}
				continue;
			}
			boolean hotbar = s.inventory instanceof PlayerInventory && s.getIndex() < 9;
			cell(c, sx, sy, hotbar ? Draw.GOLD_DARK : Draw.OUTLINE, 0xFFF0F4F8, 0xFFB8C4D0);
		}

		// Flecha de fabricación
		if (result != null) {
			int ax = x + result.x - 22, ay = y + result.y + 4;
			c.fill(ax, ay + 3, ax + 10, ay + 6, Draw.OUTLINE);
			for (int i = 0; i < 5; i++) c.fill(ax + 10 + i, ay + i, ax + 11 + i, ay + 9 - i, Draw.OUTLINE);
			c.fill(ax + 1, ay + 4, ax + 10, ay + 5, Draw.GOLD);
		}
	}

	private static void modelWindow(DrawContext c, int x1, int y1, int x2, int y2) {
		Draw.panelGradient(c, x1, y1, x2, y2, Draw.OUTLINE, 0xFF4A5878, 0xFF283048);
		for (int yy = y2 - 10; yy > y1 + 2; yy -= 10) c.fill(x1 + 2, yy, x2 - 2, yy + 1, 0x18FFFFFF);
		c.fill(x1 + 8, y2 - 8, x2 - 8, y2 - 5, 0x40000000);
	}

	/** Celda de objeto 18x18. */
	private static void cell(DrawContext c, int x, int y, int border, int fill, int inner) {
		Draw.round(c, x, y, x + 18, y + 18, border);
		c.fill(x + 1, y + 1, x + 17, y + 17, fill);
		c.fill(x + 1, y + 1, x + 17, y + 2, inner);
		c.fill(x + 1, y + 1, x + 2, y + 17, inner);
	}
}

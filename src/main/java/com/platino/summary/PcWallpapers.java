package com.platino.summary;

import com.platino.PlatinoConfig;
import net.minecraft.client.gui.DrawContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fondos de caja inspirados en DPPt, dibujados a mano (formas y degradados, sin texturas). Son solo de este
 * cliente: se guardan en la configuración por PC y caja. Elegir un fondo de Cobblemon quita el propio.
 */
public final class PcWallpapers {
	public enum Theme {
		BOSQUE("Bosque"), CIUDAD("Ciudad"), DESIERTO("Desierto"), SABANA("Sabana"), PENASCO("Peñasco"),
		VOLCAN("Volcán"), NIEVE("Nieve"), CUEVA("Cueva"), PLAYA("Playa"), MARINO("Fondo marino"),
		RIO("Río"), CIELO("Cielo"), CENTRO("Centro Pokémon"), MAQUINA("Máquina"), CUADROS("Cuadros"),
		SIMPLE("Simple");

		public final String label;

		Theme(String label) {
			this.label = label;
		}
	}

	private PcWallpapers() {}

	private static String key(UUID pc, int box) {
		return pc + ":" + box;
	}

	public static Theme get(UUID pc, int box) {
		Map<String, String> m = PlatinoConfig.get().pcWallpapers;
		if (m == null) return null;
		String v = m.get(key(pc, box));
		if (v == null) return null;
		try {
			return Theme.valueOf(v);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	public static void set(UUID pc, int box, Theme theme) {
		PlatinoConfig cfg = PlatinoConfig.get();
		if (cfg.pcWallpapers == null) cfg.pcWallpapers = new HashMap<>();
		if (theme == null) cfg.pcWallpapers.remove(key(pc, box));
		else cfg.pcWallpapers.put(key(pc, box), theme.name());
		PlatinoConfig.save();
	}

	// ---------------------------------------------------------------- Dibujo

	private static void grad(DrawContext c, int x, int y, int w, int h, int top, int bottom) {
		c.fillGradient(x, y, x + w, y + h, top, bottom);
	}

	/** Círculo relleno a base de filas (pixel art). */
	private static void disc(DrawContext c, int cx, int cy, int r, int col) {
		for (int dy = -r; dy <= r; dy++) {
			int dx = (int) Math.round(Math.sqrt(r * r - dy * dy));
			c.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, col);
		}
	}

	/** Triángulo con la punta arriba. */
	private static void peak(DrawContext c, int cx, int baseY, int halfW, int h, int col) {
		for (int i = 0; i < h; i++) {
			int hw = Math.max(0, halfW * i / Math.max(1, h));
			c.fill(cx - hw, baseY - h + i, cx + hw + 1, baseY - h + i + 1, col);
		}
	}

	/** Triángulo con la punta abajo (estalactitas). */
	private static void spike(DrawContext c, int cx, int topY, int halfW, int h, int col) {
		for (int i = 0; i < h; i++) {
			int hw = Math.max(0, halfW * (h - i) / Math.max(1, h));
			c.fill(cx - hw, topY + i, cx + hw + 1, topY + i + 1, col);
		}
	}

	public static void draw(DrawContext c, Theme t, int x, int y, int w, int h) {
		c.enableScissor(x, y, x + w, y + h);
		try {
			drawInner(c, t, x, y, w, h);
		} catch (RuntimeException e) {
			c.fill(x, y, x + w, y + h, 0xFFE0E8F0); // nunca tumbar el juego por un fondo
		} finally {
			c.disableScissor();
		}
	}

	private static void drawInner(DrawContext c, Theme t, int x, int y, int w, int h) {
		switch (t) {
			case BOSQUE -> {
				grad(c, x, y, w, h, 0xFFB8E8A0, 0xFF58A848);
				for (int i = -1; i < w / 18 + 2; i++) {
					int cx = x + i * 18 + 9;
					peak(c, cx, y + h - 6, 10, 34, 0xFF2E7A38);
					peak(c, cx, y + h - 22, 7, 22, 0xFF3C9444);
					c.fill(cx - 1, y + h - 6, cx + 2, y + h, 0xFF6A4828);
				}
				for (int i = 0; i < w / 24 + 1; i++) peak(c, x + i * 24, y + h - 30, 8, 26, 0x5038783C);
			}
			case CIUDAD -> {
				grad(c, x, y, w, h, 0xFF98C8F8, 0xFFD8ECF8);
				int bx = x;
				int[] hs = {50, 72, 40, 90, 60, 78, 46, 66};
				for (int i = 0; bx < x + w; i++) {
					int bw = 16 + (i * 7) % 10, bh = Math.min(h - 6, hs[i % hs.length]);
					c.fill(bx, y + h - bh, bx + bw, y + h, i % 2 == 0 ? 0xFF7888A0 : 0xFF8898B0);
					for (int wy = y + h - bh + 4; wy < y + h - 4; wy += 7)
						for (int wx = bx + 3; wx < bx + bw - 3; wx += 5) c.fill(wx, wy, wx + 2, wy + 3, 0xFFF8F0B0);
					bx += bw + 2;
				}
			}
			case DESIERTO -> {
				grad(c, x, y, w, h, 0xFFF8E0A0, 0xFFE8B868);
				disc(c, x + w - 24, y + 22, 12, 0xFFF8F0D0);
				for (int i = 0; i < 3; i++) {
					int base = y + h - i * 22;
					for (int xx = x; xx < x + w; xx++) {
						int top = base - 10 - (int) (8 * Math.sin((xx - x + i * 30) / 18.0));
						c.fill(xx, top, xx + 1, base, i % 2 == 0 ? 0xFFD8A050 : 0xFFE8B868);
					}
				}
			}
			case SABANA -> {
				grad(c, x, y, w, h, 0xFFF8B060, 0xFFF8E0A0);
				disc(c, x + w / 2, y + h - 30, 18, 0xFFF89838);
				c.fill(x, y + h - 22, x + w, y + h, 0xFFC8A040);
				for (int i = 0; i < w / 6; i++) c.fill(x + i * 6, y + h - 26 + (i % 3), x + i * 6 + 2, y + h - 22, 0xFFA88028);
				int tx = x + w / 4;
				c.fill(tx, y + h - 50, tx + 3, y + h - 22, 0xFF604020);
				c.fill(tx - 18, y + h - 56, tx + 21, y + h - 48, 0xFF506828);
			}
			case PENASCO -> {
				grad(c, x, y, w, h, 0xFFC8C0B0, 0xFF988870);
				for (int i = 0; i < w / 30 + 2; i++) {
					peak(c, x + i * 30, y + h, 22, 70 - (i % 3) * 14, 0xFF786858);
					peak(c, x + i * 30 + 15, y + h, 16, 44 + (i % 2) * 10, 0xFF685848);
				}
			}
			case VOLCAN -> {
				grad(c, x, y, w, h, 0xFF582020, 0xFFC84828);
				peak(c, x + w / 2, y + h, w / 2 - 6, h - 30, 0xFF402828);
				c.fill(x + w / 2 - 8, y + 30, x + w / 2 + 8, y + 34, 0xFFF88828);
				for (int i = 0; i < 4; i++) c.fill(x + w / 2 - 6 + i * 4, y + 34, x + w / 2 - 4 + i * 4, y + 50 + i * 9, 0xFFF86818);
				for (int i = 0; i < 8; i++) disc(c, x + 14 + (i * 37) % Math.max(1, w - 20), y + 10 + (i * 23) % 30, 2, 0xFFF8C848);
			}
			case NIEVE -> {
				grad(c, x, y, w, h, 0xFFD0E8F8, 0xFFF8FCFF);
				for (int i = 0; i < 40; i++) {
					int fx = x + (i * 53) % w, fy = y + (i * 37) % h;
					c.fill(fx - 1, fy, fx + 2, fy + 1, 0xFFFFFFFF);
					c.fill(fx, fy - 1, fx + 1, fy + 2, 0xFFFFFFFF);
				}
				for (int i = 0; i < w / 26 + 2; i++) peak(c, x + i * 26, y + h, 18, 24, 0xFFE8F0F8);
			}
			case CUEVA -> {
				grad(c, x, y, w, h, 0xFF483828, 0xFF786048);
				for (int i = 0; i < w / 14 + 1; i++) spike(c, x + i * 14 + 7, y, 5, 12 + (i * 7) % 16, 0xFF382818);
				for (int i = 0; i < w / 18 + 1; i++) peak(c, x + i * 18 + 3, y + h, 6, 8 + (i * 5) % 12, 0xFF382818);
			}
			case PLAYA -> {
				grad(c, x, y, w, h / 2, 0xFF80C8F8, 0xFFC8E8F8);
				c.fill(x, y + h / 2, x + w, y + h * 3 / 4, 0xFF4898E0);
				for (int i = 0; i < w / 10; i++) c.fill(x + i * 10, y + h / 2 + 6 + (i % 2) * 4, x + i * 10 + 6, y + h / 2 + 7 + (i % 2) * 4, 0xFFB0D8F8);
				c.fill(x, y + h * 3 / 4, x + w, y + h, 0xFFF0D898);
				disc(c, x + 26, y + 20, 10, 0xFFF8F0A0);
			}
			case MARINO -> {
				grad(c, x, y, w, h, 0xFF3070C0, 0xFF102858);
				for (int i = 0; i < 14; i++) {
					int bx = x + (i * 41) % w, by = y + (i * 29) % h;
					disc(c, bx, by, 2 + i % 3, 0x5090C8F8);
				}
				for (int i = 0; i < w / 16; i++) c.fill(x + i * 16 + 4, y + h - 18 - (i % 3) * 6, x + i * 16 + 6, y + h, 0xFF207850);
			}
			case RIO -> {
				grad(c, x, y, w, h, 0xFF88C860, 0xFF589838);
				for (int yy = 0; yy < h; yy++) {
					int cx = x + w / 2 + (int) (14 * Math.sin(yy / 16.0));
					c.fill(cx - 16, y + yy, cx + 16, y + yy + 1, 0xFF4890E0);
					if (yy % 9 == 0) c.fill(cx - 8, y + yy, cx, y + yy + 1, 0xFFA8D0F8);
				}
			}
			case CIELO -> {
				grad(c, x, y, w, h, 0xFF4890E8, 0xFFB8E0F8);
				for (int i = 0; i < 5; i++) {
					int cx = x + 20 + (i * 47) % Math.max(1, w - 30), cy = y + 18 + (i * 31) % Math.max(1, h - 30);
					disc(c, cx, cy, 7, 0xE0FFFFFF);
					disc(c, cx + 9, cy + 2, 6, 0xE0FFFFFF);
					disc(c, cx - 9, cy + 2, 5, 0xE0FFFFFF);
				}
			}
			case CENTRO -> {
				grad(c, x, y, w, h, 0xFFF8D8E0, 0xFFF8B8C8);
				for (int i = 0; i < 6; i++) {
					int cx = x + 16 + (i % 3) * (w / 3), cy = y + 22 + (i / 3) * (h / 2);
					disc(c, cx, cy, 10, 0x60F85878);
					c.fill(cx - 10, cy, cx + 11, cy + 1, 0x60FFFFFF);
					disc(c, cx, cy, 3, 0x80FFFFFF);
				}
			}
			case MAQUINA -> {
				grad(c, x, y, w, h, 0xFF404858, 0xFF283038);
				for (int i = 0; i < 12; i++) {
					int lx = x + (i * 31) % w, ly = y + (i * 17) % h;
					c.fill(lx, ly, lx + 30, ly + 1, 0xFF58D0A0);
					c.fill(lx + 30, ly, lx + 31, ly + 14, 0xFF58D0A0);
					c.fill(lx + 29, ly + 13, lx + 33, ly + 17, 0xFF88F8C8);
				}
			}
			case CUADROS -> {
				c.fill(x, y, x + w, y + h, 0xFF68B8B0);
				for (int yy = 0; yy < h; yy += 10)
					for (int xx = ((yy / 10) % 2) * 10; xx < w; xx += 20) c.fill(x + xx, y + yy, x + xx + 10, y + yy + 10, 0xFF88D0C8);
			}
			case SIMPLE -> {
				c.fill(x, y, x + w, y + h, 0xFFF0F0E8);
				for (int yy = 0; yy < h; yy += 8) c.fill(x, y + yy, x + w, y + yy + 4, 0xFFE0E8F0);
			}
		}
	}
}

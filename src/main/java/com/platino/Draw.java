package com.platino;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Utilidades de dibujo "pixel art" estilo DS (bordes redondeados, texto con sombra clara, barras). */
public final class Draw {
	private Draw() {}

	// Paleta Platino
	public static final int OUTLINE = 0xFF303838;
	public static final int CREAM = 0xFFF8F8F0;
	public static final int CREAM_DARK = 0xFFE0E0D0;
	public static final int SILVER = 0xFFA8B0B8;
	public static final int SILVER_DARK = 0xFF68707C;
	public static final int GOLD = 0xFFF8C830;
	public static final int GOLD_DARK = 0xFFB88818;
	public static final int TEXT_DARK = 0xFF414141;
	public static final int TEXT_SHADOW = 0xFFC8C8C0;
	public static final int MALE = 0xFF3088F8;
	public static final int FEMALE = 0xFFF85888;
	public static final int HP_GREEN = 0xFF58D080;
	public static final int HP_GREEN_HI = 0xFF70F8A8;
	public static final int HP_YELLOW = 0xFFF8C830;
	public static final int HP_YELLOW_HI = 0xFFF8E070;
	public static final int HP_RED = 0xFFF85838;
	public static final int HP_RED_HI = 0xFFF8A088;
	public static final int EXP_BLUE = 0xFF40A8F8;
	public static final int EXP_BLUE_HI = 0xFF88D0F8;

	private static final String ELLIPSIS = "…";
	private static int batchDepth;

	public static TextRenderer font() {
		return MinecraftClient.getInstance().textRenderer;
	}

	public static void batch(DrawContext c, Runnable drawing) {
		if (batchDepth > 0) {
			drawing.run();
			return;
		}
		batchDepth++;
		try {
			c.draw(drawing);
		} catch (RuntimeException | Error e) {
			releaseDrawCallback(c);
			throw e;
		} finally {
			batchDepth--;
		}
	}

	private static void releaseDrawCallback(DrawContext c) {
		c.draw(() -> {});
	}

	/** Rectángulo con esquinas recortadas de 1 px. */
	public static void round(DrawContext c, int x1, int y1, int x2, int y2, int color) {
		batch(c, () -> {
			c.fill(x1 + 1, y1, x2 - 1, y1 + 1, color);
			c.fill(x1, y1 + 1, x2, y2 - 1, color);
			c.fill(x1 + 1, y2 - 1, x2 - 1, y2, color);
		});
	}

	/** Panel con borde oscuro de 1 px y relleno (esquinas de 2 px). */
	public static void panel(DrawContext c, int x1, int y1, int x2, int y2, int border, int fill) {
		batch(c, () -> {
			c.fill(x1 + 2, y1, x2 - 2, y1 + 1, border);
			c.fill(x1 + 2, y2 - 1, x2 - 2, y2, border);
			c.fill(x1, y1 + 2, x1 + 1, y2 - 2, border);
			c.fill(x2 - 1, y1 + 2, x2, y2 - 2, border);
			c.fill(x1 + 1, y1 + 1, x1 + 2, y1 + 2, border);
			c.fill(x2 - 2, y1 + 1, x2 - 1, y1 + 2, border);
			c.fill(x1 + 1, y2 - 2, x1 + 2, y2 - 1, border);
			c.fill(x2 - 2, y2 - 2, x2 - 1, y2 - 1, border);
			c.fill(x1 + 2, y1 + 1, x2 - 2, y2 - 1, fill);
			c.fill(x1 + 1, y1 + 2, x1 + 2, y2 - 2, fill);
			c.fill(x2 - 2, y1 + 2, x2 - 1, y2 - 2, fill);
		});
	}

	/** Panel con degradado vertical. */
	public static void panelGradient(DrawContext c, int x1, int y1, int x2, int y2, int border, int top, int bottom) {
		batch(c, () -> {
			panel(c, x1, y1, x2, y2, border, top);
			c.fillGradient(x1 + 1, y1 + 2, x2 - 1, y2 - 2, top, bottom);
		});
	}

	/** Texto estilo DS: color oscuro con sombra clara desplazada 1 px. */
	public static void dsText(DrawContext c, String s, int x, int y, int color, int shadow) {
		TextRenderer f = font();
		c.draw();
		c.drawText(f, s, x + 1, y + 1, shadow, false);
		c.draw();
		c.getMatrices().push();
		c.getMatrices().translate(0, 0, 0.5f);
		c.drawText(f, s, x, y, color, false);
		c.getMatrices().pop();
		c.draw();
	}

	public static void dsText(DrawContext c, Text t, int x, int y, int color, int shadow) {
		dsText(c, t.getString(), x, y, color, shadow);
	}

	public static int width(String s) {
		return font().getWidth(s);
	}

	/** Recorta un texto para que quepa en maxW píxeles. */
	public static String fit(String s, int maxW) {
		TextRenderer f = font();
		if (s.isEmpty() || f.getWidth(s) <= maxW) return s;
		String head = f.trimToWidth(s, Math.max(0, maxW - f.getWidth(ELLIPSIS)));
		if (head.isEmpty()) head = s.substring(0, Character.charCount(s.codePointAt(0)));
		return head + ELLIPSIS;
	}

	/** Barra de PS estilo Platino con etiqueta "PS". */
	public static void hpBar(DrawContext c, int x, int y, int w, float ratio, boolean label) {
		int bx = x;
		if (label) {
			// etiqueta "PS" dorada sobre fondo oscuro
			round(c, x, y, x + 15, y + 7, OUTLINE);
			c.drawText(font(), com.platino.Tr.t("PS"), x + 2, y, GOLD, false);
			bx = x + 14;
		}
		int barX = bx;
		float clamped = Math.max(0, Math.min(1, ratio));
		batch(c, () -> drawHpFill(c, barX, y, w - (barX - x), clamped));
	}

	private static void drawHpFill(DrawContext c, int bx, int y, int bw, float ratio) {
		round(c, bx, y, bx + bw, y + 7, OUTLINE);
		c.fill(bx + 1, y + 1, bx + bw - 1, y + 6, 0xFF505858);
		int fillW = Math.round((bw - 2) * ratio);
		if (ratio > 0 && fillW < 1) fillW = 1;
		int col, hi;
		if (ratio > 0.5f) { col = HP_GREEN; hi = HP_GREEN_HI; }
		else if (ratio > 0.2f) { col = HP_YELLOW; hi = HP_YELLOW_HI; }
		else { col = HP_RED; hi = HP_RED_HI; }
		if (fillW > 0) {
			c.fill(bx + 1, y + 1, bx + 1 + fillW, y + 6, col);
			c.fill(bx + 1, y + 2, bx + 1 + fillW, y + 3, hi);
		}
	}

	public static void expBar(DrawContext c, int x, int y, int w, float ratio) {
		float clamped = Math.max(0, Math.min(1, ratio));
		batch(c, () -> {
			c.fill(x, y, x + w, y + 3, OUTLINE);
			c.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFF505858);
			int fw = Math.round((w - 2) * clamped);
			if (fw > 0) {
				c.fill(x + 1, y + 1, x + 1 + fw, y + 2, EXP_BLUE);
			}
		});
	}

	/** Insignia de estado alterado (PAR, QUE, ENV, DOR, CON). */
	public static int statusBadge(DrawContext c, String showdown, int x, int y) {
		if (showdown == null || showdown.isEmpty()) return 0;
		String label;
		int col;
		switch (showdown.toLowerCase()) {
			case "par" -> { label = "PAR"; col = 0xFFD8B020; }
			case "brn" -> { label = "QUE"; col = 0xFFE06030; }
			case "psn", "tox" -> { label = "ENV"; col = 0xFFA040A0; }
			case "slp" -> { label = "DOR"; col = 0xFF8890A0; }
			case "frz" -> { label = "CON"; col = 0xFF50B8E0; }
			default -> { return 0; }
		}
		int w = width(label) + 4;
		round(c, x, y, x + w, y + 9, col);
		c.drawText(font(), label, x + 2, y + 1, 0xFFFFFFFF, false);
		return w;
	}

	/** Pokéball diminuta (indicador de capturado). */
	public static void tinyBall(DrawContext c, int x, int y) {
		batch(c, () -> {
			c.fill(x + 1, y, x + 6, y + 1, OUTLINE);
			c.fill(x, y + 1, x + 7, y + 6, OUTLINE);
			c.fill(x + 1, y + 6, x + 6, y + 7, OUTLINE);
			c.fill(x + 1, y + 1, x + 6, y + 3, 0xFFE83030);
			c.fill(x + 1, y + 4, x + 6, y + 6, 0xFFF8F8F8);
			c.fill(x + 3, y + 3, x + 4, y + 4, 0xFFF8F8F8);
		});
	}

	public static int lerpColor(int a, int b, float t) {
		int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
		int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
		return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
				| ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
	}
}

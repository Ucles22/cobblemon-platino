package com.platino;

import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleGeneralActionSelection;
import com.cobblemon.mod.common.client.gui.battle.widgets.BattleOptionTile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

/** Menú de combate estilo pantalla táctil de Diamante/Perla/Platino: LUCHAR, BOLSA, HUIR, POKéMON. */
public final class DsBattleMenu {
	public static final int W = 176, H = 112;
	/** Origen y escala en pantalla; todo se dibuja en coordenadas locales (0..W, 0..H). */
	private static float ox, oy, scale = 1f;
	private static final int px = 0, py = 0;
	private static long lastRender;

	private DsBattleMenu() {}

	private enum Btn {
		FIGHT("LUCHAR", 16, 14, 160, 70, 0xFF781818, 0xFFF06060, 0xFFC83030, 0xFF681010),
		BAG("BOLSA", 4, 76, 60, 108, 0xFF704008, 0xFFF8B838, 0xFFD88818, 0xFF603000),
		RUN("HUIR", 64, 80, 112, 108, 0xFF183870, 0xFF68A0F8, 0xFF3068D0, 0xFF102858),
		PARTY("POKéMON", 116, 76, 172, 108, 0xFF185818, 0xFF68D068, 0xFF38A038, 0xFF0C400C);

		final String label;
		final int x1, y1, x2, y2, border, top, bottom, shadow;

		Btn(String label, int x1, int y1, int x2, int y2, int border, int top, int bottom, int shadow) {
			this.label = label; this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
			this.border = border; this.top = top; this.bottom = bottom; this.shadow = shadow;
		}

		boolean hit(double mx, double my) {
			double lx = (mx - ox) / scale, ly = (my - oy) / scale;
			return lx >= x1 && lx < x2 && ly >= y1 && ly < y2;
		}
	}

	public static boolean isActive() {
		return System.currentTimeMillis() - lastRender < 250;
	}

	private static void layout() {
		var win = MinecraftClient.getInstance().getWindow();
		PlatinoConfig cfg = PlatinoConfig.get();
		scale = Math.max(0.5f, Math.min(1.5f, cfg.battleMenuScale));
		float w = W * scale, h = H * scale;
		boolean left = !"derecha".equalsIgnoreCase(cfg.battleMenuSide);
		ox = left ? 8 + cfg.battleMenuOffsetX : win.getScaledWidth() - w - 8 + cfg.battleMenuOffsetX;
		oy = win.getScaledHeight() - h - 6 - cfg.battleMenuOffsetY;
	}

	/** Rectángulo que ocupa el menú en pantalla: {x1, y1, x2, y2}. */
	public static float[] bounds() {
		layout();
		return new float[]{ox, oy, ox + W * scale, oy + H * scale};
	}

	private static BattleOptionTile tile(BattleGeneralActionSelection sel, Btn b) {
		for (BattleOptionTile t : sel.getTiles()) {
			String p = t.getResource().getPath();
			switch (b) {
				case FIGHT -> { if (p.contains("fight")) return t; }
				case BAG -> { if (p.contains("bag")) return t; }
				case PARTY -> { if (p.contains("switch")) return t; }
				case RUN -> { if (p.contains("run") || p.contains("forfeit")) return t; }
			}
		}
		return null;
	}

	public static void render(DrawContext c, BattleGeneralActionSelection sel, int mx, int my) {
		layout();
		lastRender = System.currentTimeMillis();
		c.getMatrices().push();
		c.getMatrices().translate(ox, oy, 300);
		c.getMatrices().scale(scale, scale, 1f);

		// Fondo gris con banda blanca superior y Pokéball
		Draw.round(c, px + 3, py + 3, px + W + 3, py + H + 3, 0x60000000);
		Draw.panel(c, px, py, px + W, py + H, 0xFF202020, 0xFF585858);
		c.fill(px + 2, py + 1, px + W - 2, py + 9, 0xFFF0F0F0);
		c.fill(px + 2, py + 9, px + W - 2, py + 10, 0xFFA8A8A8);
		Draw.tinyBall(c, px + 4, py + 2);
		// Huecos decorativos (como la pantalla táctil)
		Draw.round(c, px + 4, py + 18, px + 14, py + 66, 0xFF707070);
		Draw.round(c, px + 162, py + 18, px + 172, py + 66, 0xFF707070);

		for (Btn b : Btn.values()) {
			BattleOptionTile t = tile(sel, b);
			boolean enabled = t != null;
			boolean hover = enabled && b.hit(mx, my);
			String label = b == Btn.RUN && t != null && t.getResource().getPath().contains("forfeit") ? "RENDIRSE" : b.label;
			drawButton(c, b, label, enabled, hover);
		}
		c.getMatrices().pop();
	}

	private static void drawButton(DrawContext c, Btn b, String label, boolean enabled, boolean hover) {
		int x1 = px + b.x1, y1 = py + b.y1 - (hover ? 1 : 0), x2 = px + b.x2, y2 = py + b.y2 - (hover ? 1 : 0);
		int border = enabled ? b.border : 0xFF383838;
		int top = enabled ? b.top : 0xFF909090;
		int bottom = enabled ? b.bottom : 0xFF686868;
		if (hover) {
			top = Draw.lerpColor(top, 0xFFFFFFFF, 0.25f);
			bottom = Draw.lerpColor(bottom, 0xFFFFFFFF, 0.2f);
		}
		// Sombra inferior (efecto de botón)
		Draw.round(c, x1, y1 + 2, x2, y2 + (hover ? 3 : 2), 0xFF282828);
		Draw.panelGradient(c, x1, y1, x2, y2, border, top, bottom);
		// Brillo interior
		c.fill(x1 + 3, y1 + 2, x2 - 3, y1 + 3, Draw.lerpColor(top, 0xFFFFFFFF, 0.5f));
		if (b == Btn.FIGHT) {
			// Marco interior como en el botón grande de DPPt
			int ix1 = x1 + 5, iy1 = y1 + 5, ix2 = x2 - 5, iy2 = y2 - 5;
			c.fill(ix1, iy1, ix2, iy1 + 1, Draw.lerpColor(bottom, 0xFF000000, 0.3f));
			c.fill(ix1, iy2 - 1, ix2, iy2, Draw.lerpColor(top, 0xFFFFFFFF, 0.3f));
			c.fill(ix1, iy1, ix1 + 1, iy2, Draw.lerpColor(bottom, 0xFF000000, 0.3f));
			c.fill(ix2 - 1, iy1, ix2, iy2, Draw.lerpColor(top, 0xFFFFFFFF, 0.3f));
		}
		float scale = b == Btn.FIGHT ? 1.5f : 1f;
		int tw = Draw.width(label);
		if (tw * scale > (x2 - x1) - 6) scale = ((x2 - x1) - 6) / (float) tw;
		float cx = (x1 + x2) / 2f - tw * scale / 2f;
		float cy = (y1 + y2) / 2f - 4 * scale;
		c.getMatrices().push();
		c.getMatrices().translate(cx, cy, 0);
		c.getMatrices().scale(scale, scale, 1);
		int shadow = enabled ? b.shadow : 0xFF404040;
		c.drawText(Draw.font(), label, 1, 1, shadow, false);
		c.drawText(Draw.font(), label, 0, 0, 0xFFFFFFFF, false);
		c.getMatrices().pop();
	}

	/** Devuelve true si el clic cayó en un botón (y ejecuta su acción). */
	public static boolean click(BattleGeneralActionSelection sel, double mx, double my) {
		layout();
		for (Btn b : Btn.values()) {
			if (!b.hit(mx, my)) continue;
			BattleOptionTile t = tile(sel, b);
			if (t == null) return true;
			MinecraftClient.getInstance().getSoundManager()
					.play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.4f));
			t.getOnClick().invoke();
			return true;
		}
		return false;
	}
}

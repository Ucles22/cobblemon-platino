package com.platino;

import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleGeneralActionSelection;
import com.cobblemon.mod.common.client.gui.battle.widgets.BattleOptionTile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

/**
 * Menú de combate estilo pantalla táctil de Diamante/Perla/Platino: LUCHAR, BOLSA, HUIR, POKéMON.
 * Se escala calculando las coordenadas (no con la matriz) para que el texto siga nítido.
 */
public final class DsBattleMenu {
	public static final int W = 176, H = 112;
	private static int ox, oy;
	private static float scale = 1f;
	private static long lastRender;
	private static long blockedUntil;
	private static Btn blockedBtn;

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

		int sx1() { return ox + s(x1); }
		int sy1() { return oy + s(y1); }
		int sx2() { return ox + s(x2); }
		int sy2() { return oy + s(y2); }

		boolean hit(double mx, double my) {
			return mx >= sx1() && mx < sx2() && my >= sy1() && my < sy2();
		}
	}

	private static int s(int v) {
		return Math.round(v * scale);
	}

	public static boolean isActive() {
		return System.currentTimeMillis() - lastRender < 250;
	}

	private static void layout() {
		var win = MinecraftClient.getInstance().getWindow();
		PlatinoConfig cfg = PlatinoConfig.get();
		scale = Math.max(0.6f, Math.min(1.5f, cfg.battleMenuScale));
		boolean left = !"derecha".equalsIgnoreCase(cfg.battleMenuSide);
		ox = left ? 8 + cfg.battleMenuOffsetX : win.getScaledWidth() - s(W) - 8 + cfg.battleMenuOffsetX;
		oy = win.getScaledHeight() - s(H) - 6 - cfg.battleMenuOffsetY;
	}

	/** Rectángulo que ocupa el menú en pantalla: {x1, y1, x2, y2}. */
	public static float[] bounds() {
		layout();
		return new float[]{ox, oy, ox + s(W), oy + s(H)};
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
		c.getMatrices().translate(0, 0, 300);

		int x2 = ox + s(W), y2 = oy + s(H);
		Draw.round(c, ox + 3, oy + 3, x2 + 3, y2 + 3, 0x60000000);
		Draw.panel(c, ox, oy, x2, y2, 0xFF202020, 0xFF585858);
		c.fill(ox + 2, oy + 1, x2 - 2, oy + s(9), 0xFFF0F0F0);
		c.fill(ox + 2, oy + s(9), x2 - 2, oy + s(9) + 1, 0xFFA8A8A8);
		if (scale >= 0.9f) Draw.tinyBall(c, ox + 4, oy + 1);
		Draw.round(c, ox + s(4), oy + s(18), ox + s(14), oy + s(66), 0xFF707070);
		Draw.round(c, ox + s(162), oy + s(18), ox + s(172), oy + s(66), 0xFF707070);

		for (Btn b : Btn.values()) {
			BattleOptionTile t = tile(sel, b);
			boolean available = t != null;
			boolean hover = b.hit(mx, my);
			String label = b == Btn.RUN && t != null && t.getResource().getPath().contains("forfeit") ? "RENDIRSE" : b.label;
			boolean shake = b == blockedBtn && System.currentTimeMillis() < blockedUntil;
			drawButton(c, b, label, available, hover, shake);
		}
		c.getMatrices().pop();
	}

	private static void drawButton(DrawContext c, Btn b, String label, boolean available, boolean hover, boolean shake) {
		int lift = hover ? 1 : 0;
		int dx = shake ? (int) Math.round(Math.sin(System.currentTimeMillis() / 25.0) * 2) : 0;
		int x1 = b.sx1() + dx, y1 = b.sy1() - lift, x2 = b.sx2() + dx, y2 = b.sy2() - lift;
		int top = b.top, bottom = b.bottom;
		if (!available) {
			// Mantiene su color, solo algo apagado
			top = Draw.lerpColor(top, 0xFF808080, 0.35f);
			bottom = Draw.lerpColor(bottom, 0xFF606060, 0.35f);
		} else if (hover) {
			top = Draw.lerpColor(top, 0xFFFFFFFF, 0.25f);
			bottom = Draw.lerpColor(bottom, 0xFFFFFFFF, 0.2f);
		}
		Draw.round(c, x1, y1 + 2, x2, y2 + 2 + lift, 0xFF282828);
		Draw.panelGradient(c, x1, y1, x2, y2, b.border, top, bottom);
		c.fill(x1 + 3, y1 + 2, x2 - 3, y1 + 3, Draw.lerpColor(top, 0xFFFFFFFF, 0.5f));
		if (b == Btn.FIGHT) {
			int in = Math.max(3, s(5));
			int dark = Draw.lerpColor(bottom, 0xFF000000, 0.3f), light = Draw.lerpColor(top, 0xFFFFFFFF, 0.3f);
			c.fill(x1 + in, y1 + in, x2 - in, y1 + in + 1, dark);
			c.fill(x1 + in, y2 - in - 1, x2 - in, y2 - in, light);
			c.fill(x1 + in, y1 + in, x1 + in + 1, y2 - in, dark);
			c.fill(x2 - in - 1, y1 + in, x2 - in, y2 - in, light);
		}
		// Texto siempre a escala entera (nítido). LUCHAR en x2 si cabe.
		int tw = Draw.width(label);
		int ts = (b == Btn.FIGHT && tw * 2 + 12 < x2 - x1 && 18 < y2 - y1 - 10) ? 2 : 1;
		int tx = (x1 + x2) / 2 - tw * ts / 2;
		int ty = (y1 + y2) / 2 - 4 * ts;
		c.getMatrices().push();
		c.getMatrices().translate(tx, ty, 0);
		c.getMatrices().scale(ts, ts, 1);
		Draw.dsText(c, label, 0, 0, available ? 0xFFFFFFFF : 0xFFE8E8E8, b.shadow);
		c.getMatrices().pop();
	}

	/** Devuelve true si el clic cayó en un botón (y ejecuta su acción). */
	public static boolean click(BattleGeneralActionSelection sel, double mx, double my) {
		layout();
		MinecraftClient mc = MinecraftClient.getInstance();
		for (Btn b : Btn.values()) {
			if (!b.hit(mx, my)) continue;
			BattleOptionTile t = tile(sel, b);
			if (t == null) {
				blockedBtn = b;
				blockedUntil = System.currentTimeMillis() + 350;
				mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_BASS, 0.6f));
				String msg = switch (b) {
					case BAG -> "¡No puedes usar la BOLSA en este combate!";
					case RUN -> "¡No puedes huir de este combate!";
					default -> "¡No puedes hacer eso ahora!";
				};
				DsTextBox.flash(msg);
				if (mc.player != null && !PlatinoConfig.get().battleText) mc.player.sendMessage(Text.literal(msg), true);
				return true;
			}
			mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.4f));
			t.getOnClick().invoke();
			return true;
		}
		return false;
	}
}

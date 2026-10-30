package com.platino;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Pokéreloj estilo Diamante/Perla/Platino: varias aplicaciones que se cambian con los botones rojos. */
public class PoketchScreen extends Screen {
	public enum App { CLOCK, TEAM, STEPS, COIN, TYPES, CALENDAR, CALC }

	// Dispositivo
	private static final int DW = 232, DH = 176;
	private static final int SX = 16, SY = 14, SW = 184, SH = 148;

	// Paletas de pantalla LCD: fondo, oscuro, medio, claro
	private static final int[] GREEN = {0xFF90C890, 0xFF284828, 0xFF60A060, 0xFFB8E0B0};
	private static final int[] PINK = {0xFFD09088, 0xFF603030, 0xFFA86860, 0xFFE8B8B0};
	private static final int[] GRAY = {0xFFD8D8D8, 0xFF383838, 0xFF909090, 0xFFF0F0F0};
	private static final int[] BLUE = {0xFF98ACE0, 0xFF202C58, 0xFF6878B8, 0xFFC8D4F4};

	private static final String[] MONTHS = {"ENERO", "FEBRERO", "MARZO", "ABRIL", "MAYO", "JUNIO", "JULIO",
			"AGOSTO", "SEPTIEMBRE", "OCTUBRE", "NOVIEMBRE", "DICIEMBRE"};
	private static final String[] DAYS = {"LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB", "DOM"};
	private static final int[] SEGMENTS = {0b0111111, 0b0000110, 0b1011011, 0b1001111, 0b1100110,
			0b1101101, 0b1111101, 0b0000111, 0b1111111, 0b1101111};

	private record Hit(int x1, int y1, int x2, int y2, Runnable action) {
		boolean contains(double x, double y) {
			return x >= x1 && x < x2 && y >= y1 && y < y2;
		}
	}

	private final List<Hit> hits = new ArrayList<>();
	private final Random random = new Random();
	private int dx, dy;
	private App app;
	private long pressedTop, pressedBottom, opened;

	// Calculadora
	private String calcDisplay = "0";
	private double calcAcc;
	private char calcOp;
	private boolean calcFresh = true;
	// Moneda
	private long coinStart = -10_000;
	private boolean coinHeads = true;
	private int coinFlips;
	// Tipos
	private int typeAttack = 0, typeDef1 = 0, typeDef2 = -1;
	// Calendario
	private YearMonth calMonth = YearMonth.now();

	public PoketchScreen() {
		super(Text.literal("Pokéreloj"));
		App[] apps = App.values();
		app = apps[Math.floorMod(PlatinoConfig.get().poketchApp, apps.length)];
	}

	@Override
	protected void init() {
		dx = (width - DW) / 2;
		dy = (height - DH) / 2;
		opened = System.currentTimeMillis();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
		c.fill(0, 0, width, height, 0x60101018);
	}

	// ---------------------------------------------------------------- Dibujo general

	@Override
	public void render(DrawContext c, int mouseX, int mouseY, float delta) {
		super.render(c, mouseX, mouseY, delta);
		hits.clear();
		float t = Math.min(1f, (System.currentTimeMillis() - opened) / 160f);
		dx = (width - DW) / 2;
		dy = (height - DH) / 2 + Math.round((1 - t) * 16);

		c.getMatrices().push();
		c.getMatrices().translate(0, 0, 100);
		drawBody(c, mouseX, mouseY);
		int sx = dx + SX, sy = dy + SY;
		c.enableScissor(sx, sy, sx + SW, sy + SH);
		switch (app) {
			case CLOCK -> drawClock(c, sx, sy);
			case TEAM -> drawTeam(c, sx, sy, delta);
			case STEPS -> drawSteps(c, sx, sy, mouseX, mouseY);
			case COIN -> drawCoin(c, sx, sy, mouseX, mouseY);
			case TYPES -> drawTypes(c, sx, sy, mouseX, mouseY);
			case CALENDAR -> drawCalendar(c, sx, sy, mouseX, mouseY);
			case CALC -> drawCalc(c, sx, sy, mouseX, mouseY);
		}
		c.disableScissor();
		c.getMatrices().pop();
	}

	private void drawBody(DrawContext c, int mx, int my) {
		// Correa
		c.fill(dx + 44, dy, dx + 172, dy + 10, 0xFF303030);
		c.fill(dx + 44, dy + DH - 10, dx + 172, dy + DH, 0xFF303030);
		c.fill(dx + 48, dy + 2, dx + 168, dy + 4, 0xFF484848);
		// Carcasa
		Draw.round(c, dx + 7, dy + 9, dx + DW - 3, dy + DH - 5, 0x60000000);
		Draw.panelGradient(c, dx + 4, dy + 6, dx + DW - 6, dy + DH - 8, 0xFF7888A0, 0xFFE8EEF4, 0xFFC8D2DC);
		// Marco de la pantalla
		Draw.panel(c, dx + SX - 3, dy + SY - 3, dx + SX + SW + 3, dy + SY + SH + 3, 0xFF404850, 0xFF586068);
		int[] p = palette();
		c.fill(dx + SX, dy + SY, dx + SX + SW, dy + SY + SH, p[0]);

		// Botones rojos laterales
		long now = System.currentTimeMillis();
		int bx1 = dx + SX + SW + 8, bx2 = dx + DW - 10;
		redButton(c, bx1, dy + 26, bx2, dy + 84, now - pressedTop < 140, mx, my);
		redButton(c, bx1, dy + 86, bx2, dy + 144, now - pressedBottom < 140, mx, my);
		hits.add(new Hit(bx1, dy + 26, bx2, dy + 84, () -> switchApp(-1)));
		hits.add(new Hit(bx1, dy + 86, bx2, dy + 144, () -> switchApp(1)));

		// Indicador de aplicación (puntos en la carcasa)
		App[] apps = App.values();
		int total = apps.length * 6;
		int ix = dx + SX + (SW - total) / 2;
		for (int i = 0; i < apps.length; i++) {
			int col = apps[i] == app ? 0xFFE84848 : 0xFF98A4B0;
			c.fill(ix + i * 6, dy + DH - 14, ix + i * 6 + 4, dy + DH - 12, col);
		}
	}

	private void redButton(DrawContext c, int x1, int y1, int x2, int y2, boolean pressed, int mx, int my) {
		boolean hover = mx >= x1 && mx < x2 && my >= y1 && my < y2;
		int off = pressed ? 1 : 0;
		Draw.round(c, x1, y1 + 2, x2, y2 + 2, 0xFF701818);
		int top = pressed ? 0xFFC03030 : hover ? 0xFFFF7070 : 0xFFF05050;
		int bottom = pressed ? 0xFFA02020 : hover ? 0xFFE04848 : 0xFFD03030;
		Draw.panelGradient(c, x1, y1 + off, x2, y2 + off, 0xFF902020, top, bottom);
		if (!pressed) c.fill(x1 + 3, y1 + 3, x1 + 5, y2 - 6, 0x70FFFFFF);
	}

	private int[] palette() {
		return switch (app) {
			case COIN -> PINK;
			case TYPES -> GRAY;
			case CALENDAR -> BLUE;
			default -> GREEN;
		};
	}

	private void switchApp(int dir) {
		App[] apps = App.values();
		app = apps[Math.floorMod(app.ordinal() + dir, apps.length)];
		if (dir < 0) pressedTop = System.currentTimeMillis();
		else pressedBottom = System.currentTimeMillis();
		PlatinoConfig.get().poketchApp = app.ordinal();
		PlatinoConfig.save();
		beep(1.4f);
	}

	private static void beep(float pitch) {
		MinecraftClient.getInstance().getSoundManager()
				.play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.5f));
	}

	private void text(DrawContext c, String s, int x, int y, int color) {
		c.drawText(textRenderer, s, x, y, color, false);
	}

	private void centered(DrawContext c, String s, int cx, int y, int color) {
		text(c, s, cx - textRenderer.getWidth(s) / 2, y, color);
	}

	/** Botón LCD (rectángulo con borde) con su zona de clic. */
	private void lcdButton(DrawContext c, int x1, int y1, int x2, int y2, String label, int mx, int my, Runnable action) {
		int[] p = palette();
		boolean hover = mx >= x1 && mx < x2 && my >= y1 && my < y2;
		c.fill(x1 + 1, y2, x2 + 1, y2 + 1, p[2]);
		Draw.round(c, x1, y1, x2, y2, p[1]);
		c.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, hover ? p[3] : p[0]);
		centered(c, label, (x1 + x2) / 2, (y1 + y2) / 2 - 4, p[1]);
		hits.add(new Hit(x1, y1, x2, y2, () -> {
			beep(1.8f);
			action.run();
		}));
	}

	// ---------------------------------------------------------------- Siete segmentos

	private static void digit(DrawContext c, int x, int y, int w, int h, int t, int mask, int on, int off) {
		int mid = y + h / 2;
		seg(c, mask, 0, x + t, y, x + w - t, y + t, on, off);                 // a
		seg(c, mask, 1, x + w - t, y + t, x + w, mid, on, off);               // b
		seg(c, mask, 2, x + w - t, mid, x + w, y + h - t, on, off);           // c
		seg(c, mask, 3, x + t, y + h - t, x + w - t, y + h, on, off);         // d
		seg(c, mask, 4, x, mid, x + t, y + h - t, on, off);                   // e
		seg(c, mask, 5, x, y + t, x + t, mid, on, off);                       // f
		seg(c, mask, 6, x + t, mid - t / 2, x + w - t, mid - t / 2 + t, on, off); // g
	}

	private static void seg(DrawContext c, int mask, int bit, int x1, int y1, int x2, int y2, int on, int off) {
		boolean lit = (mask >> bit & 1) == 1;
		if (lit) c.fill(x1, y1, x2, y2, on);
		else if (off != 0) c.fill(x1, y1, x2, y2, off);
	}

	private static int maskFor(char ch) {
		if (ch >= '0' && ch <= '9') return SEGMENTS[ch - '0'];
		if (ch == '-') return 0b1000000;
		if (ch == 'E') return 0b1111001;
		return 0;
	}

	// ---------------------------------------------------------------- Reloj

	private void drawClock(DrawContext c, int sx, int sy) {
		int[] p = GREEN;
		LocalDateTime now = LocalDateTime.now();
		String hh = String.format("%02d", now.getHour()), mm = String.format("%02d", now.getMinute());
		int w = 30, h = 58, t = 6, gap = 8, colon = 14;
		int total = w * 4 + gap * 2 + colon * 2;
		int x = sx + (SW - total) / 2, y = sy + 30;
		digit(c, x, y, w, h, t, maskFor(hh.charAt(0)), p[1], 0);
		digit(c, x + w + gap, y, w, h, t, maskFor(hh.charAt(1)), p[1], 0);
		int cx = x + 2 * w + gap + colon - 3;
		if (now.getSecond() % 2 == 0) {
			c.fill(cx, y + 16, cx + 6, y + 22, p[1]);
			c.fill(cx, y + 36, cx + 6, y + 42, p[1]);
		}
		int x3 = x + 2 * w + gap + colon * 2;
		digit(c, x3, y, w, h, t, maskFor(mm.charAt(0)), p[1], 0);
		digit(c, x3 + w + gap, y, w, h, t, maskFor(mm.charAt(1)), p[1], 0);
		String date = DAYS[now.getDayOfWeek().getValue() - 1] + " " + now.getDayOfMonth() + " "
				+ MONTHS[now.getMonthValue() - 1].substring(0, 3) + " " + now.getYear();
		centered(c, date, sx + SW / 2, sy + SH - 22, p[1]);
		c.fill(sx + 10, sy + SH - 10, sx + SW - 10, sy + SH - 8, p[2]);
	}

	// ---------------------------------------------------------------- Equipo

	private void drawTeam(DrawContext c, int sx, int sy, float delta) {
		int[] p = GREEN;
		List<Pokemon> slots;
		try {
			slots = CobblemonClient.INSTANCE.getStorage().getParty().getSlots();
		} catch (Throwable e) {
			slots = List.of();
		}
		// Como en DPPt: 2 columnas x 3 filas; retrato, nombre, nivel, cápsula de objeto y barra de vida
		int cw = (SW - 18) / 2, ch = (SH - 6) / 3;
		for (int i = 0; i < 6; i++) {
			int x = sx + 6 + (i % 2) * (cw + 6), y = sy + 3 + (i / 2) * ch;
			Pokemon pk = i < slots.size() ? slots.get(i) : null;
			int barY = y + ch - 13;
			// Barra: borde oscuro, fondo claro y relleno oscuro con la vida actual
			Draw.round(c, x, barY, x + cw, barY + 9, p[1]);
			c.fill(x + 1, barY + 1, x + cw - 1, barY + 8, p[3]);
			if (pk == null) {
				centered(c, "- - -", x + cw / 2, y + 8, p[2]);
				continue;
			}
			int max = Math.max(1, pk.getMaxHealth());
			float ratio = Math.max(0, Math.min(1, pk.getCurrentHealth() / (float) max));
			int fw = Math.round((cw - 4) * ratio);
			if (fw > 0) c.fill(x + 2, barY + 2, x + 2 + fw, barY + 7, ratio > 0.2f ? p[2] : p[1]);
			if (fw > 0) c.fill(x + 2, barY + 2, x + 2 + fw, barY + 3, p[1]);

			// Retrato con filtro LCD (el filtro va por delante del modelo 3D)
			int px = x, py = y + 1;
			PartyHud.drawPortrait(c, pk, i, px - 1, py - 1, 0f);
			c.draw();
			c.getMatrices().push();
			c.getMatrices().translate(0, 0, 400);
			c.fill(px + 1, py + 1, px + 19, py + 19, (0x99 << 24) | (p[0] & 0xFFFFFF));
			c.fill(px + 1, py + 1, px + 19, py + 19, (0x40 << 24) | (p[1] & 0xFFFFFF));
			c.getMatrices().pop();

			// Nombre y nivel
			boolean holds;
			try {
				holds = !pk.heldItem().isEmpty();
			} catch (Throwable t) {
				holds = false;
			}
			int textW = cw - 23 - (holds ? 10 : 0);
			text(c, Draw.fit(pk.getDisplayName(false).getString(), textW), x + 22, y + 2, p[1]);
			text(c, pk.isFainted() ? "DEB" : "Nv" + pk.getLevel(), x + 22, y + 12, p[1]);
			// Cápsula de objeto equipado (como en DPPt)
			if (holds) {
				int ix = x + cw - 8, iy = y + 8;
				Draw.round(c, ix, iy, ix + 8, iy + 11, p[1]);
				c.fill(ix + 1, iy + 1, ix + 7, iy + 4, p[3]);
				c.fill(ix + 1, iy + 5, ix + 7, iy + 10, p[2]);
			}
		}
	}

	// ---------------------------------------------------------------- Contador de pasos

	private void drawSteps(DrawContext c, int sx, int sy, int mx, int my) {
		int[] p = GREEN;
		String steps = String.format("%05d", Math.min(99999, PlatinoConfig.get().poketchSteps));
		int w = 20, h = 34, t = 4, gap = 5;
		int total = steps.length() * w + (steps.length() - 1) * gap;
		int bx1 = sx + (SW - total) / 2 - 10, by1 = sy + 26, bx2 = bx1 + total + 20, by2 = by1 + h + 16;
		Draw.round(c, bx1, by1, bx2, by2, p[1]);
		Draw.round(c, bx1 + 2, by1 + 2, bx2 - 2, by2 - 2, p[0]);
		Draw.round(c, bx1 + 3, by1 + 3, bx2 - 3, by2 - 3, p[1]);
		Draw.round(c, bx1 + 5, by1 + 5, bx2 - 5, by2 - 5, p[0]);
		for (int i = 0; i < steps.length(); i++) {
			digit(c, bx1 + 10 + i * (w + gap), by1 + 8, w, h, t, maskFor(steps.charAt(i)), p[1], p[3]);
		}
		centered(c, "PASOS", sx + SW / 2, by2 + 6, p[1]);
		int cx = sx + SW / 2;
		lcdButton(c, cx - 15, by2 + 20, cx + 15, by2 + 48, "C", mx, my, () -> {
			PlatinoConfig.get().poketchSteps = 0;
			PlatinoConfig.save();
		});
	}

	// ---------------------------------------------------------------- Lanzamonedas

	private void drawCoin(DrawContext c, int sx, int sy, int mx, int my) {
		int[] p = PINK;
		// Mesa
		c.fill(sx, sy + 70, sx + SW, sy + SH, p[2]);
		c.fill(sx, sy + 70, sx + SW, sy + 72, p[1]);
		c.fill(sx, sy + 76, sx + SW, sy + 77, p[3]);

		long el = System.currentTimeMillis() - coinStart;
		boolean flipping = el < 1100;
		int cx = sx + SW / 2;
		int r = 24;
		float lift = flipping ? (float) Math.sin(Math.PI * el / 1100.0) * 50f : 0f;
		int cy = sy + 104 - Math.round(lift);
		float squash = flipping ? (float) Math.abs(Math.cos(el / 1100.0 * Math.PI * 9)) : 1f;
		boolean showHeads = flipping ? (int) (el / (1100.0 / 18)) % 2 == 0 : coinHeads;
		int rx = Math.max(2, Math.round(r * squash));
		// Sombra
		ellipse(c, cx, sy + 128, Math.round(r * (1 - lift / 120f)), 4, (0x50 << 24));
		ellipse(c, cx, cy, rx, r, p[1]);
		ellipse(c, cx, cy, Math.max(1, rx - 2), r - 2, p[3]);
		ellipse(c, cx, cy, Math.max(1, rx - 4), r - 4, p[0]);
		if (rx > 10) {
			if (showHeads) {
				// Cara: emblema de Pokéball
				ellipse(c, cx, cy, Math.max(1, rx - 10), r - 10, p[1]);
				ellipse(c, cx, cy, Math.max(1, rx - 12), r - 12, p[0]);
				c.fill(cx - Math.max(1, rx - 10), cy - 1, cx + Math.max(1, rx - 10), cy + 1, p[1]);
				ellipse(c, cx, cy, Math.max(1, Math.round(4 * squash)), 4, p[1]);
			} else {
				// Cruz: estrella
				int s = Math.max(1, Math.round(9 * squash));
				c.fill(cx - s, cy - 1, cx + s, cy + 2, p[1]);
				c.fill(cx - 1, cy - 9, cx + 2, cy + 10, p[1]);
				for (int i = -6; i <= 6; i++) {
					int xi = Math.round(i * squash);
					c.fill(cx + xi, cy + i, cx + xi + 2, cy + i + 2, p[1]);
					c.fill(cx + xi, cy - i, cx + xi + 2, cy - i + 2, p[1]);
				}
			}
		}
		hits.add(new Hit(cx - r - 6, sy + 60, cx + r + 6, sy + 134, () -> {
			if (System.currentTimeMillis() - coinStart < 1100) return;
			coinStart = System.currentTimeMillis();
			coinHeads = random.nextBoolean();
			coinFlips++;
			beep(1.2f);
		}));
		String msg = flipping ? "…" : coinFlips == 0 ? "Toca la moneda" : coinHeads ? "¡CARA!" : "¡CRUZ!";
		centered(c, msg, cx, sy + 22, p[1]);
		if (!flipping && coinFlips > 0) centered(c, "Tiradas: " + coinFlips, cx, sy + 38, p[2]);
	}

	private static void ellipse(DrawContext c, int cx, int cy, int rx, int ry, int color) {
		for (int dy = -ry; dy <= ry; dy++) {
			int w = (int) Math.round(rx * Math.sqrt(Math.max(0, 1 - (dy * dy) / (double) (ry * ry))));
			if (w > 0) c.fill(cx - w, cy + dy, cx + w, cy + dy + 1, color);
		}
	}

	// ---------------------------------------------------------------- Indicador de movimientos

	private void drawTypes(DrawContext c, int sx, int sy, int mx, int my) {
		int[] p = GRAY;
		int n = TypeChart.NAMES.length;
		text(c, "DEFENSOR", sx + 96, sy + 4, p[2]);
		selector(c, sx + 86, sy + 14, sx + SW - 4, TypeChart.NAMES[typeDef1], mx, my,
				() -> typeDef1 = Math.floorMod(typeDef1 - 1, n), () -> typeDef1 = Math.floorMod(typeDef1 + 1, n));
		selector(c, sx + 86, sy + 36, sx + SW - 4, typeDef2 < 0 ? "- - -" : TypeChart.NAMES[typeDef2], mx, my,
				() -> typeDef2 = typeDef2 <= -1 ? n - 1 : typeDef2 - 1, () -> typeDef2 = typeDef2 >= n - 1 ? -1 : typeDef2 + 1);

		// Flecha ataque -> defensor
		int ax = sx + 30, ay = sy + 70;
		for (int i = 0; i < 18; i++) c.fill(ax + i * 2, ay - i, ax + i * 2 + 3, ay - i + 3, p[2]);
		c.fill(ax + 30, ay - 20, ax + 40, ay - 17, p[2]);
		c.fill(ax + 37, ay - 20, ax + 40, ay - 10, p[2]);
		// Defensor (silueta)
		ellipse(c, sx + 136, sy + 80, 30, 10, p[2]);
		ellipse(c, sx + 136, sy + 76, 16, 14, p[1]);

		text(c, "ATAQUE", sx + 10, sy + 80, p[2]);
		selector(c, sx + 4, sy + 90, sx + 92, TypeChart.NAMES[typeAttack], mx, my,
				() -> typeAttack = Math.floorMod(typeAttack - 1, n), () -> typeAttack = Math.floorMod(typeAttack + 1, n));

		float m = TypeChart.multiplier(typeAttack, typeDef1, typeDef2);
		int ry = sy + 116;
		Draw.round(c, sx + 4, ry, sx + SW - 4, ry + 26, p[1]);
		c.fill(sx + 5, ry + 1, sx + SW - 5, ry + 25, p[3]);
		text(c, TypeChart.describe(m), sx + 10, ry + 9, p[1]);
		String mult = TypeChart.multiplierText(m);
		text(c, mult, sx + SW - 10 - textRenderer.getWidth(mult), ry + 9, p[1]);
	}

	private void selector(DrawContext c, int x1, int y, int x2, String label, int mx, int my, Runnable prev, Runnable next) {
		int[] p = GRAY;
		lcdButton(c, x1, y, x1 + 12, y + 18, "◀", mx, my, prev);
		lcdButton(c, x2 - 12, y, x2, y + 18, "▶", mx, my, next);
		Draw.round(c, x1 + 14, y, x2 - 14, y + 18, p[1]);
		c.fill(x1 + 15, y + 1, x2 - 15, y + 17, p[3]);
		String s = Draw.fit(label, x2 - x1 - 32);
		centered(c, s, (x1 + x2) / 2, y + 5, p[1]);
	}

	// ---------------------------------------------------------------- Calendario

	private void drawCalendar(DrawContext c, int sx, int sy, int mx, int my) {
		int[] p = BLUE;
		c.fill(sx, sy, sx + SW, sy + 14, p[2]);
		for (int x = sx + 2; x < sx + SW; x += 6) c.fill(x, sy + 15, x + 3, sy + 16, p[2]);
		String title = MONTHS[calMonth.getMonthValue() - 1] + " " + calMonth.getYear();
		centered(c, title, sx + SW / 2, sy + 3, 0xFFFFFFFF);
		hits.add(new Hit(sx, sy, sx + 24, sy + 14, () -> {
			calMonth = calMonth.minusMonths(1);
			beep(1.8f);
		}));
		hits.add(new Hit(sx + SW - 24, sy, sx + SW, sy + 14, () -> {
			calMonth = calMonth.plusMonths(1);
			beep(1.8f);
		}));
		text(c, "◀", sx + 6, sy + 3, 0xFFFFFFFF);
		text(c, "▶", sx + SW - 12, sy + 3, 0xFFFFFFFF);

		String[] head = {"L", "M", "X", "J", "V", "S", "D"};
		int cw = 25, chh = 19, gx = sx + (SW - cw * 7) / 2, gy = sy + 30;
		for (int i = 0; i < 7; i++) centered(c, head[i], gx + i * cw + cw / 2, sy + 19, i >= 5 ? p[2] : p[1]);
		LocalDate today = LocalDate.now();
		int offset = calMonth.atDay(1).getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue();
		List<String> marks = PlatinoConfig.get().poketchMarks;
		for (int d = 1; d <= calMonth.lengthOfMonth(); d++) {
			int idx = offset + d - 1;
			int x = gx + (idx % 7) * cw, y = gy + (idx / 7) * chh;
			LocalDate date = calMonth.atDay(d);
			String key = date.toString();
			boolean marked = marks.contains(key);
			if (marked) c.fill(x + 2, y, x + cw - 2, y + chh - 3, p[2]);
			if (date.equals(today)) {
				Draw.round(c, x + 1, y - 1, x + cw - 1, y + chh - 2, p[1]);
				c.fill(x + 3, y + 1, x + cw - 3, y + chh - 4, marked ? p[2] : p[0]);
			}
			String s = String.valueOf(d);
			centered(c, s, x + cw / 2, y + 4, marked ? 0xFFFFFFFF : p[1]);
			hits.add(new Hit(x, y, x + cw, y + chh, () -> {
				if (!marks.remove(key)) marks.add(key);
				PlatinoConfig.save();
				beep(1.8f);
			}));
		}
	}

	// ---------------------------------------------------------------- Calculadora

	private void drawCalc(DrawContext c, int sx, int sy, int mx, int my) {
		int[] p = GREEN;
		// Pantalla
		Draw.round(c, sx + 4, sy + 4, sx + SW - 4, sy + 26, p[1]);
		c.fill(sx + 5, sy + 5, sx + SW - 5, sy + 25, p[0]);
		String shown = calcDisplay;
		int digits = 10, w = 11, h = 16, t = 2, gap = 4;
		int right = sx + SW - 10;
		int pos = 0;
		int[] masks = new int[digits];
		boolean[] dots = new boolean[digits];
		for (int i = shown.length() - 1; i >= 0 && pos < digits; i--) {
			char ch = shown.charAt(i);
			if (ch == '.') {
				dots[pos] = true;
				continue;
			}
			masks[pos++] = maskFor(ch);
		}
		for (int i = 0; i < digits; i++) {
			int x = right - (i + 1) * (w + gap) + gap;
			digit(c, x, sy + 7, w, h, t, masks[i], p[1], p[3]);
			if (dots[i]) c.fill(x + w + 1, sy + 21, x + w + 3, sy + 23, p[1]);
		}
		if (calcOp != 0) text(c, String.valueOf(calcOp), sx + 8, sy + 11, p[1]);

		// Teclado
		String[][] rows = {{"7", "8", "9", "C", ""}, {"4", "5", "6", "+", "-"}, {"1", "2", "3", "×", "÷"}, {"0", "", ".", "=", ""}};
		int unit = 32, gap2 = 3, kh = 26;
		int kx = sx + (SW - (unit * 5 + gap2 * 4)) / 2, ky = sy + 32;
		for (int r = 0; r < rows.length; r++) {
			for (int col = 0; col < 5; col++) {
				String k = rows[r][col];
				if (k.isEmpty()) continue;
				int span = (k.equals("C") || k.equals("0") || k.equals("=")) ? 2 : 1;
				int x1 = kx + col * (unit + gap2), y1 = ky + r * (kh + 3);
				int x2 = x1 + span * unit + (span - 1) * gap2;
				lcdButton(c, x1, y1, x2, y1 + kh, k, mx, my, () -> calcKey(k));
			}
		}
	}

	private void calcKey(String k) {
		switch (k) {
			case "C" -> {
				calcDisplay = "0";
				calcAcc = 0;
				calcOp = 0;
				calcFresh = true;
			}
			case "." -> {
				if (calcFresh) {
					calcDisplay = "0.";
					calcFresh = false;
				} else if (!calcDisplay.contains(".") && calcDisplay.length() < 10) {
					calcDisplay += ".";
				}
			}
			case "+", "-", "×", "÷" -> {
				double cur = parse();
				if (calcOp != 0 && !calcFresh) calcAcc = apply(calcAcc, cur, calcOp);
				else if (calcOp == 0) calcAcc = cur;
				calcOp = k.charAt(0);
				calcDisplay = format(calcAcc);
				calcFresh = true;
			}
			case "=" -> {
				if (calcOp != 0) {
					calcAcc = apply(calcAcc, parse(), calcOp);
					calcDisplay = format(calcAcc);
					calcOp = 0;
					calcFresh = true;
				}
			}
			default -> {
				if (calcFresh || calcDisplay.equals("0")) {
					calcDisplay = k;
					calcFresh = false;
				} else if (calcDisplay.replace(".", "").replace("-", "").length() < 10) {
					calcDisplay += k;
				}
			}
		}
	}

	private double parse() {
		try {
			return Double.parseDouble(calcDisplay);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static double apply(double a, double b, char op) {
		return switch (op) {
			case '+' -> a + b;
			case '-' -> a - b;
			case '×' -> a * b;
			case '÷' -> b == 0 ? Double.NaN : a / b;
			default -> b;
		};
	}

	private static String format(double v) {
		if (Double.isNaN(v) || Double.isInfinite(v) || Math.abs(v) >= 1e10) return "E";
		String s = java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
		int digitsCount = s.replace(".", "").replace("-", "").length();
		if (digitsCount > 10) {
			int intDigits = String.valueOf((long) Math.abs(v)).length();
			int decimals = Math.max(0, 10 - intDigits);
			s = java.math.BigDecimal.valueOf(v).setScale(decimals, java.math.RoundingMode.HALF_UP)
					.stripTrailingZeros().toPlainString();
		}
		return s;
	}

	// ---------------------------------------------------------------- Entrada

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			for (Hit h : new ArrayList<>(hits)) {
				if (h.contains(mouseX, mouseY)) {
					h.action().run();
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (vertical != 0) switchApp(vertical > 0 ? -1 : 1);
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_UP) {
			switchApp(-1);
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_DOWN) {
			switchApp(1);
			return true;
		}
		if (PlatinoClient.isPoketchKey(keyCode, scanCode)) {
			close();
			return true;
		}
		if (app == App.CALC) {
			String k = switch (keyCode) {
				case GLFW.GLFW_KEY_0, GLFW.GLFW_KEY_KP_0 -> "0";
				case GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_KP_1 -> "1";
				case GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_KP_2 -> "2";
				case GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_KP_3 -> "3";
				case GLFW.GLFW_KEY_4, GLFW.GLFW_KEY_KP_4 -> "4";
				case GLFW.GLFW_KEY_5, GLFW.GLFW_KEY_KP_5 -> "5";
				case GLFW.GLFW_KEY_6, GLFW.GLFW_KEY_KP_6 -> "6";
				case GLFW.GLFW_KEY_7, GLFW.GLFW_KEY_KP_7 -> "7";
				case GLFW.GLFW_KEY_8, GLFW.GLFW_KEY_KP_8 -> "8";
				case GLFW.GLFW_KEY_9, GLFW.GLFW_KEY_KP_9 -> "9";
				case GLFW.GLFW_KEY_KP_ADD -> "+";
				case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> "-";
				case GLFW.GLFW_KEY_KP_MULTIPLY -> "×";
				case GLFW.GLFW_KEY_SLASH, GLFW.GLFW_KEY_KP_DIVIDE -> "÷";
				case GLFW.GLFW_KEY_PERIOD, GLFW.GLFW_KEY_COMMA, GLFW.GLFW_KEY_KP_DECIMAL -> ".";
				case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_EQUAL -> "=";
				case GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_DELETE -> "C";
				default -> null;
			};
			if (k != null) {
				calcKey(k);
				beep(1.8f);
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}
}

package com.platino;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattleActor;
import com.cobblemon.mod.common.client.gui.battle.widgets.BattleMessagePane;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;

import java.util.List;

/** Cuadro de texto de combate estilo DS: dos líneas, texto que aparece letra a letra y flecha ▼. */
public final class DsTextBox {
	private static final int CPS = 45; // letras por segundo
	private static int lastCount = -1;
	private static int animLines = 0;
	private static long animStart, lastNewMsg;
	private static String promptShown;
	private static long promptStart;

	private static String flashText;
	private static long flashStart;

	private DsTextBox() {}

	/** Muestra un aviso temporal en el cuadro (p. ej. "¡No puedes usar la BOLSA!"). */
	public static void flash(String text) {
		flashText = text;
		flashStart = System.currentTimeMillis();
	}

	private static String plain(OrderedText t) {
		StringBuilder sb = new StringBuilder();
		t.accept((index, style, cp) -> {
			sb.appendCodePoint(cp);
			return true;
		});
		return sb.toString();
	}

	public static void render(DrawContext c, BattleMessagePane pane) {
		MinecraftClient mc = MinecraftClient.getInstance();
		long now = System.currentTimeMillis();
		int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
		float op = Math.max(0.15f, Math.min(1f, pane.getOpacity()));

		List<BattleMessagePane.BattleMessageLine> entries = pane.children();
		int n = entries.size();
		if (n != lastCount) {
			if (lastCount >= 0 && n > lastCount) {
				animLines = Math.min(2, n - lastCount);
				animStart = now;
				lastNewMsg = now;
			} else {
				animLines = 0;
			}
			lastCount = n;
		}
		String l1 = n >= 2 ? plain(entries.get(n - 2).getLine()) : "";
		String l2 = n >= 1 ? plain(entries.get(n - 1).getLine()) : "";

		int chars = (int) ((now - animStart) * CPS / 1000);
		int v1 = l1.length(), v2 = l2.length();
		if (animLines == 2) {
			v1 = Math.min(l1.length(), chars);
			v2 = Math.max(0, Math.min(l2.length(), chars - l1.length()));
		} else if (animLines == 1) {
			v2 = Math.min(l2.length(), chars);
		}
		boolean done = v1 == l1.length() && v2 == l2.length();

		// "¿Qué debería hacer X?" cuando toca elegir
		String prompt = null;
		if (DsBattleMenu.isActive() && done && now - lastNewMsg > 1200) {
			String name = activeName();
			if (name != null) prompt = com.platino.Tr.isEs() ? "¿Qué debería hacer " + name + "?" : "What will " + name + " do?";
		}
		if (prompt != null && !prompt.equals(promptShown)) {
			promptShown = prompt;
			promptStart = now;
		} else if (prompt == null) {
			promptShown = null;
		}

		// Geometría: al lado del menú DS si está visible; si no, centrado sobre la barra rápida
		int boxH = 38;
		int x, y, w;
		if (DsBattleMenu.isActive() && PlatinoConfig.get().battleMenu) {
			float[] b = DsBattleMenu.bounds();
			boolean menuLeft = b[0] < sw / 2f;
			w = Math.min(300, (int) (menuLeft ? sw - b[2] - 16 : b[0] - 16));
			x = menuLeft ? (int) b[2] + 8 : (int) b[0] - 8 - w;
			y = (int) b[3] - boxH;
		} else {
			w = Math.min(300, sw - 16);
			x = (sw - w) / 2;
			y = sh - boxH - 26;
		}

		c.getMatrices().push();
		c.getMatrices().translate(0, 0, 300);
		int a = (int) (op * 255) << 24;
		// Marco gris exterior + caja blanca interior (como DPPt)
		Draw.panel(c, x, y, x + w, y + boxH, a | 0x303840, a | 0x808890);
		Draw.panel(c, x + 3, y + 3, x + w - 3, y + boxH - 3, a | 0x485058, a | 0xF8F8F8);
		c.fill(x + 5, y + boxH - 6, x + w - 5, y + boxH - 5, a | 0xE0E0E0);

		int tx = x + 9, maxW = w - 22;
		int textCol = a | 0x484848, shadowCol = a | 0xD0D0C8;
		if (flashText != null && now - flashStart < 2200) {
			int fc = (int) Math.min(flashText.length(), (now - flashStart) * CPS / 1000);
			drawLine(c, flashText.substring(0, fc), tx, y + 9, maxW, textCol, shadowCol);
		} else if (promptShown != null) {
			int pc = (int) Math.min(promptShown.length(), (now - promptStart) * CPS / 1000);
			drawLine(c, promptShown.substring(0, pc), tx, y + 9, maxW, textCol, shadowCol);
		} else {
			drawLine(c, l1.substring(0, v1), tx, y + 9, maxW, textCol, shadowCol);
			drawLine(c, l2.substring(0, v2), tx, y + 21, maxW, textCol, shadowCol);
			if (done && n > 0 && (now / 400) % 2 == 0) {
				Draw.dsText(c, "▼", x + w - 14, y + boxH - 15, a | 0xE04040, a | 0xF0B0B0);
			}
		}
		c.getMatrices().pop();
	}

	private static void drawLine(DrawContext c, String s, int x, int y, int maxW, int col, int shadow) {
		if (s.isEmpty()) return;
		int tw = Draw.width(s);
		if (tw <= maxW) {
			Draw.dsText(c, s, x, y, col, shadow);
			return;
		}
		float scale = maxW / (float) tw;
		c.getMatrices().push();
		c.getMatrices().translate(x, y + (1 - scale) * 4, 0);
		c.getMatrices().scale(scale, scale, 1);
		Draw.dsText(c, s, 0, 0, col, shadow);
		c.getMatrices().pop();
	}

	private static String activeName() {
		try {
			ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
			if (battle == null) return null;
			var me = MinecraftClient.getInstance().player;
			if (me == null) return null;
			for (var side : battle.getSides()) {
				for (ClientBattleActor actor : side.getActors()) {
					if (!me.getUuid().equals(actor.getUuid())) continue;
					for (ActiveClientBattlePokemon ap : actor.getActivePokemon()) {
						if (ap.getBattlePokemon() != null) return ap.getBattlePokemon().getDisplayName().getString().toUpperCase();
					}
				}
			}
		} catch (Throwable ignored) {
		}
		return null;
	}
}

package com.platino;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.client.storage.ClientParty;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.status.PersistentStatus;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.UUID;

/** Cajas de combate estilo Pokémon Platino (tu Pokémon arriba a la izquierda, el rival arriba a la derecha, como Cobblemon). */
public final class BattleHud {
	public static final int W = 132;
	/** Texto gris oscuro (no negro puro), nítido y sin sombra. */

	private BattleHud() {}

	/**
	 * Sustituye a BattleOverlay.drawTile.
	 * @param ally true si es de tu lado (Cobblemon lo llama "left").
	 */
	public static void drawTile(DrawContext c, ActiveClientBattlePokemon active, boolean ally, int rank,
								String dexState, boolean hasCommand, boolean hovered) {
		ClientBattlePokemon bp = active.getBattlePokemon();
		if (bp == null) return;
		float[] prev = RenderSystem.getShaderColor().clone();
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		try {
			drawTileInner(c, bp, active, ally, rank, dexState, hasCommand, hovered);
		} finally {
			c.draw();
			RenderSystem.setShaderColor(prev[0], prev[1], prev[2], prev[3]);
		}
	}

	private static void drawTileInner(DrawContext c, ClientBattlePokemon bp, ActiveClientBattlePokemon active, boolean ally,
									  int rank, String dexState, boolean hasCommand, boolean hovered) {
		MinecraftClient mc = MinecraftClient.getInstance();
		PlatinoConfig cfg = PlatinoConfig.get();
		int sw = mc.getWindow().getScaledWidth();

		int h = ally ? 42 : 31;
		int y0 = 46 - h + rank * Math.max(40, h + 4);
		int x, y;
		if (ally) {
			x = 13 + cfg.allyOffsetX;
			y = y0 - cfg.allyOffsetY;
		} else {
			x = sw - 13 - W + cfg.foeOffsetX;
			y = y0 - cfg.foeOffsetY;
		}

		// Animación suave de la barra de PS
		float max = bp.getMaxHp();
		float target = !bp.isHpFlat() ? bp.getHpValue() : max > 0 ? bp.getHpValue() / max : 0f;
		float cur = Math.max(0, Math.min(1, target));

		// Vida baja (≤20 %): borde rojo parpadeante y pitido, como en DPPt
		boolean low = ally && cur > 0f && cur <= 0.2f;
		if (low) LowHpAlarm.mark();
		boolean blinkOn = (System.currentTimeMillis() / 350) % 2 == 0;
		int border = hovered ? Draw.GOLD_DARK : (low && blinkOn) ? 0xFFC82828 : FRAME;

		// Caja de Platino: marco oscuro, filo oliva claro, cuerpo oliva, esquina interior achaflanada y el
		// "raíl" marrón de la barra de PS que sale de la caja hacia el centro de la pantalla con una punta.
		boolean inwardRight = ally; // tu caja está a la izquierda: mira a la derecha
		drawFrame(c, x, y, W, h, inwardRight, border);

		int ix = x + 6;
		int right = x + W - 6;
		String name = Draw.fit(bp.getDisplayName().getString().toUpperCase(java.util.Locale.ROOT), W - 60);
		Draw.dsText(c, name, ix, y + 5, WHITE, DARK);

		String lvNum = String.valueOf(bp.getLevel());
		int lvx = right - Draw.width(lvNum);
		Draw.dsText(c, lvNum, lvx, y + 5, WHITE, DARK);
		int nvx = lvx - Draw.width(com.platino.Tr.t("Nv")) - 1;
		Draw.dsText(c, com.platino.Tr.t("Nv"), nvx, y + 5, WHITE, DARK);
		Gender g = bp.getGender();
		if (g == Gender.MALE) Draw.dsText(c, "♂", nvx - 8, y + 5, 0xFF4890E8, DARK);
		else if (g == Gender.FEMALE) Draw.dsText(c, "♀", nvx - 8, y + 5, 0xFFF06080, DARK);

		// Raíl + barra de PS
		int railY = ally ? y + 17 : y + 19;
		int barX2 = right - 1, barX1 = barX2 - (ally ? 82 : 70);
		drawRail(c, x, railY, W, inwardRight, barX1 - 14, barX2, cur);
		Draw.dsText(c, com.platino.Tr.t("PS"), barX1 - 12, railY - 1, 0xFFF8F0B0, DARK);

		int lx = ix;
		if (!ally && dexState != null && dexState.equalsIgnoreCase("CAUGHT")) {
			Draw.tinyBall(c, lx, railY - 1);
			lx += 10;
		}
		PersistentStatus st = bp.getStatus();
		if (st != null) {
			int bw = Draw.statusBadge(c, st.getShowdownName(), lx, railY - 2);
			if (bw > 0) lx += bw + 2;
		}
		if (!ally) {
			// % de PS del rival, a la izquierda de "PS"
			String pct = Math.round(cur * 100) + "%";
			Draw.dsText(c, pct, barX1 - 16 - Draw.width(pct), railY - 1, WHITE, DARK);
		}

		if (ally) {
			if (bp.isHpFlat()) {
				String hp = Math.round(bp.getHpValue()) + "/" + Math.round(max);
				Draw.dsText(c, hp, right - Draw.width(hp), y + 25, WHITE, DARK);
			}
			Pokemon own = findOwn(bp.getUuid());
			if (own != null) {
				float er = Math.max(0, Math.min(1, expRatio(own)));
				int ey = y + h - 7;
				int ex1 = x + 4, ex2 = x + W - 4;
				Draw.batch(c, () -> {
					c.fill(ex1, ey, ex2, ey + 3, FRAME);
					c.fill(ex1 + 1, ey + 1, ex2 - 1, ey + 2, 0xFFF8F8F8);
					int ew = Math.round((ex2 - ex1 - 2) * er);
					if (ew > 0) c.fill(ex1 + 1, ey + 1, ex1 + 1 + ew, ey + 2, 0xFF40A0F8);
				});
			}
		}

		// Indicador de que espera una orden
		if (hasCommand && (System.currentTimeMillis() / 400) % 2 == 0) {
			int ax = ally ? x - 6 : x + W + 2;
			Draw.dsText(c, ally ? "▶" : "◀", ax, y + h / 2 - 4, Draw.GOLD, Draw.GOLD_DARK);
		}
	}

	private static final int FRAME = 0xFF293028, RIM = 0xFFA8AB96, BODY = 0xFF69715A, WHITE = 0xFFF8FCF6, DARK = 0xFF2B3029;
	private static final int RAIL = 0xFF5F4A37;

	/** Marco de Platino: 2 px oscuros, 1 px de filo claro y cuerpo oliva; la esquina superior interior achaflanada. */
	private static void drawFrame(DrawContext c, int x, int y, int w, int h, boolean inwardRight, int border) {
		Draw.batch(c, () -> {
			for (int r = 0; r < h; r++) {
				int cut = r == 0 ? 2 : r == 1 ? 1 : 0;
				int l = inwardRight ? 0 : cut, rr = inwardRight ? cut : 0;
				c.fill(x + l, y + r, x + w - rr, y + r + 1, border);
			}
			c.fill(x + 2, y + 2, x + w - 2, y + h - 2, RIM);
			c.fill(x + 3, y + 3, x + w - 3, y + h - 3, BODY);
			c.fill(x + 3, y + h - 3, x + w - 3, y + h - 2, 0xFF5A604C);
		});
	}

	/**
	 * Raíl marrón (marrón / oscuro / marrón) que cruza la caja y sale 10 px hacia el centro terminando en punta,
	 * con la barra de PS de marco blanco encima.
	 */
	private static void drawRail(DrawContext c, int x, int ry, int w, boolean inwardRight, int railFrom, int barX2, float ratio) {
		float r = Math.max(0, Math.min(1, ratio));
		int barX1 = railFrom + 14;
		Draw.batch(c, () -> {
			int x1, x2;
			// cruza toda la caja y sale 10 px por el lado que mira al centro
			if (inwardRight) {
				x1 = x + 2;
				x2 = x + w + 10;
			} else {
				x1 = x - 10;
				x2 = x + w - 2;
			}
			c.fill(x1, ry, x2, ry + 2, RAIL);
			c.fill(x1, ry + 2, x2, ry + 4, FRAME);
			c.fill(x1, ry + 4, x2, ry + 6, RAIL);
			// punta
			for (int k = 0; k < 5; k++) {
				int col = inwardRight ? x2 + k : x1 - 1 - k;
				int top = ry - 2 + k, bot = ry + 8 - k;
				if (bot <= top) break;
				c.fill(col, top, col + 1, bot, k == 4 ? FRAME : RAIL);
				c.fill(col, top, col + 1, top + 1, FRAME);
				c.fill(col, bot - 1, col + 1, bot, FRAME);
			}
			// barra: marco blanco arriba/abajo, relleno de 2 filas y sombra negra
			c.fill(barX1, ry + 1, barX2, ry + 2, 0xFFF8FCF8);
			c.fill(barX1, ry + 2, barX2, ry + 4, 0xFF384030);
			c.fill(barX1, ry + 4, barX2, ry + 5, 0xFFF8FCF8);
			c.fill(barX1, ry + 5, barX2, ry + 6, 0xFF050100);
			int bw = barX2 - barX1;
			int fw = Math.round(bw * r);
			if (r > 0 && fw < 1) fw = 1;
			int col = r > 0.5f ? 0xFF18C420 : r > 0.2f ? 0xFFF8C018 : 0xFFF83818;
			if (fw > 0) c.fill(barX1, ry + 2, barX1 + fw, ry + 4, col);
		});
	}

	private static Pokemon findOwn(UUID uuid) {
		try {
			ClientParty party = CobblemonClient.INSTANCE.getStorage().getParty();
			return party.findByUUID(uuid);
		} catch (Throwable t) {
			return null;
		}
	}

	public static float expRatio(Pokemon p) {
		try {
			int lvl = p.getLevel();
			int range = p.getExperienceToLevel(lvl + 1) - p.getExperienceToLevel(lvl);
			if (range <= 0) return 0;
			return 1f - p.getExperienceToNextLevel() / (float) range;
		} catch (Throwable t) {
			return 0;
		}
	}
}

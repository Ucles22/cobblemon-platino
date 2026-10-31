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
	private static final int TXT = 0xFF484C50;

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
		int border = hovered ? Draw.GOLD : (low && blinkOn) ? 0xFFE83030 : 0xFF5A6268;

		// Caja blanca de una pieza como en DPPt: borde oscuro, filo gris claro, pestaña gris al lado de la
		// pantalla y punta hacia el centro. La barra de PS (con "PS" en rojo) va dentro de la caja.
		Draw.round(c, x + 2, y + 2, x + W + 2, y + h + 2, 0x50000000);
		Draw.panelGradient(c, x, y, x + W, y + h, border, 0xFFFAFAF8, 0xFFE8EAE4);
		c.fill(x + 1, y + 1, x + W - 1, y + 2, 0xFFC8CDC2);
		c.fill(x + 1, y + h - 2, x + W - 1, y + h - 1, 0xFFC8CDC2);
		int tabX = ally ? x + 1 : x + W - 5;
		c.fill(tabX, y + 2, tabX + 4, y + h - 2, 0xFFA1A793);
		// Punta (triángulo) hacia el centro de la pantalla
		int tipX = ally ? x + W : x - 1;
		for (int i = 0; i < 4; i++) {
			int ty = y + h / 2 - 4 + i, tb = y + h / 2 + 4 - i;
			int xx = ally ? tipX + i : tipX - i;
			c.fill(xx, ty, xx + 1, tb, i == 3 ? 0xFF333A38 : 0xFFE8EAE4);
			c.fill(xx, ty - 1, xx + 1, ty, 0xFF333A38);
			c.fill(xx, tb, xx + 1, tb + 1, 0xFF333A38);
		}

		int ix = x + (ally ? 8 : 5);
		int right = x + W - (ally ? 6 : 9);
		// Nombre + sexo (texto oscuro con sombra gris, como en DS)
		String name = Draw.fit(bp.getDisplayName().getString().toUpperCase(java.util.Locale.ROOT), W - 58);
		c.drawText(Draw.font(), name, ix, y + 4, TXT, false);
		Gender g = bp.getGender();

		// Sexo + "Nv" pequeño + número, a la derecha
		String lvNum = String.valueOf(bp.getLevel());
		int lvx = right - Draw.width(lvNum);
		c.drawText(Draw.font(), lvNum, lvx, y + 4, TXT, false);
		int nvx = lvx - Draw.width("Nv") - 1;
		c.drawText(Draw.font(), "Nv", nvx, y + 4, 0xFFC04848, false);
		if (g == Gender.MALE) c.drawText(Draw.font(), "♂", nvx - 9, y + 4, 0xFF3088F8, false);
		else if (g == Gender.FEMALE) c.drawText(Draw.font(), "♀", nvx - 9, y + 4, 0xFFE84870, false);

		// Capturado (rival salvaje): ball pequeña bajo el nombre
		if (!ally && dexState != null && dexState.equalsIgnoreCase("CAUGHT")) {
			Draw.tinyBall(c, ix + Math.min(Draw.width(name) + 3, W - 70), y + 4);
		}

		// Barra de PS: cápsula oscura con "PS" en rojo y la vida dentro
		int stripY = y + 16;
		PersistentStatus st = bp.getStatus();
		int used = st != null ? Draw.statusBadge(c, st.getShowdownName(), ix, stripY) : 0;
		int barW = ally ? 86 : 68;
		int sx = right - barW;
		Draw.batch(c, () -> {
			Draw.round(c, sx, stripY, right, stripY + 9, 0xFF4A4E52);
			// "PS" sobre fondo granate
			c.fill(sx + 1, stripY + 1, sx + 15, stripY + 8, 0xFFB83838);
			int bx = sx + 16, bw = right - 1 - bx;
			c.fill(bx, stripY + 1, bx + bw, stripY + 8, 0xFF6A7070);
			int fw = Math.round(bw * cur);
			if (cur > 0 && fw < 1) fw = 1;
			int col, hi;
			if (cur > 0.5f) { col = 0xFF40A840; hi = 0xFF68F068; }
			else if (cur > 0.2f) { col = 0xFFC8A008; hi = 0xFFF8E038; }
			else { col = 0xFFA82818; hi = 0xFFF85838; }
			if (fw > 0) {
				c.fill(bx, stripY + 1, bx + fw, stripY + 8, col);
				c.fill(bx, stripY + 2, bx + fw, stripY + 4, hi);
			}
		});

		c.drawText(Draw.font(), "PS", sx + 2, stripY + 1, 0xFFFFF0E8, false);
		if (!ally) {
			// % de PS del rival a la izquierda de la barra
			String pct = Math.round(cur * 100) + "%";
			c.drawText(Draw.font(), pct, sx - 3 - Draw.width(pct), stripY + 1, TXT, false);
		}

		if (ally) {
			// PS numéricos y barra de EXP azul en la parte baja de la misma caja
			if (bp.isHpFlat()) {
				String hp = Math.round(bp.getHpValue()) + "/" + Math.round(max);
				c.drawText(Draw.font(), hp, right - Draw.width(hp), y + 27, TXT, false);
			}
			Pokemon own = findOwn(bp.getUuid());
			if (own != null) {
				float er = Math.max(0, Math.min(1, expRatio(own)));
				int ey = y + h - 6;
				Draw.batch(c, () -> {
					c.fill(x + 6, ey, x + W - 4, ey + 3, 0xFF4A4E52);
					c.fill(x + 7, ey + 1, x + W - 5, ey + 2, 0xFF505858);
					int ew = Math.round((W - 12) * er);
					if (ew > 0) c.fill(x + 7, ey + 1, x + 7 + ew, ey + 2, Draw.EXP_BLUE);
				});
				c.drawText(Draw.font(), "EXP", ix, y + 27, 0xFF3878C8, false);
			}
		}

		// Indicador de que espera una orden
		if (hasCommand && (System.currentTimeMillis() / 400) % 2 == 0) {
			int ax = ally ? x - 6 : x + W + 2;
			Draw.dsText(c, ally ? "▶" : "◀", ax, y + h / 2 - 4, Draw.GOLD, Draw.GOLD_DARK);
		}
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

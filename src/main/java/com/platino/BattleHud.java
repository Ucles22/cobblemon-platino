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
	private static final int TXT = 0xFF404448;
	private static final int SHADOW = 0xFFC8C8C0;

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
		int border = hovered ? Draw.GOLD_DARK : (low && blinkOn) ? 0xFFC82828 : 0xFF343B3A;

		// Caja calcada de DPPt: marco oscuro de 2 px, extremo interior muy redondeado, pestaña gris en el lado
		// de la pantalla, filo gris claro arriba y abajo y cuerpo blanco.
		boolean roundRight = ally; // el extremo redondeado mira al centro de la pantalla
		drawFrame(c, x, y, W, h, roundRight, border);

		int ix = ally ? x + 9 : x + 7;            // tras la pestaña (izq. en tu caja) o tras el redondeo
		int right = ally ? x + W - 7 : x + W - 9; // antes del redondeo (tu caja) o de la pestaña (rival)

		// Nombre (mayúsculas, gris oscuro con sombra gris clara como en DS)
		String name = Draw.fit(bp.getDisplayName().getString().toUpperCase(java.util.Locale.ROOT), W - 64);
		Draw.dsText(c, name, ix, y + 5, TXT, SHADOW);

		// Sexo + "Nv" + nivel a la derecha
		String lvNum = String.valueOf(bp.getLevel());
		int lvx = right - Draw.width(lvNum);
		Draw.dsText(c, lvNum, lvx, y + 5, TXT, SHADOW);
		int nvx = lvx - Draw.width("Nv") - 1;
		Draw.dsText(c, "Nv", nvx, y + 5, TXT, SHADOW);
		Gender g = bp.getGender();
		if (g == Gender.MALE) Draw.dsText(c, "♂", nvx - 8, y + 5, 0xFF4890E8, 0xFFB8D0F0);
		else if (g == Gender.FEMALE) Draw.dsText(c, "♀", nvx - 8, y + 5, 0xFFE86078, 0xFFF0C0C8);

		// Fila de la barra: ball de capturado a la izquierda, estado, % del rival y la cápsula de PS a la derecha
		int barY = ally ? y + 17 : y + 18;
		int capW = ally ? 88 : 72;
		int capX = right - capW;
		int lx = ix;
		if (!ally && dexState != null && dexState.equalsIgnoreCase("CAUGHT")) {
			Draw.tinyBall(c, lx, barY);
			lx += 10;
		}
		PersistentStatus st = bp.getStatus();
		if (st != null) {
			int bw = Draw.statusBadge(c, st.getShowdownName(), lx, barY - 1);
			if (bw > 0) lx += bw + 2;
		}
		if (!ally) {
			String pct = Math.round(cur * 100) + "%";
			Draw.dsText(c, pct, capX - 3 - Draw.width(pct), barY, TXT, SHADOW);
		}
		drawHpCapsule(c, capX, barY, right, cur);

		if (ally) {
			// PS numéricos debajo de la barra y EXP azul en el borde inferior
			if (bp.isHpFlat()) {
				String hp = Math.round(bp.getHpValue()) + "/" + Math.round(max);
				Draw.dsText(c, hp, right - Draw.width(hp), y + 27, TXT, SHADOW);
			}
			Pokemon own = findOwn(bp.getUuid());
			if (own != null) {
				float er = Math.max(0, Math.min(1, expRatio(own)));
				int ey = y + h - 7;
				int ex1 = x + 30, ex2 = x + W - 8;
				Draw.batch(c, () -> {
					c.fill(ex1, ey, ex2, ey + 4, 0xFF3A3633);
					c.fill(ex1 + 1, ey + 1, ex2 - 1, ey + 3, 0xFF606868);
					int ew = Math.round((ex2 - ex1 - 2) * er);
					if (ew > 0) {
						c.fill(ex1 + 1, ey + 1, ex1 + 1 + ew, ey + 3, 0xFF40A0F8);
						c.fill(ex1 + 1, ey + 1, ex1 + 1 + ew, ey + 2, 0xFF88D0F8);
					}
				});
				Draw.dsText(c, "EXP", ix, ey - 2, 0xFFE0A020, 0xFF806010);
			}
		}

		// Indicador de que espera una orden
		if (hasCommand && (System.currentTimeMillis() / 400) % 2 == 0) {
			int ax = ally ? x - 6 : x + W + 2;
			Draw.dsText(c, ally ? "▶" : "◀", ax, y + h / 2 - 4, Draw.GOLD, Draw.GOLD_DARK);
		}
	}

	/** Marco de la caja: 2 px oscuros, redondeado grande en el extremo interior y pequeño en el de la pantalla. */
	private static void drawFrame(DrawContext c, int x, int y, int w, int h, boolean roundRight, int border) {
		int[] big = {5, 3, 2, 1, 1}, small = {1};
		Draw.batch(c, () -> {
			// sombra
			for (int r = 0; r < h; r++) {
				int k = Math.min(r, h - 1 - r);
				int ib = k < big.length ? big[k] : 0, is = k < small.length ? small[k] : 0;
				int l = roundRight ? is : ib, rr = roundRight ? ib : is;
				c.fill(x + l + 2, y + r + 2, x + w - rr + 2, y + r + 3, 0x40000000);
			}
			for (int r = 0; r < h; r++) {
				int k = Math.min(r, h - 1 - r);
				int ib = k < big.length ? big[k] : 0, is = k < small.length ? small[k] : 0;
				int l = roundRight ? is : ib, rr = roundRight ? ib : is;
				c.fill(x + l, y + r, x + w - rr, y + r + 1, border);
			}
			// cuerpo (2 px dentro del marco)
			int[] bigIn = {3, 1, 1};
			for (int r = 2; r < h - 2; r++) {
				int k = Math.min(r - 2, h - 3 - r);
				int ib = k < bigIn.length ? bigIn[k] : 0;
				int l = roundRight ? 2 : 2 + ib, rr = roundRight ? 2 + ib : 2;
				int col = (r == 2 || r == h - 3) ? 0xFFC8CDC2 : (r < h / 2 ? 0xFFF8F8F8 : 0xFFF2F3EE);
				c.fill(x + l, y + r, x + w - rr, y + r + 1, col);
			}
			// pestaña gris del lado de la pantalla
			int tx = roundRight ? x + 2 : x + w - 6;
			c.fill(tx, y + 3, tx + 4, y + h - 3, 0xFFA1A793);
			c.fill(roundRight ? tx + 4 : tx - 1, y + 3, roundRight ? tx + 5 : tx, y + h - 3, 0xFF8C927E);
		});
	}

	/** Cápsula de PS de DPPt: contorno oscuro con puntas redondas, "PS" rosado sobre granate y barra verde. */
	private static void drawHpCapsule(DrawContext c, int x1, int y, int x2, float ratio) {
		float r = Math.max(0, Math.min(1, ratio));
		Draw.batch(c, () -> {
			c.fill(x1 + 1, y, x2 - 1, y + 8, 0xFF3A3633);
			c.fill(x1, y + 1, x2, y + 7, 0xFF3A3633);
			c.fill(x1 + 1, y + 1, x1 + 15, y + 7, 0xFF5A2C30);
			int bx = x1 + 16, bw = x2 - 2 - bx;
			c.fill(bx, y + 1, bx + bw, y + 7, 0xFF586060);
			int fw = Math.round(bw * r);
			if (r > 0 && fw < 1) fw = 1;
			int dark, mid, hi;
			if (r > 0.5f) { dark = 0xFF1E4D1B; mid = 0xFF42A644; hi = 0xFF67EF66; }
			else if (r > 0.2f) { dark = 0xFF6A5208; mid = 0xFFC8A008; hi = 0xFFF8E038; }
			else { dark = 0xFF6A1810; mid = 0xFFC83020; hi = 0xFFF86848; }
			if (fw > 0) {
				c.fill(bx, y + 1, bx + fw, y + 7, mid);
				c.fill(bx, y + 1, bx + fw, y + 2, dark);
				c.fill(bx, y + 6, bx + fw, y + 7, dark);
				c.fill(bx, y + 3, bx + fw, y + 5, hi);
			}
		});
		// "PS" rosado, nítido
		c.getMatrices().push();
		c.getMatrices().translate(0, 0, 1);
		c.drawText(Draw.font(), "PS", x1 + 3, y, 0xFFF8B0A8, false);
		c.getMatrices().pop();
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

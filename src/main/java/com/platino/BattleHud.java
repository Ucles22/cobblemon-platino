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
		int border = hovered ? Draw.GOLD : (low && blinkOn) ? 0xFFE83030 : 0xFF181C20;

		// Caja de una sola pieza estilo DPPt: placa gris pizarra con filo claro, y la barra de PS
		// (y la de EXP) metidas dentro de la propia placa, sin cápsulas sueltas.
		Draw.round(c, x + 2, y + 2, x + W + 2, y + h + 2, 0x60000000);
		Draw.panelGradient(c, x, y, x + W, y + h, border, 0xFF6C7482, 0xFF3E4550);
		c.fill(x + 2, y + 1, x + W - 2, y + 2, 0x70FFFFFF);
		c.fill(x + 1, y + 2, x + 2, y + h - 2, 0x40FFFFFF);
		// Pestaña lateral (lado de la pantalla)
		if (ally) c.fill(x + 1, y + 3, x + 3, y + h - 3, 0xFF9AA2AE);
		else c.fill(x + W - 3, y + 3, x + W - 1, y + h - 3, 0xFF9AA2AE);

		int ix = x + (ally ? 6 : 5);
		int right = x + W - (ally ? 6 : 7);
		// Nombre + sexo (blanco con sombra oscura)
		String name = Draw.fit(bp.getDisplayName().getString().toUpperCase(java.util.Locale.ROOT), W - 56);
		Draw.dsText(c, name, ix, y + 4, 0xFFFFFFFF, 0xFF202428);
		int gx = ix + Draw.width(name) + 2;
		Gender g = bp.getGender();
		if (g == Gender.MALE) Draw.dsText(c, "♂", gx, y + 4, 0xFF68B0FF, 0xFF202428);
		else if (g == Gender.FEMALE) Draw.dsText(c, "♀", gx, y + 4, 0xFFFF88A8, 0xFF202428);

		// Nivel (derecha): "Nv" pequeño + número
		String lvNum = String.valueOf(bp.getLevel());
		int lvx = right - Draw.width(lvNum);
		Draw.dsText(c, lvNum, lvx, y + 4, 0xFFFFFFFF, 0xFF202428);
		c.getMatrices().push();
		c.getMatrices().translate(lvx - 11, y + 6, 0);
		c.getMatrices().scale(0.75f, 0.75f, 1f);
		c.drawText(Draw.font(), "Nv", 0, 0, 0xFFF8D048, false);
		c.getMatrices().pop();

		// Capturado (rival salvaje)
		if (!ally && dexState != null && dexState.equalsIgnoreCase("CAUGHT")) {
			Draw.tinyBall(c, lvx - 22, y + 4);
		}

		// Franja de PS integrada: fondo oscuro de toda la fila con "PS" dorado y la barra dentro
		int stripY = y + 15;
		PersistentStatus st = bp.getStatus();
		int used = st != null ? Draw.statusBadge(c, st.getShowdownName(), ix, stripY) : 0;
		int sx = ix + (used > 0 ? used + 2 : 0);
		int finalSx = sx;
		Draw.batch(c, () -> {
			c.fill(finalSx, stripY, right, stripY + 9, 0xFF22262C);
			c.fill(finalSx + 1, stripY + 1, right - 1, stripY + 2, 0xFF14171B);
			int lx = finalSx + 2;
			c.drawText(Draw.font(), "PS", lx, stripY + 1, 0xFFF8B830, false);
			int bx = lx + 14, bw = right - 2 - bx;
			c.fill(bx, stripY + 2, bx + bw, stripY + 7, 0xFF505862);
			int fw = Math.round(bw * cur);
			if (cur > 0 && fw < 1) fw = 1;
			int col, hi;
			if (cur > 0.5f) { col = Draw.HP_GREEN; hi = Draw.HP_GREEN_HI; }
			else if (cur > 0.2f) { col = Draw.HP_YELLOW; hi = Draw.HP_YELLOW_HI; }
			else { col = Draw.HP_RED; hi = Draw.HP_RED_HI; }
			if (fw > 0) {
				c.fill(bx, stripY + 2, bx + fw, stripY + 7, col);
				c.fill(bx, stripY + 3, bx + fw, stripY + 4, hi);
			}
		});

		if (ally) {
			// PS numéricos y franja de EXP en el borde inferior de la misma placa
			if (bp.isHpFlat()) {
				String hp = Math.round(bp.getHpValue()) + "/" + Math.round(max);
				Draw.dsText(c, hp, right - Draw.width(hp), y + 26, 0xFFFFFFFF, 0xFF202428);
			}
			Pokemon own = findOwn(bp.getUuid());
			if (own != null) {
				float er = Math.max(0, Math.min(1, expRatio(own)));
				int ey = y + h - 5;
				Draw.batch(c, () -> {
					c.fill(x + 3, ey, x + W - 3, ey + 3, 0xFF22262C);
					int ew = Math.round((W - 8) * er);
					if (ew > 0) c.fill(x + 4, ey + 1, x + 4 + ew, ey + 2, Draw.EXP_BLUE);
				});
				c.getMatrices().push();
				c.getMatrices().translate(ix, y + 28, 0);
				c.getMatrices().scale(0.75f, 0.75f, 1f);
				c.drawText(Draw.font(), "EXP", 0, 0, 0xFF88D0F8, false);
				c.getMatrices().pop();
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

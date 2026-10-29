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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Cajas de combate estilo Pokémon Platino (rival arriba a la izquierda, tu Pokémon a la derecha). */
public final class BattleHud {
	public static final int W = 124;
	private static final Map<UUID, Float> shownHp = new HashMap<>();
	private static long lastFrame = System.nanoTime();

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
		int sh = mc.getWindow().getScaledHeight();

		int h = ally ? 42 : 31;
		int x, y;
		if (ally) {
			x = sw - W - 46 + cfg.allyOffsetX;
			y = (int) (sh * 0.52f) + rank * (h + 4) - cfg.allyOffsetY;
		} else {
			x = 10 + cfg.foeOffsetX;
			y = 10 + rank * (h + 4) - cfg.foeOffsetY;
		}

		// Animación suave de la barra de PS
		float max = bp.getMaxHp();
		float target = max > 0 ? bp.getHpValue() / max : 0f;
		target = Math.max(0, Math.min(1, target));
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
		lastFrame = now;
		float cur = shownHp.getOrDefault(bp.getUuid(), target);
		float step = dt * 0.8f;
		if (Math.abs(target - cur) <= step) cur = target;
		else cur += Math.signum(target - cur) * step;
		shownHp.put(bp.getUuid(), cur);
		if (shownHp.size() > 64) shownHp.clear();

		int border = hovered ? Draw.GOLD : Draw.OUTLINE;
		// Sombra
		Draw.round(c, x + 2, y + 2, x + W + 2, y + h + 2, 0x60000000);
		Draw.panelGradient(c, x, y, x + W, y + h, border, Draw.CREAM, Draw.CREAM_DARK);
		// Franja plateada inferior (tipo DS)
		c.fill(x + 2, y + h - 4, x + W - 2, y + h - 2, Draw.SILVER);
		// Pestaña lateral
		if (ally) c.fill(x + 1, y + 3, x + 3, y + h - 3, Draw.SILVER_DARK);
		else c.fill(x + W - 3, y + 3, x + W - 1, y + h - 3, Draw.SILVER_DARK);

		int ix = x + (ally ? 6 : 5);
		// Nombre + sexo
		String name = Draw.fit(bp.getDisplayName().getString(), W - 52);
		Draw.dsText(c, name, ix, y + 4, 0xFF303030, Draw.TEXT_SHADOW);
		int gx = ix + Draw.width(name) + 2;
		Gender g = bp.getGender();
		if (g == Gender.MALE) Draw.dsText(c, "♂", gx, y + 4, Draw.MALE, 0xFFA8C8F8);
		else if (g == Gender.FEMALE) Draw.dsText(c, "♀", gx, y + 4, Draw.FEMALE, 0xFFF8B8C8);

		// Nivel (derecha)
		String lv = "Nv" + bp.getLevel();
		int lvx = x + W - 7 - Draw.width(lv);
		Draw.dsText(c, lv, lvx, y + 4, Draw.TEXT_DARK, Draw.TEXT_SHADOW);

		// Capturado (rival salvaje)
		if (!ally && dexState != null && dexState.equalsIgnoreCase("CAUGHT")) {
			Draw.tinyBall(c, lvx - 10, y + 4);
		}

		// Estado y barra de PS
		int barY = y + 15;
		PersistentStatus st = bp.getStatus();
		int sx = ix;
		int used = st != null ? Draw.statusBadge(c, st.getShowdownName(), sx, barY - 1) : 0;
		int barX = sx + (used > 0 ? used + 3 : 0);
		Draw.hpBar(c, barX, barY, x + W - 6 - barX, cur, true);

		if (ally) {
			// PS numéricos y barra de EXP
			if (bp.isHpFlat()) {
				String hp = Math.round(bp.getHpValue()) + "/ " + Math.round(max);
				Draw.dsText(c, hp, x + W - 7 - Draw.width(hp), y + 24, Draw.TEXT_DARK, Draw.TEXT_SHADOW);
			}
			Pokemon own = findOwn(bp.getUuid());
			if (own != null) {
				Draw.expBar(c, ix, y + h - 7, W - 12, expRatio(own));
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

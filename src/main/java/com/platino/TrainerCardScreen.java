package com.platino;

import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.api.pokedex.SpeciesDexRecord;
import com.cobblemon.mod.common.api.storage.player.client.ClientPokedexManager;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.text.Text;

import java.util.List;

/** Ficha de Entrenador estilo Diamante/Perla/Platino. */
public class TrainerCardScreen extends Screen {
	private static final int CW = 256, CH = 172;
	private static final int TEXT = 0xFFFFFFFF, SHADOW = 0xFF905050;
	private long opened;
	private String error;
	private int caught;
	private long caughtUpdated;

	public TrainerCardScreen() {
		super(Text.literal(com.platino.Tr.t("Ficha de Entrenador")));
	}

	@Override
	protected void init() {
		opened = System.currentTimeMillis();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
		c.fill(0, 0, width, height, 0x80101018);
	}

	@Override
	public void render(DrawContext c, int mouseX, int mouseY, float delta) {
		super.render(c, mouseX, mouseY, delta);
		if (error != null) {
			c.drawCenteredTextWithShadow(textRenderer, com.platino.Tr.t("Error en la Ficha: ") + error, width / 2, height / 2, 0xFFFF6060);
			return;
		}
		try {
			drawCard(c, mouseX, mouseY);
		} catch (Throwable t) {
			error = t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : "");
			PlatinoClient.LOGGER.error("[Cobblemon Platino] Error dibujando la Ficha de Entrenador", t);
		}
	}

	private void drawCard(DrawContext c, int mouseX, int mouseY) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) return;
		ServerStats.update();

		float t = Math.min(1f, (System.currentTimeMillis() - opened) / 180f);
		int x = (width - CW) / 2, y = (height - CH) / 2 + Math.round((1 - t) * 20);

		c.getMatrices().push();
		c.getMatrices().translate(0, 0, 100);
		Draw.round(c, x + 3, y + 4, x + CW + 3, y + CH + 4, 0x70000000);
		Draw.panel(c, x, y, x + CW, y + CH, 0xFFB04848, 0xFFF8F0E0);
		Draw.panelGradient(c, x + 3, y + 3, x + CW - 3, y + CH - 3, 0xFF983838, 0xFFF07878, 0xFFE05858);
		Draw.batch(c, () -> pokeballWatermark(c, x + 70, y + 84));

		Draw.tinyBall(c, x + 9, y + 8);
		Draw.dsText(c, com.platino.Tr.t("FICHA DE ENTRENADOR"), x + 20, y + 8, TEXT, SHADOW);
		Draw.tinyBall(c, x + 20 + Draw.width(com.platino.Tr.t("FICHA DE ENTRENADOR")) + 4, y + 8);

		String uuid = mc.player.getUuid().toString();
		String name = mc.player.getGameProfile().getName();
		String money = ServerStats.money != null ? "$" + ServerStats.money : "—";
		String dex = String.valueOf(caughtCount());
		String points = ServerStats.points != null ? ServerStats.points : "—";

		int rx1 = x + 8, rx2 = x + 168;
		row(c, rx1, rx2, y + 22, com.platino.Tr.t("Nº ID"), uuid);
		row(c, rx1, rx2, y + 40, com.platino.Tr.t("NOMBRE"), name);
		row(c, rx1, rx2, y + 62, com.platino.Tr.t("DINERO"), money);
		row(c, rx1, rx2, y + 84, "POKéDEX", dex);
		row(c, rx1, rx2, y + 106, com.platino.Tr.t("PUNTOS BATALLA"), points);

		// Medallas (de DiosesMon)
		List<DiosesmonBridge.Medal> medals = DiosesmonBridge.medals();
		int my = y + 130;
		Draw.round(c, x + 8, my, x + CW - 8, my + 32, 0x60FFE0E0);
		Draw.dsText(c, com.platino.Tr.t("MEDALLAS"), x + 12, my + 3, TEXT, SHADOW);
		if (medals.isEmpty()) {
			Draw.dsText(c, "—", x + 12, my + 17, TEXT, SHADOW);
		} else {
			int mx0 = x + 12, step = Math.min(18, (CW - 24) / Math.max(1, medals.size()));
			for (int i = 0; i < medals.size(); i++) {
				DiosesmonBridge.Medal m = medals.get(i);
				int ix = mx0 + i * step, iy = my + 13;
				Draw.round(c, ix - 1, iy - 1, ix + 17, iy + 17, 0x40000000);
				if (m.owned()) RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
				else RenderSystem.setShaderColor(0.25f, 0.25f, 0.25f, 0.7f);
				c.drawTexture(m.sprite(), ix, iy, 16, 16, 0f, 0f, 16, 16, 16, 16);
				RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
			}
		}

		if (ServerStats.clan != null) {
			String cl = "CLAN " + ServerStats.clan;
			Draw.dsText(c, cl, x + CW - 10 - Draw.width(cl), y + 8, 0xFFFFF0C0, SHADOW);
		}

		int fx1 = x + 176, fy1 = y + 22, fx2 = x + CW - 10, fy2 = y + 122;
		Draw.panel(c, fx1, fy1, fx2, fy2, 0xFFF8B8B8, 0x50FFFFFF);
		c.fill(fx1 + 8, fy2 - 10, fx2 - 8, fy2 - 6, 0x30000000);
		c.draw();
		// El muñeco se dibuja dentro de la capa de la tarjeta para quedar por delante
		c.getMatrices().translate(0, 0, 150);
		InventoryScreen.drawEntity(c, fx1 + 2, fy1 + 2, fx2 - 2, fy2 - 4, 44, 0.0625f, mouseX, mouseY, mc.player);
		c.getMatrices().pop();
	}

	private void row(DrawContext c, int x1, int x2, int y, String label, String value) {
		Draw.round(c, x1, y, x2, y + 16, 0x60FFE0E0);
		c.fill(x1 + 1, y + 15, x2 - 1, y + 16, 0x40903030);
		Draw.dsText(c, label, x1 + 4, y + 4, TEXT, SHADOW);
		int maxW = (x2 - x1) - Draw.width(label) - 14;
		int vw = Draw.width(value);
		if (vw <= maxW) {
			Draw.dsText(c, value, x2 - 4 - vw, y + 4, TEXT, SHADOW);
		} else {
			float s = Math.max(0.45f, maxW / (float) vw);
			c.getMatrices().push();
			c.getMatrices().translate(x2 - 4 - vw * s, y + 4 + (1 - s) * 4, 0);
			c.getMatrices().scale(s, s, 1);
			Draw.dsText(c, value, 0, 0, TEXT, SHADOW);
			c.getMatrices().pop();
		}
	}

	private static void pokeballWatermark(DrawContext c, int cx, int cy) {
		int col = 0x50F8D860;
		int ro = 62, ri = 46;
		for (int dy = -ro; dy <= ro; dy++) {
			int xo = (int) Math.sqrt(ro * ro - dy * dy);
			int xi = Math.abs(dy) < ri ? (int) Math.sqrt(ri * ri - dy * dy) : 0;
			if (Math.abs(dy) <= 6) {
				c.fill(cx - xo, cy + dy, cx + xo, cy + dy + 1, col);
			} else if (xi > 0) {
				c.fill(cx - xo, cy + dy, cx - xi, cy + dy + 1, col);
				c.fill(cx + xi, cy + dy, cx + xo, cy + dy + 1, col);
			} else {
				c.fill(cx - xo, cy + dy, cx + xo, cy + dy + 1, col);
			}
		}
		for (int dy = -16; dy <= 16; dy++) {
			int xo = (int) Math.sqrt(256 - dy * dy);
			c.fill(cx - xo, cy + dy, cx + xo, cy + dy + 1, col);
		}
	}

	private int caughtCount() {
		long now = System.currentTimeMillis();
		if (now - caughtUpdated >= 1000) {
			caught = countCaught();
			caughtUpdated = now;
		}
		return caught;
	}

	private static int countCaught() {
		try {
			ClientPokedexManager dex = CobblemonClient.INSTANCE.getClientPokedexData();
			int n = 0;
			for (SpeciesDexRecord r : dex.getSpeciesRecords().values()) {
				if (r.getKnowledge() == PokedexEntryProgress.CAUGHT) n++;
			}
			return n;
		} catch (Throwable e) {
			return 0;
		}
	}
}

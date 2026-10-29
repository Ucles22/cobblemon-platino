package com.platino;

import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.api.pokedex.SpeciesDexRecord;
import com.cobblemon.mod.common.api.storage.player.client.ClientPokedexManager;
import com.cobblemon.mod.common.client.CobblemonClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.text.Text;

/** Ficha de Entrenador estilo Diamante/Perla/Platino. */
public class TrainerCardScreen extends Screen {
	private static final int CW = 256, CH = 150;
	private static final int TEXT = 0xFFFFFFFF, SHADOW = 0xFF905050;
	private long opened;

	public TrainerCardScreen() {
		super(Text.literal("Ficha de Entrenador"));
	}

	@Override
	protected void init() {
		opened = System.currentTimeMillis();
		ServerStats.capturing = true;
	}

	@Override
	public void removed() {
		ServerStats.capturing = false;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
		// Fondo oscurecido sin desenfoque, para no tapar el marcador del servidor
		c.fill(0, 0, width, height, 0x80101018);
	}

	@Override
	public void render(DrawContext c, int mouseX, int mouseY, float delta) {
		ServerStats.update();
		super.render(c, mouseX, mouseY, delta);
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) return;

		// Pequeña animación de entrada
		float t = Math.min(1f, (System.currentTimeMillis() - opened) / 180f);
		int x = (width - CW) / 2, y = (height - CH) / 2 + Math.round((1 - t) * 20);

		c.getMatrices().push();
		c.getMatrices().translate(0, 0, 100);
		// Tarjeta
		Draw.round(c, x + 3, y + 4, x + CW + 3, y + CH + 4, 0x70000000);
		Draw.panel(c, x, y, x + CW, y + CH, 0xFFB04848, 0xFFF8F0E0);
		Draw.panelGradient(c, x + 3, y + 3, x + CW - 3, y + CH - 3, 0xFF983838, 0xFFF07878, 0xFFE05858);
		pokeballWatermark(c, x + 70, y + 86);

		// Cabecera
		Draw.tinyBall(c, x + 9, y + 8);
		Draw.dsText(c, "FICHA DE ENTRENADOR", x + 20, y + 8, TEXT, SHADOW);
		Draw.tinyBall(c, x + 20 + Draw.width("FICHA DE ENTRENADOR") + 4, y + 8);

		String uuid = mc.player.getUuid().toString();
		String name = mc.player.getGameProfile().getName();
		String money = ServerStats.money != null ? "$" + ServerStats.money : "—";
		String dex = String.valueOf(caughtCount());
		String points = ServerStats.points != null ? ServerStats.points : "—";

		int rx1 = x + 8, rx2 = x + 168;
		row(c, rx1, rx2, y + 22, "Nº ID", uuid);
		row(c, rx1, rx2, y + 40, "NOMBRE", name);
		row(c, rx1, rx2, y + 64, "DINERO", money);
		row(c, rx1, rx2, y + 88, "POKéDEX", dex);
		row(c, rx1, rx2, y + 112, "PUNTOS BATALLA", points);

		// Foto: el personaje del jugador con su skin
		int fx1 = x + 176, fy1 = y + 34, fx2 = x + CW - 10, fy2 = y + 132;
		Draw.panel(c, fx1, fy1, fx2, fy2, 0xFFF8B8B8, 0x50FFFFFF);
		c.getMatrices().pop();
		InventoryScreen.drawEntity(c, fx1 + 2, fy1 + 2, fx2 - 2, fy2 - 2, 40, 0.0625f, mouseX, mouseY, mc.player);

		if (ServerStats.clan != null) {
			String cl = "CLAN: " + ServerStats.clan;
			Draw.dsText(c, cl, x + CW - 10 - Draw.width(cl), y + CH - 13, 0xFFFFF0C0, SHADOW);
		}
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
			// Valores largos (UUID): letra más pequeña
			float s = Math.max(0.5f, maxW / (float) vw);
			c.getMatrices().push();
			c.getMatrices().translate(x2 - 4 - vw * s, y + 4 + (1 - s) * 4, 0);
			c.getMatrices().scale(s, s, 1);
			Draw.dsText(c, value, 0, 0, TEXT, SHADOW);
			c.getMatrices().pop();
		}
	}

	/** Pokéball amarilla de fondo, como en la ficha de DPPt. */
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

	private static int caughtCount() {
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

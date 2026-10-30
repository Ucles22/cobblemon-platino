package com.platino;

import com.cobblemon.mod.common.api.gui.GuiUtilsKt;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.gui.PartyOverlay;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.client.storage.ClientParty;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.status.PersistentStatusContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

/** Barra de equipo estilo Platino (paneles azules con retrato, nombre, nivel y PS). */
public final class PartyHud {
	private static final int W = 114;
	private static final int H = 26;
	private static final int CW = 70; // ancho compacto
	private static final int GAP = 2;

	private static MethodHandle portrait;
	private static boolean portraitFailed;

	private PartyHud() {}

	public static void render(DrawContext c, float partialTicks) {
		MinecraftClient mc = MinecraftClient.getInstance();
		PlatinoConfig cfg = PlatinoConfig.get();
		ClientParty party;
		try {
			party = CobblemonClient.INSTANCE.getStorage().getParty();
		} catch (Throwable t) {
			return;
		}
		List<Pokemon> slots = party.getSlots();
		int count = 0;
		for (Pokemon p : slots) if (p != null) count++;
		if (count == 0) return;

		boolean compact = cfg.partyCompact;
		int w = compact ? CW : W;
		int h = compact ? 18 : H;
		int total = slots.size() * (h + GAP) - GAP;
		int sh = mc.getWindow().getScaledHeight();
		int x0 = 2 + cfg.partyOffsetX;
		int y0 = (sh - total) / 2 - cfg.partyOffsetY;
		int selected = CobblemonClient.INSTANCE.getStorage().getSelectedSlot();

		for (int i = 0; i < slots.size(); i++) {
			Pokemon p = slots.get(i);
			int y = y0 + i * (h + GAP);
			boolean sel = i == selected;
			int x = x0 + (sel ? 4 : 0);
			if (p == null) {
				Draw.round(c, x, y + h / 2 - 2, x + 12, y + h / 2 + 2, 0x80303838);
				continue;
			}
			Draw.batch(c, () -> drawFrame(c, p, x, y, w, h, sel, compact));
			if (!compact) drawPortrait(c, p, i, x + 2, y + 2, partialTicks);
			int tx = compact ? x + 4 : x + 26;
			drawDetails(c, p, x, y, w, h, tx, compact);
			drawHeldItem(c, p, compact ? x + w - 14 : x + 13, compact ? y + 1 : y + 12);
		}
	}

	private static void drawFrame(DrawContext c, Pokemon p, int x, int y, int w, int h, boolean sel, boolean compact) {
		boolean fainted = p.isFainted();
		int top, bottom, border;
		if (fainted) { top = 0xFFE07868; bottom = 0xFFB84838; border = 0xFF682820; }
		else if (sel) { top = 0xFF78C0F0; bottom = 0xFF3880C8; border = Draw.GOLD; }
		else { top = 0xFF5898D0; bottom = 0xFF3068A8; border = 0xFF203858; }

		Draw.round(c, x + 1, y + 1, x + w + 1, y + h + 1, 0x60000000);
		Draw.panelGradient(c, x, y, x + w, y + h, border, top, bottom);
		if (sel) c.fill(x + 2, y + 1, x + w - 2, y + 2, 0xFFFFF0A0);

		if (!compact) {
			// Retrato circular
			int px = x + 3, py = y + 3;
			Draw.round(c, px, py, px + 20, py + 20, 0xFF203858);
			Draw.round(c, px + 1, py + 1, px + 19, py + 19, 0xFFD8E8F8);
		}
	}

	private static void drawDetails(DrawContext c, Pokemon p, int x, int y, int w, int h, int tx, boolean compact) {
		String lv = "Nv" + p.getLevel();
		String name = Draw.fit(p.getDisplayName(false).getString(), (x + w - 4 - Draw.width(lv) - 8) - tx);
		Draw.dsText(c, name, tx, y + 3, 0xFFFFFFFF, 0xFF203048);
		int gx = tx + Draw.width(name) + 1;
		Gender g = p.getGender();
		if (g == Gender.MALE) Draw.dsText(c, "♂", gx, y + 3, 0xFF88C8FF, 0xFF203048);
		else if (g == Gender.FEMALE) Draw.dsText(c, "♀", gx, y + 3, 0xFFFFA8C8, 0xFF203048);
		Draw.dsText(c, lv, x + w - 4 - Draw.width(lv), y + 3, 0xFFFFFFFF, 0xFF203048);

		// Barra de PS
		int max = Math.max(1, p.getMaxHealth());
		float ratio = p.getCurrentHealth() / (float) max;
		int barY = compact ? y + h - 6 : y + 13;
		int barX = tx;
		PersistentStatusContainer st = p.getStatus();
		if (st != null && st.getStatus() != null && !compact) {
			int bw = Draw.statusBadge(c, st.getStatus().getShowdownName(), tx, barY - 1);
			if (bw > 0) barX = tx + bw + 2;
		}
		if (compact) {
			c.fill(barX, barY, x + w - 4, barY + 3, Draw.OUTLINE);
			int fw = Math.round((x + w - 5 - barX - 1) * Math.max(0, Math.min(1, ratio)));
			int col = ratio > 0.5f ? Draw.HP_GREEN : ratio > 0.2f ? Draw.HP_YELLOW : Draw.HP_RED;
			if (fw > 0) c.fill(barX + 1, barY + 1, barX + 1 + fw, barY + 2, col);
		} else {
			Draw.hpBar(c, barX, barY, x + w - 4 - barX, ratio, false);
			// PS pequeños bajo la barra
			String hp = p.getCurrentHealth() + "/" + max;
			c.getMatrices().push();
			c.getMatrices().translate(x + w - 4 - Draw.width(hp) * 0.6f, y + 20.5f, 0);
			c.getMatrices().scale(0.6f, 0.6f, 1f);
			c.drawText(Draw.font(), hp, 0, 0, 0xFFE8F0F8, false);
			c.getMatrices().pop();
		}

		// Icono de nota
		if (Notes.has(p.getUuid())) {
			if (compact) Draw.dsText(c, "✎", x + w - 9, y + 2, Draw.GOLD, 0xFF6A4A00);
			else Draw.dsText(c, "✎", tx, y + 17, Draw.GOLD, 0xFF6A4A00);
		}
	}

	/** Llama a la función de retrato de Cobblemon (drawPosablePortrait$default), igual que hace su propia barra. */
	/** Icono real del objeto equipado, pequeño, en la esquina del retrato. */
	static void drawHeldItem(DrawContext c, Pokemon p, int x, int y) {
		ItemStack held;
		try {
			held = p.heldItem();
		} catch (Throwable t) {
			return;
		}
		if (held == null || held.isEmpty()) return;
		Draw.round(c, x - 1, y - 1, x + 12, y + 12, 0xC0F8F0D0);
		c.getMatrices().push();
		c.getMatrices().translate(x, y, 150);
		c.getMatrices().scale(0.7f, 0.7f, 1f);
		c.drawItem(held, 0, 0);
		c.getMatrices().pop();
	}

	static void drawPortrait(DrawContext c, Pokemon p, int index, int px, int py, float partialTicks) {
		if (portraitFailed) return;
		try {
			if (portrait == null) {
				MethodType type = MethodType.methodType(void.class, Identifier.class, MatrixStack.class, float.class, float.class,
						boolean.class, PosableState.class, float.class, float.class, float.class, float.class, float.class,
						float.class, boolean.class, float.class, float.class, float.class, float.class, int.class, Object.class);
				portrait = MethodHandles.lookup().findStatic(GuiUtilsKt.class, "drawPosablePortrait$default", type);
			}
			PartyOverlay overlay = CobblemonClient.INSTANCE.getOverlay();
			FloatingState state = overlay.getPokemonState(index, p.getUuid());
			state.setCurrentAspects(p.getAspects());
			MatrixStack m = c.getMatrices();
			c.enableScissor(px + 2, py + 2, px + 20, py + 20);
			m.push();
			try {
				m.translate(px + 10.5 - 1, py - 12, 0);
				portrait.invoke(p.getSpecies().getResourceIdentifier(), m, 0f, p.getForm().getBaseScale(), false,
						(PosableState) state, 0f, 0f, 0f, 0f, 0f, 0f, false, 0f, 0f, 0f, 0f, 126868, null);
			} finally {
				m.pop();
				c.disableScissor();
			}
		} catch (Throwable t) {
			portraitFailed = true;
			PlatinoClient.LOGGER.warn("[Cobblemon Platino] No se pudo dibujar el retrato; se desactiva", t);
		}
	}

	public static boolean canRender() {
		try {
			return PartyOverlay.Companion.canRender();
		} catch (Throwable t) {
			return true;
		}
	}
}

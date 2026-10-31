package com.platino.summary;

import com.cobblemon.mod.common.api.storage.party.PartyPosition;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.gui.summary.Summary;
import com.cobblemon.mod.common.client.storage.ClientParty;
import com.cobblemon.mod.common.net.messages.server.storage.party.MovePartyPokemonPacket;
import com.cobblemon.mod.common.net.messages.server.storage.party.SwapPartyPokemonPacket;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.status.PersistentStatusContainer;
import com.platino.Draw;
import com.platino.MiniModel;
import com.platino.Notes;
import com.platino.NotesScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Menú de equipo estilo Diamante/Perla: 6 paneles en dos columnas y menú DATOS / MOVER / NOTAS / SALIR. */
public class PartyScreen extends Screen {
	private static final int W = 324, H = 222, CW = 152, CH = 46;

	private enum Option {
		DATOS(com.platino.Tr.t("DATOS")), MOVER(com.platino.Tr.t("MOVER")), NOTAS(com.platino.Tr.t("NOTAS")), SALIR(com.platino.Tr.t("SALIR"));
		final String label;

		Option(String label) {
			this.label = label;
		}
	}

	private record Hit(int x1, int y1, int x2, int y2, Runnable action) {
		boolean contains(double x, double y) {
			return x >= x1 && x < x2 && y >= y1 && y < y2;
		}
	}

	private final Screen parent;
	private final List<Hit> hits = new ArrayList<>();
	/** Hueco con el cursor (0-5; 6 = botón SALIR). */
	private int cursor;
	/** Hueco con el menú abierto (-1 = ninguno) y opción marcada. */
	private int menuSlot = -1, menuCursor;
	/** Hueco que se está moviendo (-1 = ninguno). */
	private int moveFrom = -1;
	private String message;
	private long messageUntil;
	private int x0, y0;

	public PartyScreen(Screen parent) {
		this(parent, 0);
	}

	public PartyScreen(Screen parent, int cursor) {
		super(Text.literal(com.platino.Tr.t("Equipo")));
		this.parent = parent;
		this.cursor = Math.max(0, Math.min(5, cursor));
	}

	private static ClientParty party() {
		try {
			return CobblemonClient.INSTANCE.getStorage().getParty();
		} catch (Throwable t) {
			return null;
		}
	}

	private static Pokemon at(ClientParty cp, int slot) {
		if (cp == null) return null;
		List<Pokemon> s = cp.getSlots();
		return slot >= 0 && slot < s.size() ? s.get(slot) : null;
	}

	private static boolean inBattle() {
		try {
			return CobblemonClient.INSTANCE.getBattle() != null;
		} catch (Throwable t) {
			return false;
		}
	}

	private void say(String msg) {
		message = msg;
		messageUntil = System.currentTimeMillis() + 2500;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}

	@Override
	public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
		c.fill(0, 0, width, height, 0x80101018);
	}

	// ---------------------------------------------------------------- Dibujo

	@Override
	public void render(DrawContext c, int mx, int my, float delta) {
		super.render(c, mx, my, delta);
		hits.clear();
		ClientParty cp = party();
		if (cp == null) {
			close();
			return;
		}
		x0 = (width - W) / 2;
		y0 = (height - H) / 2;
		c.getMatrices().push();
		c.getMatrices().translate(0, 0, 50);

		// Fondo verde azulado con cuadros (como el menú de equipo de DPPt)
		Draw.round(c, x0 + 3, y0 + 3, x0 + W + 3, y0 + H + 3, 0x60000000);
		Draw.panel(c, x0, y0, x0 + W, y0 + H, 0xFF183838, 0xFF2C6870);
		for (int yy = y0 + 2; yy < y0 + H - 2; yy += 8) {
			for (int xx = x0 + 2 + ((yy - y0) / 8 % 2) * 8; xx < x0 + W - 2; xx += 16) {
				c.fill(xx, yy, Math.min(xx + 8, x0 + W - 2), Math.min(yy + 8, y0 + H - 2), 0xFF347880);
			}
		}

		for (int i = 0; i < 6; i++) {
			int sx = x0 + 6 + (i % 2) * (CW + 8);
			int sy = y0 + 6 + (i / 2) * (CH + 4) + (i % 2) * 8;
			drawSlot(c, at(cp, i), i, sx, sy, mx, my);
			int slot = i;
			hits.add(new Hit(sx, sy, sx + CW, sy + CH, () -> clickSlot(cp, slot)));
		}

		// Cuadro de texto inferior y botón SALIR
		int by = y0 + H - 38;
		Draw.panel(c, x0 + 6, by, x0 + W - 70, by + 32, 0xFF484858, 0xFFF8F8F8);
		c.fill(x0 + 9, by + 3, x0 + W - 73, by + 29, 0xFFF8F8F8);
		String text;
		if (message != null && System.currentTimeMillis() < messageUntil) text = message;
		else if (moveFrom >= 0) text = com.platino.Tr.t("¿Adónde quieres moverlo?");
		else if (menuSlot >= 0) {
			Pokemon p = at(cp, menuSlot);
			text = com.platino.Tr.t("¿Qué hacer con ") + (p == null ? "?" : p.getDisplayName(false).getString()) + "?";
		} else text = com.platino.Tr.t("Elige un POKéMON.");
		Draw.dsText(c, Draw.fit(text, W - 90), x0 + 14, by + 12, 0xFF303038, 0xFFD0D0D8);
		boolean exitSel = cursor == 6 && menuSlot < 0;
		boolean exitHover = mx >= x0 + W - 64 && mx < x0 + W - 6 && my >= by && my < by + 32;
		Draw.panelGradient(c, x0 + W - 64, by, x0 + W - 6, by + 32, exitSel ? Draw.GOLD : 0xFF682820,
				exitHover || exitSel ? 0xFFF08878 : 0xFFE06050, 0xFFB83828);
		String exit = moveFrom >= 0 ? com.platino.Tr.t("ANULAR") : com.platino.Tr.t("SALIR");
		Draw.dsText(c, exit, x0 + W - 35 - Draw.width(exit) / 2, by + 12, 0xFFFFFFFF, 0xFF682820);
		hits.add(new Hit(x0 + W - 64, by, x0 + W - 6, by + 32, this::pressExit));

		if (menuSlot >= 0) drawMenu(c, cp, mx, my);
		c.getMatrices().pop();
	}

	private void drawSlot(DrawContext c, Pokemon p, int i, int x, int y, int mx, int my) {
		boolean hover = mx >= x && mx < x + CW && my >= y && my < y + CH;
		boolean cur = i == cursor && menuSlot < 0;
		boolean moving = i == moveFrom;
		int border, top, bottom;
		if (p == null) {
			Draw.panelGradient(c, x, y, x + CW, y + CH, cur ? Draw.GOLD : 0xFF204848, 0xFF3C7880, 0xFF306068);
			Draw.dsText(c, "—", x + CW / 2 - 3, y + CH / 2 - 4, 0xFF80B0B0, 0xFF204848);
			return;
		}
		if (moving) { border = 0xFFF08030; top = 0xFFF8D080; bottom = 0xFFE8A040; }
		else if (p.isFainted()) { border = 0xFF682820; top = 0xFFF0A090; bottom = 0xFFD06858; }
		else if (i == 0) { border = 0xFF205868; top = 0xFF98E0E8; bottom = 0xFF58B0C0; }
		else { border = 0xFF205868; top = 0xFF88C8F0; bottom = 0xFF4890D0; }
		if (cur || hover || i == menuSlot) border = Draw.GOLD;
		Draw.round(c, x + 1, y + 1, x + CW + 1, y + CH + 1, 0x50000000);
		Draw.panelGradient(c, x, y, x + CW, y + CH, border, top, bottom);
		if (cur || i == menuSlot) {
			Draw.round(c, x - 1, y - 1, x + CW + 1, y + 1, Draw.GOLD);
			Draw.round(c, x - 1, y + CH - 1, x + CW + 1, y + CH + 1, Draw.GOLD);
		}

		// Modelo en una bola blanca
		Draw.round(c, x + 3, y + 3, x + 41, y + CH - 3, 0x60FFFFFF);
		c.draw();
		MiniModel.draw(c, p, x + 4, y + 4, 36);
		c.draw();

		int tx = x + 45;
		String name = Draw.fit(p.getDisplayName(false).getString(), CW - 62);
		Draw.dsText(c, name, tx, y + 4, 0xFFFFFFFF, 0xFF284868);
		int gx = tx + Draw.width(name) + 2;
		Gender g = p.getGender();
		if (g == Gender.MALE) Draw.dsText(c, "♂", gx, y + 4, 0xFF88C8FF, 0xFF284868);
		else if (g == Gender.FEMALE) Draw.dsText(c, "♀", gx, y + 4, 0xFFFFA8C8, 0xFF284868);
		if (Notes.has(p.getUuid())) Draw.dsText(c, "✎", x + CW - 11, y + 4, Draw.GOLD, 0xFF6A4A00);

		int max = Math.max(1, p.getMaxHealth());
		int barX = tx;
		PersistentStatusContainer st = p.getStatus();
		if (st != null && st.getStatus() != null) {
			int bw = Draw.statusBadge(c, st.getStatus().getShowdownName(), tx, y + 15);
			if (bw > 0) barX = tx + bw + 2;
		}
		Draw.hpBar(c, barX, y + 16, x + CW - 5 - barX, p.getCurrentHealth() / (float) max, true);
		Draw.dsText(c, com.platino.Tr.t("Nv.") + p.getLevel(), tx, y + 31, 0xFFFFFFFF, 0xFF284868);
		String hp = p.getCurrentHealth() + "/" + max;
		Draw.dsText(c, hp, x + CW - 6 - Draw.width(hp), y + 31, 0xFFFFFFFF, 0xFF284868);

		ItemStack held = ItemStack.EMPTY;
		try {
			held = p.heldItem();
		} catch (Throwable ignored) {
		}
		if (!held.isEmpty()) {
			c.getMatrices().push();
			c.getMatrices().translate(x + 30, y + CH - 14, 200);
			c.getMatrices().scale(0.7f, 0.7f, 1f);
			c.drawItem(held, 0, 0);
			c.getMatrices().pop();
		}
	}

	private void drawMenu(DrawContext c, ClientParty cp, int mx, int my) {
		Option[] opts = Option.values();
		int mw = 74, ih = 16, mh = opts.length * ih + 8;
		// Justo debajo, a la derecha del Pokémon elegido
		int sx = x0 + 6 + (menuSlot % 2) * (CW + 8);
		int sy = y0 + 6 + (menuSlot / 2) * (CH + 4) + (menuSlot % 2) * 8;
		int x = sx + CW - mw + 12, y = sy + CH - 6;
		if (y + mh > height - 2) y = sy - mh + 6;
		x = Math.max(2, Math.min(x, width - mw - 2));
		y = Math.max(2, y);
		c.getMatrices().push();
		c.getMatrices().translate(0, 0, 200);
		Draw.panel(c, x, y, x + mw, y + mh, 0xFF484858, 0xFFF8F8F8);
		for (int i = 0; i < opts.length; i++) {
			int iy = y + 4 + i * ih;
			boolean hover = mx >= x && mx < x + mw && my >= iy && my < iy + ih;
			if (hover) menuCursor = i;
			if (i == menuCursor) {
				Draw.round(c, x + 3, iy, x + mw - 3, iy + ih - 1, 0xFFF8E0A0);
				Draw.dsText(c, "▶", x + 6, iy + 4, 0xFFE05030, 0xFFF8C0A0);
			}
			boolean disabled = opts[i] == Option.MOVER && inBattle();
			Draw.dsText(c, opts[i].label, x + 18, iy + 4, disabled ? 0xFFA0A0A8 : 0xFF303038, 0xFFD0D0D8);
			Option o = opts[i];
			hits.add(new Hit(x, iy, x + mw, iy + ih, () -> choose(cp, o)));
		}
		c.getMatrices().pop();
	}

	// ---------------------------------------------------------------- Acciones

	private static void beep(float pitch) {
		MinecraftClient.getInstance().getSoundManager()
				.play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.4f));
	}

	private void pressExit() {
		beep(1.4f);
		if (moveFrom >= 0) moveFrom = -1;
		else if (menuSlot >= 0) menuSlot = -1;
		else close();
	}

	private void clickSlot(ClientParty cp, int slot) {
		cursor = slot;
		if (moveFrom >= 0) {
			finishMove(cp, slot);
			return;
		}
		if (menuSlot >= 0) {
			menuSlot = -1;
			return;
		}
		if (at(cp, slot) == null) return;
		menuSlot = slot;
		menuCursor = 0;
		beep(1.7f);
	}

	private void choose(ClientParty cp, Option o) {
		Pokemon p = at(cp, menuSlot);
		int slot = menuSlot;
		beep(1.6f);
		switch (o) {
			case DATOS -> {
				menuSlot = -1;
				if (p == null) return;
				DppSummaryScreen.nextParent = this;
				Summary.Companion.open(cp.getSlots(), true, slot);
			}
			case MOVER -> {
				if (inBattle()) {
					say(com.platino.Tr.t("No se puede durante un combate."));
					return;
				}
				menuSlot = -1;
				moveFrom = slot;
			}
			case NOTAS -> {
				menuSlot = -1;
				if (p != null) MinecraftClient.getInstance().setScreen(new NotesScreen(this, p));
			}
			case SALIR -> menuSlot = -1;
		}
	}

	private void finishMove(ClientParty cp, int target) {
		int from = moveFrom;
		moveFrom = -1;
		if (from == target) return;
		Pokemon a = at(cp, from), b = at(cp, target);
		if (a == null) return;
		if (b == null) new MovePartyPokemonPacket(a.getUuid(), new PartyPosition(from), new PartyPosition(target)).sendToServer();
		else new SwapPartyPokemonPacket(a.getUuid(), new PartyPosition(from), b.getUuid(), new PartyPosition(target)).sendToServer();
		beep(1.9f);
	}

	// ---------------------------------------------------------------- Entrada

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button == 1) {
			pressExit();
			return true;
		}
		if (button == 0) {
			List<Hit> list = new ArrayList<>(hits);
			for (int i = list.size() - 1; i >= 0; i--) {
				if (list.get(i).contains(mx, my)) {
					list.get(i).action().run();
					return true;
				}
			}
			if (menuSlot >= 0) menuSlot = -1;
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		ClientParty cp = party();
		if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE) {
			pressExit();
			return true;
		}
		boolean enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE;
		if (menuSlot >= 0) {
			int n = Option.values().length;
			if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_W) menuCursor = Math.floorMod(menuCursor - 1, n);
			else if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_S) menuCursor = Math.floorMod(menuCursor + 1, n);
			else if (enter) choose(cp, Option.values()[menuCursor]);
			return true;
		}
		switch (key) {
			case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> cursor = cursor == 6 ? 5 : Math.max(0, cursor - 1);
			case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> cursor = Math.min(6, cursor + 1);
			case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> cursor = cursor == 6 ? 5 : Math.max(0, cursor - 2);
			case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> cursor = cursor >= 4 ? 6 : cursor + 2;
			default -> {
				if (enter) {
					if (cursor == 6) pressExit();
					else clickSlot(cp, cursor);
					return true;
				}
				return super.keyPressed(key, scan, mods);
			}
		}
		return true;
	}
}

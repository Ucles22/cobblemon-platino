package com.platino.summary;

import com.cobblemon.mod.common.CobblemonSounds;
import com.cobblemon.mod.common.api.storage.party.PartyPosition;
import com.cobblemon.mod.common.api.storage.pc.PCPosition;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.gui.pc.PCGUI;
import com.cobblemon.mod.common.client.gui.summary.Summary;
import com.cobblemon.mod.common.client.storage.ClientBox;
import com.cobblemon.mod.common.client.storage.ClientPC;
import com.cobblemon.mod.common.client.storage.ClientParty;
import com.cobblemon.mod.common.net.messages.server.storage.SwapPCPartyPokemonPacket;
import com.cobblemon.mod.common.net.messages.server.storage.party.MovePartyPokemonPacket;
import com.cobblemon.mod.common.net.messages.server.storage.party.ReleasePartyPokemonPacket;
import com.cobblemon.mod.common.net.messages.server.storage.party.SwapPartyPokemonPacket;
import com.cobblemon.mod.common.net.messages.server.storage.pc.MovePCPokemonPacket;
import com.cobblemon.mod.common.net.messages.server.storage.pc.MovePCPokemonToPartyPacket;
import com.cobblemon.mod.common.net.messages.server.storage.pc.MovePartyPokemonToPCPacket;
import com.cobblemon.mod.common.net.messages.server.storage.pc.ReleasePCPokemonPacket;
import com.cobblemon.mod.common.net.messages.server.storage.pc.SwapPCPokemonPacket;
import com.cobblemon.mod.common.net.messages.server.storage.pc.UnlinkPlayerFromPCPacket;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.platino.Draw;
import com.platino.MiniModel;
import com.platino.Notes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * PC estilo Diamante/Perla («Mover Pokémon»): ficha grande a la izquierda, caja de 6×5 con su fondo y el equipo
 * a la derecha. Se coge un Pokémon con un clic y se suelta con otro. /platino pc off vuelve al PC de Cobblemon.
 */
public class DppPcScreen extends Screen {
	/** true mientras se abre el PC original de Cobblemon a propósito. */
	public static boolean bypass;

	private static final int W = 424, H = 256, LW = 132;
	private static final int COLS = 6, ROWS = 5, CELL = 26;

	private record Hit(int x1, int y1, int x2, int y2, Runnable action) {
		boolean contains(double x, double y) {
			return x >= x1 && x < x2 && y >= y1 && y < y2;
		}
	}

	/** Dónde está un Pokémon: en el PC (box/slot) o en el equipo (box = -1). */
	private record Spot(int box, int slot) {
		boolean party() {
			return box < 0;
		}
	}

	private final PCGUI original;
	private final ClientPC pc;
	private final List<Hit> hits = new ArrayList<>();
	private int box;
	private Spot held;
	private Pokemon heldPokemon;
	private Pokemon preview;
	private Spot releaseConfirm;
	/** Opción marcada en el menú de liberar: 0 = SÍ, 1 = NO. */
	private int confirmCursor = 1;
	private String message;
	private long messageUntil;
	private int x0, y0;

	public DppPcScreen(PCGUI original) {
		super(Text.literal("PC"));
		this.original = original;
		this.pc = original.getPc();
		int boxes = Math.max(1, pc.getBoxes().size());
		this.box = Math.floorMod(original.getOpenOnBox(), boxes);
	}

	private static ClientParty party() {
		try {
			return CobblemonClient.INSTANCE.getStorage().getParty();
		} catch (Throwable t) {
			return null;
		}
	}

	private Pokemon at(Spot s) {
		if (s == null) return null;
		if (s.party()) {
			ClientParty cp = party();
			if (cp == null) return null;
			List<Pokemon> slots = cp.getSlots();
			return s.slot() < slots.size() ? slots.get(s.slot()) : null;
		}
		if (s.box() >= pc.getBoxes().size()) return null;
		List<Pokemon> slots = pc.getBoxes().get(s.box()).getSlots();
		return s.slot() < slots.size() ? slots.get(s.slot()) : null;
	}

	private void say(String msg) {
		message = msg;
		messageUntil = System.currentTimeMillis() + 2500;
	}

	private static void sound(SoundEvent e, float pitch) {
		MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(e, pitch, 0.5f));
	}

	private static void click() {
		try {
			sound(CobblemonSounds.PC_CLICK, 1f);
		} catch (Throwable t) {
			sound(SoundEvents.UI_BUTTON_CLICK.value(), 1.6f);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
		c.fill(0, 0, width, height, 0x80101018);
	}

	/** Cierra el PC avisando al servidor, igual que el botón de salir de Cobblemon. */
	@Override
	public void close() {
		try {
			sound(CobblemonSounds.PC_OFF, 1f);
		} catch (Throwable ignored) {
		}
		MinecraftClient.getInstance().setScreen(null);
		new UnlinkPlayerFromPCPacket().sendToServer();
	}

	/** Abre el PC original de Cobblemon (fondos, ordenar, buscar, marcas…). */
	private void openOriginal() {
		bypass = true;
		try {
			MinecraftClient.getInstance().setScreen(original);
		} finally {
			bypass = false;
		}
	}

	// ---------------------------------------------------------------- Dibujo

	@Override
	public void render(DrawContext c, int mx, int my, float delta) {
		super.render(c, mx, my, delta);
		hits.clear();
		x0 = (width - W) / 2;
		y0 = (height - H) / 2;
		int boxes = pc.getBoxes().size();
		if (boxes == 0) {
			close();
			return;
		}
		box = Math.floorMod(box, boxes);
		if (held != null && (heldPokemon == null || at(held) == null || !at(held).getUuid().equals(heldPokemon.getUuid()))) {
			held = null;
			heldPokemon = null;
		}
		Pokemon hover = null;

		c.getMatrices().push();
		c.getMatrices().translate(0, 0, 50);
		Draw.round(c, x0 + 3, y0 + 3, x0 + W + 3, y0 + H + 3, 0x60000000);
		Draw.panel(c, x0, y0, x0 + W, y0 + H, 0xFF283048, 0xFF384868);
		c.fillGradient(x0 + 1, y0 + 1, x0 + W - 1, y0 + 19, 0xFF68A0D8, 0xFF3060A8);
		for (int yy = y0 + 4; yy < y0 + 18; yy += 4) c.fill(x0 + 1, yy, x0 + W - 1, yy + 1, 0x18FFFFFF);
		Draw.dsText(c, "MOVER POKéMON", x0 + 8, y0 + 6, 0xFFFFFFFF, 0xFF203060);
		button(c, x0 + W - 92, y0 + 4, x0 + W - 22, y0 + 16, "Cobblemon ⇄", mx, my, 0xFFE8E8F0, this::openOriginal);
		button(c, x0 + W - 18, y0 + 4, x0 + W - 5, y0 + 16, "×", mx, my, 0xFFE8E8F0, this::close);

		// ---- Caja
		int bx = x0 + LW + 8, by = y0 + 24, bw = COLS * CELL + 8;
		ClientBox cb = pc.getBoxes().get(box);
		Draw.panelGradient(c, bx, by, bx + bw, by + 18, 0xFF284868, 0xFFB8D8F8, 0xFF78A8E0);
		String name = cb.getName() == null || cb.getName().getString().isBlank() ? "CAJA " + (box + 1) : cb.getName().getString();
		name = Draw.fit(name.toUpperCase(Locale.ROOT), bw - 40);
		Draw.dsText(c, name, bx + bw / 2 - Draw.width(name) / 2, by + 5, 0xFF303038, 0xFFE8F0F8);
		button(c, bx + 3, by + 3, bx + 15, by + 15, "◀", mx, my, 0xFFF0F0F8, () -> changeBox(-1));
		button(c, bx + bw - 15, by + 3, bx + bw - 3, by + 15, "▶", mx, my, 0xFFF0F0F8, () -> changeBox(1));
		hits.add(new Hit(bx + 16, by, bx + bw - 16, by + 18, () -> {}));

		int gx = bx, gy = by + 20, gh = ROWS * CELL + 8;
		Draw.panel(c, gx, gy, gx + bw, gy + gh, 0xFF284868, 0xFF88B8E0);
		Identifier wall = cb.getWallpaper();
		if (wall != null) {
			try {
				c.drawTexture(wall, gx + 2, gy + 2, bw - 4, gh - 4, 0f, 0f, 1, 1, 1, 1);
				c.fill(gx + 2, gy + 2, gx + bw - 2, gy + gh - 2, 0x30FFFFFF);
			} catch (Throwable ignored) {
			}
		} else {
			for (int yy = gy + 4; yy < gy + gh - 2; yy += 4) c.fill(gx + 2, yy, gx + bw - 2, yy + 1, 0x20FFFFFF);
		}
		List<Pokemon> slots = cb.getSlots();
		for (int i = 0; i < COLS * ROWS; i++) {
			int sx = gx + 4 + (i % COLS) * CELL, sy = gy + 4 + (i / COLS) * CELL;
			Pokemon p = i < slots.size() ? slots.get(i) : null;
			Spot spot = new Spot(box, i);
			boolean over = mx >= sx && mx < sx + CELL && my >= sy && my < sy + CELL;
			if (over && p != null) hover = p;
			drawCell(c, p, spot, sx, sy, CELL, over);
			hits.add(new Hit(sx, sy, sx + CELL, sy + CELL, () -> clickSpot(spot)));
		}

		// ---- Equipo
		int px = bx + bw + 6, py = by, pw = x0 + W - 4 - px;
		Draw.panelGradient(c, px, py, px + pw, py + 18, 0xFF682820, 0xFFF08878, 0xFFD05040);
		Draw.dsText(c, "EQUIPO", px + pw / 2 - Draw.width("EQUIPO") / 2, py + 5, 0xFFFFFFFF, 0xFF682820);
		int pgh = 3 * 34 + 8;
		Draw.panel(c, px, py + 20, px + pw, py + 20 + pgh, 0xFF682820, 0xFFF8D0C8);
		ClientParty cp = party();
		List<Pokemon> ps = cp == null ? List.of() : cp.getSlots();
		int pc2 = (pw - 8) / 2;
		for (int i = 0; i < 6; i++) {
			int sx = px + 4 + (i % 2) * pc2, sy = py + 24 + (i / 2) * 34 + (i % 2) * 4;
			Pokemon p = i < ps.size() ? ps.get(i) : null;
			Spot spot = new Spot(-1, i);
			boolean over = mx >= sx && mx < sx + pc2 && my >= sy && my < sy + 30;
			if (over && p != null) hover = p;
			drawCell(c, p, spot, sx, sy, Math.min(pc2, 30), over);
			hits.add(new Hit(sx, sy, sx + pc2, sy + 30, () -> clickSpot(spot)));
		}
		int ry = py + 20 + pgh + 4;
		boolean canRelease = held != null;
		button(c, px, ry, px + pw, ry + 14, "Liberar", mx, my, canRelease ? 0xFFF8C8C0 : 0xFFD0D0D0, this::release);

		// ---- Ficha grande
		Pokemon shown = held != null ? heldPokemon : hover != null ? hover : preview;
		if (shown != null && hover != null && held == null) preview = hover;
		drawPreview(c, shown);

		// ---- Cuadro de texto inferior
		int ty = y0 + H - 30;
		Draw.panel(c, x0 + LW + 8, ty, x0 + W - 4, ty + 26, 0xFF484858, 0xFFF8F8F8);
		String text;
		if (message != null && System.currentTimeMillis() < messageUntil) text = message;
		else if (releaseConfirm != null) text = "¿Seguro que quieres liberar a " + heldPokemon.getDisplayName(false).getString() + "?";
		else if (held != null) text = "¿Dónde quieres dejar a " + heldPokemon.getDisplayName(false).getString() + "?";
		else text = "Clic: coger · Clic derecho: datos · Rueda: caja";
		Draw.dsText(c, Draw.fit(text, W - LW - 24), x0 + LW + 14, ty + 9, 0xFF303038, 0xFFD0D0D8);

		if (releaseConfirm != null) drawReleaseMenu(c, px, ry + 18, pw, mx, my);

		// Pokémon cogido siguiendo al ratón
		if (held != null && heldPokemon != null && releaseConfirm == null) {
			c.getMatrices().push();
			c.getMatrices().translate(0, 0, 300);
			c.draw();
			MiniModel.draw(c, heldPokemon, mx - 12, my - 18, 24);
			c.draw();
			c.getMatrices().pop();
		}
		c.getMatrices().pop();
	}

	private void drawCell(DrawContext c, Pokemon p, Spot spot, int x, int y, int size, boolean over) {
		boolean isHeld = held != null && held.equals(spot);
		if (over) Draw.round(c, x, y, x + size, y + size, held != null ? 0x80F8E080 : 0x60FFFFFF);
		if (p == null || isHeld) {
			if (isHeld) Draw.round(c, x + 2, y + 2, x + size - 2, y + size - 2, 0x40000000);
			return;
		}
		c.draw();
		MiniModel.draw(c, p, x + 1, y + 1, size - 2);
		c.draw();
		if (p.getShiny()) Draw.dsText(c, "★", x + size - 8, y + 1, 0xFFF8D030, 0xFF806010);
		ItemStack held = ItemStack.EMPTY;
		try {
			held = p.heldItem();
		} catch (Throwable ignored) {
		}
		if (!held.isEmpty()) {
			c.getMatrices().push();
			c.getMatrices().translate(x + size - 9, y + size - 9, 200);
			c.getMatrices().scale(0.5f, 0.5f, 1f);
			c.drawItem(held, 0, 0);
			c.getMatrices().pop();
		}
	}

	private void drawPreview(DrawContext c, Pokemon p) {
		int x = x0 + 4, y = y0 + 22, w = LW;
		c.fill(x, y, x + w, y0 + H - 4, 0xFFD0D0C0);
		for (int yy = y; yy < y0 + H - 4; yy += 3) c.fill(x, yy, x + w, yy + 1, 0x18FFFFFF);
		Draw.panelGradient(c, x + 2, y + 2, x + w - 2, y + 18, 0xFFB87820, 0xFFF8D080, 0xFFF0B048);
		if (p == null) {
			Draw.dsText(c, "—", x + 8, y + 6, 0xFFFFFFFF, 0xFF905018);
			return;
		}
		Draw.tinyBall(c, x + 6, y + 6);
		Draw.dsText(c, Draw.fit(p.getDisplayName(false).getString().toUpperCase(Locale.ROOT), w - 34), x + 16, y + 6, 0xFFFFFFFF, 0xFF905018);
		Gender g = p.getGender();
		if (g == Gender.MALE) Draw.dsText(c, "♂", x + w - 12, y + 6, 0xFF3070F0, 0xFFA8C8F8);
		else if (g == Gender.FEMALE) Draw.dsText(c, "♀", x + w - 12, y + 6, 0xFFF04870, 0xFFF8B8C8);

		int ps = 84, px = x + (w - ps) / 2, py = y + 21;
		Draw.panel(c, px, py, px + ps, py + ps, 0xFFA8A8B0, 0xFFE8ECF4);
		for (int yy = py + 3; yy < py + ps - 2; yy += 3) c.fill(px + 2, yy, px + ps - 2, yy + 1, 0xFFD8DCE8);
		c.draw();
		MiniModel.draw(c, p, px + 3, py + 3, ps - 6);
		c.draw();
		if (p.getShiny()) Draw.dsText(c, "★", px + ps - 10, py + 3, 0xFFF8D030, 0xFF806010);

		int ly = py + ps + 4;
		line(c, x, ly, w, "Nv." + p.getLevel(), p.getSpecies().getTranslatedName().getString());
		ly += 14;
		Draw.round(c, x + 2, ly, x + w - 2, ly + 14, 0xFFF8F8F0);
		int tx = x + w - 4;
		if (p.getSecondaryType() != null) tx -= TypeBadge.draw(c, p.getSecondaryType().getName(), tx - TypeBadge.WIDTH, ly + 2) + 2;
		TypeBadge.draw(c, p.getPrimaryType().getName(), tx - TypeBadge.WIDTH, ly + 2);
		ly += 16;
		String nat = "—", ab = "—";
		try {
			nat = Text.translatable(p.getEffectiveNature().getDisplayName()).getString();
			ab = Text.translatable(p.getAbility().getDisplayName()).getString();
		} catch (Throwable ignored) {
		}
		line(c, x, ly, w, "Natur.", nat);
		ly += 14;
		line(c, x, ly, w, "Habil.", ab);
		ly += 14;
		ItemStack item = ItemStack.EMPTY;
		try {
			item = p.heldItem();
		} catch (Throwable ignored) {
		}
		line(c, x, ly, w, "Objeto", item.isEmpty() ? "Ninguno" : item.getName().getString());
		ly += 14;
		int ivs = 0;
		for (int i = 0; i < 6; i++) ivs += DppSummaryScreen.iv(p, i);
		line(c, x, ly, w, "IVs", ivs + "/186" + (Notes.has(p.getUuid()) ? "  ✎" : ""));
	}

	private static void line(DrawContext c, int x, int y, int w, String label, String value) {
		Draw.round(c, x + 2, y, x + w - 2, y + 13, 0xFFF8F8F0);
		Draw.dsText(c, label, x + 6, y + 3, 0xFF906030, 0xFFE8D8C0);
		String v = Draw.fit(value, w - 16 - Draw.width(label));
		Draw.dsText(c, v, x + w - 6 - Draw.width(v), y + 3, 0xFF303038, 0xFFD8D8D0);
	}

	private void button(DrawContext c, int x1, int y1, int x2, int y2, String label, int mx, int my, int color, Runnable action) {
		boolean hover = mx >= x1 && mx < x2 && my >= y1 && my < y2;
		Draw.round(c, x1, y1, x2, y2, 0xFF303848);
		c.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, hover ? Draw.lerpColor(color, 0xFFFFFFFF, 0.4f) : color);
		c.drawText(textRenderer, label, (x1 + x2) / 2 - textRenderer.getWidth(label) / 2, (y1 + y2) / 2 - 4, 0xFF303030, false);
		hits.add(new Hit(x1, y1, x2, y2, action));
	}

	// ---------------------------------------------------------------- Acciones

	private void changeBox(int dir) {
		box = Math.floorMod(box + dir, Math.max(1, pc.getBoxes().size()));
		releaseConfirm = null;
		click();
	}

	private void clickSpot(Spot spot) {
		releaseConfirm = null;
		Pokemon target = at(spot);
		if (held == null) {
			if (target == null) return;
			held = spot;
			heldPokemon = target;
			preview = target;
			click();
			return;
		}
		if (held.equals(spot)) {
			held = null;
			heldPokemon = null;
			click();
			return;
		}
		move(held, heldPokemon, spot, target);
		held = null;
		heldPokemon = null;
		click();
	}

	private void move(Spot from, Pokemon a, Spot to, Pokemon b) {
		ClientParty cp = party();
		if (from.party() && !to.party() && b == null && cp != null) {
			int count = 0;
			for (Pokemon q : cp.getSlots()) if (q != null) count++;
			if (count <= 1) {
				say("No puedes quedarte sin Pokémon en el equipo.");
				return;
			}
		}
		if (!from.party() && !to.party()) {
			PCPosition pa = new PCPosition(from.box(), from.slot()), pb = new PCPosition(to.box(), to.slot());
			if (b == null) new MovePCPokemonPacket(a.getUuid(), pa, pb).sendToServer();
			else new SwapPCPokemonPacket(a.getUuid(), pa, b.getUuid(), pb).sendToServer();
		} else if (from.party() && to.party()) {
			PartyPosition pa = new PartyPosition(from.slot()), pb = new PartyPosition(to.slot());
			if (b == null) new MovePartyPokemonPacket(a.getUuid(), pa, pb).sendToServer();
			else new SwapPartyPokemonPacket(a.getUuid(), pa, b.getUuid(), pb).sendToServer();
		} else if (!from.party()) {
			PCPosition pa = new PCPosition(from.box(), from.slot());
			PartyPosition pb = new PartyPosition(to.slot());
			if (b == null) new MovePCPokemonToPartyPacket(a.getUuid(), pa, pb).sendToServer();
			else new SwapPCPartyPokemonPacket(b.getUuid(), pb, a.getUuid(), pa).sendToServer();
		} else {
			PartyPosition pa = new PartyPosition(from.slot());
			PCPosition pb = new PCPosition(to.box(), to.slot());
			if (b == null) new MovePartyPokemonToPCPacket(a.getUuid(), pa, pb).sendToServer();
			else new SwapPCPartyPokemonPacket(a.getUuid(), pa, b.getUuid(), pb).sendToServer();
		}
	}

	/** Menú «¿Seguro que quieres liberarlo?» con SÍ / NO, como el menú del equipo. */
	private void drawReleaseMenu(DrawContext c, int x, int y, int w, int mx, int my) {
		c.getMatrices().push();
		c.getMatrices().translate(0, 0, 250);
		hits.add(new Hit(0, 0, width, height, this::cancelRelease)); // clic fuera = NO
		List<net.minecraft.text.OrderedText> q = textRenderer.wrapLines(
				net.minecraft.text.StringVisitable.plain("¿Seguro que quieres liberarlo?"), w - 12);
		int ih = 16, mh = 6 + q.size() * 10 + 4 + 2 * ih + 4;
		Draw.panel(c, x, y, x + w, y + mh, 0xFF484858, 0xFFF8F8F8);
		for (int i = 0; i < q.size(); i++) c.drawText(textRenderer, q.get(i), x + 6, y + 6 + i * 10, 0xFF303038, false);
		int oy = y + 6 + q.size() * 10 + 4;
		String[] opts = {"SÍ", "NO"};
		for (int i = 0; i < 2; i++) {
			int iy = oy + i * ih;
			if (mx >= x && mx < x + w && my >= iy && my < iy + ih) confirmCursor = i;
			if (i == confirmCursor) {
				Draw.round(c, x + 3, iy, x + w - 3, iy + ih - 1, 0xFFF8E0A0);
				Draw.dsText(c, "▶", x + 6, iy + 4, 0xFFE05030, 0xFFF8C0A0);
			}
			Draw.dsText(c, opts[i], x + 18, iy + 4, 0xFF303038, 0xFFD0D0D8);
			int opt = i;
			hits.add(new Hit(x, iy, x + w, iy + ih, () -> {
				if (opt == 0) doRelease();
				else cancelRelease();
			}));
		}
		c.getMatrices().pop();
	}

	private void cancelRelease() {
		releaseConfirm = null;
		click();
	}

	private void release() {
		if (held == null) {
			say("Primero coge al Pokémon que quieres liberar.");
			return;
		}
		releaseConfirm = held;
		confirmCursor = 1;
		sound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.8f);
	}

	private void doRelease() {
		if (held == null || heldPokemon == null) {
			releaseConfirm = null;
			return;
		}
		Pokemon p = heldPokemon;
		if (held.party()) {
			ClientParty cp = party();
			int count = 0;
			if (cp != null) for (Pokemon q : cp.getSlots()) if (q != null) count++;
			if (count <= 1) {
				say("No puedes liberar a tu último Pokémon.");
				releaseConfirm = null;
				return;
			}
			new ReleasePartyPokemonPacket(p.getUuid(), new PartyPosition(held.slot())).sendToServer();
		} else {
			new ReleasePCPokemonPacket(p.getUuid(), new PCPosition(held.box(), held.slot())).sendToServer();
		}
		say("Adiós, " + p.getDisplayName(false).getString() + "...");
		held = null;
		heldPokemon = null;
		releaseConfirm = null;
		preview = null;
	}

	private void openSummary(Spot spot) {
		Pokemon p = at(spot);
		if (p == null) return;
		if (spot.party()) {
			ClientParty cp = party();
			DppSummaryScreen.nextParent = this;
			if (cp != null) Summary.Companion.open(cp.getSlots(), true, spot.slot());
		} else {
			DppSummaryScreen.nextParent = this;
			Summary.Companion.open(pc.getBoxes().get(spot.box()).getSlots(), false, spot.slot());
		}
	}

	// ---------------------------------------------------------------- Entrada

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		List<Hit> list = new ArrayList<>(hits);
		if (button == 1) {
			if (releaseConfirm != null) {
				cancelRelease();
				return true;
			}
			if (held != null) {
				held = null;
				heldPokemon = null;
				click();
				return true;
			}
			Spot s = spotAt(mx, my);
			if (s != null) openSummary(s);
			return true;
		}
		if (button == 0) {
			for (int i = list.size() - 1; i >= 0; i--) {
				if (list.get(i).contains(mx, my)) {
					list.get(i).action().run();
					return true;
				}
			}
		}
		return super.mouseClicked(mx, my, button);
	}

	private Spot spotAt(double mx, double my) {
		int bx = x0 + LW + 8, gy = y0 + 24 + 20, bw = COLS * CELL + 8;
		for (int i = 0; i < COLS * ROWS; i++) {
			int sx = bx + 4 + (i % COLS) * CELL, sy = gy + 4 + (i / COLS) * CELL;
			if (mx >= sx && mx < sx + CELL && my >= sy && my < sy + CELL) return new Spot(box, i);
		}
		int px = bx + bw + 6, pw = x0 + W - 4 - px, pc2 = (pw - 8) / 2;
		for (int i = 0; i < 6; i++) {
			int sx = px + 4 + (i % 2) * pc2, sy = y0 + 24 + 24 + (i / 2) * 34 + (i % 2) * 4;
			if (mx >= sx && mx < sx + pc2 && my >= sy && my < sy + 30) return new Spot(-1, i);
		}
		return null;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
		if (releaseConfirm != null) return true;
		if (vertical != 0) changeBox(vertical > 0 ? -1 : 1);
		return true;
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		if (releaseConfirm != null) {
			switch (key) {
				case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_S -> confirmCursor = 1 - confirmCursor;
				case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
					if (confirmCursor == 0) doRelease();
					else cancelRelease();
				}
				case GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_BACKSPACE -> cancelRelease();
				default -> {
				}
			}
			return true;
		}
		if (key == GLFW.GLFW_KEY_ESCAPE && held != null) {
			held = null;
			heldPokemon = null;
			return true;
		}
		if (key == GLFW.GLFW_KEY_ESCAPE || MinecraftClient.getInstance().options.inventoryKey.matchesKey(key, scan)) {
			close();
			return true;
		}
		switch (key) {
			case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_Q -> { changeBox(-1); return true; }
			case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> { changeBox(1); return true; }
			default -> { return super.keyPressed(key, scan, mods); }
		}
	}
}

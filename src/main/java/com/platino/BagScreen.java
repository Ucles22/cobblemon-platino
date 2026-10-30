package com.platino;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.api.item.PokemonSelectingItem;
import com.cobblemon.mod.common.battles.BagItems;
import com.cobblemon.mod.common.item.battle.BagItemLike;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Bolsa estilo Diamante/Perla/Platino: tus objetos ordenados por bolsillos. */
public class BagScreen extends Screen {
	private static final int W = 300, H = 186, ROW = 18, LIST_X = 118;
	private static int lastPocket = 0;

	enum Pocket {
		ITEMS("OBJETOS", 0xFFE87040),
		MEDICINE("MEDICINAS", 0xFF58B858),
		BALLS("POKé BALLS", 0xFFE84848),
		TMS("MT/MO", 0xFF4878D8),
		BERRIES("BAYAS", 0xFF88C040),
		BATTLE("COMBATE", 0xFFF0A030),
		KEY("OBJ. CLAVE", 0xFF9060C8),
		OTHER("VARIOS", 0xFF8890A0);

		final String label;
		final int color;

		Pocket(String label, int color) {
			this.label = label;
			this.color = color;
		}
	}

	private record Group(ItemStack stack, int count, List<Integer> slots) {}

	private final List<List<Group>> pockets = new ArrayList<>();
	private int pocket, selected, scroll;
	private long lastRefresh;

	/** Pantalla de combate a la que volver si se cierra sin usar nada (null fuera de combate). */
	private final Screen battleParent;
	private String flash;
	private long flashUntil;

	public BagScreen() {
		this(null);
	}

	/** Bolsa abierta desde el botón BOLSA del combate. */
	public BagScreen(Screen battleParent) {
		super(Text.literal("Bolsa"));
		this.battleParent = battleParent;
		pocket = battleParent != null ? Pocket.MEDICINE.ordinal() : lastPocket;
	}

	@Override
	public void close() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (battleParent != null && CobblemonClient.INSTANCE.getBattle() != null) mc.setScreen(battleParent);
		else super.close();
	}

	private boolean inBattle() {
		return battleParent != null;
	}

	/** ¿Se puede usar en combate? Pociones, medicinas, bayas, objetos X y Poké Balls. */
	private static boolean usableInBattle(ItemStack st) {
		if (isBall(st) || st.getItem() instanceof BagItemLike || st.getItem() instanceof PokemonSelectingItem) return true;
		try {
			// La misma comprobación que usa Cobblemon para saber si un objeto vale en combate
			return BagItems.getConvertibleForStack(st) != null;
		} catch (Throwable t) {
			return false;
		}
	}

	private static boolean isBall(ItemStack st) {
		return Registries.ITEM.getId(st.getItem()).getPath().endsWith("_ball");
	}

	@Override
	protected void init() {
		refresh();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
		c.fill(0, 0, width, height, 0x70101018);
	}

	// ---------------------------------------------------------------- Clasificación

	static Pocket classify(ItemStack stack) {
		Identifier id = Registries.ITEM.getId(stack.getItem());
		String ns = id.getNamespace(), p = id.getPath();
		boolean pokemonItem = ns.equals("cobblemon") || ns.startsWith("diosesmon");
		if (!pokemonItem) return Pocket.OTHER;
		if (p.endsWith("_ball")) return Pocket.BALLS;
		if (p.endsWith("_berry")) return Pocket.BERRIES;
		if (p.startsWith("tm_") || p.startsWith("tr_") || p.startsWith("hm_") || p.contains("technical")) return Pocket.TMS;
		if (p.startsWith("x_") || p.equals("dire_hit") || p.equals("guard_spec")) return Pocket.BATTLE;
		if (containsAny(p, "potion", "revive", "ether", "elixir", "heal", "antidote", "awakening", "full_restore",
				"remedy", "berry_juice", "herb", "medicine", "lemonade", "soda_pop", "fresh_water", "moomoo_milk")) {
			return Pocket.MEDICINE;
		}
		if (containsAny(p, "pokedex", "key", "ticket", "dowsing", "bike", "pass", "card", "lens_case", "wailmer_pail")) {
			return Pocket.KEY;
		}
		return Pocket.ITEMS;
	}

	private static boolean containsAny(String s, String... parts) {
		for (String part : parts) if (s.contains(part)) return true;
		return false;
	}

	private void refresh() {
		lastRefresh = System.currentTimeMillis();
		pockets.clear();
		for (int i = 0; i < Pocket.values().length; i++) pockets.add(new ArrayList<>());
		var player = MinecraftClient.getInstance().player;
		if (player == null) return;
		PlayerInventory inv = player.getInventory();
		for (int slot = 0; slot < inv.main.size(); slot++) {
			ItemStack st = inv.main.get(slot);
			if (st.isEmpty()) continue;
			List<Group> list = pockets.get(classify(st).ordinal());
			Group found = null;
			for (Group g : list) {
				if (ItemStack.areItemsAndComponentsEqual(g.stack(), st)) {
					found = g;
					break;
				}
			}
			if (found == null) {
				List<Integer> slots = new ArrayList<>();
				slots.add(slot);
				list.add(new Group(st.copyWithCount(1), st.getCount(), slots));
			} else {
				found.slots().add(slot);
				list.set(list.indexOf(found), new Group(found.stack(), found.count() + st.getCount(), found.slots()));
			}
		}
		List<Group> cur = pockets.get(pocket);
		selected = Math.max(0, Math.min(selected, cur.size() - 1));
	}

	// ---------------------------------------------------------------- Dibujo

	private int ox() { return (width - W) / 2; }

	private int oy() { return (height - H) / 2; }

	private int visibleRows() { return (H - 62) / ROW; }

	@Override
	public void render(DrawContext c, int mouseX, int mouseY, float delta) {
		super.render(c, mouseX, mouseY, delta);
		if (System.currentTimeMillis() - lastRefresh > 500) refresh();
		int x = ox(), y = oy();
		Pocket pk = Pocket.values()[pocket];
		List<Group> items = pockets.get(pocket);

		c.getMatrices().push();
		c.getMatrices().translate(0, 0, 100);
		// Fondo de la ventana (degradado del color del bolsillo)
		Draw.round(c, x + 3, y + 3, x + W + 3, y + H + 3, 0x60000000);
		Draw.panelGradient(c, x, y, x + W, y + H, 0xFF303840, Draw.lerpColor(pk.color, 0xFFFFFFFF, 0.55f),
				Draw.lerpColor(pk.color, 0xFFFFFFFF, 0.25f));
		for (int yy = y + 4; yy < y + H - 4; yy += 4) c.fill(x + 2, yy, x + W - 2, yy + 1, 0x18FFFFFF);

		drawBag(c, x + 14, y + 12, pk);
		if (inBattle()) Draw.dsText(c, "COMBATE", x + 14 + 45 - Draw.width("COMBATE") / 2, y + 4, 0xFFFFFFFF, 0xFF303030);
		// Nombre del bolsillo
		int nx1 = x + 8, nx2 = x + LIST_X - 6, ny = y + 102;
		Draw.panel(c, nx1, ny, nx2, ny + 16, 0xFF303840, 0xFFF8F8F8);
		String name = pk.label;
		Draw.dsText(c, name, (nx1 + nx2) / 2 - Draw.width(name) / 2, ny + 4, 0xFF404040, 0xFFC8C8C8);
		Draw.dsText(c, "◀", nx1 + 4, ny + 4, pk.color, 0xFF404040);
		Draw.dsText(c, "▶", nx2 - 10, ny + 4, pk.color, 0xFF404040);
		// Iconos de bolsillos
		Pocket[] all = Pocket.values();
		for (int i = 0; i < all.length; i++) {
			int px = x + 10 + i * 13, py = y + 122;
			boolean sel = i == pocket;
			Draw.round(c, px, py - (sel ? 2 : 0), px + 11, py + 11, sel ? 0xFF303840 : 0xFF707880);
			c.fill(px + 1, py + 1 - (sel ? 2 : 0), px + 10, py + 10, sel ? all[i].color : Draw.lerpColor(all[i].color, 0xFF909090, 0.5f));
		}

		// Lista de objetos
		int lx1 = x + LIST_X, ly1 = y + 8, lx2 = x + W - 8, rows = visibleRows();
		int ly2 = ly1 + rows * ROW + 6;
		Draw.panel(c, lx1, ly1, lx2, ly2, 0xFF303840, 0xFFF8F8F8);
		if (items.isEmpty()) {
			String empty = inBattle() ? "Nada que usar aquí" : "Bolsillo vacío";
			Draw.dsText(c, empty, (lx1 + lx2) / 2 - Draw.width(empty) / 2, (ly1 + ly2) / 2 - 4, 0xFF909090, 0xFFE0E0E0);
		}
		scroll = Math.max(0, Math.min(scroll, Math.max(0, items.size() - rows)));
		if (selected < scroll) scroll = selected;
		if (selected >= scroll + rows) scroll = selected - rows + 1;
		for (int r = 0; r < rows && scroll + r < items.size(); r++) {
			int idx = scroll + r;
			Group g = items.get(idx);
			int ry = ly1 + 3 + r * ROW;
			boolean sel = idx == selected;
			if (sel) Draw.round(c, lx1 + 3, ry, lx2 - 3, ry + ROW - 1, 0xFFD0E0F8);
			c.drawItem(g.stack(), lx1 + 14, ry + 1);
			String cnt = "×" + g.count();
			String nm = Draw.fit(g.stack().getName().getString(), lx2 - lx1 - 50 - Draw.width(cnt));
			boolean dim = inBattle() && !usableInBattle(g.stack());
			Draw.dsText(c, nm, lx1 + 33, ry + 5, dim ? 0xFFA0A0A0 : 0xFF404040, 0xFFC8C8C8);
			Draw.dsText(c, cnt, lx2 - 8 - Draw.width(cnt), ry + 5, 0xFF404040, 0xFFC8C8C8);
			if (sel && (System.currentTimeMillis() / 400) % 2 == 0) Draw.dsText(c, "▶", lx1 + 5, ry + 5, 0xFFE03030, 0xFFF0A0A0);
		}
		// Barra de desplazamiento
		if (items.size() > rows) {
			int th = Math.max(8, (ly2 - ly1 - 6) * rows / items.size());
			int ty = ly1 + 3 + (ly2 - ly1 - 6 - th) * scroll / Math.max(1, items.size() - rows);
			c.fill(lx2 - 4, ly1 + 3, lx2 - 2, ly2 - 3, 0xFFD0D0D0);
			c.fill(lx2 - 4, ty, lx2 - 2, ty + th, pk.color);
		}

		// Descripción
		int dx1 = x + 8, dy1 = y + H - 46, dx2 = x + W - 84, dy2 = y + H - 8;
		Draw.panel(c, dx1, dy1, dx2, dy2, 0xFF303840, 0xFF808890);
		Draw.panel(c, dx1 + 3, dy1 + 3, dx2 - 3, dy2 - 3, 0xFF485058, 0xFFF8F8F8);
		if (flash != null && System.currentTimeMillis() < flashUntil) {
			Draw.dsText(c, Draw.fit(flash, dx2 - dx1 - 16), dx1 + 9, dy1 + 15, 0xFFC03030, 0xFFF0C8C8);
		} else if (!items.isEmpty()) {
			Group g = items.get(selected);
			c.drawItem(g.stack(), dx1 + 8, dy1 + 11);
			List<Text> tip = Screen.getTooltipFromItem(MinecraftClient.getInstance(), g.stack());
			List<String> lines = new ArrayList<>();
			for (int i = 1; i < tip.size() && lines.size() < 2; i++) {
				String s = tip.get(i).getString().strip();
				// Se saltan líneas vacías y el id técnico ("cobblemon:poke_ball") de los tooltips avanzados
				if (!s.isEmpty() && !s.matches("^[a-z0-9_.-]+:[a-z0-9_/.-]+$")) lines.add(s);
			}
			if (lines.isEmpty()) lines.add(g.stack().getName().getString());
			for (int i = 0; i < lines.size(); i++) {
				Draw.dsText(c, Draw.fit(lines.get(i), dx2 - dx1 - 36), dx1 + 28, dy1 + 9 + i * 12, 0xFF484848, 0xFFD0D0C8);
			}
		}
		// Botones
		boolean canUse = !items.isEmpty();
		button(c, x + W - 78, y + H - 46, x + W - 8, y + H - 28, inBattle() ? "USAR" : "A LA MANO", canUse, mouseX, mouseY, 0xFF58B858);
		button(c, x + W - 78, y + H - 26, x + W - 8, y + H - 8, "CERRAR", true, mouseX, mouseY, 0xFF6890D0);
		c.getMatrices().pop();
	}

	private void button(DrawContext c, int x1, int y1, int x2, int y2, String label, boolean enabled, int mx, int my, int color) {
		boolean hover = enabled && mx >= x1 && mx < x2 && my >= y1 && my < y2;
		int top = enabled ? (hover ? Draw.lerpColor(color, 0xFFFFFFFF, 0.3f) : color) : 0xFFA0A0A0;
		Draw.round(c, x1, y1 + 2, x2, y2 + 2, 0xFF282828);
		Draw.panelGradient(c, x1, y1, x2, y2, 0xFF303840, top, Draw.lerpColor(top, 0xFF000000, 0.2f));
		Draw.dsText(c, label, (x1 + x2) / 2 - Draw.width(label) / 2, (y1 + y2) / 2 - 4, 0xFFFFFFFF, 0xFF303030);
	}

	/** Mochila dibujada a mano, del color del bolsillo. */
	private static void drawBag(DrawContext c, int x, int y, Pocket pk) {
		int body = pk.color, dark = Draw.lerpColor(pk.color, 0xFF000000, 0.45f), light = Draw.lerpColor(pk.color, 0xFFFFFFFF, 0.35f);
		// Asa
		Draw.round(c, x + 28, y, x + 62, y + 14, dark);
		c.fill(x + 33, y + 4, x + 57, y + 12, Draw.lerpColor(pk.color, 0xFFFFFFFF, 0.55f));
		// Cuerpo
		Draw.panelGradient(c, x + 4, y + 10, x + 86, y + 84, dark, light, body);
		// Solapa
		Draw.panelGradient(c, x + 8, y + 12, x + 82, y + 44, dark, body, Draw.lerpColor(body, 0xFF000000, 0.15f));
		c.fill(x + 40, y + 38, x + 50, y + 50, dark);
		c.fill(x + 42, y + 40, x + 48, y + 48, 0xFFF8C830);
		// Bolsillo frontal
		Draw.panel(c, x + 18, y + 54, x + 72, y + 78, dark, Draw.lerpColor(body, 0xFF000000, 0.1f));
		c.fill(x + 22, y + 58, x + 68, y + 59, light);
	}

	// ---------------------------------------------------------------- Entrada

	private void setPocket(int p) {
		pocket = Math.floorMod(p, Pocket.values().length);
		lastPocket = pocket;
		selected = 0;
		scroll = 0;
		beep(1.5f);
	}

	private static void beep(float pitch) {
		MinecraftClient.getInstance().getSoundManager()
				.play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.5f));
	}

	/** Pone el objeto seleccionado en la mano (como pulsar un número en el inventario). */
	private boolean moveToHand(Group g) {
		MinecraftClient mc = MinecraftClient.getInstance();
		var player = mc.player;
		if (player == null || mc.interactionManager == null) return false;
		PlayerInventory inv = player.getInventory();
		int slot = g.slots().get(0);
		if (slot < 9) {
			inv.selectedSlot = slot;
		} else {
			// En PlayerScreenHandler las ranuras 9-35 son el inventario principal
			mc.interactionManager.clickSlot(player.playerScreenHandler.syncId, slot, inv.selectedSlot, SlotActionType.SWAP, player);
		}
		return true;
	}

	private void toHand() {
		List<Group> items = pockets.get(pocket);
		if (items.isEmpty()) return;
		Group g = items.get(selected);
		if (inBattle()) {
			useInBattle(g);
			return;
		}
		if (moveToHand(g)) {
			beep(1.2f);
			super.close();
		}
	}

	/**
	 * Igual que el botón "Bag" de Cobblemon (minimiza el combate), pero además coge el objeto y lo usa:
	 * las pociones abren la selección de Pokémon de Cobblemon; las Poké Balls quedan en la mano para lanzarlas.
	 */
	private void useInBattle(Group g) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ItemStack st = g.stack();
		if (!usableInBattle(st)) {
			flash = "¡No puedes usar " + st.getName().getString() + " en combate!";
			flashUntil = System.currentTimeMillis() + 2000;
			mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.6f, 0.6f));
			return;
		}
		if (!moveToHand(g)) return;
		beep(1.2f);
		var battle = CobblemonClient.INSTANCE.getBattle();
		if (battle != null) battle.setMinimised(true);
		mc.setScreen(null);
		if (isBall(st)) {
			if (mc.player != null) {
				mc.player.sendMessage(Text.literal("§e¡Apunta al Pokémon y lanza la " + st.getName().getString() + " con clic derecho!"), true);
			}
		} else {
			PlatinoClient.useHeldItemSoon();
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button != 0) return super.mouseClicked(mx, my, button);
		int x = ox(), y = oy();
		// Flechas e iconos de bolsillo
		if (my >= y + 102 && my < y + 118) {
			if (mx >= x + 8 && mx < x + 30) { setPocket(pocket - 1); return true; }
			if (mx >= x + LIST_X - 30 && mx < x + LIST_X - 6) { setPocket(pocket + 1); return true; }
		}
		if (my >= y + 118 && my < y + 134) {
			int i = (int) ((mx - x - 10) / 13);
			if (mx >= x + 10 && i >= 0 && i < Pocket.values().length) { setPocket(i); return true; }
		}
		// Clic en el dibujo de la mochila: siguiente bolsillo
		if (mx >= x + 14 && mx < x + 104 && my >= y + 12 && my < y + 98) { setPocket(pocket + 1); return true; }
		// Lista
		int lx1 = x + LIST_X, ly1 = y + 8, lx2 = x + W - 8;
		if (mx >= lx1 && mx < lx2 && my >= ly1 + 3 && my < ly1 + 3 + visibleRows() * ROW) {
			int idx = scroll + (int) ((my - ly1 - 3) / ROW);
			if (idx < pockets.get(pocket).size()) {
				if (idx == selected) toHand();
				else { selected = idx; beep(1.7f); }
			}
			return true;
		}
		// Botones
		if (mx >= x + W - 78 && mx < x + W - 8) {
			if (my >= y + H - 46 && my < y + H - 28) { toHand(); return true; }
			if (my >= y + H - 26 && my < y + H - 8) { close(); return true; }
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
		int size = pockets.get(pocket).size();
		if (size > 0 && vertical != 0) {
			selected = Math.max(0, Math.min(size - 1, selected + (vertical > 0 ? -1 : 1)));
			beep(1.7f);
		}
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		int size = pockets.get(pocket).size();
		switch (keyCode) {
			case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> { setPocket(pocket - 1); return true; }
			case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> { setPocket(pocket + 1); return true; }
			case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> {
				if (size > 0) { selected = Math.floorMod(selected - 1, size); beep(1.7f); }
				return true;
			}
			case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> {
				if (size > 0) { selected = Math.floorMod(selected + 1, size); beep(1.7f); }
				return true;
			}
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> { toHand(); return true; }
			default -> {
				if (PlatinoClient.isBagKey(keyCode, scanCode)) { close(); return true; }
				return super.keyPressed(keyCode, scanCode, modifiers);
			}
		}
	}
}

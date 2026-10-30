package com.platino;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUI;
import com.cobblemon.mod.common.client.gui.summary.Summary;
import com.cobblemon.mod.common.client.pokedex.PokedexType;
import com.cobblemon.mod.common.item.PokedexItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Menú de pausa estilo Diamante/Perla/Platino (lista vertical a la derecha + cuadro de texto abajo). */
public class PlatinoMenuScreen extends Screen {
	private static final int W = 112, ROW = 18;
	private static int lastSelected = 0;

	private enum Entry {
		POKEDEX("POKéDEX", "Datos de los Pokémon vistos y capturados."),
		POKEMON("POKéMON", "Revisa el estado de los Pokémon de tu equipo."),
		BAG("BOLSA", "Objetos que llevas encima, ordenados por bolsillos."),
		CARD(null, "Tu Ficha de Entrenador."),
		POKETCH("POKéRELOJ", "Hora, equipo, podómetro, calendario y más."),
		OPTIONS("OPCIONES", "Cambia las opciones del juego."),
		GAME("MENÚ JUEGO", "Logros, estadísticas, desconectar…"),
		EXIT("SALIR", "Cierra este menú.");

		final String label, description;

		Entry(String label, String description) {
			this.label = label;
			this.description = description;
		}
	}

	private int selected;
	private long opened, descStart;

	public PlatinoMenuScreen() {
		super(Text.literal("Menú"));
		selected = Math.floorMod(lastSelected, Entry.values().length);
	}

	@Override
	protected void init() {
		opened = System.currentTimeMillis();
		descStart = opened;
	}

	@Override
	public boolean shouldPause() {
		return true;
	}

	@Override
	public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
		c.fill(0, 0, width, height, 0x40000000);
	}

	private String label(Entry e) {
		if (e == Entry.CARD) {
			var p = MinecraftClient.getInstance().player;
			return p != null ? p.getGameProfile().getName().toUpperCase() : "FICHA";
		}
		return e.label;
	}

	private int panelX() {
		float t = Math.min(1f, (System.currentTimeMillis() - opened) / 140f);
		return width - W - 6 + Math.round((1 - t) * 40);
	}

	@Override
	public void render(DrawContext c, int mouseX, int mouseY, float delta) {
		super.render(c, mouseX, mouseY, delta);
		Entry[] entries = Entry.values();
		int x = panelX(), y = 6;
		int h = entries.length * ROW + 10;

		c.getMatrices().push();
		c.getMatrices().translate(0, 0, 200);
		// Panel blanco con doble borde (oscuro + azul) como en DPPt
		Draw.round(c, x + 3, y + 3, x + W + 3, y + h + 3, 0x60000000);
		Draw.panel(c, x, y, x + W, y + h, 0xFF303840, 0xFFF8F8F8);
		Draw.panel(c, x + 2, y + 2, x + W - 2, y + h - 2, 0xFF6890D0, 0xFFF8F8F8);

		for (int i = 0; i < entries.length; i++) {
			int ry = y + 5 + i * ROW;
			boolean sel = i == selected;
			if (sel) Draw.round(c, x + 5, ry, x + W - 5, ry + ROW - 1, 0xFFD0E0F8);
			icon(c, entries[i], x + 16, ry + 3);
			Draw.dsText(c, label(entries[i]), x + 32, ry + 5, 0xFF404040, 0xFFC8C8C8);
			if (sel && (System.currentTimeMillis() / 400) % 2 == 0) {
				Draw.dsText(c, "▶", x + 6, ry + 5, 0xFFE03030, 0xFFF0A0A0);
			}
		}

		// Cuadro de texto con la descripción
		int bw = Math.min(300, x - 16), bh = 38, bx = 8, by = height - bh - 8;
		if (bw > 120) {
			Draw.panel(c, bx, by, bx + bw, by + bh, 0xFF303840, 0xFF808890);
			Draw.panel(c, bx + 3, by + 3, bx + bw - 3, by + bh - 3, 0xFF485058, 0xFFF8F8F8);
			String d = entries[selected].description;
			int n = (int) Math.min(d.length(), (System.currentTimeMillis() - descStart) * 60 / 1000);
			List<String> lines = wrap(d.substring(0, n), bw - 20);
			for (int i = 0; i < Math.min(2, lines.size()); i++) {
				Draw.dsText(c, lines.get(i), bx + 9, by + 9 + i * 12, 0xFF484848, 0xFFD0D0C8);
			}
		}
		c.getMatrices().pop();
	}

	private List<String> wrap(String s, int maxW) {
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : s.split(" ")) {
			String test = line.isEmpty() ? word : line + " " + word;
			if (textRenderer.getWidth(test) > maxW && !line.isEmpty()) {
				out.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(test);
			}
		}
		if (!line.isEmpty()) out.add(line.toString());
		return out;
	}

	/** Iconos pequeños dibujados a mano (12x12). */
	private static void icon(DrawContext c, Entry e, int x, int y) {
		switch (e) {
			case POKEDEX -> {
				Draw.round(c, x, y, x + 10, y + 12, 0xFF802020);
				c.fill(x + 1, y + 1, x + 9, y + 11, 0xFFE04040);
				c.fill(x + 2, y + 2, x + 6, y + 5, 0xFF60C0F0);
				c.fill(x + 2, y + 7, x + 8, y + 8, 0xFF802020);
			}
			case POKEMON -> Draw.tinyBall(c, x + 2, y + 3);
			case BAG -> {
				Draw.round(c, x, y + 3, x + 12, y + 12, 0xFF704010);
				c.fill(x + 1, y + 4, x + 11, y + 11, 0xFFE8A040);
				c.fill(x + 3, y + 1, x + 9, y + 3, 0xFF704010);
				c.fill(x + 1, y + 6, x + 11, y + 7, 0xFF704010);
			}
			case CARD -> {
				Draw.round(c, x, y + 2, x + 12, y + 11, 0xFF803030);
				c.fill(x + 1, y + 3, x + 11, y + 10, 0xFFF07878);
				c.fill(x + 2, y + 5, x + 6, y + 6, 0xFFFFFFFF);
				c.fill(x + 2, y + 7, x + 7, y + 8, 0xFFFFFFFF);
				c.fill(x + 8, y + 4, x + 10, y + 9, 0xFFFFFFFF);
			}
			case POKETCH -> {
				c.fill(x + 3, y, x + 9, y + 12, 0xFF404040);
				Draw.round(c, x, y + 2, x + 12, y + 10, 0xFF6878A0);
				c.fill(x + 2, y + 3, x + 9, y + 9, 0xFF90C890);
				c.fill(x + 10, y + 3, x + 12, y + 6, 0xFFE04040);
			}
			case OPTIONS -> {
				c.fill(x + 1, y + 2, x + 11, y + 4, 0xFF505868);
				c.fill(x + 1, y + 6, x + 11, y + 8, 0xFF505868);
				c.fill(x + 1, y + 10, x + 11, y + 12, 0xFF505868);
				c.fill(x + 3, y + 1, x + 5, y + 5, 0xFFE8A040);
				c.fill(x + 7, y + 5, x + 9, y + 9, 0xFFE8A040);
				c.fill(x + 4, y + 9, x + 6, y + 13, 0xFFE8A040);
			}
			case GAME -> {
				Draw.round(c, x, y + 1, x + 12, y + 12, 0xFF406040);
				c.fill(x + 1, y + 2, x + 11, y + 11, 0xFF70B050);
				c.fill(x + 1, y + 2, x + 11, y + 5, 0xFF8B5A2B);
			}
			case EXIT -> {
				c.fill(x + 2, y + 5, x + 9, y + 8, 0xFF505868);
				for (int i = 0; i < 4; i++) c.fill(x + 8 + i, y + 3 + i, x + 9 + i, y + 10 - i, 0xFF505868);
			}
		}
	}

	private int entryAt(double mx, double my) {
		int x = panelX();
		if (mx < x + 4 || mx > x + W - 4) return -1;
		int i = (int) Math.floor((my - 11) / ROW);
		return i >= 0 && i < Entry.values().length ? i : -1;
	}

	private void select(int i) {
		if (i == selected) return;
		selected = Math.floorMod(i, Entry.values().length);
		lastSelected = selected;
		descStart = System.currentTimeMillis();
		beep(1.6f);
	}

	@Override
	public void mouseMoved(double mouseX, double mouseY) {
		int i = entryAt(mouseX, mouseY);
		if (i >= 0) select(i);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int i = entryAt(mouseX, mouseY);
		if (button == 0 && i >= 0) {
			select(i);
			activate();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (vertical != 0) select(selected + (vertical > 0 ? -1 : 1));
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		switch (keyCode) {
			case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> {
				select(selected - 1);
				return true;
			}
			case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> {
				select(selected + 1);
				return true;
			}
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
				activate();
				return true;
			}
			default -> {
				return super.keyPressed(keyCode, scanCode, modifiers);
			}
		}
	}

	private static void beep(float pitch) {
		MinecraftClient.getInstance().getSoundManager()
				.play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.5f));
	}

	private void activate() {
		MinecraftClient mc = MinecraftClient.getInstance();
		beep(1.2f);
		lastSelected = selected;
		try {
			switch (Entry.values()[selected]) {
				case POKEDEX -> openPokedex(mc);
				case POKEMON -> openParty(mc);
				case BAG -> mc.setScreen(new BagScreen());
				case CARD -> mc.setScreen(new TrainerCardScreen());
				case POKETCH -> mc.setScreen(new PoketchScreen());
				case OPTIONS -> mc.setScreen(new OptionsScreen(this, mc.options));
				case GAME -> mc.setScreen(new GameMenuScreen(true));
				case EXIT -> close();
			}
		} catch (Throwable t) {
			PlatinoClient.LOGGER.error("[Cobblemon Platino] Error en el menú", t);
			if (mc.player != null) mc.player.sendMessage(Text.literal("§c[Cobblemon Platino] No se pudo abrir: " + t), false);
		}
	}

	private void openParty(MinecraftClient mc) {
		List<Pokemon> party = new ArrayList<>();
		for (Pokemon p : CobblemonClient.INSTANCE.getStorage().getParty().getSlots()) if (p != null) party.add(p);
		if (party.isEmpty()) {
			if (mc.player != null) mc.player.sendMessage(Text.literal("No llevas ningún Pokémon."), true);
			return;
		}
		Summary.Companion.open(party, true, 0);
	}

	private void openPokedex(MinecraftClient mc) {
		var dex = CobblemonClient.INSTANCE.getClientPokedexData();
		if (dex == null) return;
		// Usa el tipo de Pokédex que lleves encima; si no, el primero disponible
		PokedexType type = PokedexType.values()[0];
		if (mc.player != null) {
			for (ItemStack stack : mc.player.getInventory().main) {
				if (stack.getItem() instanceof PokedexItem item) {
					type = item.getType();
					break;
				}
			}
		}
		PokedexGUI.Companion.open$default(PokedexGUI.Companion, dex, type, null, null, 0b1100, null);
	}
}

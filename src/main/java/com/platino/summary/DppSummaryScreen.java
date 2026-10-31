package com.platino.summary;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.client.gui.summary.Summary;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.platino.BattleHud;
import com.platino.Draw;
import com.platino.MiniModel;
import com.platino.Notes;
import com.platino.NotesScreen;
import com.platino.calc.CalcData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;
import com.cobblemon.mod.common.api.storage.party.PartyPosition;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.storage.ClientParty;
import com.cobblemon.mod.common.net.messages.server.BenchMovePacket;
import com.cobblemon.mod.common.net.messages.server.pokemon.update.SetNicknamePacket;
import com.cobblemon.mod.common.net.messages.server.RequestMoveSwapPacket;
import com.cobblemon.mod.common.net.messages.server.storage.party.MovePartyPokemonPacket;
import com.cobblemon.mod.common.net.messages.server.storage.party.SwapPartyPokemonPacket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Pantalla de datos del Pokémon estilo Diamante/Perla (sustituye al Resumen de Cobblemon; /platino resumen off). */
public class DppSummaryScreen extends Screen {
	/** true mientras se abre el Resumen original de Cobblemon a propósito. */
	public static boolean bypass;
	/** Pantalla a la que volver al cerrar la próxima ficha que se abra (PC, menú de equipo). */
	public static Screen nextParent;
	private final Screen parent = takeParent();

	private static Screen takeParent() {
		Screen s = nextParent;
		nextParent = null;
		return s;
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}

	private static final int W = 400, H = 252, LW = 138;
	private static int lastPage = 0;

	private enum Page {
		INFO("INFO. POKéMON", 0xFF58B848, 0xFFE0F0C8),
		MEMO("NOTAS ENTRENADOR", 0xFF48A8A0, 0xFFD8F4F0),
		SKILLS("HABILIDADES", 0xFF6870C8, 0xFFC8C8F0),
		MOVES("MOVIMIENTOS", 0xFFE05850, 0xFFF8B0A8),
		STATS("IVs / EVs", 0xFFD8A028, 0xFFF8E8B0),
		MARKS("MARCAS", 0xFFD060B8, 0xFFF8D8F0);

		final String title;
		final int color, light;

		Page(String title, int color, int light) {
			this.title = title;
			this.color = color;
			this.light = light;
		}
	}

	private record Hit(int x1, int y1, int x2, int y2, Runnable action) {
		boolean contains(double x, double y) {
			return x >= x1 && x < x2 && y >= y1 && y < y2;
		}
	}

	private final List<Pokemon> party;
	private final List<Pokemon> original;
	private final boolean editable;
	private final List<Hit> hits = new ArrayList<>();
	private int index;
	private Page page;
	private int selectedMove = -1;
	private int x0, y0;
	/** Movimiento que se está sustituyendo (-1 = ninguno) y desplazamiento de la lista. */
	private int swapSlot = -1, swapScroll = 0;
	private List<MoveTemplate> swapOptions = List.of();
	/** Submenú para ordenar el equipo y hueco elegido (-1 = ninguno). */
	private boolean partyMenu;
	/** Edición del mote: null = no se está editando. */
	private String nickBuffer;
	private static final int NICK_MAX = 12;
	private int partyPick = -1;
	private String toast;
	private long toastUntil;

	public DppSummaryScreen(java.util.Collection<? extends Pokemon> original, boolean editable, int selection) {
		super(Text.literal("Datos"));
		this.original = new ArrayList<>(original);
		this.party = new ArrayList<>();
		int idx = 0, i = 0;
		for (Pokemon p : original) {
			if (p != null) {
				if (i == selection) idx = party.size();
				party.add(p);
			}
			i++;
		}
		this.editable = editable;
		this.index = Math.max(0, Math.min(idx, this.party.size() - 1));
		this.page = Page.values()[Math.floorMod(lastPage, Page.values().length)];
	}

	private Pokemon pk() {
		return party.isEmpty() ? null : party.get(index);
	}

	private static ClientParty clientParty() {
		try {
			return CobblemonClient.INSTANCE.getStorage().getParty();
		} catch (Throwable t) {
			return null;
		}
	}

	/** El Pokémon está en el equipo del jugador y la ficha permite editar. */
	private boolean canEdit(Pokemon p) {
		ClientParty cp = clientParty();
		return editable && p != null && cp != null && cp.findByUUID(p.getUuid()) != null;
	}

	private static boolean inBattle() {
		try {
			return CobblemonClient.INSTANCE.getBattle() != null;
		} catch (Throwable t) {
			return false;
		}
	}

	private void toast(String msg) {
		toast = msg;
		toastUntil = System.currentTimeMillis() + 2500;
	}

	/** Si la ficha muestra el equipo, se rehace con el orden actual (tras cambiar de sitio a un Pokémon). */
	private void syncParty() {
		ClientParty cp = clientParty();
		Pokemon cur = pk();
		if (!editable || cp == null || cur == null || cp.findByUUID(cur.getUuid()) == null) return;
		List<Pokemon> fresh = new ArrayList<>();
		for (Pokemon q : cp.getSlots()) if (q != null) fresh.add(q);
		if (fresh.isEmpty()) return;
		party.clear();
		party.addAll(fresh);
		original.clear();
		original.addAll(cp.getSlots());
		for (int i = 0; i < party.size(); i++) if (party.get(i).getUuid().equals(cur.getUuid())) index = i;
		index = Math.max(0, Math.min(index, party.size() - 1));
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
		c.fill(0, 0, width, height, 0x80101018);
	}

	// ---------------------------------------------------------------- Dibujo general

	@Override
	public void render(DrawContext c, int mouseX, int mouseY, float delta) {
		super.render(c, mouseX, mouseY, delta);
		hits.clear();
		syncParty();
		Pokemon p = pk();
		if (p == null) {
			close();
			return;
		}
		x0 = (width - W) / 2;
		y0 = (height - H) / 2;

		c.getMatrices().push();
		c.getMatrices().translate(0, 0, 50);
		Draw.round(c, x0 + 3, y0 + 3, x0 + W + 3, y0 + H + 3, 0x60000000);
		Draw.panel(c, x0, y0, x0 + W, y0 + H, 0xFF283048, 0xFF283048);

		// Banda de título (azul degradado con rayas, como DPPt)
		c.fillGradient(x0 + 1, y0 + 1, x0 + W - 1, y0 + 19, 0xFF5878C8, 0xFF2848A0);
		for (int yy = y0 + 4; yy < y0 + 18; yy += 4) c.fill(x0 + 1, yy, x0 + W - 1, yy + 1, 0x18FFFFFF);
		Draw.dsText(c, page.title, x0 + 8, y0 + 6, 0xFFFFFFFF, 0xFF203060);
		button(c, x0 + W - 92, y0 + 4, x0 + W - 22, y0 + 16, "Cobblemon ⇄", mouseX, mouseY, 0xFFE8E8F0, this::openOriginal);
		button(c, x0 + W - 18, y0 + 4, x0 + W - 5, y0 + 16, "×", mouseX, mouseY, 0xFFE8E8F0, this::close);

		drawLeft(c, p, mouseX, mouseY);
		drawTabs(c, mouseX, mouseY);
		int rx = x0 + LW + 4, ry = y0 + 40, rw = W - LW - 8, rh = H - 44;
		Draw.panel(c, rx, ry, rx + rw, ry + rh, page.color, page.light);
		switch (page) {
			case INFO -> drawInfo(c, p, rx, ry, rw);
			case MEMO -> drawMemo(c, p, rx, ry, rw, rh, mouseX, mouseY);
			case SKILLS -> drawSkills(c, p, rx, ry, rw, rh);
			case MOVES -> drawMoves(c, p, rx, ry, rw, rh, mouseX, mouseY);
			case STATS -> drawStatsPage(c, p, rx, ry, rw);
			case MARKS -> drawMarks(c, p, rx, ry, rw, rh, mouseX, mouseY);
		}
		if (page == Page.MARKS && markTip != null) drawTip(c, markTip, mouseX, mouseY);
		if (page == Page.MOVES && swapSlot >= 0) drawSwapList(c, p, rx, ry, rw, rh, mouseX, mouseY);
		if (toast != null && System.currentTimeMillis() < toastUntil) {
			int tw = Draw.width(toast) + 16;
			c.getMatrices().push();
			c.getMatrices().translate(0, 0, 200);
			Draw.panel(c, x0 + W / 2 - tw / 2, y0 + H - 22, x0 + W / 2 + tw / 2, y0 + H - 6, 0xFF303848, 0xFFF8F8F0);
			Draw.dsText(c, toast, x0 + W / 2 - tw / 2 + 8, y0 + H - 18, 0xFF303038, 0xFFD8D8D0);
			c.getMatrices().pop();
		}
		c.getMatrices().pop();
	}

	private void drawLeft(DrawContext c, Pokemon p, int mx, int my) {
		int x = x0 + 4, y = y0 + 22, w = LW - 4;
		// Fondo gris con rayas
		c.fill(x, y, x + w, y0 + H - 4, 0xFFD0D0C0);
		for (int yy = y; yy < y0 + H - 4; yy += 3) c.fill(x, yy, x + w, yy + 1, 0x18FFFFFF);
		// Placa de nombre naranja
		Draw.panelGradient(c, x + 2, y + 2, x + w - 2, y + 18, 0xFF7A3A08, 0xFFE8902C, 0xFFC0600E); // placa del nombre, naranja oscuro para leer bien el blanco
		Draw.tinyBall(c, x + 6, y + 6);
		boolean nickEditable = canEdit(p);
		if (nickBuffer != null) {
			// Campo de texto del mote
			Draw.round(c, x + 15, y + 4, x + w - 30, y + 16, 0xFFF8F8F0);
			String shown = Draw.fit(nickBuffer, w - 50);
			Draw.dsText(c, shown, x + 18, y + 6, 0xFF303038, 0xFFD8D8D0);
			if ((System.currentTimeMillis() / 400) % 2 == 0) c.fill(x + 18 + Draw.width(shown), y + 5, x + 19 + Draw.width(shown), y + 15, 0xFF303038);
			button(c, x + w - 28, y + 4, x + w - 16, y + 16, "✔", mx, my, 0xFFC8F0C0, () -> commitNick(p));
		} else {
			String name = Draw.fit(p.getDisplayName(false).getString().toUpperCase(Locale.ROOT), w - (nickEditable ? 46 : 34));
			Draw.dsText(c, name, x + 16, y + 6, 0xFFFFFFFF, 0xFF4A1E00);
			if (nickEditable) {
				button(c, x + w - 28, y + 4, x + w - 16, y + 16, "✎", mx, my, 0xFFF8E8C0, () -> {
					var nick = p.getNickname();
					nickBuffer = nick == null ? "" : nick.getString();
				});
			}
		}
		Gender g = p.getGender();
		if (g == Gender.MALE) Draw.dsText(c, "♂", x + w - 12, y + 6, 0xFFA8D0FF, 0xFF203878);
		else if (g == Gender.FEMALE) Draw.dsText(c, "♀", x + w - 12, y + 6, 0xFFFFC0D0, 0xFF782040);
		// Nivel
		Draw.round(c, x + 2, y + 19, x + w - 2, y + 32, 0xFFF8F8F0);
		Draw.dsText(c, "Nv." + p.getLevel(), x + 8, y + 22, 0xFF303038, 0xFFC8C8C0);
		// Retrato con fondo de rayas y modelo 3D
		int px = x + 10, py = y + 36, ps = w - 20;
		Draw.panel(c, px, py, px + ps, py + ps, 0xFFA8A8B0, 0xFFE8ECF4);
		for (int yy = py + 3; yy < py + ps - 2; yy += 3) c.fill(px + 2, yy, px + ps - 2, yy + 1, 0xFFD8DCE8);
		c.draw();
		MiniModel.draw(c, p, px + 4, py + 4, ps - 8);
		c.draw();
		// Marcas ●▲■♥★◆
		String[] sym = {"●", "▲", "■", "♥", "★", "◆"};
		List<Integer> markings = List.of();
		try {
			markings = p.getMarkings();
		} catch (Throwable ignored) {
		}
		int sx = x + w / 2 - 33;
		for (int i = 0; i < 6; i++) {
			boolean on = i < markings.size() && markings.get(i) != null && markings.get(i) > 0;
			c.drawText(textRenderer, sym[i], sx + i * 11, py + ps + 3, on ? 0xFF303038 : 0xFFA0A098, false);
		}
		// Ordenar equipo
		if (canEdit(p) && party.size() > 1) {
			button(c, x + 10, py + ps + 15, x + w - 10, py + ps + 28, "⇅ Equipo", mx, my, 0xFFB8D8F8, () -> {
				ClientParty cp = clientParty();
				int slot = cp == null ? 0 : Math.max(0, cp.getPosition(p.getUuid()));
				MinecraftClient.getInstance().setScreen(new PartyScreen(this, slot));
			});
		}
		// Objeto
		int iy = y0 + H - 38;
		Draw.panelGradient(c, x + 2, iy, x + w - 2, iy + 13, 0xFFB87820, 0xFFF8D080, 0xFFF0B048);
		Draw.dsText(c, "Objeto", x + 6, iy + 3, 0xFF303038, 0xFFF8E8B0);
		Draw.round(c, x + 2, iy + 14, x + w - 2, iy + 32, 0xFFF8F8F0);
		ItemStack held = ItemStack.EMPTY;
		try {
			held = p.heldItem();
		} catch (Throwable ignored) {
		}
		if (held.isEmpty()) {
			Draw.dsText(c, "Ninguno", x + 8, iy + 19, 0xFF303038, 0xFFC8C8C0);
		} else {
			c.drawItem(held, x + 5, iy + 15);
			Draw.dsText(c, Draw.fit(held.getName().getString(), w - 30), x + 24, iy + 19, 0xFF303038, 0xFFC8C8C0);
		}
		// Flechas para cambiar de Pokémon
		if (party.size() > 1) {
			button(c, x + w - 30, y + 20, x + w - 18, y + 31, "▲", mx, my, 0xFFF0F0E8, () -> switchPokemon(-1));
			button(c, x + w - 16, y + 20, x + w - 4, y + 31, "▼", mx, my, 0xFFF0F0E8, () -> switchPokemon(1));
		}
	}

	private void drawTabs(DrawContext c, int mx, int my) {
		int x = x0 + LW + 4, y = y0 + 22;
		button(c, x, y + 2, x + 10, y + 16, "◀", mx, my, 0xFF8090D0, () -> switchPage(-1));
		Page[] pages = Page.values();
		int tx = x + 13;
		for (Page pg : pages) {
			boolean sel = pg == page;
			int ty = sel ? y : y + 2;
			Draw.panel(c, tx, ty, tx + 22, y + 18, sel ? 0xFF303848 : 0xFF707888, sel ? pg.light : Draw.lerpColor(pg.color, 0xFFFFFFFF, 0.35f));
			tabIcon(c, pg, tx + 5, ty + 3, sel ? pg.color : Draw.lerpColor(pg.color, 0xFF303038, 0.2f));
			hits.add(new Hit(tx, y, tx + 22, y + 18, () -> setPage(pg)));
			tx += 24;
		}
		button(c, tx + 1, y + 2, tx + 11, y + 16, "▶", mx, my, 0xFF8090D0, () -> switchPage(1));
	}

	private static void tabIcon(DrawContext c, Page pg, int x, int y, int col) {
		switch (pg) {
			case INFO -> { c.fill(x, y, x + 12, y + 9, col); c.fill(x + 2, y + 2, x + 10, y + 6, 0xFFFFFFFF); c.fill(x + 4, y + 10, x + 8, y + 12, col); }
			case MEMO -> { c.fill(x + 1, y, x + 11, y + 12, col); for (int i = 0; i < 4; i++) c.fill(x + 3, y + 2 + i * 3, x + 9, y + 3 + i * 3, 0xFFFFFFFF); }
			case SKILLS -> { c.fill(x, y + 3, x + 12, y + 9, col); c.fill(x + 2, y + 5, x + 8, y + 7, 0xFFFFFFFF); }
			case MOVES -> { Draw.round(c, x, y, x + 12, y + 12, col); c.fill(x, y + 5, x + 12, y + 7, 0xFFFFFFFF); c.fill(x + 4, y + 4, x + 8, y + 8, 0xFFFFFFFF); }
			case STATS -> { c.fill(x, y + 7, x + 3, y + 12, col); c.fill(x + 4, y + 3, x + 7, y + 12, col); c.fill(x + 8, y, x + 11, y + 12, col); }
			case MARKS -> { c.fill(x + 3, y, x + 9, y + 6, col); c.fill(x + 2, y + 6, x + 5, y + 12, col); c.fill(x + 7, y + 6, x + 10, y + 12, col); }
		}
	}

	// ---------------------------------------------------------------- Páginas

	private void row(DrawContext c, int x, int y, int w, String label, String value, int valueColor) {
		Draw.round(c, x, y, x + 78, y + 15, 0xFFC0E0A0);
		Draw.dsText(c, label, x + 4, y + 4, 0xFF303038, 0xFFE8F8D8);
		Draw.round(c, x + 80, y, x + w, y + 15, 0xFFF8F8E8);
		Draw.dsText(c, Draw.fit(value, w - 90), x + w - 6 - Draw.width(Draw.fit(value, w - 90)), y + 4, valueColor, 0xFFD8D8C8);
	}

	private void drawInfo(DrawContext c, Pokemon p, int x, int y, int w) {
		int cx = x + 6, cw = w - 12, yy = y + 6;
		row(c, cx, yy, cw, "Nº Pokédex", String.format("%03d", p.getSpecies().getNationalPokedexNumber()), 0xFF303038);
		yy += 17;
		row(c, cx, yy, cw, "Especie", p.getSpecies().getTranslatedName().getString().toUpperCase(Locale.ROOT), 0xFF303038);
		yy += 17;
		// Tipo con insignias
		Draw.round(c, cx, yy, cx + 78, yy + 15, 0xFFC0E0A0);
		Draw.dsText(c, "Tipo", cx + 4, yy + 4, 0xFF303038, 0xFFE8F8D8);
		Draw.round(c, cx + 80, yy, cx + cw, yy + 15, 0xFFF8F8E8);
		int bx = cx + cw - 6;
		String t2 = p.getSecondaryType() == null ? null : p.getSecondaryType().getName();
		if (t2 != null) bx -= TypeBadge.draw(c, t2, bx - TypeBadge.WIDTH, yy + 2) + 2;
		TypeBadge.draw(c, p.getPrimaryType().getName(), bx - TypeBadge.WIDTH, yy + 2);
		yy += 17;
		String ot = p.getOriginalTrainerName();
		row(c, cx, yy, cw, "ED", ot == null || ot.isBlank() ? "—" : ot, 0xFF3060C8);
		yy += 17;
		String otId = p.getOriginalTrainer();
		String id = otId == null ? "—" : String.format("%05d", Math.abs(otId.hashCode()) % 100000);
		row(c, cx, yy, cw, "Nº ID", id, 0xFF303038);
		yy += 19;
		Draw.dsText(c, "Ptos. Exp.", cx + 4, yy, 0xFF303038, 0xFFE8F8D8);
		yy += 11;
		Draw.round(c, cx + 80, yy, cx + cw, yy + 14, 0xFFF8F8E8);
		String exp = String.valueOf(p.getExperience());
		Draw.dsText(c, exp, cx + cw - 6 - Draw.width(exp), yy + 3, 0xFF303038, 0xFFD8D8C8);
		yy += 17;
		Draw.dsText(c, "Nv. siguiente", cx + 4, yy, 0xFF303038, 0xFFE8F8D8);
		yy += 11;
		Draw.round(c, cx + 80, yy, cx + cw, yy + 14, 0xFFF8F8E8);
		String next = p.getLevel() >= 100 ? "0" : String.valueOf(p.getExperienceToNextLevel());
		Draw.dsText(c, next, cx + cw - 6 - Draw.width(next), yy + 3, 0xFF303038, 0xFFD8D8C8);
		yy += 18;
		Draw.dsText(c, "EXP", cx + 80, yy, 0xFFF8C830, 0xFF806010);
		Draw.expBar(c, cx + 100, yy + 2, cw - 104, p.getLevel() >= 100 ? 1f : BattleHud.expRatio(p));
	}

	private static final String[][] CHARACTERISTICS = {
			{"Le encanta comer.", "A menudo se duerme.", "Duerme mucho.", "Suele desordenar.", "Le gusta relajarse."},
			{"Orgulloso de su fuerza.", "Le gusta revolverse.", "A veces se enfada.", "Le gusta luchar.", "Tiene mal genio."},
			{"Cuerpo resistente.", "Capaz de recibir golpes.", "Muy persistente.", "Muy resistente.", "Muy perseverante."},
			{"Muy curioso.", "Muy travieso.", "Muy astuto.", "Suele estar en Babia.", "Muy melindroso."},
			{"Voluntad firme.", "Algo vanidoso.", "Muy insolente.", "Odia perder.", "Algo cabezota."},
			{"Le gusta correr.", "Atento a los sonidos.", "Impetuoso e insensato.", "Algo payaso.", "Huye rápido."}};

	private static String characteristic(Pokemon p) {
		// Estadística con el IV más alto (orden de desempate de los juegos: PS, Atq, Def, Vel, AtEsp, DefEsp)
		int[] order = {0, 1, 2, 5, 3, 4};
		int best = 0, bestIv = -1;
		for (int i : order) {
			int iv = iv(p, i);
			if (iv > bestIv) {
				bestIv = iv;
				best = i;
			}
		}
		return CHARACTERISTICS[best][Math.max(0, bestIv) % 5];
	}

	static int iv(Pokemon p, int i) {
		try {
			Number n = p.getIvs().get(CalcData.STAT_ORDER[i]);
			return n == null ? 0 : n.intValue();
		} catch (Throwable t) {
			return 0;
		}
	}

	static int ev(Pokemon p, int i) {
		try {
			Number n = p.getEvs().get(CalcData.STAT_ORDER[i]);
			return n == null ? 0 : n.intValue();
		} catch (Throwable t) {
			return 0;
		}
	}

	private static String flavour(Nature n) {
		try {
			Object f = n.getFavouriteFlavour();
			if (f == null) return null;
			return switch (f.toString().toUpperCase(Locale.ROOT)) {
				case "SPICY" -> "picante";
				case "DRY" -> "seca";
				case "SWEET" -> "dulce";
				case "BITTER" -> "amarga";
				case "SOUR" -> "ácida";
				default -> f.toString().toLowerCase(Locale.ROOT);
			};
		} catch (Throwable t) {
			return null;
		}
	}

	private void drawMemo(DrawContext c, Pokemon p, int x, int y, int w, int h, int mx, int my) {
		// Papel rayado
		for (int yy = y + 16; yy < y + h - 4; yy += 14) c.fill(x + 6, yy, x + w - 6, yy + 1, 0xFF98D0C8);
		int tx = x + 10, yy = y + 6;
		Nature n = p.getEffectiveNature();
		String nat = Text.translatable(n.getDisplayName()).getString();
		Draw.dsText(c, "Naturaleza ", tx, yy, 0xFF303038, 0xFFC8E8E0);
		Draw.dsText(c, nat + ".", tx + Draw.width("Naturaleza "), yy, 0xFFE03030, 0xFFF8C0B0);
		yy += 14;
		Draw.dsText(c, "Entrenador: " + (p.getOriginalTrainerName() == null ? "—" : p.getOriginalTrainerName()), tx, yy, 0xFF303038, 0xFFC8E8E0);
		yy += 14;
		try {
			Identifier ball = p.getCaughtBall().getName();
			String bn = Text.translatable("item." + ball.getNamespace() + "." + ball.getPath()).getString();
			Draw.dsText(c, "Capturado en " + bn + ".", tx, yy, 0xFF303038, 0xFFC8E8E0);
		} catch (Throwable ignored) {
		}
		yy += 28; // línea en blanco
		Draw.dsText(c, characteristic(p), tx, yy, 0xFF303038, 0xFFC8E8E0);
		yy += 14;
		String fl = flavour(n);
		if (fl != null) {
			Draw.dsText(c, "Le gusta la comida ", tx, yy, 0xFF303038, 0xFFC8E8E0);
			Draw.dsText(c, fl + ".", tx + Draw.width("Le gusta la comida "), yy, 0xFFE03030, 0xFFF8C0B0);
		} else {
			Draw.dsText(c, "No tiene preferencias de sabor.", tx, yy, 0xFF303038, 0xFFC8E8E0);
		}
		yy += 14;
		Draw.dsText(c, "Amistad: " + p.getFriendship() + "/255", tx, yy, 0xFF303038, 0xFFC8E8E0);
		yy += 28; // línea en blanco
		// Nota propia (de Cobblemon Platino)
		String note = Notes.get(p.getUuid());
		Draw.dsText(c, "✎ Nota:", tx, yy, 0xFF3060C8, 0xFFC8D8F0);
		button(c, x + w - 62, yy - 2, x + w - 8, yy + 10, "Editar", mx, my, 0xFFF8F8F0,
				() -> MinecraftClient.getInstance().setScreen(new NotesScreen(this, p)));
		yy += 14;
		if (note.isBlank()) {
			Draw.dsText(c, "(sin nota)", tx, yy, 0xFF808080, 0xFFD8E8E0);
		} else {
			List<OrderedText> lines = textRenderer.wrapLines(StringVisitable.plain(note), w - 20);
			// Cada línea de la nota va sobre una raya del cuaderno
			for (int i = 0; i < lines.size() && yy + i * 14 + 9 < y + h - 4; i++) {
				c.drawText(textRenderer, lines.get(i), tx, yy + i * 14, 0xFF303038, false);
			}
		}
	}

	private void drawSkills(DrawContext c, Pokemon p, int x, int y, int w, int h) {
		int cx = x + 6, cw = w - 12, yy = y + 6;
		Nature n = p.getEffectiveNature();
		int up = n.getIncreasedStat() == null ? -1 : indexOf(n.getIncreasedStat());
		int down = n.getDecreasedStat() == null ? -1 : indexOf(n.getDecreasedStat());
		// PS con barra
		Draw.round(c, cx, yy, cx + cw, yy + 26, 0xFFE8E8F8);
		Draw.dsText(c, "PS", cx + 6, yy + 4, 0xFF303038, 0xFFC8C8D8);
		String hp = p.getCurrentHealth() + "/" + p.getMaxHealth();
		Draw.dsText(c, hp, cx + cw - 6 - Draw.width(hp), yy + 4, 0xFF303038, 0xFFC8C8D8);
		Draw.hpBar(c, cx + cw - 110, yy + 15, 104, p.getCurrentHealth() / (float) Math.max(1, p.getMaxHealth()), true);
		yy += 30;
		int[] values = {0, p.getAttack(), p.getDefence(), p.getSpecialAttack(), p.getSpecialDefence(), p.getSpeed()};
		for (int i = 1; i < 6; i++) {
			int col = i == up && up != down ? 0xFFE03030 : i == down && up != down ? 0xFF3060C8 : 0xFF303038;
			Draw.round(c, cx, yy, cx + cw, yy + 15, (i % 2 == 1) ? 0xFFB8B0F0 : 0xFFD0C8F8);
			Draw.dsText(c, CalcData.STAT_LABEL[i], cx + 8, yy + 4, col, 0xFFE8E8F8);
			String v = String.valueOf(values[i]);
			Draw.round(c, cx + cw - 50, yy + 1, cx + cw - 2, yy + 14, 0xFFF8F8F8);
			Draw.dsText(c, v, cx + cw - 8 - Draw.width(v), yy + 4, 0xFF303038, 0xFFD8D8D8);
			yy += 16;
		}
		yy += 4;
		// Habilidad
		Draw.panelGradient(c, cx, yy, cx + cw, yy + 14, 0xFF404888, 0xFF8890D8, 0xFF6870C0);
		Draw.dsText(c, "Habilidad", cx + 6, yy + 3, 0xFFFFFFFF, 0xFF303870);
		String ab = "—", desc = "";
		try {
			ab = Text.translatable(p.getAbility().getDisplayName()).getString();
			desc = Text.translatable(p.getAbility().getDescription()).getString();
		} catch (Throwable ignored) {
		}
		Draw.dsText(c, ab, cx + cw - 6 - Draw.width(ab), yy + 3, 0xFFFFFFFF, 0xFF303870);
		yy += 16;
		Draw.round(c, cx, yy, cx + cw, y + h - 4, 0xFFF8F8F8);
		List<OrderedText> lines = textRenderer.wrapLines(StringVisitable.plain(desc), cw - 10);
		for (int i = 0; i < Math.min(3, lines.size()); i++) c.drawText(textRenderer, lines.get(i), cx + 5, yy + 4 + i * 10, 0xFF303038, false);
	}

	private static int indexOf(com.cobblemon.mod.common.api.pokemon.stats.Stat s) {
		for (int i = 0; i < 6; i++) if (CalcData.STAT_ORDER[i] == s) return i;
		return -1;
	}

	private void drawMoves(DrawContext c, Pokemon p, int x, int y, int w, int h, int mx, int my) {
		List<Move> moves = new ArrayList<>();
		try {
			moves.addAll(p.getMoveSet().getMoves());
		} catch (Throwable ignored) {
		}
		// Rayas salmón
		for (int i = 0; i < 4; i++) c.fill(x + 1, y + 2 + i * 30, x + w - 1, y + 17 + i * 30, 0xFFF09088);
		for (int i = 0; i < 4; i++) {
			int ry = y + 4 + i * 30;
			if (i >= moves.size()) {
				Draw.dsText(c, "—", x + 70, ry + 4, 0xFF804040, 0xFFF8C8C0);
				continue;
			}
			Move m = moves.get(i);
			MoveTemplate t = m.getTemplate();
			boolean sel = i == selectedMove;
			if (sel) Draw.round(c, x + 3, ry - 2, x + w - 3, ry + 28, 0x60FFFFFF);
			TypeBadge.draw(c, t.getElementalType().getName(), x + 8, ry);
			Draw.dsText(c, Draw.fit(t.getDisplayName().getString(), w - 70), x + 8 + TypeBadge.WIDTH + 6, ry + 3, 0xFFFFFFFF, 0xFF803030);
			Draw.round(c, x + w - 76, ry + 14, x + w - 6, ry + 26, 0xFFF8F8F8);
			String pp = "PP  " + m.getCurrentPp() + "/" + m.getMaxPp();
			Draw.dsText(c, pp, x + w - 10 - Draw.width(pp), ry + 16, 0xFF303038, 0xFFD8D8D8);
			int idx = i;
			hits.add(new Hit(x + 3, ry - 2, x + w - 3, ry + 28, () -> {
				selectedMove = selectedMove == idx ? -1 : idx;
				beep(1.7f);
			}));
			if (canEdit(p)) {
				int bx = x + 8;
				if (i > 0) button(c, bx, ry + 14, bx + 12, ry + 26, "▲", mx, my, 0xFFF8E0D8, () -> swapMoves(p, idx, idx - 1));
				if (i < moves.size() - 1) button(c, bx + 14, ry + 14, bx + 26, ry + 26, "▼", mx, my, 0xFFF8E0D8, () -> swapMoves(p, idx, idx + 1));
				button(c, bx + 30, ry + 14, bx + 84, ry + 26, "⇄ Cambiar", mx, my, 0xFFF8E0D8, () -> openSwap(p, idx));
			}
		}
		// Detalle del movimiento elegido
		int dy = y + 124;
		Draw.round(c, x + 4, dy, x + w - 4, y + h - 4, 0xFFF8F0F0);
		if (selectedMove < 0 || selectedMove >= moves.size()) {
			Draw.dsText(c, "Pulsa un movimiento para ver sus datos.", x + 10, dy + 6, 0xFF906060, 0xFFF0D8D8);
			return;
		}
		MoveTemplate t = moves.get(selectedMove).getTemplate();
		var cat = t.getDamageCategory();
		String catName = cat == DamageCategories.INSTANCE.getPHYSICAL() ? "Físico" : cat == DamageCategories.INSTANCE.getSPECIAL() ? "Especial" : "Estado";
		String pow = t.getPower() > 0 ? String.valueOf((int) t.getPower()) : "—";
		String acc = t.getAccuracy() > 0 ? (int) t.getAccuracy() + "%" : "—";
		Draw.dsText(c, catName + "   Potencia " + pow + "   Precisión " + acc, x + 10, dy + 5, 0xFF303038, 0xFFE0D0D0);
		List<OrderedText> lines = textRenderer.wrapLines(StringVisitable.plain(t.getDescription().getString()), w - 22);
		for (int i = 0; i < Math.min(5, lines.size()); i++) c.drawText(textRenderer, lines.get(i), x + 10, dy + 18 + i * 10, 0xFF303038, false);
	}

	private void drawStatsPage(DrawContext c, Pokemon p, int x, int y, int w) {
		int cx = x + 6, cw = w - 12, yy = y + 6;
		Draw.dsText(c, "Estadística", cx + 4, yy, 0xFF705010, 0xFFF8E8B0);
		Draw.dsText(c, "IV", cx + cw - 104, yy, 0xFF705010, 0xFFF8E8B0);
		Draw.dsText(c, "EV", cx + cw - 52, yy, 0xFF705010, 0xFFF8E8B0);
		yy += 13;
		int ivSum = 0, evSum = 0;
		for (int i = 0; i < 6; i++) {
			int iv = iv(p, i), ev = ev(p, i);
			ivSum += iv;
			evSum += ev;
			Draw.round(c, cx, yy, cx + cw, yy + 16, i % 2 == 0 ? 0xFFF8E0A0 : 0xFFF8ECC0);
			Draw.dsText(c, CalcData.STAT_LABEL[i], cx + 6, yy + 4, 0xFF303038, 0xFFF8F0D8);
			// Barras pequeñas
			bar(c, cx + cw - 130, yy + 6, 22, iv / 31f, iv == 31 ? 0xFF48B848 : 0xFFD8A028);
			String ivs = String.valueOf(iv);
			Draw.dsText(c, ivs, cx + cw - 92 - Draw.width(ivs), yy + 4, iv == 31 ? 0xFF308830 : 0xFF303038, 0xFFF8F0D8);
			bar(c, cx + cw - 82, yy + 6, 22, ev / 252f, 0xFF4878D8);
			String evs = String.valueOf(ev);
			Draw.dsText(c, evs, cx + cw - 36 - Draw.width(evs), yy + 4, 0xFF303038, 0xFFF8F0D8);
			yy += 18;
		}
		yy += 4;
		Draw.dsText(c, "Total IVs: " + ivSum + "/186", cx + 6, yy, 0xFF303038, 0xFFF8F0D8);
		yy += 12;
		Draw.dsText(c, "Total EVs: " + evSum + "/510", cx + 6, yy, evSum >= 510 ? 0xFF308830 : 0xFF303038, 0xFFF8F0D8);
		yy += 12;
		int perfect = 0;
		for (int i = 0; i < 6; i++) if (iv(p, i) == 31) perfect++;
		Draw.dsText(c, perfect + " IV perfectos", cx + 6, yy, 0xFF705010, 0xFFF8E8B0);
	}

	private static void bar(DrawContext c, int x, int y, int w, float ratio, int color) {
		c.fill(x, y, x + w, y + 4, 0xFF505050);
		int fw = Math.round((w - 2) * Math.max(0, Math.min(1, ratio)));
		if (fw > 0) c.fill(x + 1, y + 1, x + 1 + fw, y + 3, color);
	}

	private void drawMarks(DrawContext c, Pokemon p, int x, int y, int w, int h, int mx, int my) {
		markTip = null;
		List<Object> marks = new ArrayList<>();
		try {
			marks.addAll(p.getMarks());
		} catch (Throwable ignored) {
		}
		if (marks.isEmpty()) {
			Draw.dsText(c, "No tiene ninguna marca.", x + 10, y + 10, 0xFF804070, 0xFFF8E0F0);
			return;
		}
		int cols = 4, cell = (w - 16) / cols;
		Object hovered = null;
		for (int i = 0; i < Math.min(marks.size(), 12); i++) {
			int cx = x + 8 + (i % cols) * cell, cy = y + 8 + (i / cols) * 44;
			Object mark = marks.get(i);
			boolean over = mx >= cx + 2 && mx < cx + cell - 2 && my >= cy && my < cy + 40;
			if (over) hovered = mark;
			Draw.round(c, cx + 2, cy, cx + cell - 2, cy + 40, over ? 0xFFF8D0EC : 0xFFFFF0FA);
			Identifier tex = markTexture(mark);
			if (tex != null) c.drawTexture(tex, cx + cell / 2 - 12, cy + 4, 24, 24, 0f, 0f, 1, 1, 1, 1);
			String name = Draw.fit(markName(mark), cell - 6);
			c.drawText(textRenderer, name, cx + cell / 2 - textRenderer.getWidth(name) / 2, cy + 30, 0xFF604050, false);
		}
		if (marks.size() > 12) Draw.dsText(c, "+" + (marks.size() - 12) + " más", x + w - 60, y + h - 14, 0xFF804070, 0xFFF8E0F0);
		if (hovered != null) {
			List<Text> tip = new ArrayList<>();
			tip.add(Text.literal(markName(hovered)));
			String desc = markText(hovered, "getDescription");
			if (desc != null && !desc.isBlank()) {
				for (OrderedText line : textRenderer.wrapLines(StringVisitable.plain(desc), 180)) {
					StringBuilder sb = new StringBuilder();
					line.accept((idx, style, cp) -> {
						sb.appendCodePoint(cp);
						return true;
					});
					tip.add(Text.literal(sb.toString()).formatted(net.minecraft.util.Formatting.GRAY));
				}
			}
			markTip = tip;
		}
	}

	/** Recuadro de nombre de la marca bajo el ratón (se dibuja al final, por encima de todo). */
	private List<Text> markTip;

	private void drawTip(DrawContext c, List<Text> lines, int mx, int my) {
		int w = 0;
		for (Text t : lines) w = Math.max(w, textRenderer.getWidth(t));
		int h = lines.size() * 10 + 6;
		int x = mx + 10, y = my - h - 4;
		if (x + w + 8 > width) x = mx - w - 14;
		if (y < 2) y = my + 12;
		c.draw();
		c.getMatrices().push();
		c.getMatrices().translate(0, 0, 500);
		c.fill(x, y, x + w + 8, y + h, 0xF0100010);
		c.fill(x + 1, y + 1, x + w + 7, y + 2, 0xFF5000A0);
		c.fill(x + 1, y + h - 2, x + w + 7, y + h - 1, 0xFF28007F);
		c.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFF5000A0);
		c.fill(x + w + 6, y + 1, x + w + 7, y + h - 1, 0xFF28007F);
		for (int i = 0; i < lines.size(); i++) c.drawText(textRenderer, lines.get(i), x + 4, y + 4 + i * 10, 0xFFFFFFFF, true);
		c.getMatrices().pop();
	}

	private static String markText(Object mark, String method) {
		try {
			Object o = mark.getClass().getMethod(method).invoke(mark);
			if (o instanceof Text t) return t.getString();
			if (o instanceof String str) return Text.translatable(str).getString();
		} catch (Throwable ignored) {
		}
		return null;
	}

	private static Identifier markTexture(Object mark) {
		for (String m : new String[]{"getTexture", "getIcon", "getTextureLocation"}) {
			try {
				Method mt = mark.getClass().getMethod(m);
				Object o = mt.invoke(mark);
				if (o instanceof Identifier id) return id;
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	private static String markName(Object mark) {
		for (String m : new String[]{"getDisplayName", "getName", "getTitle"}) {
			try {
				Object o = mark.getClass().getMethod(m).invoke(mark);
				if (o instanceof Text t) return t.getString();
				if (o instanceof String s) return Text.translatable(s).getString();
				if (o instanceof Identifier id) return id.getPath();
			} catch (Throwable ignored) {
			}
		}
		return "?";
	}

	// ---------------------------------------------------------------- Cambiar movimientos

	private void swapMoves(Pokemon p, int a, int b) {
		ClientParty cp = clientParty();
		if (cp == null) return;
		int slot = cp.getPosition(p.getUuid());
		if (slot < 0) return;
		new RequestMoveSwapPacket(a, b, slot).sendToServer();
		if (selectedMove == a) selectedMove = b;
		else if (selectedMove == b) selectedMove = a;
	}

	private void openSwap(Pokemon p, int slot) {
		if (inBattle()) {
			toast("No se puede durante un combate.");
			return;
		}
		List<MoveTemplate> known = new ArrayList<>();
		for (Move m : p.getMoveSet().getMoves()) known.add(m.getTemplate());
		List<MoveTemplate> opts = new ArrayList<>();
		try {
			for (MoveTemplate t : p.getAllAccessibleMoves()) if (!known.contains(t)) opts.add(t);
		} catch (Throwable ignored) {
		}
		opts.sort(Comparator.comparing((MoveTemplate t) -> t.getDisplayName().getString(), String.CASE_INSENSITIVE_ORDER));
		if (opts.isEmpty()) {
			toast("No conoce otros movimientos.");
			return;
		}
		swapOptions = opts;
		swapSlot = slot;
		swapScroll = 0;
	}

	private static final int SWAP_ROW = 15;
	/** Barra de desplazamiento de la lista: zona de clic y arrastre. */
	private int barX1, barX2, barY1, barY2, barMax;
	private boolean barVisible, draggingBar;
	private double dragOffset;
	private int knobY, knobH;

	private int swapVisible(int rh) {
		return (rh - 40) / SWAP_ROW;
	}

	private void drawSwapList(DrawContext c, Pokemon p, int x, int y, int w, int h, int mx, int my) {
		List<Move> moves = p.getMoveSet().getMoves();
		if (swapSlot >= moves.size()) {
			swapSlot = -1;
			return;
		}
		c.getMatrices().push();
		c.getMatrices().translate(0, 0, 150);
		hits.add(new Hit(x, y, x + w, y + h, () -> {})); // bloquea los clics a lo que hay debajo
		Draw.panel(c, x, y, x + w, y + h, 0xFFE05850, 0xFFF8E8E4);
		String old = moves.get(swapSlot).getTemplate().getDisplayName().getString();
		Draw.dsText(c, Draw.fit("Olvidar " + old + " y aprender:", w - 80), x + 8, y + 7, 0xFF303038, 0xFFE8D0C8);
		button(c, x + w - 66, y + 4, x + w - 6, y + 17, "Cancelar", mx, my, 0xFFF8F8F0, () -> swapSlot = -1);
		int vis = swapVisible(h);
		swapScroll = Math.max(0, Math.min(swapScroll, Math.max(0, swapOptions.size() - vis)));
		int ly = y + 22;
		for (int i = 0; i < vis && swapScroll + i < swapOptions.size(); i++) {
			MoveTemplate t = swapOptions.get(swapScroll + i);
			int ry = ly + i * SWAP_ROW;
			boolean hover = mx >= x + 4 && mx < x + w - 10 && my >= ry && my < ry + SWAP_ROW - 1;
			Draw.round(c, x + 4, ry, x + w - 10, ry + SWAP_ROW - 1, hover ? 0xFFF8C8C0 : (i % 2 == 0 ? 0xFFF8D8D0 : 0xFFF8E4DC));
			TypeBadge.draw(c, t.getElementalType().getName(), x + 7, ry + 2);
			Draw.dsText(c, Draw.fit(t.getDisplayName().getString(), w - 130), x + 10 + TypeBadge.WIDTH, ry + 3, 0xFF303038, 0xFFE8C8C0);
			var cat = t.getDamageCategory();
			String info = (cat == DamageCategories.INSTANCE.getPHYSICAL() ? "Fís " : cat == DamageCategories.INSTANCE.getSPECIAL() ? "Esp " : "Est ")
					+ (t.getPower() > 0 ? String.valueOf((int) t.getPower()) : "—");
			Draw.dsText(c, info, x + w - 16 - Draw.width(info), ry + 3, 0xFF805050, 0xFFE8C8C0);
			MoveTemplate pick = t;
			hits.add(new Hit(x + 4, ry, x + w - 10, ry + SWAP_ROW - 1, () -> {
				new BenchMovePacket(true, p.getUuid(), moves.get(swapSlot).getTemplate(), pick).sendToServer();
				beep(1.9f);
				toast(p.getDisplayName(false).getString() + " aprendió " + pick.getDisplayName().getString() + ".");
				swapSlot = -1;
			}));
		}
		// Barra de desplazamiento
		barVisible = swapOptions.size() > vis;
		if (barVisible) {
			int th = h - 26, sy = y + 22;
			barMax = swapOptions.size() - vis;
			int kh = Math.max(12, (th - 4) * vis / swapOptions.size());
			int ky = sy + (th - 4 - kh) * swapScroll / Math.max(1, barMax);
			barX1 = x + w - 10;
			barX2 = x + w - 2;
			barY1 = sy;
			barY2 = sy + th - 4;
			knobH = kh;
			knobY = ky;
			boolean hot = draggingBar || (mx >= barX1 && mx < barX2 && my >= ky && my < ky + kh);
			c.fill(x + w - 8, sy, x + w - 4, sy + th - 4, 0xFFE0B0A8);
			c.fill(x + w - 9, ky, x + w - 3, ky + kh, hot ? 0xFFF07068 : 0xFFE05850);
		}
		c.getMatrices().pop();
	}

	// ---------------------------------------------------------------- Controles

	private void button(DrawContext c, int x1, int y1, int x2, int y2, String label, int mx, int my, int color, Runnable action) {
		boolean hover = mx >= x1 && mx < x2 && my >= y1 && my < y2;
		Draw.round(c, x1, y1, x2, y2, 0xFF303848);
		c.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, hover ? Draw.lerpColor(color, 0xFFFFFFFF, 0.4f) : color);
		c.drawText(textRenderer, label, (x1 + x2) / 2 - textRenderer.getWidth(label) / 2, (y1 + y2) / 2 - 4, 0xFF303030, false);
		hits.add(new Hit(x1, y1, x2, y2, () -> {
			beep(1.6f);
			action.run();
		}));
	}

	private static void beep(float pitch) {
		MinecraftClient.getInstance().getSoundManager()
				.play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.4f));
	}

	private void setPage(Page p) {
		if (p == page) return;
		page = p;
		lastPage = p.ordinal();
		selectedMove = -1;
		swapSlot = -1;
		beep(1.6f);
	}

	private void switchPage(int dir) {
		Page[] all = Page.values();
		setPage(all[Math.floorMod(page.ordinal() + dir, all.length)]);
	}

	private void switchPokemon(int dir) {
		if (party.size() < 2) return;
		index = Math.floorMod(index + dir, party.size());
		nickBuffer = null;
		selectedMove = -1;
		swapSlot = -1;
		beep(1.4f);
	}

	/** Abre el Resumen original de Cobblemon (para cambiar mote, ordenar movimientos, evolucionar…). */
	private void openOriginal() {
		bypass = true;
		try {
			Summary.Companion.open(original, editable, Math.max(0, original.indexOf(pk())));
		} finally {
			bypass = false;
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button == 0 && page == Page.MOVES && swapSlot >= 0 && barVisible && !partyMenu
				&& mx >= barX1 && mx < barX2 && my >= barY1 && my < barY2) {
			draggingBar = true;
			// Clic en el pomo: se agarra donde está; clic en la guía: el pomo salta ahí
			dragOffset = (my >= knobY && my < knobY + knobH) ? my - knobY : knobH / 2.0;
			dragBarTo(my);
			return true;
		}
		if (button == 0) {
			// Lo último dibujado (botones pequeños, ventanas superpuestas) tiene prioridad
			List<Hit> list = new ArrayList<>(hits);
			for (int i = list.size() - 1; i >= 0; i--) {
				Hit h = list.get(i);
				if (h.contains(mx, my)) {
					h.action().run();
					return true;
				}
			}
		}
		return super.mouseClicked(mx, my, button);
	}

	private void dragBarTo(double my) {
		int range = (barY2 - barY1) - knobH;
		if (range <= 0) return;
		double t = (my - dragOffset - barY1) / range;
		swapScroll = (int) Math.round(Math.max(0, Math.min(1, t)) * barMax);
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (draggingBar && button == 0) {
			dragBarTo(my);
			return true;
		}
		return super.mouseDragged(mx, my, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		if (button == 0 && draggingBar) {
			draggingBar = false;
			return true;
		}
		return super.mouseReleased(mx, my, button);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
		if (vertical == 0) return true;
		if (partyMenu) return true;
		if (page == Page.MOVES && swapSlot >= 0) {
			swapScroll += vertical > 0 ? -1 : 1;
			return true;
		}
		switchPokemon(vertical > 0 ? -1 : 1);
		return true;
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (nickBuffer != null) {
			addNickChars(String.valueOf(chr));
			return true;
		}
		return super.charTyped(chr, modifiers);
	}

	private void addNickChars(String text) {
		for (int cp : text.codePoints().toArray()) {
			if (nickBuffer.codePointCount(0, nickBuffer.length()) >= NICK_MAX) break;
			if (Character.isISOControl(cp) || cp == '§') continue;
			nickBuffer += new String(Character.toChars(cp));
		}
	}

	/** Envía el mote al servidor (vacío = quitar el mote), igual que el Resumen de Cobblemon. */
	private void commitNick(Pokemon p) {
		if (nickBuffer == null || p == null) return;
		String nick = nickBuffer.trim();
		nickBuffer = null;
		new SetNicknamePacket(p.getUuid(), true, nick).sendToServer();
		beep(1.9f);
		toast(nick.isEmpty() ? "Mote eliminado." : "Mote cambiado a " + nick + ".");
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (nickBuffer != null) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) nickBuffer = null;
			else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) commitNick(pk());
			else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !nickBuffer.isEmpty())
				nickBuffer = nickBuffer.substring(0, nickBuffer.offsetByCodePoints(nickBuffer.length(), -1));
			else if (keyCode == GLFW.GLFW_KEY_V && Screen.hasControlDown()) {
				String clip = MinecraftClient.getInstance().keyboard.getClipboard();
				if (clip != null) addNickChars(clip);
			}
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE && (partyMenu || swapSlot >= 0)) {
			partyMenu = false;
			swapSlot = -1;
			return true;
		}
		if (partyMenu || swapSlot >= 0) return true;
		switch (keyCode) {
			case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> { switchPage(-1); return true; }
			case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> { switchPage(1); return true; }
			case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> { switchPokemon(-1); return true; }
			case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> { switchPokemon(1); return true; }
			default -> { return super.keyPressed(keyCode, scanCode, modifiers); }
		}
	}
}

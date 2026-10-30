package com.platino.calc;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattleActor;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattleSide;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.PokemonStats;
import com.platino.Draw;
import com.platino.TypeChart;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Calculadora de daño del Pokéreloj (pantalla ampliada, estilo LCD verde).
 * Todo lo que es de elegir es un desplegable con buscador: escribes para filtrar y recorres la lista completa.
 */
public class DamageCalcScreen extends Screen {
	private static final int BG = 0xFF90C890, DARK = 0xFF284828, MID = 0xFF60A060, LIGHT = 0xFFB8E0B0, PALE = 0xFFE8F8E0;
	private static final int UP = 0xFFB03020, DOWN = 0xFF2040A0; // naturaleza: sube / baja
	private static final int ROW = 13, LIST_ROWS = 10;

	/** Estado guardado entre aperturas. */
	private static final State S = new State();

	private static final class State {
		final DamageEngine.Side atk = new DamageEngine.Side(), def = new DamageEngine.Side();
		final DamageEngine.Field field = new DamageEngine.Field();
		CalcData.SpeciesEntry atkSp, defSp;
		CalcData.MoveEntry move;
		List<CalcData.MoveEntry> atkKnownMoves = new ArrayList<>();
		int partyCursorAtk = -1, partyCursorDef = -1;
	}

	/** Opción de un desplegable. */
	private record Opt(String label, String search, String extra, Object value) {}

	/** Desplegable con buscador. */
	private final class Combo {
		final String id;
		int x, y, w;
		final Supplier<List<Opt>> options;
		final Supplier<String> current;
		final Consumer<Object> onPick;

		Combo(String id, Supplier<List<Opt>> options, Supplier<String> current, Consumer<Object> onPick) {
			this.id = id;
			this.options = options;
			this.current = current;
			this.onPick = onPick;
		}

		Combo at(int x, int y, int w) {
			this.x = x;
			this.y = y;
			this.w = w;
			return this;
		}
	}

	private record Hit(int x1, int y1, int x2, int y2, Runnable action) {
		boolean contains(double x, double y) {
			return x >= x1 && x < x2 && y >= y1 && y < y2;
		}
	}

	private final Screen parent;
	private final List<Hit> hits = new ArrayList<>();
	private final List<Combo> combos = new ArrayList<>();
	private int W, H, x0, y0;

	// Desplegable abierto
	private Combo open;
	private String query = "";
	private int listScroll, listSel;
	private List<Opt> filtered = List.of();
	private int listX, listY, listW, listH;

	public DamageCalcScreen(Screen parent) {
		super(Text.literal("Calculadora de daño"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		W = Math.min(480, width - 12);
		H = Math.min(296, height - 12);
		x0 = (width - W) / 2;
		y0 = (height - H) / 2;
		buildCombos();
	}

	@Override
	public void close() {
		if (open != null) {
			closeCombo();
			return;
		}
		MinecraftClient.getInstance().setScreen(parent);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
		c.fill(0, 0, width, height, 0x70101018);
	}

	// ---------------------------------------------------------------- Opciones de los desplegables

	private static List<Opt> speciesOpts() {
		List<Opt> out = new ArrayList<>();
		for (CalcData.SpeciesEntry sp : CalcData.species()) {
			String types = TypeEff.label(sp.type1()) + (sp.type2() != null ? "/" + TypeEff.label(sp.type2()) : "");
			out.add(new Opt(sp.display(), sp.search(), types, sp));
		}
		return out;
	}

	private static List<Opt> natureOpts() {
		List<Opt> out = new ArrayList<>();
		for (CalcData.NatureEntry n : CalcData.natures()) {
			String extra = n.up() < 0 || n.up() == n.down() ? "neutra"
					: "+" + CalcData.STAT_LABEL[n.up()] + " -" + CalcData.STAT_LABEL[n.down()];
			out.add(new Opt(n.display(), CalcData.fold(n.display()), extra, n));
		}
		return out;
	}

	private static List<Opt> abilityOpts(CalcData.SpeciesEntry sp) {
		List<Opt> out = new ArrayList<>();
		if (sp != null) for (CalcData.AbilityEntry a : sp.abilities()) out.add(new Opt(a.display(), CalcData.fold(a.display() + " " + a.id()), "", a));
		return out;
	}

	private static List<Opt> itemOpts() {
		List<Opt> out = new ArrayList<>();
		for (DamageEngine.Item i : DamageEngine.Item.values()) {
			String extra = i.boostType != null ? "+20% " + TypeEff.label(i.boostType) : "";
			out.add(new Opt(i.label, CalcData.fold(i.label), extra, i));
		}
		return out;
	}

	private static List<Opt> moveOpts() {
		List<Opt> out = new ArrayList<>();
		for (CalcData.MoveEntry m : S.atkKnownMoves) out.add(moveOpt(m, true));
		for (CalcData.MoveEntry m : CalcData.moves()) out.add(moveOpt(m, false));
		return out;
	}

	private static Opt moveOpt(CalcData.MoveEntry m, boolean known) {
		String extra = TypeEff.label(m.type()) + " " + (m.category() == DamageEngine.Category.PHYSICAL ? "Fís" : "Esp") + " " + m.power();
		return new Opt((known ? "★ " : "") + m.display(), m.search(), extra, m);
	}

	private static <E extends Enum<E>> List<Opt> enumOpts(E[] values, java.util.function.Function<E, String> label) {
		List<Opt> out = new ArrayList<>();
		for (E e : values) out.add(new Opt(label.apply(e), CalcData.fold(label.apply(e)), "", e));
		return out;
	}

	private void buildCombos() {
		combos.clear();
		for (boolean atk : new boolean[]{true, false}) {
			DamageEngine.Side s = atk ? S.atk : S.def;
			String k = atk ? "a" : "d";
			combos.add(new Combo(k + "species", DamageCalcScreen::speciesOpts,
					() -> (atk ? S.atkSp : S.defSp) == null ? "" : (atk ? S.atkSp : S.defSp).display(),
					o -> {
						applySpecies(atk, (CalcData.SpeciesEntry) o);
						if (atk) S.atkKnownMoves = new ArrayList<>();
					}));
			combos.add(new Combo(k + "nature", DamageCalcScreen::natureOpts, () -> s.natureName,
					o -> setNature(atk, (CalcData.NatureEntry) o)));
			combos.add(new Combo(k + "ability", () -> abilityOpts(atk ? S.atkSp : S.defSp),
					() -> abilityName(atk), o -> s.ability = ((CalcData.AbilityEntry) o).id()));
			combos.add(new Combo(k + "item", DamageCalcScreen::itemOpts, () -> s.item.label,
					o -> s.item = (DamageEngine.Item) o));
		}
		combos.add(new Combo("move", DamageCalcScreen::moveOpts, () -> S.move == null ? "" : S.move.display(),
				o -> S.move = (CalcData.MoveEntry) o));
		combos.add(new Combo("weather", () -> enumOpts(DamageEngine.Weather.values(), w -> w.label),
				() -> S.field.weather.label, o -> S.field.weather = (DamageEngine.Weather) o));
		combos.add(new Combo("terrain", () -> enumOpts(DamageEngine.Terrain.values(), t -> t.label),
				() -> S.field.terrain.label, o -> S.field.terrain = (DamageEngine.Terrain) o));
		combos.add(new Combo("screens", () -> enumOpts(DamageEngine.Screens.values(), t -> t.label),
				() -> S.field.screens.label, o -> S.field.screens = (DamageEngine.Screens) o));
	}

	private Combo combo(String id) {
		for (Combo cb : combos) if (cb.id.equals(id)) return cb;
		throw new IllegalArgumentException(id);
	}

	private static String abilityName(boolean atk) {
		CalcData.SpeciesEntry sp = atk ? S.atkSp : S.defSp;
		String id = (atk ? S.atk : S.def).ability;
		if (sp != null) for (CalcData.AbilityEntry a : sp.abilities()) if (a.id().equals(id)) return a.display();
		return id == null || id.isEmpty() ? "—" : id;
	}

	private static void applySpecies(boolean atk, CalcData.SpeciesEntry sp) {
		DamageEngine.Side s = atk ? S.atk : S.def;
		s.species = sp.display();
		s.base = sp.base().clone();
		s.type1 = sp.type1();
		s.type2 = sp.type2();
		if (atk) S.atkSp = sp;
		else S.defSp = sp;
		s.ability = sp.abilities().isEmpty() ? "" : sp.abilities().get(0).id();
	}

	private static void setNature(boolean atk, CalcData.NatureEntry n) {
		DamageEngine.Side s = atk ? S.atk : S.def;
		s.natureUp = n.up();
		s.natureDown = n.down();
		s.natureName = n.display();
	}

	// ---------------------------------------------------------------- Rellenar desde equipo / rival

	private void fillFromParty(boolean atk) {
		List<Pokemon> party = new ArrayList<>();
		try {
			for (Pokemon p : CobblemonClient.INSTANCE.getStorage().getParty().getSlots()) if (p != null) party.add(p);
		} catch (Throwable ignored) {
		}
		if (party.isEmpty()) return;
		int cur = ((atk ? S.partyCursorAtk : S.partyCursorDef) + 1) % party.size();
		if (atk) S.partyCursorAtk = cur;
		else S.partyCursorDef = cur;
		Pokemon p = party.get(cur);
		DamageEngine.Side s = atk ? S.atk : S.def;
		var form = p.getForm();
		String display = p.getSpecies().getTranslatedName().getString();
		if (form.getName() != null && !form.getName().equalsIgnoreCase("normal")) display += " (" + form.getName() + ")";
		CalcData.SpeciesEntry sp = new CalcData.SpeciesEntry(display, CalcData.fold(display), CalcData.stats(form.getBaseStats()),
				CalcData.typeId(p.getPrimaryType()), CalcData.typeId(p.getSecondaryType()), CalcData.abilities(form.getAbilities()));
		applySpecies(atk, sp);
		s.level = p.getLevel();
		for (int i = 0; i < 6; i++) {
			s.ivs[i] = statValue(p.getIvs(), i, 31);
			s.evs[i] = statValue(p.getEvs(), i, 0);
		}
		try {
			setNature(atk, CalcData.toEntry(p.getEffectiveNature()));
		} catch (Throwable ignored) {
		}
		try {
			s.ability = p.getAbility().getName();
		} catch (Throwable ignored) {
		}
		try {
			s.item = DamageEngine.Item.fromId(Registries.ITEM.getId(p.heldItem().getItem()).getPath());
		} catch (Throwable ignored) {
		}
		if (atk) {
			S.atkKnownMoves = new ArrayList<>();
			for (Move m : p.getMoveSet().getMoves()) {
				try {
					CalcData.MoveEntry e = CalcData.toEntry(m.getTemplate());
					if (e.category() != DamageEngine.Category.STATUS) S.atkKnownMoves.add(e);
				} catch (Throwable ignored) {
				}
			}
			if (!S.atkKnownMoves.isEmpty()) S.move = S.atkKnownMoves.get(0);
		} else {
			int max = Math.max(1, p.getMaxHealth());
			S.field.defHpPercent = Math.max(1, Math.round(p.getCurrentHealth() * 100f / max));
		}
		beep(1.6f);
	}

	private static int statValue(PokemonStats ps, int i, int fallback) {
		try {
			Number n = ps.get(CalcData.STAT_ORDER[i]);
			return n == null ? fallback : n.intValue();
		} catch (Throwable t) {
			return fallback;
		}
	}

	/** Rival activo en el combate actual (especie, nivel y % de PS; IVs 31 / EVs 0 supuestos). */
	private void fillFromBattle() {
		var battle = CobblemonClient.INSTANCE.getBattle();
		var me = MinecraftClient.getInstance().player;
		if (battle == null || me == null) return;
		for (ClientBattleSide side : battle.getSides()) {
			boolean mine = false;
			for (ClientBattleActor a : side.getActors()) if (me.getUuid().equals(a.getUuid())) mine = true;
			if (mine) continue;
			for (ClientBattleActor a : side.getActors()) {
				for (ActiveClientBattlePokemon ap : a.getActivePokemon()) {
					ClientBattlePokemon bp = ap.getBattlePokemon();
					if (bp == null) continue;
					String name = bp.getSpecies().getTranslatedName().getString();
					for (CalcData.SpeciesEntry sp : CalcData.species()) {
						if (sp.display().equals(name)) {
							applySpecies(false, sp);
							break;
						}
					}
					S.def.level = bp.getLevel();
					S.def.ivs = new int[]{31, 31, 31, 31, 31, 31};
					S.def.evs = new int[]{0, 0, 0, 0, 0, 0};
					float ratio = bp.isHpFlat() ? bp.getHpValue() / Math.max(1f, bp.getMaxHp()) : bp.getHpValue();
					S.field.defHpPercent = Math.max(1, Math.min(100, Math.round(ratio * 100)));
					beep(1.6f);
					return;
				}
			}
		}
	}

	// ---------------------------------------------------------------- Dibujo

	@Override
	public void render(DrawContext c, int mouseX, int mouseY, float delta) {
		super.render(c, mouseX, mouseY, delta);
		hits.clear();
		wheelTargets.clear();
		x0 = (width - W) / 2;
		y0 = (height - H) / 2;
		boolean blocked = open != null; // con un desplegable abierto, el resto no reacciona al ratón
		int mx = blocked ? -1 : mouseX, my = blocked ? -1 : mouseY;

		c.getMatrices().push();
		c.getMatrices().translate(0, 0, 50);
		Draw.round(c, x0 - 3, y0 - 3, x0 + W + 7, y0 + H + 7, 0x60000000);
		Draw.panelGradient(c, x0 - 6, y0 - 6, x0 + W + 6, y0 + H + 6, 0xFF7888A0, 0xFFE8EEF4, 0xFFC8D2DC);
		Draw.panel(c, x0 - 2, y0 - 2, x0 + W + 2, y0 + H + 2, 0xFF404850, BG);

		text(c, "CALCULADORA DE DAÑO", x0 + W / 2 - textRenderer.getWidth("CALCULADORA DE DAÑO") / 2, y0 + 4, DARK);
		button(c, x0 + W - 18, y0 + 2, x0 + W - 4, y0 + 13, "×", mx, my, this::close);

		int cw = (W - 30) / 2;
		int top = y0 + 18;
		int sideBottom = drawSide(c, true, x0 + 8, top, cw, mx, my);
		drawSide(c, false, x0 + W - 8 - cw, top, cw, mx, my);
		c.fill(x0 + W / 2, top, x0 + W / 2 + 1, sideBottom, MID);

		// Movimiento y campo
		int y = sideBottom + 4;
		c.fill(x0 + 6, y - 2, x0 + W - 6, y - 1, MID);
		text(c, "Movimiento", x0 + 8, y + 2, DARK);
		comboBox(c, combo("move").at(x0 + 64, y, cw - 56), mx, my);
		if (S.move != null) {
			String info = TypeEff.label(S.move.type()) + " · " + (S.move.category() == DamageEngine.Category.PHYSICAL ? "Físico" : "Especial")
					+ " · Pot. " + S.move.power();
			text(c, info, x0 + W - 8 - cw, y + 2, DARK);
		}
		toggle(c, x0 + W - 8 - 52, y + 1, "Crítico", S.field.crit, mx, my, () -> S.field.crit = !S.field.crit);
		y += ROW + 3;
		int third = (W - 16 - 8) / 3;
		comboBox(c, combo("weather").at(x0 + 8, y, third), mx, my);
		comboBox(c, combo("terrain").at(x0 + 12 + third, y, third), mx, my);
		comboBox(c, combo("screens").at(x0 + 16 + 2 * third, y, third), mx, my);
		y += ROW + 4;

		drawResult(c, x0 + 6, y, W - 12, y0 + H - 4 - y);
		c.getMatrices().pop();

		drawOpenCombo(c, mouseX, mouseY);
	}

	/** Dibuja un lado y devuelve la Y donde termina. */
	private int drawSide(DrawContext c, boolean atk, int x, int y, int w, int mx, int my) {
		DamageEngine.Side s = atk ? S.atk : S.def;
		CalcData.SpeciesEntry sp = atk ? S.atkSp : S.defSp;
		String k = atk ? "a" : "d";

		text(c, atk ? "ATACANTE" : "DEFENSOR", x, y + 2, DARK);
		int bx = x + w;
		button(c, bx - 50, y, bx, y + 11, "Equipo ▸", mx, my, () -> fillFromParty(atk));
		if (!atk && CobblemonClient.INSTANCE.getBattle() != null) button(c, bx - 88, y, bx - 54, y + 11, "Rival", mx, my, this::fillFromBattle);
		y += ROW + 1;

		comboBox(c, combo(k + "species").at(x, y, w), mx, my);
		y += ROW;
		if (sp != null) text(c, TypeEff.label(s.type1) + (s.type2 != null ? " / " + TypeEff.label(s.type2) : ""), x + 2, y + 1, MID);
		y += 11;

		stepper(c, x, y, "Nv", s.level, 1, 10, 1, 100, mx, my, v -> s.level = v);
		comboBox(c, combo(k + "nature").at(x + 62, y, w - 62), mx, my);
		y += ROW + 1;
		comboBox(c, combo(k + "ability").at(x, y, w / 2 - 2), mx, my);
		comboBox(c, combo(k + "item").at(x + w / 2 + 2, y, w - w / 2 - 2), mx, my);
		y += ROW + 3;

		// Tabla de estadísticas: nombre | base | IV | EV | total
		int cBase = x + 54, cIv = x + 76, cEv = x + 120, cTot = x + w;
		text(c, "Base", cBase, y, MID);
		text(c, "IV", cIv + 14, y, MID);
		text(c, "EV", cEv + 18, y, MID);
		text(c, "Total", cTot - textRenderer.getWidth("Total"), y, MID);
		y += 10;
		boolean physical = S.move == null || S.move.category() != DamageEngine.Category.SPECIAL;
		int used = atk ? (physical ? 1 : 3) : (physical ? 2 : 4);
		for (int i = 0; i < 6; i++) {
			int st = i;
			boolean relevant = i == used || (!atk && i == 0);
			if (relevant) Draw.round(c, x - 2, y - 1, x + w + 2, y + ROW - 2, LIGHT);
			int col = i == s.natureUp && s.natureUp != s.natureDown ? UP : i == s.natureDown && s.natureUp != s.natureDown ? DOWN : DARK;
			text(c, CalcData.STAT_LABEL[i], x, y + 1, col);
			text(c, String.valueOf(s.base[i]), cBase, y + 1, DARK);
			stepper(c, cIv, y - 1, "", s.ivs[i], 1, 31, 0, 31, mx, my, v -> s.ivs[st] = v);
			stepper(c, cEv, y - 1, "", s.evs[i], 4, 252, 0, 252, mx, my, v -> s.evs[st] = v);
			String tot = String.valueOf(s.stat(i));
			text(c, tot, cTot - textRenderer.getWidth(tot), y + 1, col);
			y += ROW - 1;
		}
		int evTotal = 0;
		for (int e : s.evs) evTotal += e;
		text(c, "EVs: " + evTotal + "/510", x, y + 1, evTotal > 510 ? UP : MID);
		y += ROW;

		if (atk) {
			stepper(c, x, y, "Etapa", s.atkStage, 1, 6, -6, 6, mx, my, v -> s.atkStage = v);
			toggle(c, x + 84, y + 1, "Quemado", s.burned, mx, my, () -> s.burned = !s.burned);
		} else {
			stepper(c, x, y, "Etapa", s.defStage, 1, 6, -6, 6, mx, my, v -> s.defStage = v);
			stepper(c, x + 84, y, "PS %", S.field.defHpPercent, 1, 10, 1, 100, mx, my, v -> S.field.defHpPercent = v);
		}
		return y + ROW;
	}

	private void drawResult(DrawContext c, int x, int y, int w, int h) {
		Draw.round(c, x, y, x + w, y + h, DARK);
		c.fill(x + 2, y + 2, x + w - 2, y + h - 2, LIGHT);
		if (S.atkSp == null || S.defSp == null || S.move == null) {
			text(c, "Elige atacante, defensor y movimiento.", x + 8, y + 7, DARK);
			text(c, "\"Equipo ▸\" rellena con tus Pokémon" + (CobblemonClient.INSTANCE.getBattle() != null ? "; \"Rival\" con el rival." : "."), x + 8, y + 19, MID);
			return;
		}
		DamageEngine.Result r = DamageEngine.calc(S.atk, S.def, S.move.toMove(), S.field);
		if (r.max() == 0) {
			text(c, r.note() != null ? r.note() : "Sin daño", x + 8, y + 7, DARK);
			text(c, r.koText(), x + 8, y + 19, DARK);
			return;
		}
		String dmg = r.min() + " - " + r.max() + " PS  (" + String.format("%.1f%% - %.1f%%", r.minPct(), r.maxPct()) + ")";
		c.getMatrices().push();
		c.getMatrices().translate(x + 8, y + 5, 0);
		c.getMatrices().scale(1.25f, 1.25f, 1f);
		text(c, Draw.fit(dmg, (int) ((w - 16) / 1.25f)), 0, 0, DARK);
		c.getMatrices().pop();
		String eff = r.effectiveness() == 1f ? "" : "  ·  " + TypeChart.describe(r.effectiveness()) + " (" + TypeChart.multiplierText(r.effectiveness()) + ")";
		String hpInfo = S.field.defHpPercent < 100 ? "  ·  PS del rival: " + r.currentHp() + "/" + r.defenderHp() : "";
		text(c, Draw.fit(r.koText() + eff + hpInfo, w - 16), x + 8, y + 19, DARK);
		// Barra de PS del defensor: lo que le queda tras el golpe (oscuro = seguro, medio = posible)
		int bx1 = x + 8, bx2 = x + w - 8, by = y + h - 11;
		if (by > y + 28) {
			Draw.round(c, bx1, by, bx2, by + 7, DARK);
			c.fill(bx1 + 1, by + 1, bx2 - 1, by + 6, BG);
			float curPct = r.currentHp() * 100f / Math.max(1, r.defenderHp());
			float leftMin = Math.max(0, curPct - r.maxPct()) / 100f, leftMax = Math.max(0, curPct - r.minPct()) / 100f;
			int fw = bx2 - bx1 - 2;
			c.fill(bx1 + 1, by + 1, bx1 + 1 + Math.round(fw * leftMax), by + 6, MID);
			c.fill(bx1 + 1, by + 1, bx1 + 1 + Math.round(fw * leftMin), by + 6, DARK);
		}
	}

	// ---------------------------------------------------------------- Desplegables

	private void comboBox(DrawContext c, Combo cb, int mx, int my) {
		boolean hover = mx >= cb.x && mx < cb.x + cb.w && my >= cb.y && my < cb.y + 12;
		Draw.round(c, cb.x, cb.y, cb.x + cb.w, cb.y + 12, DARK);
		c.fill(cb.x + 1, cb.y + 1, cb.x + cb.w - 1, cb.y + 11, hover ? PALE : LIGHT);
		String cur = cb.current.get();
		if (cur == null || cur.isEmpty()) text(c, "Buscar…", cb.x + 3, cb.y + 2, MID);
		else text(c, Draw.fit(cur, cb.w - 14), cb.x + 3, cb.y + 2, DARK);
		text(c, "▾", cb.x + cb.w - 9, cb.y + 2, DARK);
		hits.add(new Hit(cb.x, cb.y, cb.x + cb.w, cb.y + 12, () -> openCombo(cb)));
	}

	private void openCombo(Combo cb) {
		open = cb;
		query = "";
		listScroll = 0;
		listSel = 0;
		refilter();
		beep(1.8f);
	}

	private void closeCombo() {
		open = null;
		filtered = List.of();
	}

	private void refilter() {
		String q = CalcData.fold(query).trim();
		List<Opt> all = open.options.get();
		if (q.isEmpty()) {
			filtered = all;
		} else {
			List<Opt> starts = new ArrayList<>(), contains = new ArrayList<>();
			for (Opt o : all) {
				String lbl = CalcData.fold(o.label()).replace("★ ", "");
				if (lbl.startsWith(q) || o.search().startsWith(q)) starts.add(o);
				else if (o.search().contains(q) || lbl.contains(q)) contains.add(o);
			}
			starts.addAll(contains);
			filtered = starts;
		}
		listSel = Math.min(listSel, Math.max(0, filtered.size() - 1));
		listScroll = Math.max(0, Math.min(listScroll, Math.max(0, filtered.size() - LIST_ROWS)));
	}

	private void pick(Opt o) {
		Combo cb = open;
		closeCombo();
		cb.onPick.accept(o.value());
		beep(1.6f);
	}

	private void drawOpenCombo(DrawContext c, int mx, int my) {
		if (open == null) return;
		int rows = Math.min(LIST_ROWS, Math.max(1, filtered.size()));
		listW = Math.max(open.w, 170);
		listH = 14 + rows * 11 + 2;
		listX = Math.min(open.x, x0 + W - listW);
		listY = open.y + 13;
		if (listY + listH > y0 + H) listY = open.y - listH - 1; // si no cabe abajo, hacia arriba
		c.getMatrices().push();
		c.getMatrices().translate(0, 0, 400);
		Draw.round(c, listX + 2, listY + 2, listX + listW + 2, listY + listH + 2, 0x50000000);
		Draw.round(c, listX, listY, listX + listW, listY + listH, DARK);
		c.fill(listX + 1, listY + 1, listX + listW - 1, listY + listH - 1, LIGHT);
		// Línea de búsqueda
		c.fill(listX + 2, listY + 2, listX + listW - 2, listY + 13, PALE);
		String q = query.isEmpty() ? "Escribe para buscar…" : query;
		boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
		text(c, "⌕ " + q + (caret && !query.isEmpty() ? "_" : ""), listX + 4, listY + 4, query.isEmpty() ? MID : DARK);
		if (filtered.isEmpty()) {
			text(c, "Sin resultados", listX + 4, listY + 16, MID);
		}
		for (int r = 0; r < rows && listScroll + r < filtered.size(); r++) {
			int idx = listScroll + r;
			Opt o = filtered.get(idx);
			int ry = listY + 14 + r * 11;
			boolean hover = mx >= listX && mx < listX + listW && my >= ry && my < ry + 11;
			if (hover) listSel = idx;
			if (idx == listSel) c.fill(listX + 1, ry, listX + listW - 1, ry + 11, BG);
			int ew = o.extra().isEmpty() ? 0 : textRenderer.getWidth(o.extra()) + 6;
			text(c, Draw.fit(o.label(), listW - 10 - ew), listX + 4, ry + 2, DARK);
			if (ew > 0) text(c, o.extra(), listX + listW - 6 - textRenderer.getWidth(o.extra()), ry + 2, MID);
		}
		// Barra de desplazamiento
		if (filtered.size() > rows) {
			int trackY = listY + 14, trackH = rows * 11;
			int th = Math.max(6, trackH * rows / filtered.size());
			int ty = trackY + (trackH - th) * listScroll / Math.max(1, filtered.size() - rows);
			c.fill(listX + listW - 3, trackY, listX + listW - 1, trackY + trackH, MID);
			c.fill(listX + listW - 3, ty, listX + listW - 1, ty + th, DARK);
			String n = filtered.size() + "";
			text(c, n, listX + listW - 6 - textRenderer.getWidth(n), listY + 4, MID);
		}
		c.getMatrices().pop();
	}

	// ---------------------------------------------------------------- Controles

	private interface IntSet {
		void set(int v);
	}

	private void text(DrawContext c, String s, int x, int y, int color) {
		c.drawText(textRenderer, s, x, y, color, false);
	}

	private void button(DrawContext c, int x1, int y1, int x2, int y2, String label, int mx, int my, Runnable action) {
		boolean hover = mx >= x1 && mx < x2 && my >= y1 && my < y2;
		Draw.round(c, x1, y1, x2, y2, DARK);
		c.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, hover ? PALE : LIGHT);
		text(c, label, (x1 + x2) / 2 - textRenderer.getWidth(label) / 2, (y1 + y2) / 2 - 4, DARK);
		hits.add(new Hit(x1, y1, x2, y2, () -> {
			beep(1.8f);
			action.run();
		}));
	}

	private void toggle(DrawContext c, int x, int y, String label, boolean on, int mx, int my, Runnable action) {
		Draw.round(c, x, y, x + 9, y + 9, DARK);
		c.fill(x + 1, y + 1, x + 8, y + 8, on ? DARK : PALE);
		text(c, label, x + 12, y + 1, DARK);
		hits.add(new Hit(x, y - 1, x + 14 + textRenderer.getWidth(label), y + 10, () -> {
			beep(1.8f);
			action.run();
		}));
	}

	/** [-] valor [+]. Mayús + clic = paso grande; rueda del ratón sobre el valor = paso pequeño. */
	private void stepper(DrawContext c, int x, int y, String label, int value, int small, int big, int min, int max,
						 int mx, int my, IntSet set) {
		int lx = x;
		if (!label.isEmpty()) {
			text(c, label, x, y + 2, DARK);
			lx = x + textRenderer.getWidth(label) + 3;
		}
		String v = String.valueOf(value);
		int vw = 18;
		button(c, lx, y, lx + 9, y + 11, "-", mx, my,
				() -> set.set(Math.max(min, value - (Screen.hasShiftDown() ? big : small))));
		text(c, v, lx + 10 + (vw - textRenderer.getWidth(v)) / 2, y + 2, DARK);
		button(c, lx + 10 + vw, y, lx + 19 + vw, y + 11, "+", mx, my,
				() -> set.set(Math.min(max, value + (Screen.hasShiftDown() ? big : small))));
		wheelTargets.add(new Wheel(lx + 10, y, lx + 10 + vw, y + 11, d -> set.set(Math.max(min, Math.min(max, value + d * small)))));
	}

	private record Wheel(int x1, int y1, int x2, int y2, IntSet onScroll) {}

	private final List<Wheel> wheelTargets = new ArrayList<>();

	private static void beep(float pitch) {
		MinecraftClient.getInstance().getSoundManager()
				.play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.4f));
	}

	// ---------------------------------------------------------------- Entrada

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (open != null) {
			if (mx >= listX && mx < listX + listW && my >= listY + 14 && my < listY + listH) {
				int idx = listScroll + (int) ((my - listY - 14) / 11);
				if (idx >= 0 && idx < filtered.size()) pick(filtered.get(idx));
				return true;
			}
			if (!(mx >= listX && mx < listX + listW && my >= listY && my < listY + listH)) closeCombo();
			return true;
		}
		if (button == 0) {
			for (Hit h : new ArrayList<>(hits)) {
				if (h.contains(mx, my)) {
					h.action().run();
					return true;
				}
			}
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
		if (open != null) {
			int rows = Math.min(LIST_ROWS, filtered.size());
			listScroll = Math.max(0, Math.min(Math.max(0, filtered.size() - rows), listScroll - (int) Math.signum(vertical) * 3));
			return true;
		}
		for (Wheel w : new ArrayList<>(wheelTargets)) {
			if (mx >= w.x1() && mx < w.x2() && my >= w.y1() && my < w.y2()) {
				w.onScroll().set((int) Math.signum(vertical) * (Screen.hasShiftDown() ? 10 : 1));
				return true;
			}
		}
		return super.mouseScrolled(mx, my, horizontal, vertical);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (open != null) {
			switch (keyCode) {
				case GLFW.GLFW_KEY_ESCAPE -> closeCombo();
				case GLFW.GLFW_KEY_BACKSPACE -> {
					if (!query.isEmpty()) {
						query = query.substring(0, query.length() - 1);
						refilter();
					}
				}
				case GLFW.GLFW_KEY_DOWN -> {
					listSel = Math.min(filtered.size() - 1, listSel + 1);
					if (listSel >= listScroll + LIST_ROWS) listScroll = listSel - LIST_ROWS + 1;
				}
				case GLFW.GLFW_KEY_UP -> {
					listSel = Math.max(0, listSel - 1);
					if (listSel < listScroll) listScroll = listSel;
				}
				case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_TAB -> {
					if (!filtered.isEmpty()) pick(filtered.get(Math.max(0, Math.min(listSel, filtered.size() - 1))));
				}
				default -> {
					return true;
				}
			}
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (open != null && chr >= ' ') {
			if (query.length() < 32) {
				query += chr;
				listSel = 0;
				listScroll = 0;
				refilter();
			}
			return true;
		}
		return super.charTyped(chr, modifiers);
	}
}

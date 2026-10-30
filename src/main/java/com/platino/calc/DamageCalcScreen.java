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
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Calculadora de daño del Pokéreloj (pantalla ampliada, estilo LCD verde). */
public class DamageCalcScreen extends Screen {
	private static final int W = 384, H = 232;
	private static final int BG = 0xFF90C890, DARK = 0xFF284828, MID = 0xFF60A060, LIGHT = 0xFFB8E0B0;

	/** Estado guardado entre aperturas. */
	private static final State S = new State();

	private static final class State {
		final DamageEngine.Side atk = new DamageEngine.Side(), def = new DamageEngine.Side();
		CalcData.SpeciesEntry atkSp, defSp;
		int atkAbility, defAbility, atkNature = -1, defNature = -1;
		CalcData.MoveEntry move;
		List<CalcData.MoveEntry> atkKnownMoves = new ArrayList<>();
		DamageEngine.Weather weather = DamageEngine.Weather.NONE;
		boolean crit, screen, fullHp = true;
		int partyCursorAtk = -1, partyCursorDef = -1;
	}

	private record Hit(int x1, int y1, int x2, int y2, int button, Runnable action) {
		boolean contains(double x, double y) {
			return x >= x1 && x < x2 && y >= y1 && y < y2;
		}
	}

	private final Screen parent;
	private final List<Hit> hits = new ArrayList<>();
	private TextFieldWidget atkField, defField, moveField;
	private TextFieldWidget suggestFor;
	private List<Object> suggestions = new ArrayList<>();
	private int x0, y0;

	public DamageCalcScreen(Screen parent) {
		super(Text.literal("Calculadora de daño"));
		this.parent = parent;
	}

	// ---------------------------------------------------------------- Ciclo de vida

	@Override
	protected void init() {
		x0 = (width - W) / 2;
		y0 = (height - H) / 2;
		int colW = W / 2 - 14;
		atkField = field(x0 + 8, y0 + 20, colW, S.atkSp != null ? S.atkSp.display() : "");
		defField = field(x0 + W / 2 + 6, y0 + 20, colW, S.defSp != null ? S.defSp.display() : "");
		moveField = field(x0 + 8, y0 + 152, colW, S.move != null ? S.move.display() : "");
	}

	private TextFieldWidget field(int x, int y, int w, String text) {
		TextFieldWidget f = new TextFieldWidget(textRenderer, x + 2, y + 2, w - 4, 10, Text.literal(""));
		f.setDrawsBackground(false);
		f.setMaxLength(48);
		f.setEditableColor(DARK);
		f.setText(text);
		f.setChangedListener(t -> updateSuggestions(f));
		addDrawableChild(f);
		return f;
	}

	@Override
	public void close() {
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

	// ---------------------------------------------------------------- Sugerencias

	private void updateSuggestions(TextFieldWidget f) {
		suggestFor = f;
		suggestions = new ArrayList<>();
		String q = f.getText();
		if (f == moveField) {
			if (q.isBlank() && !S.atkKnownMoves.isEmpty()) suggestions.addAll(S.atkKnownMoves);
			else suggestions.addAll(CalcData.search(CalcData.moves(), CalcData.MoveEntry::search, q, 6));
		} else {
			suggestions.addAll(CalcData.search(CalcData.species(), CalcData.SpeciesEntry::search, q, 6));
		}
	}

	private void pick(Object o) {
		if (o instanceof CalcData.MoveEntry m) {
			S.move = m;
			moveField.setText(m.display());
		} else if (o instanceof CalcData.SpeciesEntry sp) {
			boolean atk = suggestFor == atkField;
			applySpecies(atk, sp);
			(atk ? atkField : defField).setText(sp.display());
			if (atk) S.atkKnownMoves = new ArrayList<>();
		}
		suggestFor = null;
		suggestions = new ArrayList<>();
		setFocused(null);
		beep(1.8f);
	}

	private static void applySpecies(boolean atk, CalcData.SpeciesEntry sp) {
		DamageEngine.Side s = atk ? S.atk : S.def;
		s.species = sp.display();
		s.base = sp.base().clone();
		s.type1 = sp.type1();
		s.type2 = sp.type2();
		if (atk) {
			S.atkSp = sp;
			S.atkAbility = 0;
		} else {
			S.defSp = sp;
			S.defAbility = 0;
		}
		s.ability = sp.abilities().isEmpty() ? "" : sp.abilities().get(0).id();
	}

	// ---------------------------------------------------------------- Rellenar desde equipo / rival

	private void fillFromParty(boolean atk) {
		List<Pokemon> party = new ArrayList<>();
		try {
			for (Pokemon p : CobblemonClient.INSTANCE.getStorage().getParty().getSlots()) if (p != null) party.add(p);
		} catch (Throwable ignored) {
		}
		if (party.isEmpty()) return;
		int cur = atk ? S.partyCursorAtk : S.partyCursorDef;
		cur = (cur + 1) % party.size();
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
		CalcData.NatureEntry n = CalcData.toEntry(p.getEffectiveNature());
		setNature(atk, n);
		try {
			s.ability = p.getAbility().getName();
			List<CalcData.AbilityEntry> abs = sp.abilities();
			for (int i = 0; i < abs.size(); i++) if (abs.get(i).id().equals(s.ability)) {
				if (atk) S.atkAbility = i;
				else S.defAbility = i;
			}
		} catch (Throwable ignored) {
		}
		try {
			s.item = DamageEngine.Item.fromId(Registries.ITEM.getId(p.heldItem().getItem()).getPath());
		} catch (Throwable ignored) {
		}
		(atk ? atkField : defField).setText(display);
		if (atk) {
			S.atkKnownMoves = new ArrayList<>();
			for (Move m : p.getMoveSet().getMoves()) {
				try {
					CalcData.MoveEntry e = CalcData.toEntry(m.getTemplate());
					if (e.category() != DamageEngine.Category.STATUS) S.atkKnownMoves.add(e);
				} catch (Throwable ignored) {
				}
			}
			if (!S.atkKnownMoves.isEmpty()) {
				S.move = S.atkKnownMoves.get(0);
				moveField.setText(S.move.display());
			}
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

	private static void setNature(boolean atk, CalcData.NatureEntry n) {
		DamageEngine.Side s = atk ? S.atk : S.def;
		s.natureUp = n.up();
		s.natureDown = n.down();
		s.natureName = n.display();
		List<CalcData.NatureEntry> all = CalcData.natures();
		for (int i = 0; i < all.size(); i++) if (all.get(i).display().equals(n.display())) {
			if (atk) S.atkNature = i;
			else S.defNature = i;
		}
	}

	/** Rival activo en el combate actual (especie y nivel; IVs 31 / EVs 0 supuestos). */
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
					defField.setText(name);
					beep(1.6f);
					return;
				}
			}
		}
	}

	// ---------------------------------------------------------------- Dibujo

	@Override
	public void render(DrawContext c, int mouseX, int mouseY, float delta) {
		hits.clear();
		x0 = (width - W) / 2;
		y0 = (height - H) / 2;
		c.getMatrices().push();
		c.getMatrices().translate(0, 0, 50);
		// Carcasa del Pokéreloj y pantalla LCD
		Draw.round(c, x0 - 3, y0 - 3, x0 + W + 7, y0 + H + 7, 0x60000000);
		Draw.panelGradient(c, x0 - 6, y0 - 6, x0 + W + 6, y0 + H + 6, 0xFF7888A0, 0xFFE8EEF4, 0xFFC8D2DC);
		Draw.panel(c, x0 - 2, y0 - 2, x0 + W + 2, y0 + H + 2, 0xFF404850, BG);

		int colW = W / 2 - 14;
		drawSide(c, true, x0 + 8, colW, mouseX, mouseY);
		drawSide(c, false, x0 + W / 2 + 6, colW, mouseX, mouseY);
		c.fill(x0 + W / 2, y0 + 4, x0 + W / 2 + 1, y0 + 140, MID);

		// Movimiento y condiciones
		int my = y0 + 142;
		text(c, "MOVIMIENTO", x0 + 8, my, DARK);
		box(c, moveField);
		if (S.move != null) {
			String info = TypeEff.label(S.move.type()) + " · " + (S.move.category() == DamageEngine.Category.PHYSICAL ? "Físico" : "Especial")
					+ " · Pot. " + S.move.power();
			text(c, info, x0 + 8, my + 26, DARK);
		}
		int tx = x0 + W / 2 + 6;
		toggle(c, tx, my, "Crítico", S.crit, mouseX, mouseY, () -> S.crit = !S.crit);
		toggle(c, tx + 60, my, "Pantalla", S.screen, mouseX, mouseY, () -> S.screen = !S.screen);
		toggle(c, tx + 128, my, "PS llenos", S.fullHp, mouseX, mouseY, () -> S.fullHp = !S.fullHp);
		String w = switch (S.weather) {
			case NONE -> "Sin clima";
			case SUN -> "Sol";
			case RAIN -> "Lluvia";
		};
		selector(c, tx, my + 14, colW, "Clima: " + w, mouseX, mouseY,
				() -> S.weather = DamageEngine.Weather.values()[Math.floorMod(S.weather.ordinal() - 1, 3)],
				() -> S.weather = DamageEngine.Weather.values()[(S.weather.ordinal() + 1) % 3]);

		drawResult(c, x0 + 6, y0 + 182, W - 12, H - 188);

		// Botón cerrar
		lcdButton(c, x0 + W - 22, y0 + 3, x0 + W - 6, y0 + 15, "×", mouseX, mouseY, this::close);
		c.getMatrices().pop();

		super.render(c, mouseX, mouseY, delta);
		drawSuggestions(c, mouseX, mouseY);
	}

	private void drawSide(DrawContext c, boolean atk, int x, int w, int mx, int my) {
		DamageEngine.Side s = atk ? S.atk : S.def;
		CalcData.SpeciesEntry sp = atk ? S.atkSp : S.defSp;
		int y = y0 + 6;
		text(c, atk ? "ATACANTE" : "DEFENSOR", x, y, DARK);
		lcdButton(c, x + w - 54, y - 2, x + w, y + 9, "Equipo ▸", mx, my, () -> fillFromParty(atk));
		if (!atk && CobblemonClient.INSTANCE.getBattle() != null) {
			lcdButton(c, x + w - 98, y - 2, x + w - 58, y + 9, "Rival", mx, my, this::fillFromBattle);
		}
		box(c, atk ? atkField : defField);
		y = y0 + 36;
		if (sp != null) {
			String types = TypeEff.label(s.type1) + (s.type2 != null ? " / " + TypeEff.label(s.type2) : "");
			text(c, types, x, y, MID);
		}
		y += 11;
		stepper(c, x, y, "Nv", s.level, mx, my, d -> s.level = clamp(s.level + d, 1, 100), 1, 10);
		List<CalcData.NatureEntry> nats = CalcData.natures();
		int ni = atk ? S.atkNature : S.defNature;
		selector(c, x + 70, y, w - 70, s.natureName, mx, my, () -> stepNature(atk, -1), () -> stepNature(atk, 1));
		y += 14;
		List<CalcData.AbilityEntry> abs = sp == null ? List.of() : sp.abilities();
		int ai = atk ? S.atkAbility : S.defAbility;
		String abName = abs.isEmpty() ? "Habilidad: —" : abs.get(Math.min(ai, abs.size() - 1)).display();
		selector(c, x, y, w, abName, mx, my, () -> stepAbility(atk, -1), () -> stepAbility(atk, 1));
		y += 14;
		selector(c, x, y, w, "Objeto: " + s.item.label, mx, my,
				() -> s.item = DamageEngine.Item.values()[Math.floorMod(s.item.ordinal() - 1, DamageEngine.Item.values().length)],
				() -> s.item = DamageEngine.Item.values()[(s.item.ordinal() + 1) % DamageEngine.Item.values().length]);
		y += 16;
		boolean physical = S.move == null || S.move.category() != DamageEngine.Category.SPECIAL;
		if (atk) {
			int i = physical ? 1 : 3;
			statRow(c, x, y, w, s, i, mx, my);
			y += 14;
			stepper(c, x, y, "Etapa", s.atkStage, mx, my, d -> s.atkStage = clamp(s.atkStage + d, -6, 6), 1, 6);
			toggle(c, x + 80, y, "Quemado", s.burned, mx, my, () -> s.burned = !s.burned);
		} else {
			statRow(c, x, y, w, s, 0, mx, my);
			y += 14;
			statRow(c, x, y, w, s, physical ? 2 : 4, mx, my);
			y += 14;
			stepper(c, x, y, "Etapa", s.defStage, mx, my, d -> s.defStage = clamp(s.defStage + d, -6, 6), 1, 6);
		}
	}

	private void statRow(DrawContext c, int x, int y, int w, DamageEngine.Side s, int i, int mx, int my) {
		String label = CalcData.STAT_LABEL[i];
		text(c, label + " " + s.stat(i), x, y + 2, DARK);
		stepper(c, x + 66, y, "IV", s.ivs[i], mx, my, d -> s.ivs[i] = clamp(s.ivs[i] + d, 0, 31), 1, 31);
		stepper(c, x + 124, y, "EV", s.evs[i], mx, my, d -> s.evs[i] = clamp(s.evs[i] + d, 0, 252), 4, 252);
	}

	private void stepNature(boolean atk, int dir) {
		List<CalcData.NatureEntry> all = CalcData.natures();
		if (all.isEmpty()) return;
		int cur = atk ? S.atkNature : S.defNature;
		int n = Math.floorMod(cur + dir, all.size());
		setNature(atk, all.get(n));
	}

	private void stepAbility(boolean atk, int dir) {
		CalcData.SpeciesEntry sp = atk ? S.atkSp : S.defSp;
		if (sp == null || sp.abilities().isEmpty()) return;
		int n = Math.floorMod((atk ? S.atkAbility : S.defAbility) + dir, sp.abilities().size());
		if (atk) S.atkAbility = n;
		else S.defAbility = n;
		(atk ? S.atk : S.def).ability = sp.abilities().get(n).id();
	}

	private void drawResult(DrawContext c, int x, int y, int w, int h) {
		Draw.round(c, x, y, x + w, y + h, DARK);
		c.fill(x + 2, y + 2, x + w - 2, y + h - 2, LIGHT);
		if (S.atkSp == null || S.defSp == null || S.move == null) {
			text(c, "Elige atacante, defensor y movimiento.", x + 8, y + 8, DARK);
			text(c, "Pulsa \"Equipo ▸\" para usar tus Pokémon.", x + 8, y + 22, MID);
			return;
		}
		DamageEngine.Result r = DamageEngine.calc(S.atk, S.def, S.move.toMove(), S.crit, S.weather, S.screen, S.fullHp);
		if (r.note() != null && r.max() == 0) {
			text(c, r.note(), x + 8, y + 8, DARK);
			text(c, r.koText(), x + 8, y + 22, DARK);
			return;
		}
		String dmg = "DAÑO: " + r.min() + " - " + r.max() + " PS   (" + String.format("%.1f%% - %.1f%%", r.minPct(), r.maxPct()) + ")";
		c.getMatrices().push();
		c.getMatrices().translate(x + 8, y + 6, 0);
		c.getMatrices().scale(1.25f, 1.25f, 1f);
		text(c, Draw.fit(dmg, (int) ((w - 16) / 1.25f)), 0, 0, DARK);
		c.getMatrices().pop();
		String eff = r.effectiveness() == 1f ? "" : "  ·  " + TypeChart.describe(r.effectiveness()) + " (" + TypeChart.multiplierText(r.effectiveness()) + ")";
		text(c, r.koText() + eff, x + 8, y + 22, DARK);
		// Barra de PS del defensor tras el golpe
		int bx1 = x + 8, bx2 = x + w - 8, by = y + h - 12;
		Draw.round(c, bx1, by, bx2, by + 7, DARK);
		c.fill(bx1 + 1, by + 1, bx2 - 1, by + 6, BG);
		float leftMin = Math.max(0, 1 - r.maxPct() / 100f), leftMax = Math.max(0, 1 - r.minPct() / 100f);
		int fw = bx2 - bx1 - 2;
		c.fill(bx1 + 1, by + 1, bx1 + 1 + Math.round(fw * leftMax), by + 6, MID);
		c.fill(bx1 + 1, by + 1, bx1 + 1 + Math.round(fw * leftMin), by + 6, DARK);
	}

	private void drawSuggestions(DrawContext c, int mx, int my) {
		if (suggestFor == null || !suggestFor.isFocused() || suggestions.isEmpty()) return;
		int x = suggestFor.getX() - 2, y = suggestFor.getY() + 12, w = suggestFor.getWidth() + 4;
		c.getMatrices().push();
		c.getMatrices().translate(0, 0, 400);
		int h = suggestions.size() * 11 + 2;
		Draw.round(c, x, y, x + w, y + h, DARK);
		c.fill(x + 1, y + 1, x + w - 1, y + h - 1, LIGHT);
		for (int i = 0; i < suggestions.size(); i++) {
			Object o = suggestions.get(i);
			int ry = y + 1 + i * 11;
			boolean hover = mx >= x && mx < x + w && my >= ry && my < ry + 11;
			if (hover) c.fill(x + 1, ry, x + w - 1, ry + 11, BG);
			String label;
			if (o instanceof CalcData.MoveEntry m) {
				label = m.display() + "  " + TypeEff.label(m.type()) + " " + m.power();
			} else {
				CalcData.SpeciesEntry sp = (CalcData.SpeciesEntry) o;
				label = sp.display();
			}
			text(c, Draw.fit(label, w - 8), x + 4, ry + 2, DARK);
			hits.add(0, new Hit(x, ry, x + w, ry + 11, 0, () -> pick(o)));
		}
		c.getMatrices().pop();
	}

	// ---------------------------------------------------------------- Controles

	private interface IntStep {
		void apply(int delta);
	}

	private void text(DrawContext c, String s, int x, int y, int color) {
		c.drawText(textRenderer, s, x, y, color, false);
	}

	private void box(DrawContext c, TextFieldWidget f) {
		int x = f.getX() - 2, y = f.getY() - 2, w = f.getWidth() + 4;
		Draw.round(c, x, y, x + w, y + 13, DARK);
		c.fill(x + 1, y + 1, x + w - 1, y + 12, f.isFocused() ? 0xFFE8F8E0 : LIGHT);
		if (f.getText().isEmpty() && !f.isFocused()) text(c, "Buscar…", x + 3, y + 3, MID);
	}

	private void lcdButton(DrawContext c, int x1, int y1, int x2, int y2, String label, int mx, int my, Runnable action) {
		boolean hover = mx >= x1 && mx < x2 && my >= y1 && my < y2;
		Draw.round(c, x1, y1, x2, y2, DARK);
		c.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, hover ? LIGHT : BG);
		text(c, label, (x1 + x2) / 2 - textRenderer.getWidth(label) / 2, (y1 + y2) / 2 - 4, DARK);
		hits.add(new Hit(x1, y1, x2, y2, 0, () -> {
			beep(1.8f);
			action.run();
		}));
	}

	private void toggle(DrawContext c, int x, int y, String label, boolean on, int mx, int my, Runnable action) {
		Draw.round(c, x, y + 1, x + 9, y + 10, DARK);
		c.fill(x + 1, y + 2, x + 8, y + 9, on ? DARK : LIGHT);
		text(c, label, x + 12, y + 2, DARK);
		hits.add(new Hit(x, y, x + 14 + textRenderer.getWidth(label), y + 11, 0, () -> {
			beep(1.8f);
			action.run();
		}));
	}

	private void selector(DrawContext c, int x, int y, int w, String label, int mx, int my, Runnable prev, Runnable next) {
		lcdButton(c, x, y, x + 10, y + 11, "◀", mx, my, prev);
		lcdButton(c, x + w - 10, y, x + w, y + 11, "▶", mx, my, next);
		String s = Draw.fit(label, w - 26);
		text(c, s, x + w / 2 - textRenderer.getWidth(s) / 2, y + 2, DARK);
	}

	/** [-] valor [+]. Mayús + clic = paso grande. */
	private void stepper(DrawContext c, int x, int y, String label, int value, int mx, int my, IntStep step, int small, int big) {
		text(c, label, x, y + 2, DARK);
		int lx = x + textRenderer.getWidth(label) + 3;
		String v = String.valueOf(value);
		int vw = Math.max(16, textRenderer.getWidth(v) + 4);
		lcdButton(c, lx, y, lx + 9, y + 11, "-", mx, my, () -> step.apply(-(Screen.hasShiftDown() ? big : small)));
		text(c, v, lx + 10 + (vw - textRenderer.getWidth(v)) / 2, y + 2, DARK);
		lcdButton(c, lx + 10 + vw, y, lx + 19 + vw, y + 11, "+", mx, my, () -> step.apply(Screen.hasShiftDown() ? big : small));
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	private static void beep(float pitch) {
		MinecraftClient.getInstance().getSoundManager()
				.play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.4f));
	}

	// ---------------------------------------------------------------- Entrada

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button == 0) {
			for (Hit h : new ArrayList<>(hits)) {
				if (h.contains(mx, my)) {
					h.action().run();
					return true;
				}
			}
		}
		boolean r = super.mouseClicked(mx, my, button);
		if (getFocused() instanceof TextFieldWidget f) updateSuggestions(f);
		else suggestFor = null;
		return r;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER && suggestFor != null && !suggestions.isEmpty()) {
			pick(suggestions.get(0));
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}
}

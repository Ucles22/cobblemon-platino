package com.platino.calc;

import com.cobblemon.mod.common.api.abilities.PotentialAbility;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.text.Text;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Datos de la calculadora leídos de Cobblemon en el cliente. El servidor envía al conectar todas sus especies
 * y movimientos (también los custom), así que aquí aparecen solos.
 */
public final class CalcData {
	public static final Stats[] STAT_ORDER = {Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED};
	public static final String[] STAT_LABEL = {"PS", "Ataque", "Defensa", "At. Esp.", "Def. Esp.", "Velocidad"};

	public record SpeciesEntry(String display, String search, int[] base, String type1, String type2, List<AbilityEntry> abilities) {}

	public record AbilityEntry(String id, String display) {}

	public record MoveEntry(String display, String search, String type, DamageEngine.Category category, int power) {
		public DamageEngine.Move toMove() {
			return new DamageEngine.Move(display, type, category, power);
		}
	}

	public record NatureEntry(String display, int up, int down) {}

	private static List<SpeciesEntry> species = List.of();
	private static int speciesCount = -1;
	private static List<MoveEntry> moves = List.of();
	private static int movesCount = -1;
	private static List<NatureEntry> natures = List.of();

	private CalcData() {}

	public static String fold(String s) {
		String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return n.toLowerCase(Locale.ROOT);
	}

	static int statIndex(Stat s) {
		for (int i = 0; i < STAT_ORDER.length; i++) if (STAT_ORDER[i] == s) return i;
		return -1;
	}

	static int[] stats(Map<Stat, Integer> map) {
		int[] out = new int[6];
		for (int i = 0; i < 6; i++) {
			Integer v = map == null ? null : map.get(STAT_ORDER[i]);
			out[i] = v == null ? 1 : v;
		}
		return out;
	}

	static String typeId(ElementalType t) {
		return t == null ? null : t.getName().toLowerCase(Locale.ROOT);
	}

	static List<AbilityEntry> abilities(Iterable<PotentialAbility> pool) {
		List<AbilityEntry> out = new ArrayList<>();
		if (pool == null) return out;
		for (PotentialAbility pa : pool) {
			try {
				var t = pa.getTemplate();
				String id = t.getName();
				if (out.stream().anyMatch(a -> a.id().equals(id))) continue;
				out.add(new AbilityEntry(id, Text.translatable(t.getDisplayName()).getString()));
			} catch (Throwable ignored) {
			}
		}
		return out;
	}

	public static synchronized List<SpeciesEntry> species() {
		int count = PokemonSpecies.count();
		if (count == speciesCount) return species;
		List<SpeciesEntry> list = new ArrayList<>();
		for (Species s : PokemonSpecies.getSpecies()) {
			try {
				String name = s.getTranslatedName().getString();
				list.add(new SpeciesEntry(name, fold(name + " " + s.getName()), stats(s.getBaseStats()),
						typeId(s.getPrimaryType()), typeId(s.getSecondaryType()), abilities(s.getAbilities())));
				for (FormData f : s.getForms()) {
					String fn = f.getName();
					if (fn == null || fn.equalsIgnoreCase("normal")) continue;
					String display = name + " (" + fn + ")";
					list.add(new SpeciesEntry(display, fold(display + " " + s.getName()), stats(f.getBaseStats()),
							typeId(f.getPrimaryType()), typeId(f.getSecondaryType()), abilities(f.getAbilities())));
				}
			} catch (Throwable ignored) {
			}
		}
		list.sort(Comparator.comparing(SpeciesEntry::display, String.CASE_INSENSITIVE_ORDER));
		species = list;
		speciesCount = count;
		return species;
	}

	public static synchronized List<MoveEntry> moves() {
		int count = Moves.count();
		if (count == movesCount) return moves;
		List<MoveEntry> list = new ArrayList<>();
		for (MoveTemplate m : Moves.all()) {
			try {
				MoveEntry e = toEntry(m);
				if (e.category() != DamageEngine.Category.STATUS) list.add(e);
			} catch (Throwable ignored) {
			}
		}
		list.sort(Comparator.comparing(MoveEntry::display, String.CASE_INSENSITIVE_ORDER));
		moves = list;
		movesCount = count;
		return moves;
	}

	public static MoveEntry toEntry(MoveTemplate m) {
		var cat = m.getDamageCategory();
		DamageEngine.Category c = cat == DamageCategories.INSTANCE.getPHYSICAL() ? DamageEngine.Category.PHYSICAL
				: cat == DamageCategories.INSTANCE.getSPECIAL() ? DamageEngine.Category.SPECIAL : DamageEngine.Category.STATUS;
		String name = m.getDisplayName().getString();
		return new MoveEntry(name, fold(name + " " + m.getName()), typeId(m.getElementalType()), c, (int) Math.round(m.getPower()));
	}

	public static synchronized List<NatureEntry> natures() {
		if (!natures.isEmpty()) return natures;
		List<NatureEntry> list = new ArrayList<>();
		for (Nature n : Natures.all()) list.add(toEntry(n));
		list.sort(Comparator.comparing(NatureEntry::display, String.CASE_INSENSITIVE_ORDER));
		natures = list;
		return natures;
	}

	public static NatureEntry toEntry(Nature n) {
		return new NatureEntry(Text.translatable(n.getDisplayName()).getString(),
				n.getIncreasedStat() == null ? -1 : statIndex(n.getIncreasedStat()),
				n.getDecreasedStat() == null ? -1 : statIndex(n.getDecreasedStat()));
	}

	public static <T> List<T> search(List<T> all, java.util.function.Function<T, String> key, String query, int limit) {
		String q = fold(query).trim();
		List<T> starts = new ArrayList<>(), contains = new ArrayList<>();
		for (T t : all) {
			String k = key.apply(t);
			if (q.isEmpty() || k.startsWith(q)) starts.add(t);
			else if (k.contains(q)) contains.add(t);
			if (starts.size() >= limit) break;
		}
		starts.addAll(contains);
		return starts.subList(0, Math.min(limit, starts.size()));
	}
}

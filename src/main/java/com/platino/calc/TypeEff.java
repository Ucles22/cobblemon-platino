package com.platino.calc;

import com.platino.TypeChart;

import java.util.List;

/** Eficacia de tipos por nombre de tipo de Cobblemon ("fire", "water"…), usando la tabla del Pokéreloj. */
public final class TypeEff {
	public static final List<String> IDS = List.of("normal", "fire", "water", "grass", "electric", "ice", "fighting",
			"poison", "ground", "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy");

	private TypeEff() {}

	public static int index(String type) {
		return type == null ? -1 : IDS.indexOf(type.toLowerCase());
	}

	/** Nombre en español para mostrar ("FUEGO"); si el tipo no es estándar, el propio nombre. */
	public static String label(String type) {
		int i = index(type);
		return i >= 0 ? TypeChart.NAMES[i] : type == null ? "" : type.toUpperCase();
	}

	public static float multiplier(String attack, String def1, String def2) {
		int a = index(attack), d1 = index(def1), d2 = index(def2);
		if (a < 0 || d1 < 0) return 1f;
		return TypeChart.multiplier(a, d1, d2);
	}
}

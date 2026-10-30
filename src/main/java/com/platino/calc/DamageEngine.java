package com.platino.calc;

/**
 * Fórmula oficial de daño (generación 5 en adelante), sin dependencias de Cobblemon.
 * Los datos de especies y movimientos se rellenan desde el cliente (incluidas las especies custom del servidor).
 */
public final class DamageEngine {
	private DamageEngine() {}

	public enum Category { PHYSICAL, SPECIAL, STATUS }

	public enum Weather {
		NONE("Sin clima"), SUN("Sol"), RAIN("Lluvia");
		public final String label;
		Weather(String label) { this.label = label; }
	}

	public enum Terrain {
		NONE("Sin campo"), ELECTRIC("Campo Eléctrico"), PSYCHIC("Campo Psíquico"), GRASSY("Campo de Hierba"), MISTY("Campo de Niebla");
		public final String label;
		Terrain(String label) { this.label = label; }
	}

	public enum Screens {
		NONE("Sin pantallas"), REFLECT("Reflejo"), LIGHT_SCREEN("Pantalla Luz"), AURORA_VEIL("Velo Aurora");
		public final String label;
		Screens(String label) { this.label = label; }
	}

	/** Condiciones del combate. */
	public static final class Field {
		public boolean crit;
		public Weather weather = Weather.NONE;
		public Terrain terrain = Terrain.NONE;
		public Screens screens = Screens.NONE;
		/** % de PS actuales del defensor (1-100). */
		public int defHpPercent = 100;
	}

	/** Objetos con efecto en el daño (el servidor no tiene objetos custom). */
	public enum Item {
		NONE("Ninguno", null),
		CHOICE_BAND("Cinta Elección", "choice_band"),
		CHOICE_SPECS("Gafas Elección", "choice_specs"),
		LIFE_ORB("Vidasfera", "life_orb"),
		EXPERT_BELT("Cinta Experto", "expert_belt"),
		MUSCLE_BAND("Cinta Fuerte", "muscle_band"),
		WISE_GLASSES("Gafas Especiales", "wise_glasses"),
		ASSAULT_VEST("Chaleco Asalto", "assault_vest"),
		EVIOLITE("Mineral Evolutivo", "eviolite"),
		CHARCOAL("Carbón", "charcoal", "fire"),
		MYSTIC_WATER("Agua Mística", "mystic_water", "water"),
		MIRACLE_SEED("Semilla Milagro", "miracle_seed", "grass"),
		MAGNET("Imán", "magnet", "electric"),
		NEVER_MELT_ICE("Antiderretir", "never_melt_ice", "ice"),
		BLACK_BELT("Cinturón Negro", "black_belt", "fighting"),
		POISON_BARB("Flecha Venenosa", "poison_barb", "poison"),
		SOFT_SAND("Arena Fina", "soft_sand", "ground"),
		SHARP_BEAK("Pico Afilado", "sharp_beak", "flying"),
		TWISTED_SPOON("Cuchara Torcida", "twisted_spoon", "psychic"),
		SILVER_POWDER("Polvo Plata", "silver_powder", "bug"),
		HARD_STONE("Piedra Dura", "hard_stone", "rock"),
		SPELL_TAG("Hechizo", "spell_tag", "ghost"),
		DRAGON_FANG("Colmillo Dragón", "dragon_fang", "dragon"),
		BLACK_GLASSES("Gafas de Sol", "black_glasses", "dark"),
		METAL_COAT("Revestimiento Metálico", "metal_coat", "steel"),
		SILK_SCARF("Pañuelo Seda", "silk_scarf", "normal"),
		FAIRY_FEATHER("Pluma Hada", "fairy_feather", "fairy");

		public final String label, id, boostType;

		Item(String label, String id) {
			this(label, id, null);
		}

		Item(String label, String id, String boostType) {
			this.label = label;
			this.id = id;
			this.boostType = boostType;
		}

		/** Por el id del objeto de Cobblemon ("cobblemon:choice_band"). */
		public static Item fromId(String path) {
			if (path == null) return NONE;
			for (Item i : values()) if (path.equals(i.id)) return i;
			return NONE;
		}
	}

	/** Un Pokémon de la calculadora. Las estadísticas siguen el orden HP, Atk, Def, SpA, SpD, Spe. */
	public static final class Side {
		public String species = "";
		public int[] base = {50, 50, 50, 50, 50, 50};
		public String type1 = "normal", type2 = null;
		public int level = 100;
		public int[] ivs = {31, 31, 31, 31, 31, 31};
		public int[] evs = {0, 0, 0, 0, 0, 0};
		/** Estadística que sube / baja la naturaleza (índice 1-5) o -1. */
		public int natureUp = -1, natureDown = -1;
		public String natureName = "Seria";
		public String ability = "";
		public Item item = Item.NONE;
		public int atkStage = 0, defStage = 0;
		public boolean burned = false;

		public int stat(int i) {
			int core = (2 * base[i] + ivs[i] + evs[i] / 4) * level / 100;
			if (i == 0) return base[0] == 1 ? 1 : core + level + 10; // Shedinja
			double nat = i == natureUp && i != natureDown ? 1.1 : i == natureDown && i != natureUp ? 0.9 : 1.0;
			return (int) Math.floor((core + 5) * nat);
		}
	}

	public record Move(String name, String type, Category category, int power) {}

	public record Result(int min, int max, int defenderHp, int currentHp, float effectiveness, String note) {
		public float minPct() { return defenderHp <= 0 ? 0 : min * 100f / defenderHp; }
		public float maxPct() { return defenderHp <= 0 ? 0 : max * 100f / defenderHp; }

		/** "KO en 2 golpes", "Posible KO en 1"… (sobre los PS actuales). */
		public String koText() {
			if (max <= 0) return effectiveness == 0 ? "No le afecta" : "Sin daño";
			int hitsMax = (int) Math.ceil(currentHp / (double) max);
			int hitsMin = min <= 0 ? 99 : (int) Math.ceil(currentHp / (double) min);
			if (hitsMax == hitsMin) return hitsMax == 1 ? "¡KO de un golpe!" : "KO en " + hitsMax + " golpes";
			return "Posible KO en " + hitsMax + " (seguro en " + Math.min(hitsMin, 99) + ")";
		}
	}

	private static double stageMul(int s) {
		return s >= 0 ? (2 + s) / 2.0 : 2.0 / (2 - s);
	}

	private static int pokeRound(double v) {
		// Redondeo de los juegos: ,5 hacia abajo
		return (int) Math.ceil(v - 0.5);
	}

	public static Result calc(Side atk, Side def, Move move, Field f) {
		boolean crit = f.crit;
		Weather weather = f.weather;
		boolean defFullHp = f.defHpPercent >= 100;
		int hp = def.stat(0);
		int cur = Math.max(1, (int) Math.floor(hp * Math.max(1, Math.min(100, f.defHpPercent)) / 100.0));
		if (move == null || move.category() == Category.STATUS || move.power() <= 0) {
			return new Result(0, 0, hp, cur, 1f, "Movimiento de estado");
		}
		boolean physical = move.category() == Category.PHYSICAL;
		String mt = move.type();
		String aAb = norm(atk.ability), dAb = norm(def.ability);

		// Inmunidades por habilidad
		String immune = immunity(dAb, mt);
		if (immune != null) return new Result(0, 0, hp, cur, 0f, immune);

		float eff = TypeEff.multiplier(mt, def.type1, def.type2);
		if (eff == 0f) return new Result(0, 0, hp, cur, 0f, "Inmune por tipo");

		// Potencia
		double power = move.power();
		if (aAb.equals("technician") && power <= 60) power *= 1.5;
		if (atk.item == Item.MUSCLE_BAND && physical) power *= 1.1;
		if (atk.item == Item.WISE_GLASSES && !physical) power *= 1.1;
		if (atk.item.boostType != null && atk.item.boostType.equals(mt)) power *= 1.2;
		// Campos (se supone que ambos están en el suelo salvo tipo Volador o Levitación)
		boolean atkGrounded = !"flying".equals(atk.type1) && !"flying".equals(atk.type2) && !aAb.equals("levitate");
		boolean defGrounded = !"flying".equals(def.type1) && !"flying".equals(def.type2) && !dAb.equals("levitate");
		if (atkGrounded) {
			if (f.terrain == Terrain.ELECTRIC && mt.equals("electric")) power *= 5325 / 4096.0;
			if (f.terrain == Terrain.PSYCHIC && mt.equals("psychic")) power *= 5325 / 4096.0;
			if (f.terrain == Terrain.GRASSY && mt.equals("grass")) power *= 5325 / 4096.0;
		}
		if (defGrounded) {
			if (f.terrain == Terrain.MISTY && mt.equals("dragon")) power *= 0.5;
			String mn = norm(move.name());
			if (f.terrain == Terrain.GRASSY && (mn.equals("earthquake") || mn.equals("terremoto") || mn.equals("bulldoze")
					|| mn.equals("terratemblor") || mn.equals("magnitude") || mn.equals("magnitud"))) power *= 0.5;
		}
		power = Math.max(1, pokeRound(power));

		// Ataque
		int aIdx = physical ? 1 : 3, dIdx = physical ? 2 : 4;
		int aStage = crit ? Math.max(0, atk.atkStage) : atk.atkStage;
		int dStage = crit ? Math.min(0, def.defStage) : def.defStage;
		double a = Math.floor(atk.stat(aIdx) * stageMul(aStage));
		if (physical && (aAb.equals("hugepower") || aAb.equals("purepower"))) a *= 2;
		if (physical && aAb.equals("guts") && atk.burned) a *= 1.5;
		if (physical && atk.item == Item.CHOICE_BAND) a *= 1.5;
		if (!physical && atk.item == Item.CHOICE_SPECS) a *= 1.5;
		if (dAb.equals("thickfat") && (mt.equals("fire") || mt.equals("ice"))) a *= 0.5;
		a = Math.max(1, Math.floor(a));

		// Defensa
		double d = Math.floor(def.stat(dIdx) * stageMul(dStage));
		if (!physical && def.item == Item.ASSAULT_VEST) d *= 1.5;
		if (def.item == Item.EVIOLITE) d *= 1.5;
		d = Math.max(1, Math.floor(d));

		int base = (int) Math.floor(Math.floor(Math.floor(2.0 * atk.level / 5 + 2) * power * a / d) / 50) + 2;

		// Modificadores
		double mod = 1.0;
		if (weather == Weather.SUN) mod *= mt.equals("fire") ? 1.5 : mt.equals("water") ? 0.5 : 1;
		if (weather == Weather.RAIN) mod *= mt.equals("water") ? 1.5 : mt.equals("fire") ? 0.5 : 1;
		double baseW = Math.floor(base * mod);
		if (crit) baseW = Math.floor(baseW * 1.5);

		int min = Integer.MAX_VALUE, max = 0;
		for (int r = 85; r <= 100; r++) {
			double dmg = Math.floor(baseW * r / 100.0);
			boolean stab = mt.equals(atk.type1) || mt.equals(atk.type2);
			if (stab) dmg = Math.floor(dmg * (aAb.equals("adaptability") ? 2.0 : 1.5));
			dmg = Math.floor(dmg * eff);
			if (physical && atk.burned && !aAb.equals("guts")) dmg = Math.floor(dmg * 0.5);
			double other = 1.0;
			boolean screenHit = f.screens == Screens.AURORA_VEIL || (physical ? f.screens == Screens.REFLECT : f.screens == Screens.LIGHT_SCREEN);
			if (screenHit && !crit) other *= 0.5;
			if (eff > 1f && (dAb.equals("filter") || dAb.equals("solidrock") || dAb.equals("prismarmor"))) other *= 0.75;
			if (defFullHp && (dAb.equals("multiscale") || dAb.equals("shadowshield"))) other *= 0.5;
			if (eff > 1f && atk.item == Item.EXPERT_BELT) other *= 4915 / 4096.0;
			if (atk.item == Item.LIFE_ORB) other *= 5324 / 4096.0;
			dmg = pokeRound(dmg * other);
			int v = (int) Math.max(1, dmg);
			min = Math.min(min, v);
			max = Math.max(max, v);
		}
		return new Result(min, max, hp, cur, eff, null);
	}

	private static String immunity(String ab, String type) {
		return switch (ab) {
			case "levitate", "eartheater" -> type.equals("ground") ? "Inmune (habilidad)" : null;
			case "flashfire", "wellbakedbody" -> type.equals("fire") ? "Inmune (habilidad)" : null;
			case "waterabsorb", "stormdrain", "dryskin" -> type.equals("water") ? "Inmune (habilidad)" : null;
			case "voltabsorb", "lightningrod", "motordrive" -> type.equals("electric") ? "Inmune (habilidad)" : null;
			case "sapsipper" -> type.equals("grass") ? "Inmune (habilidad)" : null;
			default -> null;
		};
	}

	/** "Huge Power" / "huge_power" / "hugepower" -> "hugepower". */
	public static String norm(String s) {
		return s == null ? "" : s.toLowerCase().replaceAll("[^a-z]", "");
	}
}

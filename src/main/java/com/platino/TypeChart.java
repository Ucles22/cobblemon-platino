package com.platino;

/** Tabla de tipos (generación 6 en adelante, con Hada) para el indicador de movimientos del Pokéreloj. */
public final class TypeChart {
	public static final String[] NAMES = {"NORMAL", "FUEGO", "AGUA", "PLANTA", "ELÉCTRICO", "HIELO", "LUCHA", "VENENO",
			"TIERRA", "VOLADOR", "PSÍQUICO", "BICHO", "ROCA", "FANTASMA", "DRAGÓN", "SINIESTRO", "ACERO", "HADA"};

	private static final int NOR = 0, FUE = 1, AGU = 2, PLA = 3, ELE = 4, HIE = 5, LUC = 6, VEN = 7, TIE = 8,
			VOL = 9, PSI = 10, BIC = 11, ROC = 12, FAN = 13, DRA = 14, SIN = 15, ACE = 16, HAD = 17;
	private static final float[][] CHART = new float[18][18];

	static {
		for (float[] row : CHART) java.util.Arrays.fill(row, 1f);
		set(NOR, .5f, ROC, ACE); set(NOR, 0f, FAN);
		set(FUE, .5f, FUE, AGU, ROC, DRA); set(FUE, 2f, PLA, HIE, BIC, ACE);
		set(AGU, .5f, AGU, PLA, DRA); set(AGU, 2f, FUE, TIE, ROC);
		set(ELE, .5f, ELE, PLA, DRA); set(ELE, 2f, AGU, VOL); set(ELE, 0f, TIE);
		set(PLA, .5f, FUE, PLA, VEN, VOL, BIC, DRA, ACE); set(PLA, 2f, AGU, TIE, ROC);
		set(HIE, .5f, FUE, AGU, HIE, ACE); set(HIE, 2f, PLA, TIE, VOL, DRA);
		set(LUC, .5f, VEN, VOL, PSI, BIC, HAD); set(LUC, 2f, NOR, HIE, ROC, SIN, ACE); set(LUC, 0f, FAN);
		set(VEN, .5f, VEN, TIE, ROC, FAN); set(VEN, 2f, PLA, HAD); set(VEN, 0f, ACE);
		set(TIE, .5f, PLA, BIC); set(TIE, 2f, FUE, ELE, VEN, ROC, ACE); set(TIE, 0f, VOL);
		set(VOL, .5f, ELE, ROC, ACE); set(VOL, 2f, PLA, LUC, BIC);
		set(PSI, .5f, PSI, ACE); set(PSI, 2f, LUC, VEN); set(PSI, 0f, SIN);
		set(BIC, .5f, FUE, LUC, VEN, VOL, FAN, ACE, HAD); set(BIC, 2f, PLA, PSI, SIN);
		set(ROC, .5f, LUC, TIE, ACE); set(ROC, 2f, FUE, HIE, VOL, BIC);
		set(FAN, .5f, SIN); set(FAN, 2f, PSI, FAN); set(FAN, 0f, NOR);
		set(DRA, .5f, ACE); set(DRA, 2f, DRA); set(DRA, 0f, HAD);
		set(SIN, .5f, LUC, SIN, HAD); set(SIN, 2f, PSI, FAN);
		set(ACE, .5f, FUE, AGU, ELE, ACE); set(ACE, 2f, HIE, ROC, HAD);
		set(HAD, .5f, FUE, VEN, ACE); set(HAD, 2f, LUC, DRA, SIN);
	}

	private TypeChart() {}

	private static void set(int attack, float value, int... defenders) {
		for (int d : defenders) CHART[attack][d] = value;
	}

	/** @param def2 -1 si el defensor solo tiene un tipo. */
	public static float multiplier(int attack, int def1, int def2) {
		float m = CHART[attack][def1];
		if (def2 >= 0 && def2 != def1) m *= CHART[attack][def2];
		return m;
	}

	public static String describe(float m) {
		if (m == 0f) return "No afecta";
		if (m >= 4f) return "¡Súper eficaz!";
		if (m >= 2f) return "Muy eficaz";
		if (m <= 0.25f) return "Muy poco eficaz";
		if (m < 1f) return "Poco eficaz";
		return "Normal";
	}

	public static String multiplierText(float m) {
		if (m == 0f) return "x0";
		if (m == 0.25f) return "x¼";
		if (m == 0.5f) return "x½";
		return "x" + (int) m;
	}
}

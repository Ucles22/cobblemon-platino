package com.platino;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.MinecraftClient;

/** config/cobbleplatino.json */
public class PlatinoConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final TypeToken<PlatinoConfig> TYPE = TypeToken.get(PlatinoConfig.class);
	private static final JsonFileStore STORE = new JsonFileStore("cobbleplatino.json");
	private static PlatinoConfig instance = new PlatinoConfig();

	/** Cajas de vida estilo Platino en combate. */
	public boolean battleHud = true;
	/** Menú LUCHAR/BOLSA/HUIR/POKéMON estilo DS. /platino combate menu on|off */
	public boolean battleMenu = true;
	/** Cuadro de texto de combate estilo DS. /platino combate texto on|off */
	public boolean battleText = true;
	/** Pitido de vida baja en combate. /platino combate pitido on|off */
	public boolean lowHpBeep = true;
	/** BOLSA del menú de combate abre la Bolsa DPPt. /platino combate bolsa on|off */
	public boolean battleBag = true;
	/** Posición del menú DS: "izquierda" o "derecha". Tamaño: 0.5 - 1.5. */
	public String battleMenuSide = "izquierda";
	public float battleMenuScale = 0.8f;
	public int battleMenuOffsetX = 0;
	public int battleMenuOffsetY = 0;
	/** Barra de equipo estilo Platino (sustituye la de Cobblemon). */
	public boolean partyHud = true;

	/** Pokéreloj: última aplicación abierta, pasos y días marcados en el calendario. */
	public int poketchApp = 0;
	public long poketchSteps = 0;
	public java.util.List<String> poketchMarks = new java.util.ArrayList<>();
	/** Inventario de Minecraft estilo Platino. */
	public boolean inventoryTheme = true;
	/** Recolorear PC, Resumen, equipo y botones de combate de Cobblemon (reinicia para aplicar). */
	public boolean screensTheme = true;
	/** Pantalla de datos del Pokémon estilo Diamante/Perla en vez del Resumen de Cobblemon (/platino resumen). */
	public boolean dppSummary = true;
	/** PC estilo Diamante/Perla en vez del de Cobblemon (/platino pc). */
	public boolean dppPc = true;
	/** Paleta de las pantallas de Cobblemon: diamante, perla, platino u original. Cambiar con /platino paleta. */
	public String palette = "diamante";

	/** Posición de la barra de equipo: desplazamiento desde el borde izquierdo / centro vertical. */
	public int partyOffsetX = 0;
	public int partyOffsetY = 0;
	/** Barra de equipo más pequeña (sin retrato). */
	public boolean partyCompact = false;

	/** Caja propia: arriba a la izquierda. Caja del rival: arriba a la derecha. Desplazamientos en píxeles. */
	public int foeOffsetX = 0;
	public int foeOffsetY = 0;
	public int allyOffsetX = 0;
	public int allyOffsetY = 0;

	public static PlatinoConfig get() {
		return instance;
	}

	public static void load() {
		instance = sanitize(STORE.readNow(GSON, TYPE));
		save();
	}

	public static void reload(Runnable onApplied) {
		STORE.read(GSON, TYPE).thenAcceptAsync(loaded -> {
			instance = sanitize(loaded);
			save();
			onApplied.run();
		}, MinecraftClient.getInstance());
	}

	public static void save() {
		STORE.write(GSON.toJson(instance));
	}

	private static PlatinoConfig sanitize(PlatinoConfig loaded) {
		PlatinoConfig defaults = new PlatinoConfig();
		if (loaded == null) return defaults;
		if (loaded.palette == null) loaded.palette = defaults.palette;
		if (loaded.battleMenuSide == null) loaded.battleMenuSide = defaults.battleMenuSide;
		if (loaded.poketchMarks == null) loaded.poketchMarks = new java.util.ArrayList<>();
		return loaded;
	}
}

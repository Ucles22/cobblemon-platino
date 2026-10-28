package com.platino;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** config/cobbleplatino.json */
public class PlatinoConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static PlatinoConfig instance = new PlatinoConfig();

	/** Cajas de vida estilo Platino en combate. */
	public boolean battleHud = true;
	/** Barra de equipo estilo Platino (sustituye la de Cobblemon). */
	public boolean partyHud = true;
	/** Inventario de Minecraft estilo Platino. */
	public boolean inventoryTheme = true;

	/** Posición de la barra de equipo: desplazamiento desde el borde izquierdo / centro vertical. */
	public int partyOffsetX = 0;
	public int partyOffsetY = 0;
	/** Barra de equipo más pequeña (sin retrato). */
	public boolean partyCompact = false;

	/** Caja del rival: arriba a la izquierda. Caja propia: a la derecha. Desplazamientos en píxeles. */
	public int foeOffsetX = 0;
	public int foeOffsetY = 0;
	public int allyOffsetX = 0;
	public int allyOffsetY = 0;

	public static PlatinoConfig get() {
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("cobbleplatino.json");
	}

	public static void load() {
		PlatinoConfig cfg = null;
		if (Files.exists(path())) {
			try (Reader r = Files.newBufferedReader(path(), StandardCharsets.UTF_8)) {
				cfg = GSON.fromJson(r, PlatinoConfig.class);
			} catch (Exception e) {
				PlatinoClient.LOGGER.error("[Cobblemon Platino] cobbleplatino.json no válido, usando valores por defecto", e);
			}
		}
		instance = cfg != null ? cfg : new PlatinoConfig();
		save();
	}

	public static void save() {
		try {
			Files.createDirectories(path().getParent());
			try (Writer w = Files.newBufferedWriter(path(), StandardCharsets.UTF_8)) {
				GSON.toJson(instance, w);
			}
		} catch (Exception e) {
			PlatinoClient.LOGGER.error("[Cobblemon Platino] No se pudo guardar la configuración", e);
		}
	}
}

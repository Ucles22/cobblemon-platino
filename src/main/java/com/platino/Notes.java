package com.platino;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Notas por Pokémon (por UUID), guardadas en config/cobbleplatino_notas.json. Solo en tu PC. */
public final class Notes {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static Map<String, String> notes = new LinkedHashMap<>();

	private Notes() {}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("cobbleplatino_notas.json");
	}

	public static void load() {
		if (!Files.exists(path())) return;
		try (Reader r = Files.newBufferedReader(path(), StandardCharsets.UTF_8)) {
			Map<String, String> m = GSON.fromJson(r, new TypeToken<LinkedHashMap<String, String>>() {}.getType());
			if (m != null) notes = m;
		} catch (Exception e) {
			PlatinoClient.LOGGER.error("[Cobblemon Platino] No se pudieron leer las notas", e);
		}
	}

	private static void save() {
		try {
			Files.createDirectories(path().getParent());
			try (Writer w = Files.newBufferedWriter(path(), StandardCharsets.UTF_8)) {
				GSON.toJson(notes, w);
			}
		} catch (Exception e) {
			PlatinoClient.LOGGER.error("[Cobblemon Platino] No se pudieron guardar las notas", e);
		}
	}

	public static String get(UUID id) {
		return id == null ? "" : notes.getOrDefault(id.toString(), "");
	}

	public static boolean has(UUID id) {
		return id != null && !get(id).isBlank();
	}

	public static void set(UUID id, String text) {
		if (id == null) return;
		if (text == null || text.isBlank()) notes.remove(id.toString());
		else notes.put(id.toString(), text.strip());
		save();
	}
}

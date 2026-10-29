package com.platino;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Notas por Pokémon (por UUID), guardadas en config/cobbleplatino_notas.json. Solo en tu PC. */
public final class Notes {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final TypeToken<LinkedHashMap<String, String>> STORED_TYPE = new TypeToken<LinkedHashMap<String, String>>() {};
	private static final JsonFileStore STORE = new JsonFileStore("cobbleplatino_notas.json");
	private static final Map<UUID, String> notes = new LinkedHashMap<>();

	private Notes() {}

	public static void load() {
		Map<String, String> stored = STORE.readNow(GSON, STORED_TYPE);
		if (stored == null) return;
		notes.clear();
		stored.forEach(Notes::putStored);
	}

	private static void putStored(String key, String text) {
		if (text == null || text.isBlank()) return;
		try {
			notes.put(UUID.fromString(key), text);
		} catch (IllegalArgumentException e) {
			PlatinoClient.LOGGER.warn("[Cobblemon Platino] Nota con id no válido, se ignora: {}", key);
		}
	}

	private static void save() {
		Map<String, String> stored = new LinkedHashMap<>();
		notes.forEach((id, text) -> stored.put(id.toString(), text));
		STORE.write(GSON.toJson(stored));
	}

	public static String get(UUID id) {
		return id == null ? "" : notes.getOrDefault(id, "");
	}

	public static boolean has(UUID id) {
		return id != null && notes.containsKey(id);
	}

	public static void set(UUID id, String text) {
		if (id == null) return;
		if (text == null || text.isBlank()) notes.remove(id);
		else notes.put(id, text.strip());
		save();
	}
}

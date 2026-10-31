package com.platino;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.MinecraftClient;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Traducción del mod: el texto se escribe en español en el código y, si el idioma del cliente no es español,
 * se busca su versión inglesa en assets/cobbleplatino/i18n/en.json. Solo hay español e inglés.
 */
public final class Tr {
	private static Map<String, String> en;

	private Tr() {}

	/** true si el cliente está en cualquier variante de español (es_es, es_mx, es_ar…). */
	public static boolean isEs() {
		try {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc == null || mc.getLanguageManager() == null) return true;
			String lang = mc.getLanguageManager().getLanguage();
			return lang == null || lang.toLowerCase().startsWith("es");
		} catch (Throwable t) {
			return true;
		}
	}

	/** Texto en el idioma del cliente (el argumento es el texto en español). */
	public static String t(String es) {
		if (es == null || isEs()) return es;
		String v = english().get(es);
		return v == null ? es : v;
	}

	private static synchronized Map<String, String> english() {
		if (en != null) return en;
		Map<String, String> m = new HashMap<>();
		try (InputStream in = Tr.class.getResourceAsStream("/assets/cobbleplatino/i18n/en.json")) {
			if (in != null) {
				Map<String, String> loaded = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8),
						new TypeToken<Map<String, String>>() {}.getType());
				if (loaded != null) m.putAll(loaded);
			}
		} catch (Exception e) {
			PlatinoClient.LOGGER.warn("[Cobblemon Platino] No se pudo cargar la traducción inglesa", e);
		}
		en = m;
		return en;
	}
}

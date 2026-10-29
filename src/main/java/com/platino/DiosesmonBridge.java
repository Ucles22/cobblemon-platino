package com.platino;

import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Lectura opcional del mod "Diosesmon Client" (marcador y medallas) por reflexión,
 * para no depender de él: si no está instalado, simplemente no devuelve nada.
 */
public final class DiosesmonBridge {
	public record Medal(Identifier sprite, boolean owned) {}

	private static boolean checked, present;
	private static Method getLines, lineText, getSnapshot, snapIds, snapLocations, snapOwned, activeIndices;

	private DiosesmonBridge() {}

	private static void init() {
		if (checked) return;
		checked = true;
		try {
			ClassLoader cl = DiosesmonBridge.class.getClassLoader();
			Class<?> state = Class.forName("net.diosesmon.client.scoreboard.state.ScoreboardClientState", true, cl);
			getLines = state.getMethod("getLines");
			lineText = Class.forName("net.diosesmon.client.scoreboard.state.ScoreboardLine", true, cl).getMethod("text");
			present = true;
			try {
				Class<?> medal = Class.forName("net.diosesmon.client.scoreboard.state.MedalClientState", true, cl);
				getSnapshot = medal.getMethod("getSnapshot");
				Class<?> snap = Class.forName("net.diosesmon.client.scoreboard.state.MedalClientState$Snapshot", true, cl);
				snapIds = snap.getMethod("ids");
				snapLocations = snap.getMethod("locations");
				snapOwned = snap.getMethod("owned");
				try {
					Class<?> cat = Class.forName("net.diosesmon.main.medallas.MedalCatalog", true, cl);
					activeIndices = cat.getMethod("activeGenerationIndices", List.class, List.class);
				} catch (Throwable ignored) {
				}
			} catch (Throwable ignored) {
			}
			PlatinoClient.LOGGER.info("[Cobblemon Platino] Diosesmon Client detectado: se usará su marcador");
		} catch (Throwable t) {
			present = false;
		}
	}

	public static boolean isPresent() {
		init();
		return present;
	}

	/** Líneas del marcador lateral de DiosesMon ("DINERO: 227.945", "CLAN: (STAFF)", …). */
	public static List<Text> scoreboardLines() {
		init();
		List<Text> out = new ArrayList<>();
		if (!present) return out;
		try {
			Object list = getLines.invoke(null);
			if (list instanceof List<?> l) {
				for (Object line : l) {
					Object t = lineText.invoke(line);
					if (t instanceof Text tx) out.add(tx);
				}
			}
		} catch (Throwable ignored) {
		}
		return out;
	}

	/** Medallas de la generación activa, con si las tienes o no. */
	public static List<Medal> medals() {
		init();
		List<Medal> out = new ArrayList<>();
		if (getSnapshot == null) return out;
		try {
			Object snap = getSnapshot.invoke(null);
			if (snap == null) return out;
			List<?> ids = (List<?>) snapIds.invoke(snap);
			List<?> locs = (List<?>) snapLocations.invoke(snap);
			List<?> owned = (List<?>) snapOwned.invoke(snap);
			List<?> indices = null;
			if (activeIndices != null) {
				try {
					indices = (List<?>) activeIndices.invoke(null, ids, owned);
				} catch (Throwable ignored) {
				}
			}
			if (indices == null || indices.isEmpty()) {
				List<Integer> all = new ArrayList<>();
				for (int i = 0; i < locs.size(); i++) all.add(i);
				indices = all;
			}
			for (Object o : indices) {
				int i = ((Number) o).intValue();
				if (i < 0 || i >= locs.size()) continue;
				Object loc = locs.get(i);
				if (loc instanceof Identifier id) {
					out.add(new Medal(id, i < owned.size() && Boolean.TRUE.equals(owned.get(i))));
				}
			}
		} catch (Throwable ignored) {
		}
		return out;
	}
}

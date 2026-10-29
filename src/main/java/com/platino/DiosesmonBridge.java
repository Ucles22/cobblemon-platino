package com.platino;

import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lectura opcional del mod "Diosesmon Client" (marcador y medallas) por reflexión,
 * para no depender de él: si no está instalado, simplemente no devuelve nada.
 */
public final class DiosesmonBridge {
	public record Medal(Identifier sprite, boolean owned) {}

	private static final String STATE_PACKAGE = "net.diosesmon.client.scoreboard.state.";
	private static final String MEDAL_CATALOG = "net.diosesmon.main.medallas.MedalCatalog";
	private static final Set<String> reportedFailures = new HashSet<>();
	private static Object cachedLinesSource;
	private static List<Text> cachedLines = List.of();
	private static Object cachedMedalSnapshot;
	private static List<Medal> cachedMedals = List.of();

	private DiosesmonBridge() {}

	private static final class ScoreboardHandles {
		static final MethodHandle GET_LINES;
		static final MethodHandle LINE_TEXT;

		static {
			MethodHandle getLines = null, lineText = null;
			try {
				getLines = generic(load(STATE_PACKAGE + "ScoreboardClientState").getMethod("getLines"));
				lineText = generic(load(STATE_PACKAGE + "ScoreboardLine").getMethod("text"));
				PlatinoClient.LOGGER.info("[Cobblemon Platino] Diosesmon Client detectado: se usará su marcador");
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				getLines = null;
				lineText = null;
			}
			GET_LINES = getLines;
			LINE_TEXT = lineText;
		}
	}

	private static final class MedalHandles {
		static final MethodHandle GET_SNAPSHOT;
		static final MethodHandle SNAPSHOT_IDS;
		static final MethodHandle SNAPSHOT_LOCATIONS;
		static final MethodHandle SNAPSHOT_OWNED;
		static final MethodHandle ACTIVE_INDICES;

		static {
			MethodHandle getSnapshot = null, ids = null, locations = null, owned = null, activeIndices = null;
			try {
				getSnapshot = generic(load(STATE_PACKAGE + "MedalClientState").getMethod("getSnapshot"));
				Class<?> snapshot = load(STATE_PACKAGE + "MedalClientState$Snapshot");
				ids = generic(snapshot.getMethod("ids"));
				locations = generic(snapshot.getMethod("locations"));
				owned = generic(snapshot.getMethod("owned"));
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				getSnapshot = null;
			}
			try {
				activeIndices = generic(load(MEDAL_CATALOG).getMethod("activeGenerationIndices", List.class, List.class));
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				activeIndices = null;
			}
			GET_SNAPSHOT = getSnapshot;
			SNAPSHOT_IDS = ids;
			SNAPSHOT_LOCATIONS = locations;
			SNAPSHOT_OWNED = owned;
			ACTIVE_INDICES = activeIndices;
		}
	}

	private static Class<?> load(String name) throws ClassNotFoundException {
		return Class.forName(name, true, DiosesmonBridge.class.getClassLoader());
	}

	private static MethodHandle generic(Method method) throws IllegalAccessException {
		MethodHandle handle = MethodHandles.lookup().unreflect(method);
		return handle.asType(MethodType.genericMethodType(handle.type().parameterCount()));
	}

	public static boolean isPresent() {
		return ScoreboardHandles.GET_LINES != null;
	}

	/** Líneas del marcador lateral de DiosesMon ("DINERO: 227.945", "CLAN: (STAFF)", …). */
	public static List<Text> scoreboardLines() {
		if (!isPresent()) return List.of();
		try {
			Object source = (Object) ScoreboardHandles.GET_LINES.invokeExact();
			if (source != cachedLinesSource) {
				cachedLines = readLines(source);
				cachedLinesSource = source;
			}
		} catch (Throwable t) {
			reportFailure("marcador", t);
			cachedLinesSource = null;
			cachedLines = List.of();
		}
		return cachedLines;
	}

	private static List<Text> readLines(Object source) throws Throwable {
		if (!(source instanceof List<?> rawLines)) return List.of();
		List<Text> out = new ArrayList<>(rawLines.size());
		for (Object line : rawLines) {
			Object text = (Object) ScoreboardHandles.LINE_TEXT.invokeExact(line);
			if (text instanceof Text t) out.add(t);
		}
		return List.copyOf(out);
	}

	/** Medallas de la generación activa, con si las tienes o no. */
	public static List<Medal> medals() {
		if (MedalHandles.GET_SNAPSHOT == null) return List.of();
		try {
			Object snapshot = (Object) MedalHandles.GET_SNAPSHOT.invokeExact();
			if (snapshot != cachedMedalSnapshot) {
				cachedMedals = snapshot == null ? List.of() : readMedals(snapshot);
				cachedMedalSnapshot = snapshot;
			}
		} catch (Throwable t) {
			reportFailure("medallas", t);
			cachedMedalSnapshot = null;
			cachedMedals = List.of();
		}
		return cachedMedals;
	}

	private static List<Medal> readMedals(Object snapshot) throws Throwable {
		List<?> ids = (List<?>) (Object) MedalHandles.SNAPSHOT_IDS.invokeExact(snapshot);
		List<?> locations = (List<?>) (Object) MedalHandles.SNAPSHOT_LOCATIONS.invokeExact(snapshot);
		List<?> owned = (List<?>) (Object) MedalHandles.SNAPSHOT_OWNED.invokeExact(snapshot);
		List<Medal> out = new ArrayList<>();
		for (int index : activeIndices(ids, owned, locations.size())) {
			if (index < 0 || index >= locations.size()) continue;
			if (locations.get(index) instanceof Identifier sprite) {
				out.add(new Medal(sprite, index < owned.size() && Boolean.TRUE.equals(owned.get(index))));
			}
		}
		return List.copyOf(out);
	}

	private static List<Integer> activeIndices(List<?> ids, List<?> owned, int count) {
		if (MedalHandles.ACTIVE_INDICES != null) {
			try {
				Object result = (Object) MedalHandles.ACTIVE_INDICES.invokeExact((Object) ids, (Object) owned);
				if (result instanceof List<?> indices && !indices.isEmpty()) {
					List<Integer> out = new ArrayList<>(indices.size());
					for (Object index : indices) out.add(((Number) index).intValue());
					return out;
				}
			} catch (Throwable t) {
				reportFailure("generación de medallas", t);
			}
		}
		List<Integer> all = new ArrayList<>(count);
		for (int i = 0; i < count; i++) all.add(i);
		return all;
	}

	private static void reportFailure(String feature, Throwable t) {
		if (reportedFailures.add(feature)) {
			PlatinoClient.LOGGER.warn("[Cobblemon Platino] No se pudo leer {} de Diosesmon Client", feature, t);
		}
	}
}

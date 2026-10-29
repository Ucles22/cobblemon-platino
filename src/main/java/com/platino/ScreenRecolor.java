package com.platino;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceReloader;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.profiler.Profiler;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Recolorea en memoria (al cargar recursos) las texturas de interfaz de Cobblemon (PC, resumen, equipo, combate)
 * con una paleta de la 4ª generación (Diamante, Perla o Platino). No modifica ni redistribuye ningún archivo de Cobblemon.
 */
public final class ScreenRecolor implements IdentifiableResourceReloadListener {
	private static final List<String> FOLDERS = List.of("textures/gui/summary", "textures/gui/pc",
			"textures/gui/party", "textures/gui/battle", "textures/gui/interact");
	private static final List<String> SKIP = List.of("type", "status", "ball", "gender", "shiny", "pokedex",
			"marking", "wallpaper", "icon", "portrait", "overlay", "pentagon", "move_", "summary_move.", "glow");
	private static final List<Identifier> replaced = new ArrayList<>();
	private static final AtomicInteger latestRequest = new AtomicInteger();

	/** Rampas de color por luminosidad (oscuro -> claro). */
	private static final Map<String, int[]> PALETTES = Map.of(
			"diamante", new int[]{0x0C1420, 0x18284A, 0x24406E, 0x3A64A0, 0x78A8D8, 0xC8E0F4, 0xF8FCFF},
			"perla", new int[]{0x1A0C14, 0x40182E, 0x642846, 0x96436E, 0xD088AC, 0xF2CCDE, 0xFFF8FC},
			"platino", new int[]{0x121212, 0x2A2826, 0x46423A, 0x6E6650, 0xB8A060, 0xEADCA8, 0xFFFDF0});
	private static final float[] STOPS = {0f, 0.12f, 0.22f, 0.35f, 0.55f, 0.78f, 1f};

	private record Request(int id, String palette, int[] ramp) {}

	private record Recolored(Request request, Map<Identifier, NativeImage> images) {}

	@Override
	public Identifier getFabricId() {
		return Identifier.of(PlatinoClient.MOD_ID, "recolor");
	}

	@Override
	public Collection<Identifier> getFabricDependencies() {
		return List.of(ResourceReloadListenerKeys.TEXTURES);
	}

	@Override
	public CompletableFuture<Void> reload(ResourceReloader.Synchronizer synchronizer, ResourceManager manager,
										  Profiler prepareProfiler, Profiler applyProfiler,
										  Executor prepareExecutor, Executor applyExecutor) {
		Request request = newRequest();
		CompletableFuture<Recolored> prepared = CompletableFuture.supplyAsync(() -> prepare(manager, request), prepareExecutor);
		return prepared.thenCompose(synchronizer::whenPrepared)
				.thenAcceptAsync(ScreenRecolor::apply, applyExecutor)
				.whenComplete((ignored, error) -> {
					if (error != null) prepared.thenAccept(ScreenRecolor::release);
				});
	}

	public static void reapply() {
		MinecraftClient client = MinecraftClient.getInstance();
		Request request = newRequest();
		ResourceManager manager = client.getResourceManager();
		CompletableFuture.supplyAsync(() -> prepare(manager, request), Util.getMainWorkerExecutor())
				.thenAcceptAsync(ScreenRecolor::apply, client)
				.exceptionally(ScreenRecolor::logReapplyFailure);
	}

	private static Request newRequest() {
		PlatinoConfig cfg = PlatinoConfig.get();
		int[] ramp = cfg.screensTheme ? PALETTES.get(cfg.palette) : null;
		return new Request(latestRequest.incrementAndGet(), cfg.palette, ramp);
	}

	private static Recolored prepare(ResourceManager manager, Request request) {
		Map<Identifier, NativeImage> images = new LinkedHashMap<>();
		if (request.ramp() == null) return new Recolored(request, images);
		for (String folder : FOLDERS) {
			Map<Identifier, Resource> found = manager.findResources(folder,
					id -> id.getNamespace().equals("cobblemon") && id.getPath().endsWith(".png"));
			for (Map.Entry<Identifier, Resource> e : found.entrySet()) {
				Identifier id = e.getKey();
				String path = id.getPath();
				String file = path.substring(path.lastIndexOf('/') + 1).toLowerCase();
				if (SKIP.stream().anyMatch(file::contains)) continue;
				NativeImage img = readRecolored(id, e.getValue(), request.ramp());
				if (img != null) images.put(id, img);
			}
		}
		return new Recolored(request, images);
	}

	private static NativeImage readRecolored(Identifier id, Resource resource, int[] ramp) {
		NativeImage img = null;
		try (InputStream in = resource.getInputStream()) {
			img = NativeImage.read(in);
			recolor(img, ramp);
			return img;
		} catch (IOException | RuntimeException ex) {
			if (img != null) img.close();
			PlatinoClient.LOGGER.debug("[Cobblemon Platino] No se pudo recolorear {}", id, ex);
			return null;
		}
	}

	private static void apply(Recolored result) {
		if (result.request().id() != latestRequest.get()) {
			release(result);
			return;
		}
		TextureManager tm = MinecraftClient.getInstance().getTextureManager();
		// Restaurar las originales (se recargarán desde Cobblemon cuando se usen)
		for (Identifier id : replaced) tm.destroyTexture(id);
		replaced.clear();

		int done = 0;
		for (Map.Entry<Identifier, NativeImage> e : result.images().entrySet()) {
			try {
				tm.registerTexture(e.getKey(), new RecoloredTexture(e.getValue()));
				replaced.add(e.getKey());
				done++;
			} catch (RuntimeException ex) {
				e.getValue().close();
				PlatinoClient.LOGGER.warn("[Cobblemon Platino] No se pudo registrar la textura recoloreada {}", e.getKey(), ex);
			}
		}
		if (result.request().ramp() != null) {
			PlatinoClient.LOGGER.info("[Cobblemon Platino] {} texturas recoloreadas (paleta {})", done, result.request().palette());
		}
	}

	private static void release(Recolored result) {
		for (NativeImage img : result.images().values()) img.close();
	}

	private static Void logReapplyFailure(Throwable error) {
		PlatinoClient.LOGGER.error("[Cobblemon Platino] No se pudo aplicar la paleta", error);
		return null;
	}

	/** Solo cambia píxeles grises (la interfaz de Cobblemon); los colores (tipos, iconos) se quedan. */
	static void recolor(NativeImage img, int[] ramp) {
		float[] hsv = new float[3];
		for (int yy = 0; yy < img.getHeight(); yy++) {
			for (int xx = 0; xx < img.getWidth(); xx++) {
				int abgr = img.getColor(xx, yy);
				int a = (abgr >>> 24) & 255;
				if (a == 0) continue;
				int b = (abgr >> 16) & 255, g = (abgr >> 8) & 255, r = abgr & 255;
				rgbToHsv(r, g, b, hsv);
				if (hsv[1] > 0.18f) continue;
				float lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f;
				int rgb = sample(ramp, lum);
				int nr = (rgb >> 16) & 255, ng = (rgb >> 8) & 255, nb = rgb & 255;
				img.setColor(xx, yy, (a << 24) | (nb << 16) | (ng << 8) | nr);
			}
		}
	}

	private static int sample(int[] ramp, float t) {
		for (int i = 1; i < STOPS.length; i++) {
			if (t <= STOPS[i]) {
				float f = (t - STOPS[i - 1]) / (STOPS[i] - STOPS[i - 1]);
				return Draw.lerpColor(0xFF000000 | ramp[i - 1], 0xFF000000 | ramp[i], f) & 0xFFFFFF;
			}
		}
		return ramp[ramp.length - 1];
	}

	private static void rgbToHsv(int r, int g, int b, float[] out) {
		float rf = r / 255f, gf = g / 255f, bf = b / 255f;
		float max = Math.max(rf, Math.max(gf, bf)), min = Math.min(rf, Math.min(gf, bf));
		float d = max - min, h;
		if (d == 0) h = 0;
		else if (max == rf) h = 60 * (((gf - bf) / d) % 6);
		else if (max == gf) h = 60 * (((bf - rf) / d) + 2);
		else h = 60 * (((rf - gf) / d) + 4);
		if (h < 0) h += 360;
		out[0] = h;
		out[1] = max == 0 ? 0 : d / max;
		out[2] = max;
	}

	private static int hsvToRgb(float h, float s, float v) {
		float c = v * s, x = c * (1 - Math.abs((h / 60f) % 2 - 1)), m = v - c, r, g, b;
		if (h < 60) { r = c; g = x; b = 0; }
		else if (h < 120) { r = x; g = c; b = 0; }
		else if (h < 180) { r = 0; g = c; b = x; }
		else if (h < 240) { r = 0; g = x; b = c; }
		else if (h < 300) { r = x; g = 0; b = c; }
		else { r = c; g = 0; b = x; }
		int ri = Math.round((r + m) * 255), gi = Math.round((g + m) * 255), bi = Math.round((b + m) * 255);
		return (ri << 16) | (gi << 8) | bi;
	}
}

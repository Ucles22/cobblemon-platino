package com.platino;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Recolorea en memoria (al cargar recursos) las texturas de interfaz de Cobblemon (PC, resumen, equipo, combate)
 * con una paleta de la 4ª generación (Diamante, Perla o Platino). No modifica ni redistribuye ningún archivo de Cobblemon.
 */
public final class ScreenRecolor implements SimpleSynchronousResourceReloadListener {
	private static final List<String> FOLDERS = List.of("textures/gui/summary/", "textures/gui/pc/",
			"textures/gui/party/", "textures/gui/battle/", "textures/gui/interact/");
	private static final List<String> SKIP = List.of("type", "status", "ball", "gender", "shiny", "pokedex",
			"marking", "wallpaper", "icon", "portrait", "overlay", "pentagon", "move_", "summary_move.", "glow");
	private static final List<Identifier> replaced = new ArrayList<>();

	/** Rampas de color por luminosidad (oscuro -> claro). */
	private static final Map<String, int[]> PALETTES = Map.of(
			"diamante", new int[]{0x0C1420, 0x18284A, 0x24406E, 0x3A64A0, 0x78A8D8, 0xC8E0F4, 0xF8FCFF},
			"perla", new int[]{0x1A0C14, 0x40182E, 0x642846, 0x96436E, 0xD088AC, 0xF2CCDE, 0xFFF8FC},
			"platino", new int[]{0x121212, 0x2A2826, 0x46423A, 0x6E6650, 0xB8A060, 0xEADCA8, 0xFFFDF0});
	private static final float[] STOPS = {0f, 0.12f, 0.22f, 0.35f, 0.55f, 0.78f, 1f};

	@Override
	public Identifier getFabricId() {
		return Identifier.of(PlatinoClient.MOD_ID, "recolor");
	}

	@Override
	public Collection<Identifier> getFabricDependencies() {
		return List.of(ResourceReloadListenerKeys.TEXTURES);
	}

	@Override
	public void reload(ResourceManager manager) {
		var tm = MinecraftClient.getInstance().getTextureManager();
		// Restaurar las originales (se recargarán desde Cobblemon cuando se usen)
		for (Identifier id : replaced) tm.destroyTexture(id);
		replaced.clear();

		int[] ramp = PALETTES.get(PlatinoConfig.get().palette);
		if (!PlatinoConfig.get().screensTheme || ramp == null) return;
		int done = 0;
		Map<Identifier, Resource> found = manager.findResources("textures/gui",
				id -> id.getNamespace().equals("cobblemon") && id.getPath().endsWith(".png"));
		for (Map.Entry<Identifier, Resource> e : found.entrySet()) {
			Identifier id = e.getKey();
			String path = id.getPath();
			if (FOLDERS.stream().noneMatch(path::startsWith)) continue;
			String file = path.substring(path.lastIndexOf('/') + 1).toLowerCase();
			if (SKIP.stream().anyMatch(file::contains)) continue;
			try (InputStream in = e.getValue().getInputStream()) {
				NativeImage img = NativeImage.read(in);
				recolor(img, ramp);
				tm.registerTexture(id, new NativeImageBackedTexture(img));
				replaced.add(id);
				done++;
			} catch (Exception ex) {
				PlatinoClient.LOGGER.debug("[Cobblemon Platino] No se pudo recolorear {}", id, ex);
			}
		}
		PlatinoClient.LOGGER.info("[Cobblemon Platino] {} texturas recoloreadas (paleta {})", done, PlatinoConfig.get().palette);
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

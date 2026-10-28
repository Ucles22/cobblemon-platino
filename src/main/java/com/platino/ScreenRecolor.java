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
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Recolorea en memoria (al cargar recursos) las texturas de interfaz de Cobblemon (PC, resumen, equipo, combate)
 * con la paleta plateada/dorada de Pokémon Platino. No modifica ni redistribuye ningún archivo de Cobblemon.
 */
public final class ScreenRecolor implements SimpleSynchronousResourceReloadListener {
	private static final List<String> FOLDERS = List.of("textures/gui/summary/", "textures/gui/pc/",
			"textures/gui/party/", "textures/gui/battle/", "textures/gui/interact/");
	private static final List<String> SKIP = List.of("type", "status", "ball", "gender", "shiny", "pokedex",
			"marking", "mark", "wallpaper", "icon_", "_icon", "portrait");

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
		if (!PlatinoConfig.get().screensTheme) return;
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
				recolor(img);
				MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(img));
				done++;
			} catch (Exception ex) {
				PlatinoClient.LOGGER.debug("[Cobblemon Platino] No se pudo recolorear {}", id, ex);
			}
		}
		PlatinoClient.LOGGER.info("[Cobblemon Platino] {} texturas de Cobblemon recoloreadas al estilo Platino", done);
	}

	/** Azules/turquesas -> plata azulada; morados/rosas -> dorado. Resto intacto. */
	static void recolor(NativeImage img) {
		float[] hsv = new float[3];
		for (int yy = 0; yy < img.getHeight(); yy++) {
			for (int xx = 0; xx < img.getWidth(); xx++) {
				int abgr = img.getColor(xx, yy);
				int a = (abgr >>> 24) & 255;
				if (a == 0) continue;
				int b = (abgr >> 16) & 255, g = (abgr >> 8) & 255, r = abgr & 255;
				rgbToHsv(r, g, b, hsv);
				float h = hsv[0], s = hsv[1], v = hsv[2];
				if (s < 0.12f) continue; // grises y blancos se quedan
				if (h >= 150 && h <= 265) {
					h = 215; s = s * 0.32f; v = Math.min(1f, v * 1.04f + 0.03f);
				} else if (h > 265 && h <= 335) {
					h = 44; s = Math.min(1f, s * 0.9f + 0.1f); v = Math.min(1f, v * 1.05f);
				} else {
					continue;
				}
				int rgb = hsvToRgb(h, s, v);
				int nr = (rgb >> 16) & 255, ng = (rgb >> 8) & 255, nb = rgb & 255;
				img.setColor(xx, yy, (a << 24) | (nb << 16) | (ng << 8) | nr);
			}
		}
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

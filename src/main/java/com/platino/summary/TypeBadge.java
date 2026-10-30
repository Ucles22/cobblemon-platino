package com.platino.summary;

import com.platino.Draw;
import net.minecraft.client.gui.DrawContext;

import java.util.Locale;
import java.util.Map;

/** Insignia de tipo estilo DPPt (rectángulo de color con el nombre en blanco). */
public final class TypeBadge {
	public static final int WIDTH = 46;

	private static final Map<String, Integer> COLORS = Map.ofEntries(
			Map.entry("normal", 0xFFA8A878), Map.entry("fire", 0xFFF08030), Map.entry("water", 0xFF6890F0),
			Map.entry("grass", 0xFF78C850), Map.entry("electric", 0xFFE8C020), Map.entry("ice", 0xFF78C8C8),
			Map.entry("fighting", 0xFFC03028), Map.entry("poison", 0xFFA040A0), Map.entry("ground", 0xFFD0B058),
			Map.entry("flying", 0xFFA890F0), Map.entry("psychic", 0xFFF85888), Map.entry("bug", 0xFFA8B820),
			Map.entry("rock", 0xFFB8A038), Map.entry("ghost", 0xFF705898), Map.entry("dragon", 0xFF7038F8),
			Map.entry("dark", 0xFF705848), Map.entry("steel", 0xFFA0A0C0), Map.entry("fairy", 0xFFE890B0));
	private static final Map<String, String> LABELS = Map.ofEntries(
			Map.entry("normal", "NORMAL"), Map.entry("fire", "FUEGO"), Map.entry("water", "AGUA"),
			Map.entry("grass", "PLANTA"), Map.entry("electric", "ELÉC"), Map.entry("ice", "HIELO"),
			Map.entry("fighting", "LUCHA"), Map.entry("poison", "VENENO"), Map.entry("ground", "TIERRA"),
			Map.entry("flying", "VOLAD"), Map.entry("psychic", "PSÍQ"), Map.entry("bug", "BICHO"),
			Map.entry("rock", "ROCA"), Map.entry("ghost", "FANTAS"), Map.entry("dragon", "DRAGÓN"),
			Map.entry("dark", "SINIES"), Map.entry("steel", "ACERO"), Map.entry("fairy", "HADA"));

	private TypeBadge() {}

	/** Dibuja la insignia y devuelve su ancho. */
	public static int draw(DrawContext c, String type, int x, int y) {
		String t = type == null ? "normal" : type.toLowerCase(Locale.ROOT);
		int col = COLORS.getOrDefault(t, 0xFF909090);
		String label = LABELS.getOrDefault(t, t.toUpperCase(Locale.ROOT));
		Draw.panelGradient(c, x, y, x + WIDTH, y + 11, Draw.lerpColor(col, 0xFF000000, 0.45f),
				Draw.lerpColor(col, 0xFFFFFFFF, 0.2f), col);
		String fit = Draw.fit(label, WIDTH - 4);
		Draw.dsText(c, fit, x + WIDTH / 2 - Draw.width(fit) / 2, y + 2, 0xFFFFFFFF, Draw.lerpColor(col, 0xFF000000, 0.5f));
		return WIDTH;
	}
}

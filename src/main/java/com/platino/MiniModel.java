package com.platino;

import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.cobblemon.mod.common.util.math.QuaternionUtilsKt;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Modelo 3D del Pokémon en miniatura y quieto, dibujado igual que en las casillas del PC de Cobblemon
 * (recuadro 25 px, escala 2.5, giro 13°/35°), ampliado o reducido al tamaño pedido.
 */
public final class MiniModel {
	private static final Map<UUID, FloatingState> STATES = new HashMap<>();
	/** Pokémon cuyo modelo ya ha completado la pose de perfil (a partir de ahí se dibuja quieto). */
	private static final java.util.Set<UUID> SETTLED = new java.util.HashSet<>();
	private static MethodHandle draw;
	private static boolean failed;

	private MiniModel() {}

	public static void draw(DrawContext c, Pokemon p, int x, int y, int size) {
		if (failed) return;
		try {
			if (draw == null) {
				MethodType type = MethodType.methodType(void.class, RenderablePokemon.class, MatrixStack.class, Quaternionf.class,
						PoseType.class, PosableState.class, float.class, float.class, boolean.class, boolean.class,
						float.class, float.class, float.class, float.class, float.class, float.class, int.class, Object.class);
				draw = MethodHandles.lookup().findStatic(PokemonGuiUtilsKt.class, "drawProfilePokemon$default", type);
			}
			if (STATES.size() > 64) {
				STATES.clear();
				SETTLED.clear();
			}
			FloatingState state = STATES.computeIfAbsent(p.getUuid(), id -> new FloatingState());
			state.setCurrentAspects(p.getAspects());

			// Igual que StorageSlot del PC: recuadro de 25 px, translate(12.5, 1), escala 2.5 y tamaño de modelo 4.5
			float k = size / 25f;
			MatrixStack m = c.getMatrices();
			c.enableScissor(x, y, x + size, y + size);
			m.push();
			try {
				m.translate(x + size * 0.5, y + k, 250);
				m.scale(2.5f * k, 2.5f * k, 1f);
				// La 1ª vez se avanza el tiempo (2 s) para que el modelo complete la pose de perfil, como en el PC;
				// después se pasa 0 y se queda quieto en esa pose.
				float ticks = SETTLED.add(p.getUuid()) ? 40f : 0f;
				// Giro nuevo en cada dibujo, como hace StorageSlot del PC: (13°, 35°, 0°).
				// Cobblemon modifica el cuaternión que recibe, así que no se puede reutilizar entre fotogramas.
				Quaternionf rotation = QuaternionUtilsKt.fromEulerXYZDegrees(new Quaternionf(), new Vector3f(13f, 35f, 0f));
				draw.invoke(p.asRenderablePokemon(), m, rotation, (PoseType) null, (PosableState) state, ticks, 4.5f,
						false, false, 0f, 0f, 0f, 0f, 0f, 0f, 32648, null);
			} finally {
				m.pop();
				c.disableScissor();
			}
		} catch (Throwable t) {
			failed = true;
			PlatinoClient.LOGGER.warn("[Cobblemon Platino] No se pudo dibujar el modelo en miniatura; se desactiva", t);
		}
	}

	public static boolean failed() {
		return failed;
	}
}

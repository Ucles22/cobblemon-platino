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
 * Modelo 3D completo del Pokémon en miniatura y quieto, con el mismo método que usa el Resumen de Cobblemon
 * (recuadro 66 px, escala 2.0, giro 325°), reducido al tamaño pedido.
 */
public final class MiniModel {
	private static final Map<UUID, FloatingState> STATES = new HashMap<>();
	/** De frente, mirándote (el Resumen usa 325° de giro para verlo a tres cuartos). */
	private static final Quaternionf ROTATION = QuaternionUtilsKt.fromEulerXYZDegrees(new Quaternionf(), new Vector3f(8f, 0f, 0f));
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
			if (STATES.size() > 64) STATES.clear();
			FloatingState state = STATES.computeIfAbsent(p.getUuid(), id -> new FloatingState());
			state.setCurrentAspects(p.getAspects());

			float k = size / 66f;
			MatrixStack m = c.getMatrices();
			c.enableScissor(x, y, x + size, y + size);
			m.push();
			try {
				m.translate(x + size * 0.5, y - 10 * k, 250);
				m.scale(2f * k, 2f * k, 2f * k);
				// Mismos valores por defecto que el Resumen (máscara 8136), pero con tiempo 0 = imagen fija
				draw.invoke(p.asRenderablePokemon(), m, ROTATION, (PoseType) null, (PosableState) state, 0f, 0f,
						false, false, 0f, 0f, 0f, 0f, 0f, 0f, 8136, null);
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

package com.platino.mixin;

import com.platino.PlatinoConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Constructor;

/**
 * Tema «Platino» para el registro de combate de Cobblemon Battle Extras (si está instalado): caja oliva con marco
 * oscuro, filo claro, cabecera oscura y texto blanco, como las cajas de combate de Platino. /platino combate registro.
 */
@Pseudo
@Mixin(targets = "name.modid.client.BattleUiThemePalette", remap = false)
public abstract class BattleExtrasThemeMixin {
	private static Object platino$theme;
	private static boolean platino$failed;

	@Inject(method = "getActiveBattleLogTheme", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
	private static void platino$activeTheme(CallbackInfoReturnable<Object> cir) {
		if (platino$failed || !PlatinoConfig.get().battleLogTheme) return;
		try {
			if (platino$theme == null) {
				Class<?> cls = Class.forName("name.modid.client.BattleUiThemePalette$Theme");
				Constructor<?> ctor = cls.getDeclaredConstructors()[0];
				ctor.setAccessible(true);
				platino$theme = ctor.newInstance("platino",
						0xFF69715A, // muestra
						0xF26A7259, // fondo
						0xF2626A52, // degradado
						0xF2565E48, // fondo inferior
						0xFF293028, // cabecera
						0xFF3C4438, // cabecera clara
						0xFF293028, // borde
						0xFFA8AB96, // filo claro
						0xFFC89858, // acento (raíl)
						0xFFF8FCF6, // texto de cabecera
						0xFF2B3029, // sombra del texto
						0xFFD0D4C0); // texto atenuado
			}
			cir.setReturnValue(platino$theme);
		} catch (Throwable t) {
			platino$failed = true;
			com.platino.PlatinoClient.LOGGER.warn("[Cobblemon Platino] No se pudo aplicar el tema al registro de Battle Extras", t);
		}
	}
}

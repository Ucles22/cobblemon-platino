package com.platino.mixin;

import com.cobblemon.mod.common.client.gui.summary.Summary;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.platino.PlatinoConfig;
import com.platino.summary.DppSummaryScreen;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

/** Abre la pantalla de datos estilo Diamante/Perla en lugar del Resumen de Cobblemon (/platino resumen off). */
@Mixin(value = Summary.Companion.class, remap = false)
public abstract class SummaryOpenMixin {
	@Inject(method = "open", at = @At("HEAD"), cancellable = true, remap = false)
	private void platino$open(Collection<? extends Pokemon> party, boolean editable, int selection, CallbackInfo ci) {
		if (DppSummaryScreen.bypass || !PlatinoConfig.get().dppSummary) {
			DppSummaryScreen.nextParent = null;
			return;
		}
		if (party == null || party.stream().allMatch(p -> p == null)) return;
		try {
			MinecraftClient.getInstance().setScreen(new DppSummaryScreen(party, editable, selection));
			ci.cancel();
		} catch (Throwable t) {
			com.platino.PlatinoClient.LOGGER.warn("[Cobblemon Platino] Fallo al abrir la ficha DPPt; se usa la de Cobblemon", t);
		}
	}
}

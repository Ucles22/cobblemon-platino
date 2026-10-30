package com.platino.mixin;

import com.cobblemon.mod.common.client.gui.pc.PCGUI;
import com.cobblemon.mod.common.client.gui.pc.PCGUIConfiguration;
import com.platino.PlatinoConfig;
import com.platino.summary.DppPcScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cambia el PC normal de Cobblemon por el PC estilo Diamante/Perla (no toca el del pasto). /platino pc off. */
@Mixin(MinecraftClient.class)
public abstract class PcScreenMixin {
	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void platino$pc(Screen screen, CallbackInfo ci) {
		if (!(screen instanceof PCGUI gui) || DppPcScreen.bypass || !PlatinoConfig.get().dppPc) return;
		try {
			if (gui.getConfiguration().getClass() != PCGUIConfiguration.class) return;
			((MinecraftClient) (Object) this).setScreen(new DppPcScreen(gui));
			ci.cancel();
		} catch (Throwable t) {
			com.platino.PlatinoClient.LOGGER.warn("[Cobblemon Platino] Fallo al abrir el PC DP; se usa el de Cobblemon", t);
		}
	}
}

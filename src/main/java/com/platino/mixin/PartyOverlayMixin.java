package com.platino.mixin;

import com.cobblemon.mod.common.client.gui.PartyOverlay;
import com.platino.PartyHud;
import com.platino.PlatinoConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sustituye la barra de equipo de Cobblemon por la versión Platino. */
@Mixin(PartyOverlay.class)
public abstract class PartyOverlayMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void platino$render(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		if (!PlatinoConfig.get().partyHud) return;
		ci.cancel();
		if (MinecraftClient.getInstance().options.hudHidden || !PartyHud.canRender()) return;
		PartyHud.render(context, tickCounter.getLastDuration());
	}
}

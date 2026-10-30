package com.platino.mixin;

import com.platino.PlatinoConfig;
import com.platino.PlatinoMenuScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Al pulsar Esc se abre el menú estilo DPPt en vez del menú de pausa (que sigue en "MENÚ JUEGO"). */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	@Shadow
	public Screen currentScreen;

	@Shadow
	public abstract void setScreen(Screen screen);

	@Inject(method = "openGameMenu", at = @At("HEAD"), cancellable = true)
	private void platino$openMenu(boolean pauseOnly, CallbackInfo ci) {
		MinecraftClient self = (MinecraftClient) (Object) this;
		if (!PlatinoConfig.get().pauseMenu || pauseOnly || currentScreen != null || self.world == null) return;
		setScreen(new PlatinoMenuScreen());
		ci.cancel();
	}
}

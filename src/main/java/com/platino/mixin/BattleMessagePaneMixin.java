package com.platino.mixin;

import com.cobblemon.mod.common.client.gui.battle.widgets.BattleMessagePane;
import com.platino.DsTextBox;
import com.platino.PlatinoConfig;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sustituye el registro de combate de Cobblemon por el cuadro de texto estilo DS. */
@Mixin(BattleMessagePane.class)
public abstract class BattleMessagePaneMixin {
	@Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
	private void platino$render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!PlatinoConfig.get().battleText) return;
		ci.cancel();
		DsTextBox.render(context, (BattleMessagePane) (Object) this);
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void platino$click(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
		if (PlatinoConfig.get().battleText) cir.setReturnValue(false);
	}

	@Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
	private void platino$drag(double mouseX, double mouseY, int button, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
		if (PlatinoConfig.get().battleText) cir.setReturnValue(false);
	}
}

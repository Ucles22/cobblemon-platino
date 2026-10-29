package com.platino.mixin;

import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleGeneralActionSelection;
import com.platino.DsBattleMenu;
import com.platino.PlatinoConfig;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dibuja el menú LUCHAR/BOLSA/HUIR/POKéMON estilo DS en lugar de las fichas de Cobblemon. */
@Mixin(BattleGeneralActionSelection.class)
public abstract class BattleGeneralSelectionMixin {
	@Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
	private void platino$render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!PlatinoConfig.get().battleMenu) return;
		ci.cancel();
		DsBattleMenu.render(context, (BattleGeneralActionSelection) (Object) this, mouseX, mouseY);
	}

	/** Las fichas originales (invisibles) no deben reaccionar a los clics. */
	@Inject(method = "mousePrimaryClicked", at = @At("HEAD"), cancellable = true, remap = false)
	private void platino$click(double mouseX, double mouseY, CallbackInfoReturnable<Boolean> cir) {
		if (!PlatinoConfig.get().battleMenu) return;
		cir.setReturnValue(DsBattleMenu.click((BattleGeneralActionSelection) (Object) this, mouseX, mouseY));
	}
}

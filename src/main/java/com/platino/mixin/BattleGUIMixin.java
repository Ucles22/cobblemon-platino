package com.platino.mixin;

import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleActionSelection;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleGeneralActionSelection;
import com.platino.DsBattleMenu;
import com.platino.PlatinoConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Los botones DS están fuera del área original, así que el clic se captura aquí primero. */
@Mixin(BattleGUI.class)
public abstract class BattleGUIMixin {
	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void platino$click(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
		if (!PlatinoConfig.get().battleMenu || button != 0) return;
		BattleActionSelection sel = ((BattleGUI) (Object) this).getCurrentActionSelection();
		if (sel instanceof BattleGeneralActionSelection g && DsBattleMenu.click(g, mouseX, mouseY)) {
			cir.setReturnValue(true);
		}
	}
}

package com.platino.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.platino.InventoryTheme;
import com.platino.PlatinoConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Inventario de supervivencia: cambia la textura de fondo por el diseño Platino. */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
	@WrapOperation(method = "drawBackground", require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V"))
	private void platino$background(DrawContext context, Identifier texture, int x, int y, int u, int v, int w, int h, Operation<Void> original) {
		if (!PlatinoConfig.get().inventoryTheme) {
			original.call(context, texture, x, y, u, v, w, h);
			return;
		}
		InventoryTheme.drawBackground(context, ((InventoryScreen) (Object) this).getScreenHandler(), x, y, w, h,
				InventoryTheme.Layout.SURVIVAL);
	}
}

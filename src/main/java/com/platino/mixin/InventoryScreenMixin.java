package com.platino.mixin;

import com.platino.InventoryTheme;
import com.platino.PlatinoConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Inventario de supervivencia: cambia la textura de fondo por el diseño Platino. */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
	@Redirect(method = "drawBackground", require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V"))
	private void platino$background(DrawContext context, Identifier texture, int x, int y, int u, int v, int w, int h) {
		if (!PlatinoConfig.get().inventoryTheme) {
			context.drawTexture(texture, x, y, u, v, w, h);
			return;
		}
		InventoryTheme.drawBackground(context, ((InventoryScreen) (Object) this).getScreenHandler(), x, y, w, h,
				InventoryTheme.Layout.SURVIVAL);
	}
}

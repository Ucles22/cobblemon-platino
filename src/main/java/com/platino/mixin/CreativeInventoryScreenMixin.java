package com.platino.mixin;

import com.platino.InventoryTheme;
import com.platino.PlatinoConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Inventario creativo: fondo estilo Platino en todas las pestañas. */
@Mixin(CreativeInventoryScreen.class)
public abstract class CreativeInventoryScreenMixin {
	@Redirect(method = "drawBackground", require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V"))
	private void platino$background(DrawContext context, Identifier texture, int x, int y, int u, int v, int w, int h) {
		String path = texture.getPath();
		if (!PlatinoConfig.get().inventoryTheme || !path.contains("creative_inventory/tab_")) {
			context.drawTexture(texture, x, y, u, v, w, h);
			return;
		}
		InventoryTheme.Layout layout = path.endsWith("tab_inventory.png") ? InventoryTheme.Layout.CREATIVE_INVENTORY
				: path.endsWith("tab_item_search.png") ? InventoryTheme.Layout.CREATIVE_SEARCH
				: InventoryTheme.Layout.CREATIVE_ITEMS;
		InventoryTheme.drawBackground(context, ((CreativeInventoryScreen) (Object) this).getScreenHandler(),
				x, y, w, h, layout);
	}
}

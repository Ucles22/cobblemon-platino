package com.platino.mixin;

import com.platino.ServerStats;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Escucha el texto dibujado (solo con la Ficha abierta) para leer el marcador del servidor. */
@Mixin(DrawContext.class)
public abstract class DrawContextTextMixin {
	@Inject(method = "drawText(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;IIIZ)I", at = @At("HEAD"), require = 0)
	private void platino$str(TextRenderer tr, String text, int x, int y, int color, boolean shadow, CallbackInfoReturnable<Integer> cir) {
		if (ServerStats.capturing) ServerStats.capture(text);
	}

	@Inject(method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;IIIZ)I", at = @At("HEAD"), require = 0)
	private void platino$ordered(TextRenderer tr, OrderedText text, int x, int y, int color, boolean shadow, CallbackInfoReturnable<Integer> cir) {
		if (ServerStats.capturing) ServerStats.capture(text);
	}

	@Inject(method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)I", at = @At("HEAD"), require = 0)
	private void platino$text(TextRenderer tr, Text text, int x, int y, int color, boolean shadow, CallbackInfoReturnable<Integer> cir) {
		if (ServerStats.capturing) ServerStats.capture(text);
	}
}

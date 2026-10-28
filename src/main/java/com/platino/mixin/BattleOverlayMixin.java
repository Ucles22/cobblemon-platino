package com.platino.mixin;

import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.gui.battle.BattleOverlay;
import com.platino.BattleHud;
import com.platino.PlatinoConfig;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sustituye las tarjetas de combate de Cobblemon por cajas estilo Platino. */
@Mixin(value = BattleOverlay.class, remap = false)
public abstract class BattleOverlayMixin {
	@Inject(method = "drawTile", at = @At("HEAD"), cancellable = true, remap = false)
	private void platino$drawTile(DrawContext context, float tickDelta, ActiveClientBattlePokemon active, boolean left,
								  int rank, PokedexEntryProgress dexState, boolean hasCommand, boolean isHovered,
								  boolean isCompact, CallbackInfo ci) {
		if (!PlatinoConfig.get().battleHud) return;
		ci.cancel();
		BattleHud.drawTile(context, active, left, rank, dexState == null ? null : dexState.name(), hasCommand, isHovered);
	}
}

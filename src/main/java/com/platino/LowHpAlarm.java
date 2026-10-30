package com.platino;

import com.cobblemon.mod.common.client.CobblemonClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

/** Pitido "pi-pi… pi-pi…" mientras tu Pokémon en combate tiene la vida en rojo. */
public final class LowHpAlarm {
	private static volatile long lastSeen;
	private static long nextBeep;
	private static int phase;

	private LowHpAlarm() {}

	/** Lo llama la caja de combate cada fotograma en que tu Pokémon está en rojo. */
	public static void mark() {
		lastSeen = System.currentTimeMillis();
	}

	public static void tick(MinecraftClient client) {
		long now = System.currentTimeMillis();
		if (!PlatinoConfig.get().lowHpBeep || now - lastSeen > 400 || CobblemonClient.INSTANCE.getBattle() == null) {
			phase = 0;
			return;
		}
		if (now < nextBeep) return;
		// Dos pitidos seguidos y una pausa
		client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(),
				phase == 0 ? 1.9f : 1.7f, 0.35f));
		nextBeep = now + (phase == 0 ? 170 : 600);
		phase = 1 - phase;
	}
}

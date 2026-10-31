package com.platino;

import com.cobblemon.mod.common.client.CobblemonClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Alarma de vida baja de DPPt: un bucle «pi-po… pi-po…» que suena mientras tu Pokémon en combate tiene la vida en
 * rojo. El sonido es assets/cobbleplatino/sounds/low_hp.ogg; con un paquete de recursos se puede cambiar por otro.
 */
public final class LowHpAlarm {
	private static final SoundEvent LOW_HP = SoundEvent.of(Identifier.of(PlatinoClient.MOD_ID, "low_hp"));
	private static volatile long lastSeen;
	private static Loop playing;

	private LowHpAlarm() {}

	/** Lo llama la caja de combate cada fotograma en que tu Pokémon está en rojo. */
	public static void mark() {
		lastSeen = System.currentTimeMillis();
	}

	static boolean active() {
		try {
			return PlatinoConfig.get().lowHpBeep && System.currentTimeMillis() - lastSeen <= 400
					&& CobblemonClient.INSTANCE.getBattle() != null;
		} catch (Throwable t) {
			return false;
		}
	}

	public static void tick(MinecraftClient client) {
		if (!active()) return;
		if (playing == null || playing.isDone() || !client.getSoundManager().isPlaying(playing)) {
			playing = new Loop();
			client.getSoundManager().play(playing);
		}
	}

	/** Sonido en bucle que se apaga solo en cuanto deja de haber vida baja. */
	private static final class Loop extends MovingSoundInstance {
		Loop() {
			super(LOW_HP, SoundCategory.MASTER, SoundInstance.createRandom());
			this.repeat = true;
			this.repeatDelay = 0;
			this.volume = 0.45f;
			this.pitch = 1f;
			this.relative = true;
			this.attenuationType = SoundInstance.AttenuationType.NONE;
		}

		@Override
		public void tick() {
			if (!active()) setDone();
		}
	}
}

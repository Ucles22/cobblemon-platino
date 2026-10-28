package com.platino;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlatinoClient implements ClientModInitializer {
	public static final String MOD_ID = "cobbleplatino";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		LOGGER.info("[Cobblemon Platino] Cargado");
	}
}

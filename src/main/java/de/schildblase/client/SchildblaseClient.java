package de.schildblase.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchildblaseClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("schildblase-client");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Schildblase initialisiert (client entrypoint).");
    }
}

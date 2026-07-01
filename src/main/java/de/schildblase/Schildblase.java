package de.schildblase;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Schildblase implements ModInitializer {
    public static final String MOD_ID = "schildblase";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Schildblase initialisiert (main entrypoint).");
    }
}

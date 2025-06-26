package io.fjsn.chirp.internal;


import java.util.logging.Level;
import java.util.logging.Logger;

public class ChirpLogger {

    public static final Logger CHIRP_LOGGER = Logger.getLogger("chirp");
    public static boolean debug = false;

    public static void severe(String message) {
        CHIRP_LOGGER.log(Level.SEVERE, message);
    }

    public static void warning(String message) {
        CHIRP_LOGGER.log(Level.WARNING, message);
    }

    public static void info(String message) {
        CHIRP_LOGGER.log(Level.INFO, message);
    }

    public static void debug(String message) {
        if (!debug) return;
        CHIRP_LOGGER.log(Level.INFO, message);
    }
}

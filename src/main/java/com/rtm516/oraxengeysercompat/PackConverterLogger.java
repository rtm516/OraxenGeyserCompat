package com.rtm516.oraxengeysercompat;

import org.geysermc.pack.converter.util.LogListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.logging.Logger;

public class PackConverterLogger implements LogListener {
    private Logger logger;

    public PackConverterLogger(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void debug(@NotNull String message) {
        logger.fine(message);
    }

    @Override
    public void info(@NotNull String message) {
        logger.info(message);
    }

    @Override
    public void warn(@NotNull String message) {
        logger.warning(message);
    }

    @Override
    public void error(@NotNull String message) {
        logger.severe(message);
    }

    @Override
    public void error(@NotNull String message, @Nullable Throwable exception) {
        logger.severe(message);
        if (exception != null) {
            exception.printStackTrace();
        }
    }
}

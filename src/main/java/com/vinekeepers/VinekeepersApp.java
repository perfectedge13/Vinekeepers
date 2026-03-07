package com.vinekeepers;

import com.vinekeepers.core.Bootstrap;
import com.vinekeepers.env.EnvLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Main entry point for Vinekeepers — a collection of AI-based bots.
 */
public class VinekeepersApp {

    private static final Logger log = LoggerFactory.getLogger(VinekeepersApp.class);

    public static void main(String[] args) {
        EnvLoader.load(".env");
        log.info("Vinekeepers — AI bots");
        Bootstrap bootstrap = new Bootstrap();
        Path configPath = Paths.get("config", "bots.yaml");
        bootstrap.loadConfig(configPath);
        bootstrap.withDiscord();
        bootstrap.withGitHub();
        Runtime.getRuntime().addShutdownHook(new Thread(bootstrap::shutdown));
        log.info("Engine running; events will be processed from Discord and GitHub (stub)");
    }
}

package com.foodrisk.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Automatically detects and loads key-value pairs from a .env file
 * into the Spring ConfigurableEnvironment and System properties.
 *
 * Checks current directory, parent directory, and sibling directories.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DotenvEnvironmentPostProcessor.class);
    private static final String PROPERTY_SOURCE_NAME = "dotenvProperties";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> props = loadDotenvMap();
        if (!props.isEmpty()) {
            props.forEach((k, v) -> {
                if (System.getProperty(k) == null && System.getenv(k) == null) {
                    System.setProperty(k, String.valueOf(v));
                }
            });

            if (!environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
                if (environment.getPropertySources().contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
                    environment.getPropertySources().addAfter(
                            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                            new MapPropertySource(PROPERTY_SOURCE_NAME, props)
                    );
                } else {
                    environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, props));
                }
                log.info("DotenvEnvironmentPostProcessor loaded {} properties from .env", props.size());
            }
        }
    }

    /**
     * Programmatic fallback entry point to populate System properties.
     */
    public static void load() {
        Map<String, Object> props = loadDotenvMap();
        props.forEach((k, v) -> {
            if (System.getProperty(k) == null && System.getenv(k) == null) {
                System.setProperty(k, String.valueOf(v));
            }
        });
    }

    public static Map<String, Object> loadDotenvMap() {
        return loadDotenvMap(findDotenvPath());
    }

    public static Map<String, Object> loadDotenvMap(Path envPath) {
        Map<String, Object> map = new HashMap<>();
        if (envPath == null) {
            return map;
        }

        try {
            List<String> lines = Files.readAllLines(envPath, StandardCharsets.UTF_8);
            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("export ")) {
                    line = line.substring("export ".length()).trim();
                }
                int eqIdx = line.indexOf('=');
                if (eqIdx > 0) {
                    String key = line.substring(0, eqIdx).trim();
                    String val = line.substring(eqIdx + 1).trim();
                    if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                        if (val.length() >= 2) {
                            val = val.substring(1, val.length() - 1);
                        }
                    }
                    map.put(key, val);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read .env file at {}: {}", envPath, e.getMessage());
        }
        return map;
    }

    public static Path findDotenvPath() {
        return findDotenvPath(Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize());
    }

    static Path findDotenvPath(Path startDirectory) {
        Path fallbackExample = null;
        for (Path directory = startDirectory.toAbsolutePath().normalize(); directory != null; directory = directory.getParent()) {
            Path candidate = directory.resolve(".env");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            if (fallbackExample == null) {
                Path candidateExample = directory.resolve(".env.example");
                if (Files.isRegularFile(candidateExample)) {
                    fallbackExample = candidateExample;
                }
            }
        }
        return fallbackExample;
    }
}

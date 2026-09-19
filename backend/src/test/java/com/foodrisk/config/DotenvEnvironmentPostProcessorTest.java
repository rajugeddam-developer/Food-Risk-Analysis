package com.foodrisk.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DotenvEnvironmentPostProcessorTest {

    @Test
    void testLoadDotenvMapFindsAndParsesEnv() {
        Map<String, Object> props = DotenvEnvironmentPostProcessor.loadDotenvMap();
        assertNotNull(props);
        assertTrue(props.containsKey("DB_NAME"));
        assertEquals("food_risk_analysis", props.get("DB_NAME"));
        assertTrue(props.containsKey("JWT_SECRET"));
    }

    @Test
    void testPostProcessEnvironmentAddsPropertySource() {
        DotenvEnvironmentPostProcessor processor = new DotenvEnvironmentPostProcessor();
        ConfigurableEnvironment environment = new StandardEnvironment();
        processor.postProcessEnvironment(environment, new SpringApplication());

        assertTrue(environment.getPropertySources().contains("dotenvProperties"));
        assertEquals("food_risk_analysis", environment.getProperty("DB_NAME"));
    }
}

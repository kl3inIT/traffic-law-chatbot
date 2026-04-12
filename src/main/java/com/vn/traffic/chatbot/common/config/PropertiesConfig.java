package com.vn.traffic.chatbot.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers typed {@code @ConfigurationProperties} beans for the application.
 *
 * <p>Using {@code @EnableConfigurationProperties} here (not on the properties class itself)
 * follows the Spring Boot recommended pattern for library-style configuration.
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class PropertiesConfig {
}

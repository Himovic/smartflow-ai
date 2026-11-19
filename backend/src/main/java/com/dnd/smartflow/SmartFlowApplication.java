package com.dnd.smartflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SmartFlow AI + QHSE — modular monolith entry point.
 * Developed by D&amp;D | Data&amp;Development.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableAsync
@EnableCaching
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class SmartFlowApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartFlowApplication.class, args);
    }
}

package com.bank.esps.application.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scheduling configuration
 * Enables scheduled tasks (e.g., reconciliation jobs)
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
    // Configuration for scheduled tasks
}

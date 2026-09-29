package com.portfoliopro.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled}; the pending-order job is the only user so far. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}

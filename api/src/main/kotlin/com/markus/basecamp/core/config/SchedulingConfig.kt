package com.markus.basecamp.core.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Turns on @Scheduled jobs (todo reminders, notification clean-up). Tests switch it off with
 * basecamp.scheduling.enabled=false so a real timer tick cannot interfere with what they count.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = ["basecamp.scheduling.enabled"], havingValue = "true", matchIfMissing = true)
class SchedulingConfig

package com.markus.basecamp.core.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync

/** Turns on @Async, used to parse an uploaded demo in the background instead of blocking the upload request. */
@Configuration
@EnableAsync
class AsyncConfig

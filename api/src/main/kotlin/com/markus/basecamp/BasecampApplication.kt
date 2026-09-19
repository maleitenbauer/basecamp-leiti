package com.markus.basecamp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class BasecampApplication

fun main(args: Array<String>) {
    runApplication<BasecampApplication>(*args)
}

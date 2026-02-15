package com.kineto.deploymentmanager

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.DockerComposeContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File

@ActiveProfiles("local")
@SpringBootTest
@Testcontainers
class ApplicationTest {

    companion object {
        @Container
        val composeContainer = DockerComposeContainer(File("./docker-compose.yaml"))
            .withExposedService("postgres", 5432)
            .withExposedService("kafka", 9092)
            .withExposedService("localstack", 4566)
    }

    @Test
    fun `context loads`() {
        assertTrue(true)
    }
}
package com.kineto.deploymentmanager

import com.kineto.deploymentmanager.config.AWSProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@EnableConfigurationProperties(AWSProperties::class)
@SpringBootApplication
class DeploymentManager

fun main(args: Array<String>) {
    runApplication<DeploymentManager>(*args)
}

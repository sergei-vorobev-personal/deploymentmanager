package com.kineto.deploymentmanager.config

import org.springframework.boot.context.properties.ConfigurationProperties
import software.amazon.awssdk.regions.Region

@ConfigurationProperties(prefix = "aws")
data class AWSProperties(
    val endpoint: String,
    val accessKeyId: String,
    val accessKey: String,
    val region: Region,
    val lambdaRoleArn: String,
    val bucket: String,
)

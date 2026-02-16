package com.kineto.deploymentmanager.config

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.lambda.LambdaClient
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI

@Configuration
class AWSConfig(
    private val awsProperties: AWSProperties,
    private val meterRegistry: MeterRegistry,
) {
    @Bean
    fun lambdaClient(): LambdaClient =
        LambdaClient.builder()
            .endpointOverride(URI.create(awsProperties.endpoint))
            .region(awsProperties.region)
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(awsProperties.accessKeyId, awsProperties.accessKey)
                )
            )
            .overrideConfiguration {
                it.addMetricPublisher(MicrometerAWSMetricsPublisher("lambda", meterRegistry))
            }
            .build()

    @Bean
    fun s3Client(): S3Client = S3Client.builder()
        .endpointOverride(URI.create(awsProperties.endpoint))
        .region(awsProperties.region)
        .forcePathStyle(true)
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(awsProperties.accessKeyId, awsProperties.accessKey)
            )
        )
        .overrideConfiguration {
            it.addMetricPublisher(MicrometerAWSMetricsPublisher("s3", meterRegistry))
        }
        .build()
}

@ConfigurationProperties(prefix = "aws")
data class AWSProperties(
    val endpoint: String,
    val accessKeyId: String,
    val accessKey: String,
    val region: Region,
    val lambdaRoleArn: String,
    val bucket: String,
)
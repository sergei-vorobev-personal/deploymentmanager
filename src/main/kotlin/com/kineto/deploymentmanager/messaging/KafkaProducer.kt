package com.kineto.deploymentmanager.messaging

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val log = KotlinLogging.logger {}

@Service
class KafkaProducer(
    @param:Value("\${kafka.topic}") private val topic: String,
    private val kafkaTemplate: KafkaTemplate<String, ApplicationEvent>,
) {
    @Transactional
    fun sendDeploymentEvent(
        key: String,
        applicationEvent: ApplicationEvent
    ) {
        kafkaTemplate.send(topic, key, applicationEvent).whenComplete { result, ex ->
            if (ex != null) {
                log.error(ex) {
                    "Failed to send deployment event. key=$key topic=$topic"
                }
                throw ex
            } else {
                log.info {
                    "Event sent successfully. key=$key partition=${result.recordMetadata.partition()} offset=${result.recordMetadata.offset()}"
                }
            }
        }
    }
}
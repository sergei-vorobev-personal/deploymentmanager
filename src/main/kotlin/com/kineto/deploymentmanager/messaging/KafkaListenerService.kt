package com.kineto.deploymentmanager.messaging

import com.kineto.deploymentmanager.messaging.ApplicationEventType.*
import com.kineto.deploymentmanager.service.DeploymentService
import mu.KotlinLogging
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

private val log = KotlinLogging.logger {}

@Service
class KafkaListenerService(
    private val deploymentService: DeploymentService,
) {

    @KafkaListener(topics = ["\${kafka.topic}"])
    fun handle(event: ApplicationEvent) {
        log.info { "Received application event $event" }
        when (event.type) {
            CREATE_REQUESTED -> deploymentService.create(event.applicationName)
            UPDATE_REQUESTED -> deploymentService.update(event.applicationName)
            DELETE_REQUESTED -> deploymentService.delete(event.applicationName)
        }
    }
}
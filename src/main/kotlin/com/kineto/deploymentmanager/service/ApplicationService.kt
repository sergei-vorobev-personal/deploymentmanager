package com.kineto.deploymentmanager.service

import com.kineto.deploymentmanager.dto.GetStatusResponse
import com.kineto.deploymentmanager.exception.APIException
import com.kineto.deploymentmanager.messaging.ApplicationEvent
import com.kineto.deploymentmanager.messaging.ApplicationEventType
import com.kineto.deploymentmanager.messaging.KafkaProducer
import com.kineto.deploymentmanager.model.Application
import com.kineto.deploymentmanager.model.ApplicationState.*
import com.kineto.deploymentmanager.repository.ApplicationRepository
import mu.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*


private val log = KotlinLogging.logger {}

@Service
class ApplicationService(
    private val kafkaProducer: KafkaProducer,
    private val applicationRepository: ApplicationRepository,
) {
    @Transactional
    fun requestDeployment(
        name: String,
        s3Key: String,
        s3Bucket: String,
    ): GetStatusResponse {
        val app = applicationRepository.findByIdOrNull(name)?.apply {
            this.s3Key = s3Key
            this.s3Bucket = s3Bucket
        } ?: applicationRepository.save(
            Application(
                id = name,
                s3Key = s3Key,
                s3Bucket = s3Bucket,
                functionName = "${name}-${UUID.randomUUID()}",
                state = NEW,
            )
        )
        val (eventType, nextState) = when (app.state) {
            NEW,
            DELETED,
            CREATE_FAILED,
            FAILED,
            INACTIVE -> ApplicationEventType.CREATE_REQUESTED to CREATE_REQUESTED

            ACTIVE, UPDATE_FAILED -> ApplicationEventType.UPDATE_REQUESTED to UPDATE_REQUESTED

            CREATE_REQUESTED,
            UPDATE_REQUESTED,
            DELETE_REQUESTED,
            PENDING -> throw APIException.DeploymentInProgressException(name)

            DELETE_FAILED -> throw APIException.DeletionFailedException(name)
        }
        app.error = null
        app.state = nextState
        applicationRepository.save(app)

        kafkaProducer.sendApplicationEvent(
            key = name,
            applicationEvent = ApplicationEvent(app.id, eventType)
        )
        if (eventType == ApplicationEventType.CREATE_REQUESTED) {
            log.info { "Creation of $name requested. Location: $s3Bucket/$s3Key" }
        } else {
            log.info { "Update of $name requested. Location: $s3Bucket/$s3Key" }
        }
        return GetStatusResponse(
            name = app.id,
            state = app.state,
            updatedAt = app.updatedAt
        )
    }

    @Transactional(readOnly = true)
    fun getStatus(
        name: String,
    ): GetStatusResponse {
        val app = applicationRepository.findByIdOrNull(name) ?: throw APIException.ApplicationNotFoundException(name)
        log.info { "Application $name status: ${app.state}." }
        return GetStatusResponse(
            name = app.id,
            state = app.state,
            error = app.error,
            updatedAt = app.updatedAt
        )
    }

    @Transactional
    fun requestDeletion(
        name: String,
    ): GetStatusResponse {
        val app = applicationRepository.findByIdOrNull(name) ?: throw APIException.ApplicationNotFoundException(name)
        if (app.state == DELETED || app.state == DELETE_REQUESTED) {
            return GetStatusResponse(
                name = app.id,
                state = app.state,
                updatedAt = app.updatedAt
            )
        }
        app.error = null
        app.state = DELETE_REQUESTED
        applicationRepository.save(app)
        kafkaProducer.sendApplicationEvent(
            key = name,
            applicationEvent = ApplicationEvent(
                applicationName = app.id,
                type = ApplicationEventType.DELETE_REQUESTED
            )
        )
        log.info { "Deletion of $name requested." }
        return GetStatusResponse(
            name = app.id,
            state = app.state,
            updatedAt = app.updatedAt
        )
    }
}
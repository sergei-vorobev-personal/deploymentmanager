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
            this.error = null
        } ?: Application(
            id = name,
            s3Key = s3Key,
            s3Bucket = s3Bucket,
            functionName = "${name}-${UUID.randomUUID()}",
            state = NEW,
        )

        return when (app.state) {
            NEW,
            DELETED,
            CREATE_FAILED -> requestCreate(app)

            ACTIVE, UPDATE_FAILED -> requestUpdate(app)

            CREATING,
            UPDATING,
            CREATE_REQUESTED,
            UPDATE_REQUESTED,
            DELETE_REQUESTED-> throw APIException.DeploymentInProgressException(name)

            DELETE_FAILED -> throw APIException.DeletionFailedException(name)
        }
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

    private fun requestCreate(
        app: Application,
    ): GetStatusResponse {
        app.state = CREATE_REQUESTED
        applicationRepository.save(app)

        kafkaProducer.sendApplicationEvent(
            key = app.id,
            applicationEvent = ApplicationEvent(app.id, ApplicationEventType.CREATE_REQUESTED)
        )
        log.info { "Creation of ${app.id} requested. Location: ${app.s3Bucket}/${app.s3Key}" }
        return GetStatusResponse(
            name = app.id,
            state = app.state,
            updatedAt = app.updatedAt
        )
    }

    private fun requestUpdate(
        app: Application,
    ): GetStatusResponse {
        app.state = UPDATE_REQUESTED
        applicationRepository.save(app)

        kafkaProducer.sendApplicationEvent(
            key = app.id,
            applicationEvent = ApplicationEvent(app.id, ApplicationEventType.UPDATE_REQUESTED)
        )
        log.info { "Update of ${app.id} requested. Location: ${app.s3Bucket}/${app.s3Key}" }
        return GetStatusResponse(
            name = app.id,
            state = app.state,
            updatedAt = app.updatedAt
        )
    }
}
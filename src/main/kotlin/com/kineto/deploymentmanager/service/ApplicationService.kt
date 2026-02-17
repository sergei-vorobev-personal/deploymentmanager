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
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClient
import software.amazon.awssdk.http.HttpStatusCode.NOT_FOUND
import java.util.*


private val log = KotlinLogging.logger {}

@Service
class ApplicationService(
    private val webClient: WebClient,
    private val kafkaProducer: KafkaProducer,
    private val applicationRepository: ApplicationRepository,
) {

    fun callLambda(name: String): ResponseEntity<String> {
        val app = applicationRepository.findByIdOrNull(name)
            ?: throw APIException.ApplicationNotFoundException(name)
        if (app.state == DELETED || app.state == DELETE_REQUESTED) {
            return ResponseEntity
                .status(NOT_FOUND)
                .body("Application $name has been deleted")
        }
        if (app.state == FAILED) {
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Application $name is not available")
        }
        if (app.state != ACTIVE || app.url == null) {
            return ResponseEntity
                .status(SERVICE_UNAVAILABLE)
                .body("Application $name is not ready yet")
        }
        log.info { "Invoking Lambda function ${app.functionName} for application ${app.id} via URL: ${app.url}" }

        val response = webClient.get()
            .uri(app.url!!)
            .retrieve()
            .toEntity(String::class.java)
            .block()

        return ResponseEntity
            .status(response!!.statusCode)
            .headers(response.headers)
            .body(response.body)
    }

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
        kafkaProducer.sendApplicationEvent(
            key = name,
            applicationEvent = ApplicationEvent(
                applicationName = app.id,
                type = ApplicationEventType.DELETE_REQUESTED
            )
        )
        app.state = DELETE_REQUESTED
        applicationRepository.save(app)
        log.info { "Deletion of $name requested." }
        return GetStatusResponse(
            name = app.id,
            state = app.state,
            updatedAt = app.updatedAt
        )
    }
}
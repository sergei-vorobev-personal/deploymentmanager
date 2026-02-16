package com.kineto.deploymentmanager.service

import com.kineto.deploymentmanager.exception.APIException
import com.kineto.deploymentmanager.exception.AWSException
import com.kineto.deploymentmanager.model.ApplicationState
import com.kineto.deploymentmanager.repository.ApplicationRepository
import mu.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant.now

private val log = KotlinLogging.logger {}

@Service
class DeploymentService(
    private val awsFacadeService: AWSFacadeService,
    private val applicationRepository: ApplicationRepository,
) {

    @Transactional
    fun create(
        applicationName: String,
    ) {
        val app = applicationRepository.findByIdOrNull(applicationName)
            ?: throw APIException.ApplicationNotFoundException(applicationName)
        try {
            val lambdaCreationResponse = awsFacadeService.createLambda(
                applicationName = applicationName,
                functionName = app.functionName,
                s3Key = app.s3Key,
                s3Bucket = app.s3Bucket,
            )
            app.state = lambdaCreationResponse.state
            app.url = lambdaCreationResponse.url
            log.info { "Application ${app.id} created successfully. State: $lambdaCreationResponse" }
        } catch (e: AWSException) {
            app.state = ApplicationState.CREATE_FAILED
            app.error = e.message
            log.error("Error occurred during application ${app.id} creation.", e)
        }
        app.updatedAt = now()
        applicationRepository.save(app)
    }

    @Transactional
    fun update(
        applicationName: String,
    ) {
        val app = applicationRepository.findByIdOrNull(applicationName)
            ?: throw APIException.ApplicationNotFoundException(applicationName)
        try {
            val lambdaState = awsFacadeService.updateLambda(
                functionName = app.functionName,
                s3Key = app.s3Key,
                s3Bucket = app.s3Bucket,
            )
            app.state = lambdaState
            log.info { "Application ${app.id} updated successfully. State: $lambdaState" }
        } catch (e: AWSException) {
            app.state = ApplicationState.UPDATE_FAILED
            app.error = e.message
            log.error("Error occurred during application ${app.id} update.", e)
        }
        app.updatedAt = now()
        applicationRepository.save(app)
    }

    @Transactional
    fun delete(
        applicationName: String,
    ) {
        val app = applicationRepository.findByIdOrNull(applicationName)
            ?: throw APIException.ApplicationNotFoundException(applicationName)
        try {
            awsFacadeService.deleteLambda(app.functionName)
            app.state = ApplicationState.DELETED
            log.info { "Application ${app.id} deleted successfully." }
        } catch (e: AWSException) {
            app.state = ApplicationState.DELETE_FAILED
            app.error = e.message
            log.error("Error occurred during application ${app.id} delete.", e)
        }
        app.updatedAt = now()

        applicationRepository.save(app)
    }
}
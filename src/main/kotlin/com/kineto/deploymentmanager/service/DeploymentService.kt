package com.kineto.deploymentmanager.service

import com.kineto.deploymentmanager.dto.LambdaState
import com.kineto.deploymentmanager.exception.APIException
import com.kineto.deploymentmanager.exception.AWSException
import com.kineto.deploymentmanager.model.Application
import com.kineto.deploymentmanager.model.ApplicationState
import com.kineto.deploymentmanager.model.ApplicationState.*
import com.kineto.deploymentmanager.model.Operation
import com.kineto.deploymentmanager.model.PendingDeployment
import com.kineto.deploymentmanager.repository.ApplicationRepository
import com.kineto.deploymentmanager.repository.PendingDeploymentRepository
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
    private val pendingDeploymentRepository: PendingDeploymentRepository,
) {

    @Transactional
    fun create(
        applicationName: String,
    ) {
        val app = applicationRepository.findByIdOrNull(applicationName)
            ?: throw APIException.ApplicationNotFoundException(applicationName)
        try {
            val lambdaCreationResponse = awsFacadeService.createLambdaAsync(
                applicationName = applicationName,
                functionName = app.functionName,
                s3Key = app.s3Key,
                s3Bucket = app.s3Bucket,
            )
            val state = when (lambdaCreationResponse.state) {
                LambdaState.ACTIVE -> ACTIVE
                LambdaState.PENDING -> CREATING
                LambdaState.FAILED -> CREATE_FAILED
            }
            app.state = state
            app.url = lambdaCreationResponse.url
            app.error = lambdaCreationResponse.error
            log.info { "Application ${app.id} created. State: ${lambdaCreationResponse.state}" }
        } catch (e: AWSException) {
            app.state = CREATE_FAILED
            app.error = e.message
            log.error("Error occurred during application ${app.id} creation.", e)
        }
        app.updatedAt = now()
        applicationRepository.save(app)
        if (app.state == CREATING) {
            pendingDeploymentRepository.save(
                PendingDeployment(
                    id = app.id,
                    functionName = app.functionName,
                    operation = Operation.CREATE
                )
            )
        }
    }

    @Transactional
    fun update(
        applicationName: String,
    ) {
        val app = applicationRepository.findByIdOrNull(applicationName)
            ?: throw APIException.ApplicationNotFoundException(applicationName)
        try {
            val lambdaCreationResponse = awsFacadeService.updateLambdaAsync(
                functionName = app.functionName,
                s3Key = app.s3Key,
                s3Bucket = app.s3Bucket,
            )
            val state = when (lambdaCreationResponse.state) {
                LambdaState.ACTIVE -> ACTIVE
                LambdaState.PENDING -> UPDATING
                LambdaState.FAILED -> UPDATE_FAILED
            }
            app.state = state
            app.error = lambdaCreationResponse.error
            log.info { "Application ${app.id} updated. State: ${app.state}" }
        } catch (e: AWSException.ResourceNotFoundException) {
            log.error("Application ${app.id} has been deleted.", e)
            app.state = DELETED
        } catch (e: AWSException) {
            app.state = UPDATE_FAILED
            app.error = e.message
            log.error("Error occurred during application ${app.id} update.", e)
        }
        app.updatedAt = now()
        applicationRepository.save(app)
        if (app.state == UPDATING) {
            pendingDeploymentRepository.save(
                PendingDeployment(
                    id = app.id,
                    functionName = app.functionName,
                    operation = Operation.UPDATE
                )
            )
        }
    }

    @Transactional
    fun delete(
        applicationName: String,
    ) {
        val app = applicationRepository.findByIdOrNull(applicationName)
            ?: throw APIException.ApplicationNotFoundException(applicationName)
        try {
            awsFacadeService.deleteLambda(app.functionName)
            app.state = DELETED
            log.info { "Application ${app.id} has been deleted successfully." }
        } catch (e: AWSException.ResourceNotFoundException) {
            log.error("Application ${app.id} has been already deleted.", e)
            app.state = DELETED
        } catch (e: AWSException) {
            app.state = DELETE_FAILED
            app.error = e.message
            log.error("Error occurred during application ${app.id} delete.", e)
        }
        app.updatedAt = now()

        applicationRepository.save(app)
    }

    @Transactional
    fun ensureActive(
        app: Application,
        operation: Operation,
    ): LambdaState {
        log.info("Checking status of Lambda ${app.functionName}")
        try {
            val lambdaStateResponse = awsFacadeService.getLambdaState(app.functionName)
            val state = lambdaStateResponse.state
            log.info("State of Lambda ${app.functionName}: $state")
            when (state) {
                LambdaState.ACTIVE -> {
                    app.state = ACTIVE
                }

                LambdaState.PENDING -> {
                    app.state = if (operation == Operation.CREATE) CREATING else UPDATING
                }

                else -> {
                    app.state = if (operation == Operation.CREATE) CREATE_FAILED else UPDATE_FAILED
                    app.error = lambdaStateResponse.reason
                }
            }
            app.updatedAt = now()
            applicationRepository.save(app)

            return state
        } catch (e: AWSException) {
            app.state = if (operation == Operation.CREATE) CREATE_FAILED else UPDATE_FAILED
            app.error = e.message
            app.updatedAt = now()
            applicationRepository.save(app)
            return LambdaState.FAILED
        }
    }
}
package com.kineto.deploymentmanager.service

import com.kineto.deploymentmanager.dto.LambdaState
import com.kineto.deploymentmanager.model.Application
import com.kineto.deploymentmanager.model.ApplicationState.CREATE_FAILED
import com.kineto.deploymentmanager.model.ApplicationState.UPDATE_FAILED
import com.kineto.deploymentmanager.model.Operation
import com.kineto.deploymentmanager.model.PendingDeployment
import com.kineto.deploymentmanager.repository.ApplicationRepository
import com.kineto.deploymentmanager.repository.PendingDeploymentRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant.now

private val log = LoggerFactory.getLogger(DeploymentPollingService::class.java)

@Component
class DeploymentPollingService(
    private val applicationRepository: ApplicationRepository,
    private val pendingDeploymentRepository: PendingDeploymentRepository,
    private val deploymentService: DeploymentService,
) {

    @Scheduled(fixedRate = 1_000)
    fun pollDeployments() {
        val pendingDeployments = pendingDeploymentRepository.findAll(POLLING_BATCH_SIZE)
        val applications = applicationRepository.findAllByIdIn(pendingDeployments.map { it.id })
            .associateBy { it.id }
        pendingDeployments.forEach {
            val app = applications[it.id]!!
            val lambdaState = deploymentService.ensureActive(app, it.operation)
            if (lambdaState == LambdaState.PENDING) {
                if (it.attempts < it.maxAttempts) {
                    rescheduleDeploymentCheck(it)
                } else {
                    handleMaxAttemptsExceeded(app, it)
                }
            } else {
                pendingDeploymentRepository.delete(it)
            }
        }
    }

    private fun handleMaxAttemptsExceeded(
        app: Application,
        deployment: PendingDeployment,
    ) {
        val failedState = when (deployment.operation) {
            Operation.UPDATE -> UPDATE_FAILED
            Operation.CREATE -> CREATE_FAILED
        }
        app.state = failedState
        app.error = "Couldn't deploy app ${deployment.id}, max attempts exceeded"
        app.updatedAt = now()
        applicationRepository.save(app)
        pendingDeploymentRepository.delete(deployment)
    }

    private fun rescheduleDeploymentCheck(deployment: PendingDeployment) {
        deployment.attempts++
        deployment.isLocked = false
        deployment.updatedAt = now()
        log.info("Lambda ${deployment.functionName} is not ready yet")
        pendingDeploymentRepository.save(deployment)
    }

    companion object {
        private const val POLLING_BATCH_SIZE = 100
    }
}
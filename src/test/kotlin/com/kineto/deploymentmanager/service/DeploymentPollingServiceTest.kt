package com.kineto.deploymentmanager.service

import com.kineto.deploymentmanager.dto.LambdaState
import com.kineto.deploymentmanager.model.Application
import com.kineto.deploymentmanager.model.ApplicationState.CREATE_FAILED
import com.kineto.deploymentmanager.model.ApplicationState.CREATING
import com.kineto.deploymentmanager.model.Operation
import com.kineto.deploymentmanager.model.PendingDeployment
import com.kineto.deploymentmanager.repository.ApplicationRepository
import com.kineto.deploymentmanager.repository.PendingDeploymentRepository
import com.kineto.deploymentmanager.testfixtures.application
import com.kineto.deploymentmanager.testfixtures.deployment
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Captor
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class DeploymentPollingServiceTest(
    @param:Mock private val applicationRepository: ApplicationRepository,
    @param:Mock private val pendingDeploymentRepository: PendingDeploymentRepository,
    @param:Mock private val deploymentService: DeploymentService,
) {
    @Captor
    lateinit var captor: ArgumentCaptor<Application>

    @Captor
    lateinit var deploymentCaptor: ArgumentCaptor<PendingDeployment>

    @InjectMocks
    lateinit var service: DeploymentPollingService

    @Test
    fun `should delete pending deployment when lambda is ACTIVE`() {
        val deployment = deployment(Operation.CREATE, 0)
        val app = application(CREATING)
        `when`(pendingDeploymentRepository.findAll(100))
            .thenReturn(listOf(deployment))
        `when`(applicationRepository.findAllByIdIn(listOf(deployment.id)))
            .thenReturn(listOf(app))
        `when`(deploymentService.ensureActive(app, Operation.CREATE))
            .thenReturn(LambdaState.ACTIVE)

        service.pollDeployments()

        verify(pendingDeploymentRepository).delete(deployment)
        verify(pendingDeploymentRepository, never()).save(any())
    }

    @Test
    fun `should reschedule pending deployment when lambda is PENDING`() {
        val deployment = deployment(Operation.CREATE, 0)
        val app = application(CREATING)
        `when`(pendingDeploymentRepository.findAll(100))
            .thenReturn(listOf(deployment))
        `when`(applicationRepository.findAllByIdIn(listOf(deployment.id)))
            .thenReturn(listOf(app))
        `when`(deploymentService.ensureActive(app, Operation.CREATE))
            .thenReturn(LambdaState.PENDING)

        service.pollDeployments()

        verify(pendingDeploymentRepository, never()).delete(any())
        verify(pendingDeploymentRepository).save(deploymentCaptor.capture())

        val updatedDeployment = deploymentCaptor.value
        assertEquals(1, updatedDeployment.attempts)
        assertFalse(updatedDeployment.isLocked)
    }

    @Test
    fun `should set FAILED state when max attempts exceeded`() {
        val deployment = deployment(Operation.CREATE, 60)
        val app = application(CREATING)
        `when`(pendingDeploymentRepository.findAll(100))
            .thenReturn(listOf(deployment))
        `when`(applicationRepository.findAllByIdIn(listOf(deployment.id)))
            .thenReturn(listOf(app))
        `when`(deploymentService.ensureActive(app, Operation.CREATE))
            .thenReturn(LambdaState.PENDING)

        service.pollDeployments()

        verify(pendingDeploymentRepository).delete(deployment)
        verify(pendingDeploymentRepository, never()).save(any())

        verify(applicationRepository).save(captor.capture())
        val updatedApp = captor.value
        assertEquals(CREATE_FAILED, updatedApp.state)
        assertEquals(
            "Couldn't deploy app test-app, max attempts exceeded",
            updatedApp.error
        )
    }
}
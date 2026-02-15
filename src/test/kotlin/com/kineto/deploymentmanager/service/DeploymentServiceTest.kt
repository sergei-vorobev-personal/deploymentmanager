package com.kineto.deploymentmanager.service

import com.kineto.deploymentmanager.exception.APIException
import com.kineto.deploymentmanager.exception.AWSException
import com.kineto.deploymentmanager.model.Application
import com.kineto.deploymentmanager.model.ApplicationState.*
import com.kineto.deploymentmanager.repository.ApplicationRepository
import com.kineto.deploymentmanager.testfixtures.application
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.util.*
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class DeploymentServiceTest(
    @param:Mock private val awsFacadeService: AWSFacadeService,
    @param:Mock private val applicationRepository: ApplicationRepository,
) {
    @Captor
    lateinit var captor: ArgumentCaptor<Application>

    @InjectMocks
    lateinit var service: DeploymentService

    @Test
    fun `create should create lambda and update state`() {
        val app = application(CREATE_REQUESTED)
        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.of(app))
        `when`(
            awsFacadeService.createLambda(app.functionName, app.s3Key, app.s3Bucket)
        ).thenReturn(ACTIVE)

        service.create("test-app")

        verify(applicationRepository).save(captor.capture())
        val updatedApp = captor.allValues.first()

        assertEquals(ACTIVE, updatedApp.state)
    }

    @Test
    fun `create should set state CREATE_FAILED when couldn't create lambda`() {
        val app = application(CREATE_REQUESTED)
        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.of(app))
        `when`(
            awsFacadeService.createLambda(app.functionName, app.s3Key, app.s3Bucket)
        ).thenThrow(AWSException.LambdaException("aws error"))

        service.create("test-app")

        verify(applicationRepository).save(captor.capture())
        val updatedApp = captor.allValues.first()

        assertEquals(CREATE_FAILED, updatedApp.state)
        assertEquals("AWS Lambda error: aws error", updatedApp.error)
    }

    @Test
    fun `create should throw ApplicationNotFoundException when application does not exist`() {
        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.empty())
        val exception = assertThrows<APIException.ApplicationNotFoundException> { service.create("test-app") }
        assertEquals("Application test-app not found.", exception.message)
    }

    @Test
    fun `update should update lambda and update state`() {
        val app = application(UPDATE_REQUESTED)
        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.of(app))
        `when`(
            awsFacadeService.updateLambda(app.functionName, app.s3Key, app.s3Bucket)
        ).thenReturn(ACTIVE)

        service.update("test-app")

        verify(applicationRepository).save(captor.capture())
        val updatedApp = captor.allValues.first()

        assertEquals(ACTIVE, updatedApp.state)
    }

    @Test
    fun `update should set state UPDATE_FAILED when couldn't update lambda`() {
        val app = application(UPDATE_REQUESTED)
        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.of(app))
        `when`(
            awsFacadeService.updateLambda(app.functionName, app.s3Key, app.s3Bucket)
        ).thenThrow(AWSException.LambdaException("aws error"))

        service.update("test-app")

        verify(applicationRepository).save(captor.capture())
        val updatedApp = captor.allValues.first()

        assertEquals(UPDATE_FAILED, updatedApp.state)
        assertEquals("AWS Lambda error: aws error", updatedApp.error)
    }

    @Test
    fun `update should throw ApplicationNotFoundException when application does not exist`() {
        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.empty())
        val exception = assertThrows<APIException.ApplicationNotFoundException> { service.update("test-app") }
        assertEquals("Application test-app not found.", exception.message)
    }

    @Test
    fun `delete should delete lambda and update state`() {
        val app = application(DELETE_REQUESTED)
        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.of(app))

        service.delete("test-app")

        verify(awsFacadeService).deleteLambda(app.functionName)
        verify(applicationRepository).save(captor.capture())
        val updatedApp = captor.allValues.first()

        assertEquals(DELETED, updatedApp.state)
    }

    @Test
    fun `delete should set state DELETE_FAILED when couldn't delete lambda`() {
        val app = application(DELETE_REQUESTED)
        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.of(app))
        `when`(
            awsFacadeService.deleteLambda(app.functionName)
        ).thenThrow(AWSException.LambdaException("aws error"))

        service.delete("test-app")

        verify(applicationRepository).save(captor.capture())
        val updatedApp = captor.allValues.first()

        assertEquals(DELETE_FAILED, updatedApp.state)
        assertEquals("AWS Lambda error: aws error", updatedApp.error)
    }

    @Test
    fun `delete should throw ApplicationNotFoundException when application does not exist`() {
        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.empty())
        val exception = assertThrows<APIException.ApplicationNotFoundException> { service.delete("test-app") }
        assertEquals("Application test-app not found.", exception.message)
    }
}
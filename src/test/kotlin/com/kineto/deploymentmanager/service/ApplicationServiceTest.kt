package com.kineto.deploymentmanager.service

import com.kineto.deploymentmanager.exception.APIException
import com.kineto.deploymentmanager.messaging.ApplicationEvent
import com.kineto.deploymentmanager.messaging.ApplicationEventType
import com.kineto.deploymentmanager.messaging.KafkaProducer
import com.kineto.deploymentmanager.model.Application
import com.kineto.deploymentmanager.model.ApplicationState
import com.kineto.deploymentmanager.model.ApplicationState.*
import com.kineto.deploymentmanager.repository.ApplicationRepository
import com.kineto.deploymentmanager.testfixtures.application
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import java.util.*

@ExtendWith(MockitoExtension::class)
class ApplicationServiceTest(
    @param:Mock private val kafkaProducer: KafkaProducer,
    @param:Mock private val applicationRepository: ApplicationRepository,
) {
    @Captor
    lateinit var captor: ArgumentCaptor<Application>

    @InjectMocks
    private lateinit var applicationService: ApplicationService

    @Test
    fun `requestDeployment creates new application in DB if not exists and sends kafka creation event`() {
        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.empty())
        `when`(applicationRepository.save(any(Application::class.java))).thenAnswer { it.arguments[0] }

        val response = applicationService.requestDeployment("test-app", "key", "bucket")

        assertEquals(CREATE_REQUESTED, response.state)
        verify(kafkaProducer)
            .sendApplicationEvent("test-app", ApplicationEvent("test-app", ApplicationEventType.CREATE_REQUESTED))
        verify(applicationRepository).save(captor.capture())
        val app = captor.value
        assertEquals(CREATE_REQUESTED, app.state)
        assertEquals("key", app.s3Key)
        assertEquals("bucket", app.s3Bucket)
        assertTrue(app.functionName.startsWith("test-app-"))
    }

    @ParameterizedTest
    @EnumSource(ApplicationState::class, names = ["NEW", "DELETED", "CREATE_FAILED"])
    fun `requestDeployment sends kafka create event and updates application state`(state: ApplicationState) {
        val application = application(state)
        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.of(application))

        val response = applicationService.requestDeployment("test-app", "key", "bucket")

        assertEquals(CREATE_REQUESTED, response.state)
        verify(kafkaProducer)
            .sendApplicationEvent("test-app", ApplicationEvent("test-app", ApplicationEventType.CREATE_REQUESTED))
        verify(applicationRepository).save(captor.capture())

        val reCreatedApp = captor.value
        assertEquals(CREATE_REQUESTED, reCreatedApp.state)
        assertEquals("key", reCreatedApp.s3Key)
        assertEquals("bucket", reCreatedApp.s3Bucket)
        assertEquals("bucket", reCreatedApp.s3Bucket)
        assertEquals(application.functionName, reCreatedApp.functionName)
    }

    @ParameterizedTest
    @EnumSource(ApplicationState::class, names = ["ACTIVE", "UPDATE_FAILED"])
    fun `requestDeployment sends kafka update event and updates application state`(state: ApplicationState) {
        val application = application(state)
        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.of(application))

        val response = applicationService.requestDeployment("test-app", "key", "bucket")

        assertEquals(UPDATE_REQUESTED, response.state)
        verify(kafkaProducer)
            .sendApplicationEvent("test-app", ApplicationEvent("test-app", ApplicationEventType.UPDATE_REQUESTED))

        verify(applicationRepository).save(captor.capture())

        val updatedApp = captor.value
        assertEquals(UPDATE_REQUESTED, updatedApp.state)
        assertEquals("key", updatedApp.s3Key)
        assertEquals("bucket", updatedApp.s3Bucket)
        assertEquals("bucket", updatedApp.s3Bucket)
        assertEquals(application.functionName, updatedApp.functionName)
    }

    @ParameterizedTest
    @EnumSource(
        ApplicationState::class,
        names = ["CREATE_REQUESTED", "UPDATE_REQUESTED", "DELETE_REQUESTED", "CREATING", "UPDATING"]
    )
    fun `requestDeployment throws DeploymentInProgressException for in-progress state`(state: ApplicationState) {
        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.of(application(state)))

        val exception = assertThrows<APIException.DeploymentInProgressException> {
            applicationService.requestDeployment("test-app", "key", "bucket")
        }
        assertEquals("Deployment in progress: test-app", exception.message)

        verifyNoInteractions(kafkaProducer)
    }

    @Test
    fun `getStatus returns application status`() {
        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.of(application(ACTIVE)))

        val response = applicationService.getStatus("test-app")

        assertEquals("test-app", response.name)
        assertEquals(ACTIVE, response.state)

        verifyNoInteractions(kafkaProducer)
    }

    @Test
    fun `getStatus throws ApplicationNotFoundException when application not found`() {
        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.empty())

        val exception = assertThrows<APIException.ApplicationNotFoundException> {
            applicationService.getStatus("test-app")
        }

        assertEquals("Application test-app not found.", exception.message)
        verifyNoInteractions(kafkaProducer)
    }

    @Test
    fun `requestDeletion sends delete event and updates state`() {
        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.of(application(ACTIVE)))
        `when`(applicationRepository.save(any(Application::class.java))).thenAnswer { it.arguments[0] }

        val response = applicationService.requestDeletion("test-app")

        assertEquals(DELETE_REQUESTED, response.state)
        verify(kafkaProducer)
            .sendApplicationEvent("test-app", ApplicationEvent("test-app", ApplicationEventType.DELETE_REQUESTED))
    }

    @Test
    fun `requestDeletion throws ApplicationNotFoundException when application not found`() {
        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.empty())

        val exception = assertThrows<APIException.ApplicationNotFoundException> {
            applicationService.requestDeletion("test-app")
        }

        assertEquals("Application test-app not found.", exception.message)
    }
}
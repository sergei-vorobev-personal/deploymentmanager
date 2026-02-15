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
import com.kineto.deploymentmanager.testfixtures.lambdaResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import org.springframework.util.MultiValueMapAdapter
import java.util.*

@ExtendWith(MockitoExtension::class)
class ApplicationServiceTest(
    @param:Mock private val awsFacadeService: AWSFacadeService,
    @param:Mock private val kafkaProducer: KafkaProducer,
    @param:Mock private val applicationRepository: ApplicationRepository,
) {
    @InjectMocks
    private lateinit var applicationService: ApplicationService

    @Test
    fun `invoke calls awsFacadeService and returns Lambda response`() {
        val app = application(ACTIVE)
        val params = MultiValueMapAdapter(mapOf("param1" to listOf("paramValue")))
        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.of(app))
        `when`(awsFacadeService.invokeLambda(app.functionName, params)).thenReturn(lambdaResponse(200, "ok"))

        val response =
            applicationService.invoke("test-app", params)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("ok", response.body)
        assertEquals(1, response.headers.size())
        assertEquals("application/json", response.headers["Content-Type"]!!.first())

        verify(awsFacadeService).invokeLambda(app.functionName, params)
        verifyNoMoreInteractions(awsFacadeService)
        verifyNoInteractions(kafkaProducer)
    }

    @Test
    fun `invoke throws exception when application not found`() {
        val params = MultiValueMapAdapter(mapOf("param1" to listOf("paramValue")))

        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.empty())

        val exception = assertThrows<APIException.ApplicationNotFoundException> {
            applicationService.invoke("test-app", params)
        }
        assertEquals("Application test-app not found.", exception.message)

        verifyNoInteractions(kafkaProducer, awsFacadeService)
    }

    @Test
    fun `invoke returns 404 if application deleted`() {
        val params = MultiValueMapAdapter(mapOf("param1" to listOf("paramValue")))

        `when`(applicationRepository.findById("test-app"))
            .thenReturn(Optional.of(application(DELETED)))

        val response = applicationService.invoke("test-app", params)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(response.body!!, "Application test-app has been deleted")

        verifyNoInteractions(awsFacadeService, kafkaProducer)
    }

    @Test
    fun `invoke returns 503 if application not ready`() {
        val params = MultiValueMapAdapter(mapOf("param1" to listOf("paramValue")))

        `when`(applicationRepository.findById("test-app"))
            .thenReturn(Optional.of(application(UPDATE_REQUESTED)))

        val response = applicationService.invoke("test-app", params)

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
        assertEquals(response.body!!, "Application test-app is not ready yet")

        verifyNoInteractions(awsFacadeService, kafkaProducer)
    }

    @Test
    fun `invoke returns 500 if application not available`() {
        val params = MultiValueMapAdapter(mapOf("param1" to listOf("paramValue")))

        `when`(applicationRepository.findById("test-app"))
            .thenReturn(Optional.of(application(FAILED)))

        val response = applicationService.invoke("test-app", params)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals(response.body!!, "Application test-app is not available")

        verifyNoInteractions(awsFacadeService, kafkaProducer)
    }

    @Test
    fun `requestDeployment creates new application in DB if not exists and sends kafka creation event`() {
        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.empty())
        `when`(applicationRepository.save(any(Application::class.java))).thenAnswer { it.arguments[0] }

        val response = applicationService.requestDeployment("test-app", "key", "bucket")

        assertEquals(CREATE_REQUESTED, response.state)
        verify(kafkaProducer)
            .sendApplicationEvent("test-app", ApplicationEvent("test-app", ApplicationEventType.CREATE_REQUESTED))

        verifyNoInteractions(awsFacadeService)
    }

    @ParameterizedTest
    @EnumSource(ApplicationState::class, names = ["NEW", "DELETED", "CREATE_FAILED", "FAILED", "INACTIVE"])
    fun `requestDeployment sends kafka create event and updates application state`(state: ApplicationState) {
        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.of(application(state)))

        val response = applicationService.requestDeployment("test-app", "key", "bucket")

        assertEquals(CREATE_REQUESTED, response.state)
        verify(kafkaProducer)
            .sendApplicationEvent("test-app", ApplicationEvent("test-app", ApplicationEventType.CREATE_REQUESTED))

        verifyNoInteractions(awsFacadeService)
    }

    @ParameterizedTest
    @EnumSource(ApplicationState::class, names = ["ACTIVE", "UPDATE_FAILED"])
    fun `requestDeployment sends kafka update event and updates application state`(state: ApplicationState) {
        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.of(application(state)))

        val response = applicationService.requestDeployment("test-app", "key", "bucket")

        assertEquals(UPDATE_REQUESTED, response.state)
        verify(kafkaProducer)
            .sendApplicationEvent("test-app", ApplicationEvent("test-app", ApplicationEventType.UPDATE_REQUESTED))

        verifyNoInteractions(awsFacadeService)
    }

    @ParameterizedTest
    @EnumSource(
        ApplicationState::class,
        names = ["CREATE_REQUESTED", "UPDATE_REQUESTED", "DELETE_REQUESTED", "PENDING"]
    )
    fun `requestDeployment throws DeploymentInProgressException for in-progress state`(state: ApplicationState) {
        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.of(application(state)))

        val exception = assertThrows<APIException.DeploymentInProgressException> {
            applicationService.requestDeployment("test-app", "key", "bucket")
        }
        assertEquals("Deployment in progress: test-app", exception.message)

        verifyNoInteractions(kafkaProducer, awsFacadeService)
    }

    @Test
    fun `getStatus returns application status`() {
        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.of(application(ACTIVE)))

        val response = applicationService.getStatus("test-app")

        assertEquals("test-app", response.name)
        assertEquals(ACTIVE, response.state)

        verifyNoInteractions(kafkaProducer, awsFacadeService)
    }

    @Test
    fun `getStatus throws ApplicationNotFoundException when application not found`() {
        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.empty())

        val exception = assertThrows<APIException.ApplicationNotFoundException> {
            applicationService.getStatus("test-app")
        }

        assertEquals("Application test-app not found.", exception.message)
        verifyNoInteractions(kafkaProducer, awsFacadeService)
    }

    @Test
    fun `requestDeletion sends delete event and updates state`() {
        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.of(application(ACTIVE)))
        `when`(applicationRepository.save(any(Application::class.java))).thenAnswer { it.arguments[0] }

        val response = applicationService.requestDeletion("test-app")

        assertEquals(DELETE_REQUESTED, response.state)
        verify(kafkaProducer)
            .sendApplicationEvent("test-app", ApplicationEvent("test-app", ApplicationEventType.DELETE_REQUESTED))
        verifyNoInteractions(awsFacadeService)
    }

    @Test
    fun `requestDeletion throws ApplicationNotFoundException when application not found`() {
        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.empty())

        val exception = assertThrows<APIException.ApplicationNotFoundException> {
            applicationService.requestDeletion("test-app")
        }

        assertEquals("Application test-app not found.", exception.message)
        verifyNoInteractions(kafkaProducer, awsFacadeService)
    }
}
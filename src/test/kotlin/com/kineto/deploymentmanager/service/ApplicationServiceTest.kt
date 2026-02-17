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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import org.springframework.util.MultiValueMapAdapter
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.util.*

@ExtendWith(MockitoExtension::class)
class ApplicationServiceTest(
    @param:Mock private val kafkaProducer: KafkaProducer,
    @param:Mock private val applicationRepository: ApplicationRepository,
) {

    private val webClient = WebClient.builder().exchangeFunction(shortCircuitingExchangeFunction).build()

    @Captor
    lateinit var captor: ArgumentCaptor<Application>


    private val applicationService = ApplicationService(webClient, kafkaProducer, applicationRepository)

    @Test
    fun `callLambda calls webClient and returns Lambda response`() {
        val app = application(ACTIVE)
        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.of(app))

        val response = applicationService.callLambda("test-app")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("ok", response.body)
        assertEquals(1, response.headers.size())
        assertEquals("application/json", response.headers["Content-Type"]!!.first())

        verifyNoInteractions(kafkaProducer)
    }

    @Test
    fun `invoke throws exception when application not found`() {
        val params = MultiValueMapAdapter(mapOf("param1" to listOf("paramValue")))

        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.empty())

        val exception = assertThrows<APIException.ApplicationNotFoundException> {
            applicationService.callLambda("test-app")
        }
        assertEquals("Application test-app not found.", exception.message)

        verifyNoInteractions(kafkaProducer)
    }

    @Test
    fun `invoke returns 404 if application deleted`() {
        `when`(applicationRepository.findById("test-app"))
            .thenReturn(Optional.of(application(DELETED)))

        val response = applicationService.callLambda("test-app")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(response.body!!, "Application test-app has been deleted")

        verifyNoInteractions(kafkaProducer)
    }

    @Test
    fun `invoke returns 503 if application not ready`() {
        `when`(applicationRepository.findById("test-app"))
            .thenReturn(Optional.of(application(UPDATE_REQUESTED)))

        val response = applicationService.callLambda("test-app")

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
        assertEquals(response.body!!, "Application test-app is not ready yet")

        verifyNoInteractions(kafkaProducer)
    }

    @Test
    fun `invoke returns 500 if application not available`() {
        `when`(applicationRepository.findById("test-app"))
            .thenReturn(Optional.of(application(FAILED)))

        val response = applicationService.callLambda("test-app")

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals(response.body!!, "Application test-app is not available")

        verifyNoInteractions(kafkaProducer)
    }

    @Test
    fun `requestDeployment creates new application in DB if not exists and sends kafka creation event`() {
        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.empty())
        `when`(applicationRepository.save(any(Application::class.java))).thenAnswer { it.arguments[0] }

        val response = applicationService.requestDeployment("test-app", "key", "bucket")

        assertEquals(CREATE_REQUESTED, response.state)
        verify(kafkaProducer)
            .sendApplicationEvent("test-app", ApplicationEvent("test-app", ApplicationEventType.CREATE_REQUESTED))
    }

    @ParameterizedTest
    @EnumSource(ApplicationState::class, names = ["NEW", "DELETED", "CREATE_FAILED", "FAILED", "INACTIVE"])
    fun `requestDeployment sends kafka create event and updates application state`(state: ApplicationState) {
        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.of(application(state)))

        val response = applicationService.requestDeployment("test-app", "key", "bucket")

        assertEquals(CREATE_REQUESTED, response.state)
        verify(kafkaProducer)
            .sendApplicationEvent("test-app", ApplicationEvent("test-app", ApplicationEventType.CREATE_REQUESTED))
        verify(applicationRepository).save(captor.capture())

        val updatedApp = captor.value
        assertEquals("key", updatedApp.s3Key)
        assertEquals("bucket", updatedApp.s3Bucket)
    }

    @ParameterizedTest
    @EnumSource(ApplicationState::class, names = ["ACTIVE", "UPDATE_FAILED"])
    fun `requestDeployment sends kafka update event and updates application state`(state: ApplicationState) {
        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.of(application(state)))

        val response = applicationService.requestDeployment("test-app", "key", "bucket")

        assertEquals(UPDATE_REQUESTED, response.state)
        verify(kafkaProducer)
            .sendApplicationEvent("test-app", ApplicationEvent("test-app", ApplicationEventType.UPDATE_REQUESTED))
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

    companion object {
        val clientResponse: ClientResponse = ClientResponse
            .create(HttpStatus.OK)
            .header("Content-Type", "application/json")
            .body("ok").build()
        val shortCircuitingExchangeFunction = ExchangeFunction {
            Mono.just(clientResponse)
        }
    }
}
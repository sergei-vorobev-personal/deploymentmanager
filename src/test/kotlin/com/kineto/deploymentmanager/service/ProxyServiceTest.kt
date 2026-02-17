package com.kineto.deploymentmanager.service

import com.kineto.deploymentmanager.exception.APIException
import com.kineto.deploymentmanager.model.Application
import com.kineto.deploymentmanager.model.ApplicationState.*
import com.kineto.deploymentmanager.repository.ApplicationRepository
import com.kineto.deploymentmanager.testfixtures.application
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
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
class ProxyServiceTest(
    @param:Mock private val applicationRepository: ApplicationRepository,
) {

    private val webClient = WebClient.builder().exchangeFunction(shortCircuitingExchangeFunction).build()

    private val proxyService = ProxyService(webClient, applicationRepository)

    @Test
    fun `callLambda calls webClient and returns Lambda response`() {
        val app = application(ACTIVE)
        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.of(app))

        val response = proxyService.callLambda("test-app", "/sub", params)!!

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("ok", response.body)
        assertEquals(1, response.headers.size())
        assertEquals("application/json", response.headers["Content-Type"]!!.first())
    }

    @Test
    fun `invoke throws exception when application not found`() {
        `when`(applicationRepository.findById("test-app")).thenReturn(Optional.empty())

        val exception = assertThrows<APIException.ApplicationNotFoundException> {
            proxyService.callLambda("test-app", "/sub", params)
        }
        assertEquals("Application test-app not found.", exception.message)
    }

    @Test
    fun `invoke returns 404 if application deleted`() {
        `when`(applicationRepository.findById("test-app"))
            .thenReturn(Optional.of(application(DELETED)))

        val response = proxyService.callLambda("test-app", "/sub", params)!!

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(response.body!!, "Application test-app has been deleted")
    }

    @Test
    fun `invoke returns 503 if application not ready`() {
        `when`(applicationRepository.findById("test-app"))
            .thenReturn(Optional.of(application(UPDATE_REQUESTED)))

        val response = proxyService.callLambda("test-app", "/sub", params)!!

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
        assertEquals(response.body!!, "Application test-app is not ready yet")
    }

    @Test
    fun `invoke returns 500 if application not available`() {
        `when`(applicationRepository.findById("test-app"))
            .thenReturn(Optional.of(application(FAILED)))

        val response = proxyService.callLambda("test-app", "/sub", params)!!

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals(response.body!!, "Application test-app is not available")
    }

    companion object {
        val params = MultiValueMapAdapter(mapOf("param1" to listOf("paramValue")))
        val clientResponse: ClientResponse = ClientResponse
            .create(HttpStatus.OK)
            .header("Content-Type", "application/json")
            .body("ok").build()
        val shortCircuitingExchangeFunction = ExchangeFunction {
            Mono.just(clientResponse)
        }
    }
}
package com.kineto.deploymentmanager.service

import com.kineto.deploymentmanager.exception.APIException
import com.kineto.deploymentmanager.model.ApplicationState.*
import com.kineto.deploymentmanager.repository.ApplicationRepository
import mu.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.util.MultiValueMap
import org.springframework.web.reactive.function.client.WebClient
import software.amazon.awssdk.http.HttpStatusCode.NOT_FOUND


private val log = KotlinLogging.logger {}

@Service
class ProxyService(
    private val webClient: WebClient,
    private val applicationRepository: ApplicationRepository,
) {

    fun callLambda(
        name: String,
        subpath: String,
        params: MultiValueMap<String, String>,
    ): ResponseEntity<String>? {
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

        return webClient.get()
            .uri("${app.url!!}$subpath") {
                it.queryParams(params).build()
            }
            .retrieve()
            .toEntity(String::class.java)
            .block()
    }
}
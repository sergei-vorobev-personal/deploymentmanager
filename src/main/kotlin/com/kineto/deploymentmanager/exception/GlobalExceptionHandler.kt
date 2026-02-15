package com.kineto.deploymentmanager.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(APIException::class)
    fun handleAPIException(ex: APIException): ResponseEntity<Map<String, String>> {
        val status = if (ex is APIException.ApplicationNotFoundException) {
            HttpStatus.NOT_FOUND
        } else {
            HttpStatus.INTERNAL_SERVER_ERROR
        }
        return ResponseEntity.status(status)
            .body(mapOf("error" to ex.message))
    }

    @ExceptionHandler(AWSException::class)
    fun handleAWSException(ex: AWSException): ResponseEntity<Map<String, String>> {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("error" to ex.message))
    }
}
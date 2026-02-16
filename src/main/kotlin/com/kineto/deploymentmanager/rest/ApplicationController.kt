package com.kineto.deploymentmanager.rest

import com.kineto.deploymentmanager.dto.GetStatusResponse
import com.kineto.deploymentmanager.service.ApplicationService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/applications")
class ApplicationController(
    private val applicationService: ApplicationService,
) {

    @GetMapping("/{name}")
    fun invokeLambda(
        @PathVariable("name") name: String,
    ): ResponseEntity<String> = applicationService.callLambda(name)

    @PostMapping
    fun deploy(
        @RequestParam("name") name: String,
        @RequestParam("s3Key") s3Key: String,
        @RequestParam("s3Bucket") s3Bucket: String,
    ): ResponseEntity<GetStatusResponse> {
        return ResponseEntity(
            applicationService.requestDeployment(name, s3Key, s3Bucket),
            HttpStatus.ACCEPTED
        )
    }

    @GetMapping("/{name}/status")
    fun getStatus(
        @PathVariable("name") name: String,
    ): ResponseEntity<GetStatusResponse> = ResponseEntity.ok(applicationService.getStatus(name))

    @DeleteMapping("/{name}")
    fun delete(
        @PathVariable("name") name: String,
    ): ResponseEntity<GetStatusResponse> {
        return ResponseEntity(
            applicationService.requestDeletion(name),
            HttpStatus.ACCEPTED
        )
    }
}
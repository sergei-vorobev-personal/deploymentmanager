package com.kineto.deploymentmanager.rest


import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.kineto.deploymentmanager.dto.GetStatusResponse
import com.kineto.deploymentmanager.exception.APIException
import com.kineto.deploymentmanager.model.ApplicationState.*
import com.kineto.deploymentmanager.service.ApplicationService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(ApplicationController::class)
class ApplicationControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var applicationService: ApplicationService

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `deploy should return accepted with status response`() {
        val appName = "myApp"
        val s3Key = "key"
        val s3Bucket = "bucket"
        val response = GetStatusResponse("myApp", CREATE_REQUESTED)
        `when`(applicationService.requestDeployment(appName, s3Key, s3Bucket))
            .thenReturn(response)

        mockMvc.perform(
            post("/applications")
                .param("name", appName)
                .param("s3Key", s3Key)
                .param("s3Bucket", s3Bucket)
        )
            .andExpect(status().isAccepted)
            .andExpect(content().json(objectMapper.writeValueAsString(response)))
    }

    @Test
    fun `getStatus should return ok with status response`() {
        val appName = "myApp"
        val response = GetStatusResponse("myApp", ACTIVE)
        `when`(applicationService.getStatus(appName)).thenReturn(response)

        mockMvc.perform(get("/applications/$appName/status"))
            .andExpect(status().isOk)
            .andExpect(content().json(objectMapper.writeValueAsString(response)))
    }

    @Test
    fun `delete should return accepted with status response`() {
        val appName = "myApp"
        val response = GetStatusResponse("myApp", DELETE_REQUESTED)
        `when`(applicationService.requestDeletion(appName)).thenReturn(response)

        mockMvc.perform(delete("/applications/$appName"))
            .andExpect(status().isAccepted)
            .andExpect(content().json(objectMapper.writeValueAsString(response)))
    }

    @Test
    fun `handleAPIException should return correct error`() {
        `when`(applicationService.getStatus("missingApp"))
            .thenThrow(APIException.ApplicationNotFoundException("myApp"))

        mockMvc.perform(get("/applications/missingApp/status"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("Application myApp not found."))
    }

    @Test
    fun `handleAPIException should return correct error for internal server error`() {
        val appName = "myApp"
        val s3Key = "key"
        val s3Bucket = "bucket"
        `when`(applicationService.requestDeployment(appName, s3Key, s3Bucket))
            .thenThrow(APIException.DeploymentInProgressException("myApp"))

        mockMvc.perform(
            post("/applications")
                .param("name", appName)
                .param("s3Key", s3Key)
                .param("s3Bucket", s3Bucket)
        )
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.error").value("Deployment in progress: myApp"))
    }
}

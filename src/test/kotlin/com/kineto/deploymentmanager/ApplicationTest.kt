package com.kineto.deploymentmanager

import com.kineto.deploymentmanager.dto.GetStatusResponse
import com.kineto.deploymentmanager.model.ApplicationState
import com.kineto.deploymentmanager.rest.ApplicationController
import com.kineto.deploymentmanager.rest.HelperController
import com.kineto.deploymentmanager.testfixtures.MultipartFileTestUtil
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.util.MultiValueMapAdapter
import org.springframework.web.multipart.MultipartFile
import org.testcontainers.containers.DockerComposeContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File
import kotlin.test.assertEquals


@ActiveProfiles("local")
@SpringBootTest
@Testcontainers
class ApplicationTest {

    @Autowired
    private lateinit var applicationController: ApplicationController

    @Autowired
    private lateinit var helperController: HelperController

    companion object {
        @Container
        val composeContainer = DockerComposeContainer(File("./docker-compose.yaml"))
            .withExposedService("postgres", 5432)
            .withExposedService("kafka", 9092)
            .withExposedService("localstack", 4566)
    }

    @Test
    fun `should upload file, create lambda, get status, update lambda, get status, invoke lambda and delete lambda`() {
        val zipFile: MultipartFile = MultipartFileTestUtil.fromResource("sample/function.zip")
        val appName = "myApp"
        val s3Key = "s3Key"
        val s3Bucket = "deployments"

        helperController.uploadZipToS3(zipFile, s3Key, s3Bucket)

        // deploy
        val createResponseBody = applicationController.deploy(appName, s3Key, s3Bucket).body!!
        assertEquals(ApplicationState.CREATE_REQUESTED, createResponseBody.state)

        var status: GetStatusResponse? = null
        repeat(10) {
            status = applicationController.getStatus(appName).body!!
            if (status.state == ApplicationState.ACTIVE) return@repeat
            Thread.sleep(500)
        }
        assertEquals(ApplicationState.ACTIVE, status?.state)

        // update
        val updateResponseBody = applicationController.deploy(appName, s3Key, s3Bucket).body!!
        assertEquals(ApplicationState.UPDATE_REQUESTED, updateResponseBody.state)

        var updatedStatus: GetStatusResponse? = null
        repeat(10) {
            updatedStatus = applicationController.getStatus(appName).body!!
            if (updatedStatus.state == ApplicationState.ACTIVE) return@repeat
            Thread.sleep(500)
        }
        assertEquals(ApplicationState.ACTIVE, updatedStatus?.state)

        // invoke
        val lambdaResponseBody = applicationController.invoke(
            name = appName,
            params = MultiValueMapAdapter(mapOf("param1" to listOf("paramValue")))
        ).body!!
        assertEquals("Hello World from Lambda!", lambdaResponseBody)

        // delete
        val deleteResponseBody = applicationController.delete(appName).body!!
        assertEquals(ApplicationState.DELETE_REQUESTED, deleteResponseBody.state)

        var deletedStatus: GetStatusResponse? = null
        repeat(10) {
            deletedStatus = applicationController.getStatus(appName).body!!
            if (deletedStatus.state == ApplicationState.DELETED) return@repeat
            Thread.sleep(500)
        }
        assertEquals(ApplicationState.DELETED, deletedStatus?.state)
    }
}
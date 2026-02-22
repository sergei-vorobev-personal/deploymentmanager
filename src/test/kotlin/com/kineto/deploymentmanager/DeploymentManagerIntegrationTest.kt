package com.kineto.deploymentmanager

import com.kineto.deploymentmanager.dto.GetStatusResponse
import com.kineto.deploymentmanager.model.Application
import com.kineto.deploymentmanager.model.ApplicationState
import com.kineto.deploymentmanager.repository.ApplicationRepository
import com.kineto.deploymentmanager.rest.ApplicationController
import com.kineto.deploymentmanager.rest.HelperController
import com.kineto.deploymentmanager.service.ProxyService
import com.kineto.deploymentmanager.testfixtures.MultipartFileTestUtil
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
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
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertTrue


@ActiveProfiles("local")
@SpringBootTest
@Testcontainers
class DeploymentManagerIntegrationTest {

    @Autowired
    private lateinit var applicationController: ApplicationController

    @Autowired
    private lateinit var proxyService: ProxyService

    @Autowired
    private lateinit var helperController: HelperController

    @Autowired
    private lateinit var applicationRepository: ApplicationRepository

    companion object {
        @Container
        val composeContainer = DockerComposeContainer(File("./docker-compose.yaml"))
            .withExposedService("postgres", 5432)
            .withExposedService("kafka", 9092)
            .withExposedService("localstack", 4566)

        val params = MultiValueMapAdapter(mapOf<String, List<String>>())
    }

    @Test
    @Disabled
    fun `should upload file, create lambda, get status, update lambda, get status, invoke lambda and delete lambda`() {
        val zipFile: MultipartFile = MultipartFileTestUtil.fromResource("sample/helloworld.zip")
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

        // invoke
        val lambdaResponseBody = proxyService.callLambda(
            name = appName,
            subpath = "",
            params = params,
        )!!.body!!
        assertEquals("Hello World from Lambda!", lambdaResponseBody)

        val updatedZipFile: MultipartFile = MultipartFileTestUtil.fromResource("sample/helloworld-updated.zip")
        val updatedS3Key = "s3Key-1"
        // upload new file
        helperController.uploadZipToS3(updatedZipFile, updatedS3Key, s3Bucket)

        // update
        val updateResponseBody = applicationController.deploy(appName, updatedS3Key, s3Bucket).body!!
        assertEquals(ApplicationState.UPDATE_REQUESTED, updateResponseBody.state)

        var updatedStatus: GetStatusResponse? = null
        repeat(10) {
            updatedStatus = applicationController.getStatus(appName).body!!
            if (updatedStatus.state == ApplicationState.ACTIVE) return@repeat
            Thread.sleep(500)
        }
        assertEquals(ApplicationState.ACTIVE, updatedStatus?.state)


        // invoke
        val updatedLambdaResponseBody = proxyService.callLambda(
            name = appName,
            subpath = "",
            params = params,
        )!!.body!!
        assertEquals("Hello World from Lambda! Updated!", updatedLambdaResponseBody)

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

        applicationRepository.deleteAll()
    }

    @Test
    @Disabled
    fun `should deploy 1000 same hello-worlds`() {
        val zipFile: MultipartFile = MultipartFileTestUtil.fromResource("sample/helloworld.zip")
        val appName = "hello"
        val s3Key = "helloworld"
        val s3Bucket = "deployments"

        helperController.uploadZipToS3(zipFile, s3Key, s3Bucket)
        val dispatcher = Executors.newFixedThreadPool(10).asCoroutineDispatcher()

        runBlocking {
            (1..1000).map { i ->
                launch(dispatcher) {
                    val createResponseBody =
                        applicationController.deploy("$appName-$i", s3Key, s3Bucket).body!!

                    assertEquals(ApplicationState.CREATE_REQUESTED, createResponseBody.state)
                }
            }.joinAll()
        }

        dispatcher.close()
        var apps: List<Application> = listOf()
        repeat(30) {
            apps = applicationRepository.findAll()
            if (apps.all { it.state == ApplicationState.ACTIVE }) return@repeat
            Thread.sleep(5000)
        }
        assertEquals(1000, apps.size)
        assertTrue { apps.all { it.state == ApplicationState.ACTIVE } }

        val maxDeploymentDurationSec = apps.maxOfOrNull { it.updatedAt.epochSecond - it.createdAt.epochSecond }
        assertTrue(maxDeploymentDurationSec!! < 120L)
        applicationRepository.deleteAll()
    }
}
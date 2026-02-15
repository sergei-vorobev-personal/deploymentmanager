package com.kineto.deploymentmanager.testfixtures

import com.kineto.deploymentmanager.dto.LambdaResponse
import com.kineto.deploymentmanager.model.Application
import com.kineto.deploymentmanager.model.ApplicationState
import org.springframework.core.io.ClassPathResource
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.multipart.MultipartFile

val application: (ApplicationState) -> Application = { state ->
    Application(
        id = "test-app",
        functionName = "test-app-function",
        state = state,
        s3Key = "s3key",
        s3Bucket = "s3bucket"
    )
}

val lambdaResponse: (Int, String) -> LambdaResponse = { code, body ->
    LambdaResponse(
        statusCode = code,
        headers = mapOf("Content-Type" to "application/json"),
        body = body
    )
}

object MultipartFileTestUtil {

    fun fromResource(resourcePath: String, formFieldName: String = "zipFIle"): MultipartFile {
        val resource = ClassPathResource(resourcePath)

        return resource.inputStream.use { inputStream ->
            MockMultipartFile(
                formFieldName,
                resource.filename,
                "application/zip",
                inputStream
            )
        }
    }
}


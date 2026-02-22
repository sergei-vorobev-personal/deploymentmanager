package com.kineto.deploymentmanager.testfixtures

import com.kineto.deploymentmanager.model.Application
import com.kineto.deploymentmanager.model.ApplicationState
import com.kineto.deploymentmanager.model.Operation
import com.kineto.deploymentmanager.model.PendingDeployment
import org.springframework.core.io.ClassPathResource
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.multipart.MultipartFile

val application: (ApplicationState) -> Application = { state ->
    Application(
        id = "test-app",
        functionName = "test-app-function",
        state = state,
        s3Key = "s3key",
        url = "https://localhost:8080/",
        s3Bucket = "s3bucket"
    )
}

val deployment: (Operation, Int) -> PendingDeployment = { operation, attempts ->
    PendingDeployment(
        id = "test-app",
        functionName = "test-app-function",
        operation = operation,
        attempts = attempts,
        isLocked = true,
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


package com.kineto.deploymentmanager.testfixtures

import com.kineto.deploymentmanager.dto.LambdaResponse
import com.kineto.deploymentmanager.model.Application
import com.kineto.deploymentmanager.model.ApplicationState

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


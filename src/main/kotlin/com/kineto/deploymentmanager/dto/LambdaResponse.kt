package com.kineto.deploymentmanager.dto

data class LambdaResponse(
    val statusCode: Int,
    val headers: Map<String, String>?,
    val body: String?,
)
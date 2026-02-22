package com.kineto.deploymentmanager.dto

data class LambdaCreationResponse(
    val state: LambdaState,
    val url: String? = null,
    val error: String? = null,
)

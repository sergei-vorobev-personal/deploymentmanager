package com.kineto.deploymentmanager.dto

data class LambdaStateResponse(
    val state: LambdaState,
    val reason: String? = null,
)

enum class LambdaState {
    ACTIVE, PENDING, FAILED
}
package com.kineto.deploymentmanager.dto

import com.kineto.deploymentmanager.model.ApplicationState

data class LambdaCreationResponse(
    val state: ApplicationState,
    val url: String? = null,
)

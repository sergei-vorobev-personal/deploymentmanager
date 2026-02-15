package com.kineto.deploymentmanager.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.kineto.deploymentmanager.model.ApplicationState
import java.time.Instant

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GetStatusResponse(
    val name: String,
    val state: ApplicationState,
    val error: String? = null,
    val updatedAt: Instant? = null,
)

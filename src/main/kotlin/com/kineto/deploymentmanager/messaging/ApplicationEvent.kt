package com.kineto.deploymentmanager.messaging

data class ApplicationEvent(
    val applicationName: String,
    val type: ApplicationEventType,
)

enum class ApplicationEventType {
    CREATE_REQUESTED,
    UPDATE_REQUESTED,
    DELETE_REQUESTED,
}
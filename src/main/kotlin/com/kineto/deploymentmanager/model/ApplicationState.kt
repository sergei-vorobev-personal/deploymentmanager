package com.kineto.deploymentmanager.model

enum class ApplicationState {
    NEW,
    CREATE_REQUESTED,
    UPDATE_REQUESTED,
    DELETE_REQUESTED,
    DELETED,
    ACTIVE,
    FAILED,
    CREATE_FAILED,
    UPDATE_FAILED,
    DELETE_FAILED,
    INACTIVE,
    PENDING;
}
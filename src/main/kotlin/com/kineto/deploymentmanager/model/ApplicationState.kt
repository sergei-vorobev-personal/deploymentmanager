package com.kineto.deploymentmanager.model

enum class ApplicationState {
    NEW,
    CREATE_REQUESTED,
    CREATING,
    UPDATE_REQUESTED,
    UPDATING,
    DELETE_REQUESTED,
    DELETED,
    ACTIVE,
    CREATE_FAILED,
    UPDATE_FAILED,
    DELETE_FAILED,
    ;
}
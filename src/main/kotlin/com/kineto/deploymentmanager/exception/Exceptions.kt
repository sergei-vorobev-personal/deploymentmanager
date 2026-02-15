package com.kineto.deploymentmanager.exception

sealed class AWSException(override val message: String) : RuntimeException(message) {
    class LambdaQuotaExceededException(val reason: String) :
        AWSException("AWS Lambda quota exceeded: $reason")

    class ResourceNotFoundException(message: String) :
        AWSException("The resource specified in the request does not exist: $message")

    class ResourceAlreadyExistsException(message: String) :
        AWSException("The resource already exists, or another operation is in progress: $message")

    class ServiceException(message: String) :
        AWSException("The Lambda service encountered an internal error: $message")

    class LambdaException(message: String) :
        AWSException("AWS Lambda error: $message")

    class S3Exception(message: String) :
        AWSException(message)
}

sealed class APIException(override val message: String) : RuntimeException(message) {
    class ApplicationNotFoundException(name: String) :
        APIException("Application $name not found.")

    class DeploymentInProgressException(name: String) :
        APIException("Deployment in progress: $name")

    class DeletionFailedException(name: String) :
        APIException("Previous attempt to delete $name failed")

    class InternalApplicationException(message: String) :
        APIException(message)
}


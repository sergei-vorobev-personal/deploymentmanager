package com.kineto.deploymentmanager.service

import com.kineto.deploymentmanager.config.AWSProperties
import com.kineto.deploymentmanager.dto.LambdaResponse
import com.kineto.deploymentmanager.exception.APIException
import com.kineto.deploymentmanager.exception.AWSException
import com.kineto.deploymentmanager.model.ApplicationState
import mu.KotlinLogging
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.lambda.LambdaClient
import software.amazon.awssdk.services.lambda.model.*
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import tools.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path

private val log = KotlinLogging.logger {}

@Service
class AWSFacadeService(
    private val awsProperties: AWSProperties,
    private val lambdaClient: LambdaClient,
    private val s3Client: S3Client,
    private val objectMapper: ObjectMapper,
) {
    fun invokeLambda(
        functionName: String,
    ): LambdaResponse {
        awsLambdaCall {
            val response = lambdaClient.invoke { it.functionName(functionName) }
            val payload = response.payload().asUtf8String()
            log.debug("Function $functionName invocation response:") { payload }
            val lambdaResponse = objectMapper.readValue(payload, LambdaResponse::class.java)
            return lambdaResponse
        }
    }

    fun createLambda(
        functionName: String,
        s3Key: String,
        s3Bucket: String,
    ): ApplicationState {
        awsLambdaCall {
            val waiter = lambdaClient.waiter()
            lambdaClient.createFunction {
                it.functionName(functionName)
                    .runtime(Runtime.NODEJS18_X)
                    .role(awsProperties.lambdaRoleArn)
                    .handler(AWS_LAMBDA_HANDLER)
                    .code { codeBuilder ->
                        codeBuilder.s3Bucket(s3Bucket).s3Key(s3Key)
                    }
            }
            val responseOrException = waiter.waitUntilFunctionActiveV2 {
                it.functionName(functionName)
            }.matched()
            if (responseOrException.response().isPresent) {
                val state = responseOrException.response().get().configuration().state()
                log.info("Lambda function $functionName created, state: $state")
                return ApplicationState.valueOf(state.name)
            } else if (responseOrException.exception().isPresent) {
                val exception = responseOrException.exception().get()
                log.error("Lambda function $functionName creation failed.", exception)
                val error = exception.localizedMessage
                throw AWSException.LambdaException(error)
            } else {
                log.error("Lambda function $functionName creation failed.")
                return ApplicationState.CREATE_FAILED
            }
        }
    }

    fun updateLambda(
        functionName: String,
        s3Key: String,
        s3Bucket: String,
    ): ApplicationState {
        awsLambdaCall {
            val waiter = lambdaClient.waiter()
            lambdaClient.updateFunctionCode {
                it.functionName(functionName)
                    .publish(true)
                    .s3Bucket(s3Bucket)
                    .s3Key(s3Key)
            }
            val responseOrException = waiter.waitUntilFunctionUpdatedV2 {
                it.functionName(functionName)
            }.matched()
            if (responseOrException.response().isPresent) {
                val state = responseOrException.response().get().configuration().state()
                log.info("Lambda function $functionName updated, state: $state")
                return ApplicationState.valueOf(state.name)
            } else if (responseOrException.exception().isPresent) {
                val exception = responseOrException.exception().get()
                log.error("Lambda function $functionName update failed.", exception)
                val message = exception.localizedMessage
                throw AWSException.LambdaException(message)
            } else {
                log.error("Lambda function $functionName update failed.")
                return ApplicationState.UPDATE_FAILED
            }
        }
    }

    fun deleteLambda(functionName: String) {
        awsLambdaCall {
            lambdaClient.deleteFunction { it.functionName(functionName) }
            log.info { "Lambda function $functionName deleted" }
        }
    }

    fun uploadZip(
        zipPath: Path,
        key: String,
        bucket: String,
    ) {
        try {
            val contentLength = Files.size(zipPath)

            val request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(ZIP_CONTENT_TYPE)
                .contentLength(contentLength)
                .build()

            s3Client.putObject(
                request,
                RequestBody.fromFile(zipPath)
            )
            log.info { "$zipPath Successfully uploaded to $bucket/$key" }
        } catch (e: S3Exception) {
            log.error(e.awsErrorDetails().toString(), e)
            throw AWSException.S3Exception(e.awsErrorDetails().toString())
        } catch (e: Exception) {
            val message = e.message ?: "Unknown exception"
            log.error(message, e)
            throw APIException.InternalApplicationException(message)
        }
    }

    private inline fun <T> awsLambdaCall(block: () -> T): T {
        try {
            return block()
        } catch (e: LambdaException) {
            val message = e.awsErrorDetails()?.errorMessage() ?: e.message ?: "Unknown AWS error"
            log.error(message, e)
            throw when (e) {
                is ResourceNotFoundException ->
                    AWSException.ResourceNotFoundException(message)

                is ResourceConflictException ->
                    AWSException.ResourceAlreadyExistsException(message)

                is TooManyRequestsException,
                is CodeStorageExceededException ->
                    AWSException.LambdaQuotaExceededException(message)

                is ServiceException ->
                    AWSException.ServiceException(message)

                else ->
                    AWSException.LambdaException(message)
            }
        } catch (e: Exception) {
            val message = e.message ?: "Unknown exception"
            log.error(message, e)
            throw APIException.InternalApplicationException(message)
        }
    }

    companion object {
        private const val AWS_LAMBDA_HANDLER = "index.handler"
        private const val ZIP_CONTENT_TYPE = "application/zip"
    }
}
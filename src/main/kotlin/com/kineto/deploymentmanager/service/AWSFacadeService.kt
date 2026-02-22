package com.kineto.deploymentmanager.service

import com.kineto.deploymentmanager.config.AWSProperties
import com.kineto.deploymentmanager.dto.LambdaCreationResponse
import com.kineto.deploymentmanager.dto.LambdaState
import com.kineto.deploymentmanager.dto.LambdaStateResponse
import com.kineto.deploymentmanager.exception.AWSException
import mu.KotlinLogging
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.lambda.LambdaClient
import software.amazon.awssdk.services.lambda.model.*
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import java.nio.file.Files
import java.nio.file.Path

private val log = KotlinLogging.logger {}

@Service
class AWSFacadeService(
    private val awsProperties: AWSProperties,
    private val lambdaClient: LambdaClient,
    private val s3Client: S3Client,
) {

    fun createLambdaAsync(
        applicationName: String,
        functionName: String,
        s3Key: String,
        s3Bucket: String,
    ): LambdaCreationResponse {
        awsLambdaCall {
            val response = lambdaClient.createFunction {
                it.functionName(functionName)
                    .runtime(Runtime.NODEJS18_X)
                    .role(awsProperties.lambdaRoleArn)
                    .handler(AWS_LAMBDA_HANDLER)
                    .tags(mapOf("_custom_id_" to applicationName))
                    .code { codeBuilder ->
                        codeBuilder.s3Bucket(s3Bucket).s3Key(s3Key)
                    }
            }
            val urlConfig = lambdaClient.createFunctionUrlConfig {
                it.functionName(functionName)
                it.authType("NONE")
            }
            val state = when (response.state()) {
                State.PENDING -> LambdaState.PENDING
                State.ACTIVE -> LambdaState.ACTIVE
                else -> LambdaState.FAILED
            }
            val error = if (LambdaState.FAILED == state) response.stateReason() else null
            return LambdaCreationResponse(
                state = state,
                url = urlConfig.functionUrl(),
                error = error
            )
        }
    }

    fun updateLambdaAsync(
        functionName: String,
        s3Key: String,
        s3Bucket: String,
    ): LambdaCreationResponse {
        awsLambdaCall {
            val response = lambdaClient.updateFunctionCode {
                it.functionName(functionName)
                    .publish(true)
                    .s3Bucket(s3Bucket)
                    .s3Key(s3Key)
            }
            val state = when (response.state()) {
                State.PENDING -> LambdaState.PENDING
                State.ACTIVE -> LambdaState.ACTIVE
                else -> LambdaState.FAILED
            }
            val error = if (LambdaState.FAILED == state) response.stateReason() else null
            return LambdaCreationResponse(
                state = state,
                error = error
            )
        }
    }

    fun deleteLambda(functionName: String) {
        awsLambdaCall {
            lambdaClient.deleteFunction { it.functionName(functionName) }
            log.info { "Lambda function $functionName deleted" }
        }
    }

    fun getLambdaState(functionName: String): LambdaStateResponse {
        awsLambdaCall {
            val response = lambdaClient.getFunctionConfiguration { it.functionName(functionName) }
            val state = when (response.state()) {
                State.PENDING -> LambdaState.PENDING
                State.ACTIVE -> LambdaState.ACTIVE
                else -> LambdaState.FAILED
            }
            val reason = response.stateReason()

            return LambdaStateResponse(state, reason)
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
        } catch (e: SdkException) {
            throw AWSException.SDKException(e.message ?: "Unknown SDK error")
        } catch (e: Exception) {
            val message = e.message ?: "Unknown exception"
            log.error(message, e)
            throw AWSException.SDKException(message)
        }
    }

    private inline fun <T> awsLambdaCall(block: () -> T): T {
        try {
            return block()
        } catch (e: SdkException) {
            throw AWSException.SDKException(e.message ?: "Unknown SDK error")
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
            throw AWSException.SDKException(message)
        }
    }

    companion object {
        private const val AWS_LAMBDA_HANDLER = "index.handler"
        private const val ZIP_CONTENT_TYPE = "application/zip"
    }
}
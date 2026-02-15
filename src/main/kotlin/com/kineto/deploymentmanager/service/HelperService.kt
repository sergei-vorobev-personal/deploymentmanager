package com.kineto.deploymentmanager.service

import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private val log = KotlinLogging.logger {}

@Service
class HelperService(
    private val awsFacadeService: AWSFacadeService,
) {

    @Transactional
    fun uploadToS3(
        zipFile: MultipartFile,
        key: String,
        bucket: String,
    ) {
        val tempFile = Files.createTempFile("upload-", ".zip")
        log.debug {"Temp file created: ${tempFile.fileName}"}
        try {
            zipFile.inputStream.use { input ->
                Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING)
            }
            awsFacadeService.uploadZip(
                zipPath = tempFile,
                key = key,
                bucket = bucket,
            )
            log.debug {"Zip file uploaded to $bucket/$key"}
        } finally {
            Files.deleteIfExists(tempFile)
            log.debug {"Temp file deleted: ${tempFile.fileName}"}
        }
    }
}
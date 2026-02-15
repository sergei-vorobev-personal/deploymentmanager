package com.kineto.deploymentmanager.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Service
class HelperService(
    private val awsFacadeService: AWSFacadeService,
) {

    @Transactional
    fun uploadToS3(
        zipFile: MultipartFile,
        name: String,
        bucket: String,
    ) {
        val tempFile = Files.createTempFile("upload-", ".zip")
        try {
            zipFile.inputStream.use { input ->
                Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING)
            }
            awsFacadeService.uploadZip(
                zipPath = tempFile,
                key = name,
                bucket = bucket,
            )
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }
}
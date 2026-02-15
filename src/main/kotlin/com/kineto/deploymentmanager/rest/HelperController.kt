package com.kineto.deploymentmanager.rest

import com.kineto.deploymentmanager.service.HelperService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/helper")
class HelperController(
    private val helperService: HelperService,
) {

    @PostMapping("/upload")
    fun uploadZipToS3(
        @RequestParam("zipFile") zipFile: MultipartFile,
        @RequestParam("key") key: String,
        @RequestParam("bucket") bucket: String,
    ) {
        return helperService.uploadToS3(zipFile, key, bucket)
    }
}
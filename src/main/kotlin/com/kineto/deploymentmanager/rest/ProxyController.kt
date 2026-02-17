package com.kineto.deploymentmanager.rest

import com.kineto.deploymentmanager.service.ProxyService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/proxy")
class ProxyController(
    private val proxyService: ProxyService,
) {

    @GetMapping("/{name}/**")
    fun invokeLambda(
        @PathVariable("name") name: String,
        @RequestParam params: MultiValueMap<String, String>,
        request: HttpServletRequest,
    ): ResponseEntity<String> {
        val subpath = request.requestURL.toString().substringAfter("/proxy/$name")
        return proxyService.callLambda(name, subpath, params)
    }
}
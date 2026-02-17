package com.kineto.deploymentmanager.rest


import com.kineto.deploymentmanager.exception.APIException
import com.kineto.deploymentmanager.service.ProxyService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.ResponseEntity
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.util.MultiValueMapAdapter

@WebMvcTest(ProxyController::class)
class ProxyControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var proxyService: ProxyService

    @Test
    fun `callLambda should return string from service`() {
        val appName = "myApp"
        `when`(
            proxyService.callLambda(
                name = appName,
                subpath = "",
                params = MultiValueMapAdapter(mapOf<String, List<String>>())
            )
        ).thenReturn(ResponseEntity.ok("invoked"))

        mockMvc.perform(get("/proxy/$appName"))
            .andExpect(status().isOk)
            .andExpect(content().string("invoked"))
    }

    @Test
    fun `callLambda with path and params should return string from service`() {
        val appName = "myApp"
        `when`(
            proxyService.callLambda(
                name = appName,
                subpath = "/path",
                params = MultiValueMapAdapter(mapOf("name" to listOf("1")))
            )
        ).thenReturn(ResponseEntity.ok("invoked"))

        mockMvc.perform(get("/proxy/$appName/path?name=1"))
            .andExpect(status().isOk)
            .andExpect(content().string("invoked"))
    }

    @Test
    fun `handleAWSException should return correct error`() {
        val appName = "myApp"
        `when`(
            proxyService.callLambda(
                name = appName,
                subpath = "/path",
                params = MultiValueMapAdapter(mapOf("name" to listOf("1")))
            )
        ).thenThrow(APIException.ApplicationNotFoundException(appName))

        mockMvc.perform(get("/proxy/$appName/path?name=1"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("Application myApp not found."))
    }
}

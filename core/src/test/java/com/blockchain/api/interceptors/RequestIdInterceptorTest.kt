package com.blockchain.api.interceptors

import com.nhaarman.mockitokotlin2.mock
import kotlin.test.assertEquals
import okhttp3.Interceptor
import okhttp3.Request
import org.junit.Test

class RequestIdInterceptorTest {
    private val generatedHeaderValue = "489a62fd-a936-473d-8687-8f702aefd30c"
    private val interceptor: RequestIdInterceptor = RequestIdInterceptor { generatedHeaderValue }

    @Test
    fun `Any intercepted request will contain an X-Request-ID header`() {
        val initialRequest = InterceptorTestUtility.givenABasicRequest()
        val expectedRequestToBeTriggered = initialRequest.withRequestID(generatedHeaderValue)
        val response = expectedRequestToBeTriggered.someResponse()

        val interceptorChain: Interceptor.Chain = mock {
            on { request() }.thenReturn(initialRequest)
            on {
                proceed(InterceptorTestUtility.withAnyRequestMatching(expectedRequestToBeTriggered))
            }.thenReturn(response)
        }

        val returnedResponse = interceptor.intercept(interceptorChain)

        val headerValue = returnedResponse.request.header("X-Request-ID")
        assertEquals(generatedHeaderValue, headerValue)
    }

    @Test
    fun `Keep the X-Request-ID header for any intercepted request with it`() {
        val headerValueAlreadyPresent = "8b56ecaa-bd62-4a22-927f-015db47f6f49"
        val initialRequest = InterceptorTestUtility.givenABasicRequest().withRequestID(headerValueAlreadyPresent)
        val response = initialRequest.someResponse()

        val interceptorChain: Interceptor.Chain = mock {
            on { request() }.thenReturn(initialRequest)
            on { proceed(InterceptorTestUtility.withAnyRequestMatching(initialRequest)) }.thenReturn(response)
        }

        val returnedResponse = interceptor.intercept(interceptorChain)

        val headerValue = returnedResponse.request.header("X-Request-ID")
        assertEquals(headerValueAlreadyPresent, headerValue)
    }
}

private fun Request.withRequestID(header: String): Request =
    newBuilder().addHeader("X-Request-ID", header).build()

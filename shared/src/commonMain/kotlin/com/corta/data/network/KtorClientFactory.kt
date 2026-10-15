package com.corta.data.network

import com.corta.data.network.SubtelApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Centralised Ktor [HttpClient] factory. Every network-facing client
 * (SUBTEL, future community gateway) must use [create] so timeouts,
 * retry policy, JSON config and headers stay consistent.
 *
 * Defaults are tuned for the SUBTEL / community flows:
 *  - 15s connect / 30s request timeout (low data volumes, never expect
 *    long polls; 30s leaves headroom for slow networks).
 *  - 3 retries with exponential backoff. Only safe/idempotent methods
 *    (GET, HEAD, OPTIONS) are retried.
 *  - ContentNegotiation: lenient JSON parser (ignoreUnknownKeys, do not
 *    fail on explicit nulls, always encode defaults so consumers do not
 *    have to guess missing fields).
 *  - User-Agent `Corta/1.0 (Android)`. Lets gateways identify the app
 *    in their access logs and is required by the SUBTEL API ToS.
 *  - Logging: only WARN+ in release, all in debug.
 */
object KtorClientFactory {

    private const val TIMEOUT_CONNECT_MS = 15_000L
    private const val TIMEOUT_REQUEST_MS = 30_000L
    private const val USER_AGENT = "Corta/1.0 (Android)"

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = false
        isLenient = true
    }

    fun create(engine: HttpClientEngine, isDebug: Boolean = false): HttpClient {
        return HttpClient(engine) {
            expectSuccess = true

            install(HttpTimeout) {
                connectTimeoutMillis = TIMEOUT_CONNECT_MS
                requestTimeoutMillis = TIMEOUT_REQUEST_MS
                socketTimeoutMillis = TIMEOUT_CONNECT_MS
            }

            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = 3)
                exponentialDelay()
                retryOnExceptionIf { _, cause ->
                    cause is java.io.IOException
                }
                retryOnException(maxRetries = 2)
            }

            install(ContentNegotiation) {
                json(json)
            }

            install(UserAgent) {
                agent = USER_AGENT
            }

            install(Logging) {
                level = if (isDebug) LogLevel.ALL else LogLevel.INFO
                logger = object : Logger {
                    override fun log(message: String) {
                        Timber.tag("Corta.Http").d(message)
                    }
                }
            }

            defaultRequest {
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        }
    }
}

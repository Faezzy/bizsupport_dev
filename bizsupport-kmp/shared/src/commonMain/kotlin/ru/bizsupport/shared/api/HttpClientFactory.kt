package ru.bizsupport.shared.api

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

object HttpClientFactory {

    fun create(tokenProvider: () -> String?): HttpClient {
        return HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    prettyPrint = false
                })
            }

            install(Logging) {
                level = LogLevel.NONE // Поменять на BODY для отладки
            }

            install(Auth) {
                bearer {
                    loadTokens {
                        val token = tokenProvider()
                        if (token != null) BearerTokens(token, "") else null
                    }
                }
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 10_000
            }

            defaultRequest {
                url("http://10.0.2.2:8080/") // Android emulator → localhost
            }
        }
    }
}

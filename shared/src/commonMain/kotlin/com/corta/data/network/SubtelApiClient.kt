package com.corta.data.network

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SubtelRuleDto(
    val pattern: String,
    val action: String,
    val isRegex: Boolean
)

expect fun provideHttpClientEngine(): HttpClientEngine

class SubtelApiClient {
    private val client = HttpClient(provideHttpClientEngine()) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
    
    /**
     * Representación simplificada de la lista SUBTEL basada en la norma de prefijos:
     *
     * - 600: llamadas comerciales/masivas potencialmente deseadas o esperadas.
     *        En la app las tratamos como "WARN" (avisar, no bloquear).
     * - 809: llamadas comerciales/masivas no deseadas.
     *        En la app las tratamos como "BLOCK" (bloqueo por defecto).
     *
     * Los patrones se definen como regex para que coincidan con cualquier número
     * que comience con esos prefijos, tal como exige la normativa.
     */
    suspend fun fetchLatestSpamList(): List<SubtelRuleDto> {
        delay(1500) // Simula latencia de red
        return listOf(
            // Prefijo 809: llamadas no deseadas -> bloquear
            SubtelRuleDto(
                pattern = "^809.*",
                action = "BLOCK",
                isRegex = true
            ),
            // Prefijo 600: llamadas comerciales/masivas potencialmente deseadas -> avisar
            SubtelRuleDto(
                pattern = "^600.*",
                action = "WARN",
                isRegex = true
            )
        )
    }
}

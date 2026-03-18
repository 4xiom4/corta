package com.corta.data.network

import io.ktor.client.engine.*
import io.ktor.client.engine.android.*

actual fun provideHttpClientEngine(): HttpClientEngine {
    return Android.create()
}

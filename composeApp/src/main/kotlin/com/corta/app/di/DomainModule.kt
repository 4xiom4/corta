package com.corta.app.di

import com.corta.domain.CallValidator
import com.corta.domain.SpamEvaluator
import org.koin.dsl.module

/**
 * Domain services: pure-logic evaluators and validators. These depend on
 * repositories from [dataModule] but never on Android types.
 */
val domainModule = module {
    single {
        SpamEvaluator(get())
    }
    single {
        CallValidator(get())
    }
}

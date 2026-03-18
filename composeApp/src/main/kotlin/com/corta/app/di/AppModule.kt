package com.corta.app.di

import com.corta.data.DatabaseDriverFactory
import com.corta.db.CortaDatabase
import com.corta.domain.CallValidator
import com.corta.domain.CortaRepository
import com.corta.domain.SpamEvaluator
import org.koin.dsl.module

val appModule = module {
    single {
        DatabaseDriverFactory(get()).createDriver(null)
    }
    single {
        CortaDatabase(get())
    }
    single {
        CortaRepository(get())
    }
    single {
        SpamEvaluator(get())
    }
    single {
        CallValidator(get())
    }
}

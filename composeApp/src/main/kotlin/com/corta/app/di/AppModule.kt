package com.corta.app.di

import com.corta.app.ui.CortaAppViewModel
import com.corta.app.ui.CallLogViewModel
import com.corta.app.ui.sms.SmsViewModel
import com.corta.app.ui.contacts.ContactsViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Presentation layer: ViewModels.
 */
val appModule = module {
    includes(coreModule, dataModule, domainModule)
    viewModel { CortaAppViewModel(get(), get(), get()) }
    viewModel { CallLogViewModel(get(), get()) }
    viewModel { SmsViewModel(get(), get()) }
    viewModel { ContactsViewModel(get()) }
}

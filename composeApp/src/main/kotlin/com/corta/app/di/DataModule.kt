package com.corta.app.di

import com.corta.app.data.AndroidSystemLogImporter
import com.corta.app.data.LocalMockCollaborativeRulesGateway
import com.corta.app.data.PrefsCommunityAccessPolicy
import com.corta.app.ui.contacts.AndroidContactRepository
import com.corta.data.network.SubtelApiClient
import com.corta.domain.CollaborativeRulesGateway
import com.corta.domain.CommunityAccessPolicy
import com.corta.domain.ContactRepository
import com.corta.domain.CortaRepository
import org.koin.dsl.module

/**
 * Data layer: repositories, gateways, Android-only bridges.
 * Depends on [coreModule] for the SQL driver and schema.
 */
val dataModule = module {
    single<CollaborativeRulesGateway> {
        LocalMockCollaborativeRulesGateway()
    }
    single<CommunityAccessPolicy> {
        PrefsCommunityAccessPolicy(get())
    }
    single<ContactRepository> {
        AndroidContactRepository(get())
    }
    single<SubtelApiClient> {
        SubtelApiClient()
    }
    single {
        CortaRepository(
            database = get(),
            collaborativeGateway = get(),
            communityAccessPolicy = get()
        )
    }
    single {
        AndroidSystemLogImporter(get())
    }
}

package com.blockchain.koin

import info.blockchain.wallet.api.dust.BchDustService
import info.blockchain.wallet.api.dust.DustApi
import info.blockchain.wallet.api.dust.DustService
import info.blockchain.wallet.ethereum.EthAccountApi
import info.blockchain.wallet.ethereum.EthEndpoints
import info.blockchain.wallet.metadata.MetadataInteractor
import info.blockchain.wallet.metadata.MetadataService
import info.blockchain.wallet.multiaddress.MultiAddressFactory
import info.blockchain.wallet.multiaddress.MultiAddressFactoryBtc
import info.blockchain.wallet.payload.BalanceManagerBch
import info.blockchain.wallet.payload.BalanceManagerBtc
import info.blockchain.wallet.payload.PayloadManager
import info.blockchain.wallet.payload.PayloadScopeWiper
import org.koin.dsl.bind
import org.koin.dsl.module
import retrofit2.Retrofit

val walletModule = module {

    scope(payloadScopeQualifier) {

        scoped {
            PayloadManager(get(), get(), get(), get(), get(), get(), get())
        }

        factory { MultiAddressFactoryBtc(bitcoinApi = get()) }.bind(MultiAddressFactory::class)

        factory { BalanceManagerBtc(bitcoinApi = get()) }

        factory { BalanceManagerBch(bitcoinApi = get()) }
    }

    factory {
        MetadataInteractor(
            metadataService = get()
        )
    }

    single {
        get<Retrofit>(apiRetrofit).create(MetadataService::class.java)
    }

    factory {
        BchDustService(
            api = get<Retrofit>(kotlinApiRetrofit).create(DustApi::class.java),
            apiCode = getProperty("api-code")
        )
    }.bind(DustService::class)

    single {
        object : PayloadScopeWiper {
            override fun wipe() {
                if (!payloadScope.closed) {
                    payloadScope.close()
                }
            }
        }
    }.bind(PayloadScopeWiper::class)

    factory {
        EthAccountApi(
            ethEndpoints = get<Retrofit>(apiRetrofit).create(EthEndpoints::class.java),
            apiCode = getProperty("api-code")
        )
    }
}

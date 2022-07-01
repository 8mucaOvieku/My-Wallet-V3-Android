package piuk.blockchain.android.ui.recover

import com.blockchain.nabu.datamanagers.NabuDataManager
import info.blockchain.wallet.metadata.Metadata
import info.blockchain.wallet.metadata.MetadataDerivation
import info.blockchain.wallet.metadata.MetadataInteractor
import io.reactivex.rxjava3.core.Completable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import piuk.blockchain.androidcore.data.auth.metadata.WalletRecoveryMetadata
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.utils.SessionPrefs
import piuk.blockchain.androidcore.utils.extensions.then

class AccountRecoveryInteractor(
    private val payloadDataManager: PayloadDataManager,
    private val prefs: SessionPrefs,
    private val metadataInteractor: MetadataInteractor,
    private val metadataDerivation: MetadataDerivation,
    private val nabuDataManager: NabuDataManager
) {

    fun recoverCredentials(seedPhrase: String): Completable {

        val masterKey = payloadDataManager.generateMasterKeyFromSeed(seedPhrase)
        val metadataNode = metadataDerivation.deriveMetadataNode(masterKey)

        return metadataInteractor.loadRemoteMetadata(
            Metadata.newInstance(
                metaDataHDNode = metadataDerivation.deserializeMetadataNode(metadataNode),
                type = WalletRecoveryMetadata.WALLET_CREDENTIALS_METADATA_NODE,
                metadataDerivation = metadataDerivation
            )
        )
            .flatMapCompletable { json ->
                val credentials = Json.decodeFromString<WalletRecoveryMetadata>(json)
                payloadDataManager.initializeAndDecrypt(
                    credentials.sharedKey,
                    credentials.guid,
                    credentials.password
                )
            }
    }

    private fun restoreWallet() = Completable.fromCallable {
        payloadDataManager.wallet?.let { wallet ->
            prefs.sharedKey = wallet.sharedKey
            prefs.walletGuid = wallet.guid
        }
    }

    fun recoverWallet(): Completable {
        return restoreWallet().then {
            nabuDataManager.resetUserKyc()
        }
    }
}

package piuk.blockchain.android.ui.recover

import com.blockchain.featureflag.FeatureFlag
import com.squareup.moshi.Moshi
import info.blockchain.wallet.bip44.HDWalletFactory
import info.blockchain.wallet.metadata.Metadata
import info.blockchain.wallet.metadata.MetadataDerivation
import info.blockchain.wallet.metadata.MetadataInteractor
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.Locale
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.bitcoinj.crypto.MnemonicCode
import org.bitcoinj.crypto.MnemonicException
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.base.BasePresenter
import piuk.blockchain.androidcore.data.auth.metadata.WalletCredentialsMetadata
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.utils.PersistentPrefs
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import timber.log.Timber

class RecoverFundsPresenter(
    private val payloadDataManager: PayloadDataManager,
    private val prefs: PersistentPrefs,
    private val metadataInteractor: MetadataInteractor,
    private val metadataDerivation: MetadataDerivation,
    private val moshi: Moshi,
    private val json: Json,
    private val disableMoshiFeatureFlag: FeatureFlag
) : BasePresenter<RecoverFundsView>() {

    private val mnemonicChecker: MnemonicCode by unsafeLazy {
        // We only support US english mnemonics atm
        val wis = HDWalletFactory::class.java.classLoader?.getResourceAsStream(
            "wordlist/" + Locale("en", "US") + ".txt"
        ) ?: throw MnemonicException.MnemonicWordException("cannot read BIP39 word list")

        MnemonicCode(wis, null)
    }

    override fun onViewReady() {
        // No-op
    }

    fun onContinueClicked(recoveryPhrase: String) =
        try {
            if (recoveryPhrase.isEmpty() || !isValidMnemonic(recoveryPhrase)) {
                view.showError(R.string.invalid_recovery_phrase_1)
            } else {
                recoverWallet(recoveryPhrase)
            }
        } catch (e: Exception) {
            // This should never happen
            Timber.wtf(e)
            view.showError(R.string.restore_failed)
        }

    private fun isValidMnemonic(
        recoveryPhrase: String
    ): Boolean = try {
        val words = recoveryPhrase.trim().split("\\s+".toRegex())
        mnemonicChecker.check(words)
        true
    } catch (e: MnemonicException) {
        false
    }

    private fun recoverCredentials(recoveryPhrase: String): Single<WalletCredentialsMetadata> {
        require(recoveryPhrase.isNotEmpty())

        val masterKey = payloadDataManager.generateMasterKeyFromSeed(recoveryPhrase)
        val metadataNode = metadataDerivation.deriveMetadataNode(masterKey)

        return disableMoshiFeatureFlag.enabled.flatMapMaybe { isMoshiDisabled ->
            metadataInteractor.loadRemoteMetadata(
                Metadata.newInstance(
                    metaDataHDNode = metadataDerivation.deserializeMetadataNode(metadataNode),
                    type = WalletCredentialsMetadata.WALLET_CREDENTIALS_METADATA_NODE,
                    metadataDerivation = metadataDerivation
                )
            )
                .map {
                    if (isMoshiDisabled) {
                        json.decodeFromString<WalletCredentialsMetadata>(it)
                    } else {
                        moshi.adapter(WalletCredentialsMetadata::class.java).fromJson(it)
                            ?: throw NoSuchElementException()
                    }
                }
        }.toSingle()
    }

    private fun recoverWallet(recoveryPhrase: String) {
        compositeDisposable += recoverCredentials(recoveryPhrase)
            .flatMapCompletable { creds ->
                payloadDataManager.initializeAndDecrypt(
                    creds.sharedKey,
                    creds.guid,
                    creds.password
                )
            }
            .observeOn(Schedulers.io())
            .subscribeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe {
                view.showProgressDialog(R.string.restoring_wallet)
            }.doOnTerminate {
                view.dismissProgressDialog()
            }.subscribeBy(
                onComplete = {
                    prefs.sharedKey = payloadDataManager.wallet!!.sharedKey
                    prefs.walletGuid = payloadDataManager.wallet!!.guid
                    view.startPinEntryActivity()
                },
                onError = {
                    Timber.e(it)
                    view.gotoCredentialsActivity(recoveryPhrase)
                }
            )
    }
}

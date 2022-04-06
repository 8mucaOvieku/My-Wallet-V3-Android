package piuk.blockchain.android.ui.recover

import com.blockchain.api.NabuApiException
import com.blockchain.api.NabuErrorTypes
import com.blockchain.commonarch.presentation.mvi.MviModel
import com.blockchain.enviroment.EnvironmentConfig
import com.blockchain.logging.RemoteLogger
import info.blockchain.wallet.bip44.HDWalletFactory
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.subscribeBy
import java.util.Locale
import org.bitcoinj.crypto.MnemonicCode
import org.bitcoinj.crypto.MnemonicException
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import timber.log.Timber

class AccountRecoveryModel(
    initialState: AccountRecoveryState,
    mainScheduler: Scheduler,
    environmentConfig: EnvironmentConfig,
    remoteLogger: RemoteLogger,
    private val interactor: AccountRecoveryInteractor
) : MviModel<AccountRecoveryState, AccountRecoveryIntents>(
    initialState,
    mainScheduler,
    environmentConfig,
    remoteLogger
) {

    private val mnemonicChecker: MnemonicCode by unsafeLazy {
        // We only support US english mnemonics atm
        val wis = HDWalletFactory::class.java.classLoader?.getResourceAsStream(
            "wordlist/" + Locale("en", "US") + ".txt"
        ) ?: throw MnemonicException.MnemonicWordException("cannot read BIP39 word list")

        MnemonicCode(wis, null)
    }

    override fun performAction(previousState: AccountRecoveryState, intent: AccountRecoveryIntents): Disposable? {
        return when (intent) {
            is AccountRecoveryIntents.VerifySeedPhrase -> verifyMnemonic(seedPhrase = intent.seedPhrase)
            is AccountRecoveryIntents.RecoverWalletCredentials -> recoverCredentials(seedPhrase = intent.seedPhrase)
            is AccountRecoveryIntents.RestoreWallet -> restoreWallet()
            else -> null
        }
    }

    private fun verifyMnemonic(seedPhrase: String): Disposable? {
        val correctedPhrase = seedPhrase.lowercase().trim()
        val seedWords = correctedPhrase.split("\\W+".toRegex())
        when {
            seedWords.size < 12 -> {
                process(AccountRecoveryIntents.UpdateStatus(AccountRecoveryStatus.WORD_COUNT_ERROR))
            }
            else -> try {
                mnemonicChecker.check(seedWords)
                process(
                    AccountRecoveryIntents.RecoverWalletCredentials(
                        seedPhrase = correctedPhrase
                    )
                )
            } catch (e: MnemonicException) {
                process(AccountRecoveryIntents.UpdateStatus(AccountRecoveryStatus.INVALID_PHRASE))
            }
        }
        return null
    }

    private fun recoverCredentials(seedPhrase: String): Disposable {
        return interactor.recoverCredentials(seedPhrase)
            .subscribeBy(
                onComplete = {
                    process(AccountRecoveryIntents.RestoreWallet)
                },
                onError = {
                    process(AccountRecoveryIntents.UpdateStatus(AccountRecoveryStatus.RECOVERY_FAILED))
                }
            )
    }

    private fun restoreWallet(): Disposable =
        interactor.recoverWallet()
            .subscribeBy(
                onComplete = {
                    process(AccountRecoveryIntents.UpdateStatus(AccountRecoveryStatus.RECOVERY_SUCCESSFUL))
                },
                onError = { throwable ->
                    Timber.e(throwable)
                    if (throwable is NabuApiException && throwable.getErrorType() == NabuErrorTypes.Conflict) {
                        // Resetting KYC is already in progress
                        process(AccountRecoveryIntents.UpdateStatus(AccountRecoveryStatus.RECOVERY_SUCCESSFUL))
                    } else {
                        process(AccountRecoveryIntents.UpdateStatus(AccountRecoveryStatus.RESET_KYC_FAILED))
                    }
                }
            )
}

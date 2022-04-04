package piuk.blockchain.android.ui.linkbank

import com.blockchain.banking.BankTransferAction
import com.blockchain.commonarch.presentation.mvi.MviModel
import com.blockchain.core.payments.model.BankPartner
import com.blockchain.core.payments.model.LinkedBank
import com.blockchain.core.payments.model.LinkedBankErrorState
import com.blockchain.core.payments.model.LinkedBankState
import com.blockchain.enviroment.EnvironmentConfig
import com.blockchain.extensions.exhaustive
import com.blockchain.logging.RemoteLogger
import com.blockchain.network.PollResult
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.subscribeBy
import piuk.blockchain.android.simplebuy.ErrorState
import piuk.blockchain.android.simplebuy.SimpleBuyInteractor

class BankAuthModel(
    private val interactor: SimpleBuyInteractor,
    initialState: BankAuthState,
    uiScheduler: Scheduler,
    environmentConfig: EnvironmentConfig,
    remoteLogger: RemoteLogger
) : MviModel<BankAuthState, BankAuthIntent>(
    initialState,
    uiScheduler,
    environmentConfig,
    remoteLogger
) {
    override fun performAction(previousState: BankAuthState, intent: BankAuthIntent): Disposable? =
        when (intent) {
            is BankAuthIntent.CancelOrder,
            is BankAuthIntent.CancelOrderAndResetAuthorisation -> (
                previousState.id?.let {
                    interactor.cancelOrder(it)
                } ?: Completable.complete()
                )
                .subscribeBy(
                    onComplete = {
                        process(BankAuthIntent.OrderCanceled)
                    },
                    onError = {
                        process(BankAuthIntent.ErrorIntent())
                    }
                )
            is BankAuthIntent.UpdateAccountProvider -> processBankLinkingUpdate(intent)
            is BankAuthIntent.GetLinkedBankState -> processBankLinkStateUpdate(intent)
            is BankAuthIntent.StartPollingForLinkStatus -> processLinkStatusPolling(
                intent, previousState.linkBankTransfer?.partner
            )
            is BankAuthIntent.StartBankApproval -> {
                interactor.updateApprovalStatus(intent.callbackPath)
                null
            }
            else -> null
        }

    private fun processBankLinkingUpdate(intent: BankAuthIntent.UpdateAccountProvider) =
        interactor.updateSelectedBankAccountId(
            linkingId = intent.linkingBankId,
            providerAccountId = intent.accountProviderId,
            accountId = intent.accountId,
            partner = intent.linkBankTransfer.partner,
            source = intent.authSource,
            action = BankTransferAction.LINK
        ).subscribeBy(
            onComplete = {
                process(BankAuthIntent.StartPollingForLinkStatus(intent.linkingBankId))
            },
            onError = {
                process(BankAuthIntent.ProviderAccountIdUpdateError)
            }
        )

    private fun processBankLinkStateUpdate(intent: BankAuthIntent.GetLinkedBankState) =
        interactor.pollForBankLinkingCompleted(
            intent.linkingBankId
        ).subscribeBy(
            onSuccess = {
                when (it.state) {
                    LinkedBankState.ACTIVE -> process(BankAuthIntent.LinkedBankStateSuccess(it))
                    LinkedBankState.BLOCKED,
                    LinkedBankState.UNKNOWN -> handleBankLinkingError(it)
                    LinkedBankState.PENDING,
                    LinkedBankState.CREATED -> process(
                        BankAuthIntent.BankAuthErrorState(ErrorState.BankLinkingTimeout)
                    )
                }
            },
            onError = {
                process(BankAuthIntent.BankAuthErrorState(ErrorState.BankLinkingFailed))
            }
        )

    private fun processLinkStatusPolling(
        intent: BankAuthIntent.StartPollingForLinkStatus,
        partner: BankPartner?
    ) = Single.defer {
        interactor.pollForLinkedBankState(
            intent.bankId,
            partner
        )
    }
        .subscribeBy(
            onSuccess = {
                when (it) {
                    is PollResult.FinalResult -> {
                        interactor.updateOneTimeTokenPath(it.value.callbackPath)
                        updateIntentForLinkedBankState(it, partner)
                    }
                    is PollResult.Cancel -> {
                    }
                    is PollResult.TimeOut -> process(
                        BankAuthIntent.BankAuthErrorState(ErrorState.BankLinkingTimeout)
                    )
                }
            },
            onError = {
                process(BankAuthIntent.BankAuthErrorState(ErrorState.BankLinkingFailed))
            }
        )

    private fun updateIntentForLinkedBankState(
        pollResult: PollResult<LinkedBank>,
        partner: BankPartner?
    ) {
        when (pollResult.value.state) {
            LinkedBankState.ACTIVE -> {
                process(BankAuthIntent.LinkedBankStateSuccess(pollResult.value))
            }
            LinkedBankState.BLOCKED -> {
                handleBankLinkingError(pollResult.value)
            }
            LinkedBankState.PENDING,
            LinkedBankState.CREATED -> {
                when (partner) {
                    BankPartner.YODLEE -> process(
                        BankAuthIntent.BankAuthErrorState(ErrorState.BankLinkingTimeout)
                    )
                    BankPartner.YAPILY -> process(
                        BankAuthIntent.UpdateLinkingUrl(pollResult.value.authorisationUrl)
                    )
                }
            }
            LinkedBankState.UNKNOWN -> process(
                BankAuthIntent.BankAuthErrorState(ErrorState.BankLinkingFailed)
            )
        }
    }

    @Suppress("IMPLICIT_CAST_TO_ANY")
    private fun handleBankLinkingError(it: LinkedBank) {
        when (it.errorStatus) {
            LinkedBankErrorState.ACCOUNT_ALREADY_LINKED -> process(
                BankAuthIntent.BankAuthErrorState(ErrorState.LinkedBankAlreadyLinked)
            )
            LinkedBankErrorState.UNKNOWN -> process(BankAuthIntent.BankAuthErrorState(ErrorState.BankLinkingFailed))
            LinkedBankErrorState.NOT_INFO_FOUND -> process(
                BankAuthIntent.BankAuthErrorState(ErrorState.LinkedBankInfoNotFound)
            )
            LinkedBankErrorState.ACCOUNT_TYPE_UNSUPPORTED -> process(
                BankAuthIntent.BankAuthErrorState(ErrorState.LinkedBankAccountUnsupported)
            )
            LinkedBankErrorState.NAMES_MISMATCHED -> process(
                BankAuthIntent.BankAuthErrorState(ErrorState.LinkedBankNamesMismatched)
            )
            LinkedBankErrorState.REJECTED -> process(
                BankAuthIntent.BankAuthErrorState(ErrorState.LinkedBankRejected)
            )
            LinkedBankErrorState.EXPIRED -> process(
                BankAuthIntent.BankAuthErrorState(ErrorState.LinkedBankExpired)
            )
            LinkedBankErrorState.FAILURE -> process(
                BankAuthIntent.BankAuthErrorState(ErrorState.LinkedBankFailure)
            )
            LinkedBankErrorState.INTERNAL_FAILURE -> process(
                BankAuthIntent.BankAuthErrorState(ErrorState.LinkedBankInternalFailure)
            )
            LinkedBankErrorState.INVALID -> process(
                BankAuthIntent.BankAuthErrorState(ErrorState.LinkedBankInvalid)
            )
            LinkedBankErrorState.FRAUD -> process(
                BankAuthIntent.BankAuthErrorState(ErrorState.LinkedBankFraud)
            )
            LinkedBankErrorState.NONE -> {
                // check the state is not a linking final state
                if (it.state == LinkedBankState.BLOCKED) {
                    process(BankAuthIntent.BankAuthErrorState(ErrorState.BankLinkingFailed))
                } else {
                    // do nothing
                }
            }
        }.exhaustive
    }
}

package piuk.blockchain.android.ui.transactionflow.flow

import android.app.Activity.RESULT_CANCELED
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.databinding.FragmentTxFlowInProgressBinding
import piuk.blockchain.android.ui.linkbank.BankAuthActivity
import piuk.blockchain.android.ui.linkbank.BankAuthSource
import piuk.blockchain.android.ui.transactionflow.engine.TransactionIntent
import piuk.blockchain.android.ui.transactionflow.engine.TransactionState
import piuk.blockchain.android.ui.transactionflow.engine.TxExecutionStatus
import piuk.blockchain.android.ui.transactionflow.flow.customisations.TransactionProgressCustomisations
import timber.log.Timber

class TransactionProgressFragment : TransactionFlowFragment<FragmentTxFlowInProgressBinding>() {

    override fun initBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentTxFlowInProgressBinding =
        FragmentTxFlowInProgressBinding.inflate(inflater, container, false)

    private val customiser: TransactionProgressCustomisations by inject()
    private val MAX_STACKTRACE_CHARS = 400

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.txProgressView.onCtaClick(
            text = getString(R.string.common_ok)
        ) { activity.finish() }
    }

    override fun render(newState: TransactionState) {
        Timber.d("!TRANSACTION!> Rendering! TransactionProgressFragment")

        customiser.transactionProgressStandardIcon(newState)?.let {
            binding.txProgressView.setAssetIcon(it)
        } ?: binding.txProgressView.setAssetIcon(newState.sendingAsset)

        handleStatusUpdates(newState)
    }

    private fun handleStatusUpdates(
        newState: TransactionState
    ) {
        when (newState.executionStatus) {
            is TxExecutionStatus.InProgress -> binding.txProgressView.showTxInProgress(
                customiser.transactionProgressTitle(newState),
                customiser.transactionProgressMessage(newState)
            )
            is TxExecutionStatus.Completed -> {
                analyticsHooks.onTransactionSuccess(newState)
                binding.txProgressView.showTxSuccess(
                    customiser.transactionCompleteTitle(newState),
                    customiser.transactionCompleteMessage(newState),
                    customiser.transactionCompleteIcon(newState)
                )
            }
            is TxExecutionStatus.ApprovalRequired -> {
                binding.txProgressView.showTxInProgress(
                    customiser.transactionProgressTitle(newState),
                    customiser.transactionProgressMessage(newState)
                )
                model.process(TransactionIntent.ApprovalTriggered)
                startActivityForResult(
                    BankAuthActivity.newInstance(
                        newState.executionStatus.approvalData, BankAuthSource.DEPOSIT, requireContext()
                    ),
                    PAYMENT_APPROVAL
                )
                // dismiss()
            }
            is TxExecutionStatus.Error -> {
                analyticsHooks.onTransactionFailure(
                    newState, collectStackTraceString(newState.executionStatus.exception)
                )
                binding.txProgressView.showTxError(
                    customiser.transactionProgressExceptionMessage(newState),
                    getString(R.string.send_progress_error_subtitle)
                )
            }
            else -> {
                // do nothing
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PAYMENT_APPROVAL && resultCode == RESULT_CANCELED) {
            model.process(
                TransactionIntent.TransactionApprovalDenied
            )
        }
    }

    private fun collectStackTraceString(e: Throwable): String {
        var stackTraceString = ""
        var index = 0
        while (stackTraceString.length <= MAX_STACKTRACE_CHARS && index < e.stackTrace.size) {
            stackTraceString += "${e.stackTrace[index]}\n"
            index++
        }
        Timber.d("Sending trace to analytics: $stackTraceString")
        return stackTraceString
    }

    companion object {
        fun newInstance(): TransactionProgressFragment = TransactionProgressFragment()
        private const val PAYMENT_APPROVAL = 3974
    }
}

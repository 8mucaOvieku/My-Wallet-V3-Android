package piuk.blockchain.android.ui.transactionflow.flow

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.blockchain.componentlib.viewextensions.visible
import com.blockchain.componentlib.viewextensions.visibleIf
import com.blockchain.core.price.ExchangeRates
import com.blockchain.koin.scopedInject
import com.blockchain.preferences.CurrencyPrefs
import org.koin.android.ext.android.inject
import piuk.blockchain.android.databinding.FragmentTxFlowConfirmBinding
import piuk.blockchain.android.ui.customviews.BlockchainListDividerDecor
import piuk.blockchain.android.ui.transactionflow.engine.TransactionIntent
import piuk.blockchain.android.ui.transactionflow.engine.TransactionState
import piuk.blockchain.android.ui.transactionflow.engine.TxExecutionStatus
import piuk.blockchain.android.ui.transactionflow.flow.adapter.ConfirmTransactionDelegateAdapter
import piuk.blockchain.android.ui.transactionflow.flow.customisations.TransactionConfirmationCustomisations
import piuk.blockchain.android.ui.transactionflow.plugin.TxFlowWidget
import timber.log.Timber

class ConfirmTransactionFragment : TransactionFlowFragment<FragmentTxFlowConfirmBinding>() {

    private val exchangeRates: ExchangeRates by scopedInject()
    private val prefs: CurrencyPrefs by scopedInject()
    private val mapper: TxConfirmReadOnlyMapperCheckout by scopedInject()
    private val customiser: TransactionConfirmationCustomisations by inject()

    private var headerSlot: TxFlowWidget? = null

    private val listAdapter: ConfirmTransactionDelegateAdapter by lazy {
        ConfirmTransactionDelegateAdapter(
            model = model,
            mapper = mapper,
            selectedCurrency = prefs.selectedFiatCurrency,
            exchangeRates = exchangeRates
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding.confirmDetailsList) {
            addItemDecoration(BlockchainListDividerDecor(requireContext()))

            layoutManager = LinearLayoutManager(
                requireContext(),
                LinearLayoutManager.VERTICAL,
                false
            )
            adapter = listAdapter
        }
        model.process(TransactionIntent.ValidateTransaction)
    }

    override fun initBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentTxFlowConfirmBinding =
        FragmentTxFlowConfirmBinding.inflate(inflater, container, false)

    override fun render(newState: TransactionState) {
        Timber.d("!TRANSACTION!> Rendering! ConfirmTransactionFragment")
        // We _should_ always have a pending Tx when we get here
        newState.pendingTx?.let {
            listAdapter.items = newState.pendingTx.confirmations.toList()
        }

        if (newState.executionStatus == TxExecutionStatus.Cancelled) {
            activity.finish()
        }

        with(binding) {
            with(confirmCtaButton) {
                text = customiser.confirmCtaText(newState)
                isEnabled = newState.nextEnabled
                setOnClickListener { onCtaClick(newState) }
            }

            with(buttonCancel) {
                text = customiser.cancelButtonText(newState.action)
                visibleIf { customiser.cancelButtonVisible(newState.action) }
                onClick = { onCancelClick() }
            }

            if (customiser.confirmDisclaimerVisibility(newState.action)) {
                confirmDisclaimer.apply {
                    text = customiser.confirmDisclaimerBlurb(newState, requireContext())
                    visible()
                    movementMethod = LinkMovementMethod.getInstance()
                }
            }
            initialiseHeaderSlotIfNeeded(newState)
        }

        headerSlot?.update(newState)
    }

    private fun FragmentTxFlowConfirmBinding.initialiseHeaderSlotIfNeeded(state: TransactionState) {
        if (headerSlot == null) {
            headerSlot = customiser.confirmInstallHeaderView(
                requireContext(),
                confirmHeaderSlot,
                state
            ).apply {
                initControl(model, customiser, analyticsHooks)
            }
        }
    }

    private fun onCtaClick(state: TransactionState) {
        analyticsHooks.onConfirmationCtaClick(state)
        model.process(TransactionIntent.ExecuteTransaction)
    }

    private fun onCancelClick() {
        model.process(TransactionIntent.CancelTransaction)
    }

    companion object {
        fun newInstance(): ConfirmTransactionFragment = ConfirmTransactionFragment()
    }
}

package piuk.blockchain.android.ui.transactionflow.flow

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.blockchain.coincore.BlockchainAccount
import com.blockchain.coincore.CryptoAddress
import com.blockchain.coincore.SingleAccount
import com.blockchain.componentlib.viewextensions.getTextString
import com.blockchain.componentlib.viewextensions.gone
import com.blockchain.componentlib.viewextensions.visible
import com.blockchain.componentlib.viewextensions.visibleIf
import com.blockchain.koin.scopedInject
import com.google.android.material.snackbar.Snackbar
import info.blockchain.balance.AssetInfo
import info.blockchain.balance.asAssetInfoOrThrow
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.databinding.FragmentTxFlowEnterAddressBinding
import piuk.blockchain.android.scan.QrScanResultProcessor
import piuk.blockchain.android.ui.customviews.BlockchainSnackbar
import piuk.blockchain.android.ui.customviews.EditTextUpdateThrottle
import piuk.blockchain.android.ui.scan.QrExpected
import piuk.blockchain.android.ui.scan.QrScanActivity
import piuk.blockchain.android.ui.scan.QrScanActivity.Companion.getRawScanData
import piuk.blockchain.android.ui.transactionflow.engine.TransactionIntent
import piuk.blockchain.android.ui.transactionflow.engine.TransactionState
import piuk.blockchain.android.ui.transactionflow.flow.customisations.TargetAddressSheetState
import piuk.blockchain.android.ui.transactionflow.flow.customisations.TargetSelectionCustomisations
import piuk.blockchain.android.ui.transactionflow.plugin.TxFlowWidget
import piuk.blockchain.android.util.clearErrorState
import piuk.blockchain.android.util.setErrorState
import timber.log.Timber

class EnterTargetAddressFragment : TransactionFlowFragment<FragmentTxFlowEnterAddressBinding>() {

    private val customiser: TargetSelectionCustomisations by inject()
    private val qrProcessor: QrScanResultProcessor by scopedInject()
    private var sourceSlot: TxFlowWidget? = null
    private var state = TransactionState()

    private val disposables = CompositeDisposable()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            fieldAddress.addTextChangedListener(addressTextWatcher)
            addressEntry.setEndIconOnClickListener { onLaunchAddressScan() }

            walletSelect.apply {
                onLoadError = {
                    hideTransferList()
                }
                onListLoaded = {
                    if (it) hideTransferList()
                }
            }
        }
    }

    override fun initBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentTxFlowEnterAddressBinding =
        FragmentTxFlowEnterAddressBinding.inflate(inflater, container, false)

    private fun onAddressEditUpdated(s: Editable?) {
        val address = s.toString()

        if (address.isEmpty()) {
            model.process(TransactionIntent.EnteredAddressReset)
        } else {
            binding.walletSelect.clearSelectedAccount()
            addressEntered(address, state.sendingAsset.asAssetInfoOrThrow())
        }
    }

    override fun render(newState: TransactionState) {
        Timber.d("!TRANSACTION!> Rendering! EnterTargetAddressFragment")

        with(binding) {

            if (sourceSlot == null) {
                sourceSlot = customiser.installAddressSheetSource(requireContext(), fromDetails, newState)

                setupTransferList(newState)
                setupLabels(newState)
            }
            sourceSlot?.update(newState)

            upsellGroup.visibleIf { customiser.shouldShowCustodialUpsell(newState) }

            if (customiser.selectTargetShowManualEnterAddress(newState)) {
                showManualAddressEntry(newState)
            } else {
                hideManualAddressEntry(newState)
            }

            customiser.issueFlashMessage(newState, null).takeIf { it.isNotEmpty() }?.let { errorMsg ->
                addressEntry.setErrorState(errorMsg)
                warningMessage.gone()
            } ?: hideErrorState()

            ctaButton.isEnabled = newState.nextEnabled
            ctaButton.setOnClickListener {
                onCtaClick(newState)
            }
        }
        state = newState
    }

    private fun setupLabels(state: TransactionState) {
        with(binding) {
            titleFrom.title = customiser.selectTargetSourceLabel(state)
            titleTo.title = customiser.selectTargetDestinationLabel(state)
            subtitle.visibleIf { customiser.selectTargetShouldShowSubtitle(state) }
            subtitle.text = customiser.selectTargetSubtitle(state)
            warningMessage.text = customiser.selectTargetAddressInputWarning(state)
            titlePick.apply {
                visibleIf { customiser.selectTargetShouldShowTargetPickTitle(state) }
                title = customiser.selectTargetAddressTitlePick(state)
            }
        }
    }

    private fun hideErrorState() {
        binding.addressEntry.clearErrorState()
        binding.warningMessage.visible()
    }

    private fun showManualAddressEntry(newState: TransactionState) {
        val address = if (newState.selectedTarget is CryptoAddress) {
            newState.selectedTarget.label
        } else {
            ""
        }

        with(binding) {
            if (address.isNotEmpty() && address != fieldAddress.getTextString()) {
                fieldAddress.setText(address, TextView.BufferType.EDITABLE)
            }
            addressEntry.hint = customiser.selectTargetAddressInputHint(newState)
            inputSwitcher.visible()
            inputSwitcher.displayedChild = NONCUSTODIAL_INPUT
        }
    }

    private fun hideManualAddressEntry(newState: TransactionState) {
        val msg = customiser.selectTargetNoAddressMessageText(newState)

        with(binding) {
            if (msg != null) {
                inputSwitcher.visible()
                noManualEnterMsg.text = msg

                internalSendClose.setOnClickListener {
                    inputSwitcher.gone()
                }

                titlePick.gone()
                inputSwitcher.displayedChild = CUSTODIAL_INPUT
            } else {
                inputSwitcher.gone()
                titlePick.gone()
            }
        }
    }

    private fun setupTransferList(state: TransactionState) {
        val fragmentState = customiser.enterTargetAddressFragmentState(state)

        with(binding.walletSelect) {
            initialise(
                source = Single.just(fragmentState.accounts.filterIsInstance<BlockchainAccount>()),
                status = customiser.selectTargetStatusDecorator(state),
                shouldShowSelectionStatus = true,
                assetAction = state.action
            )

            when (fragmentState) {
                is TargetAddressSheetState.SelectAccountWhenWithinMaxLimit -> {
                    onAccountSelected = {
                        accountSelected(it)
                    }
                }
                is TargetAddressSheetState.TargetAccountSelected -> {
                    updatedSelectedAccount(
                        fragmentState.accounts.filterIsInstance<BlockchainAccount>().first()
                    )
                    onAccountSelected = {
                        accountSelected(it)
                    }
                }
                else -> {
                    // do nothing
                }
            }
        }
    }

    private fun hideTransferList() {
        binding.titlePick.gone()
        binding.walletSelect.gone()
    }

    private fun accountSelected(account: BlockchainAccount) {
        require(account is SingleAccount)
        analyticsHooks.onAccountSelected(account, state)

        binding.walletSelect.updatedSelectedAccount(account)
        // TODO update the selected target (account type) instead so the render method knows what to show  & hide
        setAddressValue("")
        model.process(TransactionIntent.TargetSelectionUpdated(account))
    }

    private fun onLaunchAddressScan() {
        analyticsHooks.onScanQrClicked(state)
        QrScanActivity.start(this, QrExpected.ASSET_ADDRESS_QR(state.sendingAsset.asAssetInfoOrThrow()))
    }

    private fun addressEntered(address: String, asset: AssetInfo) {
        analyticsHooks.onManualAddressEntered(state)
        model.process(TransactionIntent.ValidateInputTargetAddress(address, asset))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) =
        when (requestCode) {
            QrScanActivity.SCAN_URI_RESULT -> handleScanResult(resultCode, data)
            else -> super.onActivityResult(requestCode, resultCode, data)
        }

    private fun setAddressValue(value: String) {
        with(binding.fieldAddress) {
            removeTextChangedListener(addressTextWatcher)
            setText(value, TextView.BufferType.EDITABLE)
            setSelection(value.length)
            addTextChangedListener(addressTextWatcher)
        }
    }

    private fun handleScanResult(resultCode: Int, data: Intent?) {
        Timber.d("Got QR scan result!")
        if (resultCode == Activity.RESULT_OK) {
            data.getRawScanData()?.let { rawScan ->
                disposables += qrProcessor.processScan(rawScan, false)
                    .flatMapMaybe { qrProcessor.selectAssetTargetFromScan(state.sendingAsset.asAssetInfoOrThrow(), it) }
                    .subscribeBy(
                        onSuccess = {
                            // TODO update the selected target (address type) instead so the render method knows what to show  & hide
                            setAddressValue(it.address)
                            binding.walletSelect.clearSelectedAccount()
                            model.process(TransactionIntent.TargetSelectionUpdated(it))
                        },
                        onComplete = {
                            BlockchainSnackbar.make(
                                binding.root,
                                getString(R.string.scan_mismatch_transaction_target, state.sendingAsset.displayTicker),
                                duration = Snackbar.LENGTH_SHORT,
                            ).show()
                        },
                        onError = {
                            BlockchainSnackbar.make(
                                binding.root,
                                getString(R.string.scan_failed),
                                duration = Snackbar.LENGTH_SHORT,
                            ).show()
                        }
                    )
            }
        }
    }

    private fun onCtaClick(state: TransactionState) {
        analyticsHooks.onEnterAddressCtaClick(state)
        model.process(TransactionIntent.TargetSelected)
    }

    private val addressTextWatcher = EditTextUpdateThrottle(
        updateFn = ::onAddressEditUpdated,
        updateDelayMillis = ADDRESS_UPDATE_INTERVAL
    )

    companion object {
        private const val NONCUSTODIAL_INPUT = 0
        private const val CUSTODIAL_INPUT = 1
        private const val ADDRESS_UPDATE_INTERVAL = 500L

        fun newInstance(): EnterTargetAddressFragment = EnterTargetAddressFragment()
    }
}

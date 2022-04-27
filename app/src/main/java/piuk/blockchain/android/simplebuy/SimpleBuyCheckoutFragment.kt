package piuk.blockchain.android.simplebuy

import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.blockchain.commonarch.presentation.mvi.MviFragment
import com.blockchain.componentlib.viewextensions.gone
import com.blockchain.componentlib.viewextensions.setOnClickListenerDebounced
import com.blockchain.componentlib.viewextensions.visible
import com.blockchain.componentlib.viewextensions.visibleIf
import com.blockchain.core.custodial.models.Promo
import com.blockchain.extensions.exhaustive
import com.blockchain.koin.scopedInject
import com.blockchain.nabu.datamanagers.OrderState
import com.blockchain.nabu.datamanagers.PaymentMethod.Companion.GOOGLE_PAY_PAYMENT_ID
import com.blockchain.nabu.datamanagers.custodialwalletimpl.PaymentMethodType
import com.blockchain.nabu.models.data.RecurringBuyFrequency
import com.blockchain.payments.googlepay.interceptor.OnGooglePayDataReceivedListener
import com.blockchain.payments.googlepay.manager.GooglePayManager
import com.blockchain.payments.googlepay.manager.request.GooglePayRequestBuilder
import com.blockchain.payments.googlepay.manager.request.allowedAuthMethods
import com.blockchain.payments.googlepay.manager.request.allowedCardNetworks
import com.blockchain.utils.secondsToDays
import info.blockchain.balance.AssetInfo
import info.blockchain.balance.FiatValue
import info.blockchain.balance.Money
import info.blockchain.balance.isCustodialOnly
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.databinding.FragmentSimplebuyCheckoutBinding
import piuk.blockchain.android.databinding.PromoLayoutBinding
import piuk.blockchain.android.simplebuy.sheets.SimpleBuyCancelOrderBottomSheet
import piuk.blockchain.android.ui.base.ErrorDialogData
import piuk.blockchain.android.ui.base.ErrorSlidingBottomDialog
import piuk.blockchain.android.ui.customviews.BlockchainListDividerDecor
import piuk.blockchain.android.ui.linkbank.yapily.permission.SAFECONNECT_TERMS_FILE_EXTENSION
import piuk.blockchain.android.ui.linkbank.yapily.permission.SAFECONNECT_TERMS_FILE_NAME
import piuk.blockchain.android.urllinks.ORDER_PRICE_EXPLANATION
import piuk.blockchain.android.urllinks.PRIVATE_KEY_EXPLANATION
import piuk.blockchain.android.urllinks.TRADING_ACCOUNT_LOCKS
import piuk.blockchain.android.urllinks.URL_YAPILY_PRIVACY_POLICY
import piuk.blockchain.android.util.StringAnnotationClickEvent
import piuk.blockchain.android.util.StringUtils
import piuk.blockchain.android.util.getFilePath
import piuk.blockchain.android.util.openPdfFile
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import java.time.ZonedDateTime

class SimpleBuyCheckoutFragment :
    MviFragment<SimpleBuyModel, SimpleBuyIntent, SimpleBuyState, FragmentSimplebuyCheckoutBinding>(),
    SimpleBuyScreen,
    SimpleBuyCancelOrderBottomSheet.Host,
    OnGooglePayDataReceivedListener {

    override val model: SimpleBuyModel by scopedInject()
    private val googlePayManager: GooglePayManager by inject()

    private var lastState: SimpleBuyState? = null
    private val checkoutAdapterDelegate = CheckoutAdapterDelegate()

    private val isForPendingPayment: Boolean by unsafeLazy {
        arguments?.getBoolean(PENDING_PAYMENT_ORDER_KEY, false) ?: false
    }

    private val showOnlyOrderData: Boolean by unsafeLazy {
        arguments?.getBoolean(SHOW_ONLY_ORDER_DATA, false) ?: false
    }

    override fun initBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentSimplebuyCheckoutBinding =
        FragmentSimplebuyCheckoutBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recycler.apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = checkoutAdapterDelegate
            addItemDecoration(BlockchainListDividerDecor(requireContext()))
        }

        model.process(SimpleBuyIntent.FlowCurrentScreen(FlowScreen.CHECKOUT))
        if (!showOnlyOrderData) {
            setupToolbar()
        }

        model.process(SimpleBuyIntent.FetchWithdrawLockTime)
    }

    private fun setupToolbar() {
        activity.updateToolbar(
            toolbarTitle = if (isForPendingPayment) {
                getString(R.string.order_details)
            } else {
                getString(R.string.checkout)
            },
            backAction = { if (!isForPendingPayment) activity.onBackPressed() }
        )
    }

    override fun backPressedHandled(): Boolean = isForPendingPayment

    override fun navigator(): SimpleBuyNavigator =
        (activity as? SimpleBuyNavigator) ?: throw IllegalStateException(
            "Parent must implement SimpleBuyNavigator"
        )

    override fun onBackPressed(): Boolean = true

    override fun render(newState: SimpleBuyState) {
        // Event should be triggered only the first time a state is rendered
        if (lastState == null) {
            analytics.logEvent(
                eventWithPaymentMethod(
                    SimpleBuyAnalytics.CHECKOUT_SUMMARY_SHOWN,
                    newState.selectedPaymentMethod?.paymentMethodType?.toAnalyticsString().orEmpty()
                )
            )
            lastState = newState
        }

        newState.selectedCryptoAsset?.let { renderPrivateKeyLabel(it) }
        val payment = newState.selectedPaymentMethod
        val note = when {
            payment?.isCard() == true -> showWithdrawalPeriod(newState)
            payment?.isFunds() == true -> getString(R.string.purchase_funds_note)
            payment?.isBank() == true -> showWithdrawalPeriod(newState)
            else -> ""
        }

        binding.purchaseNote.apply {
            if (note.isBlank())
                gone()
            else {
                visible()
                movementMethod = LinkMovementMethod.getInstance()
                text = note
            }
        }

        binding.termsAndPrivacy.apply {
            if (newState.isOpenBankingTransfer()) {
                visible()

                val linksMap = mapOf(
                    "terms" to StringAnnotationClickEvent.CustomCta(::downloadSafeConnectTos),
                    "privacy" to StringAnnotationClickEvent.OpenUri(Uri.parse(URL_YAPILY_PRIVACY_POLICY))
                )

                text = StringUtils.getStringWithMappedAnnotations(
                    context = requireContext(),
                    stringId = R.string.yapily_permission_confirmation_buy,
                    linksMap = linksMap
                )
                movementMethod = LinkMovementMethod.getInstance()
            } else {
                gone()
            }
        }

        if (newState.buyErrorState != null) {
            showErrorState(newState.buyErrorState)
            return
        }

        showAmountForMethod(newState)

        updateStatusPill(newState)

        if (newState.paymentOptions.availablePaymentMethods.isEmpty()) {
            model.process(
                SimpleBuyIntent.FetchPaymentDetails(
                    newState.fiatCurrency, newState.selectedPaymentMethod?.id.orEmpty()
                )
            )
        } else {
            checkoutAdapterDelegate.items = getCheckoutFields(newState)
        }

        configureButtons(newState)

        when (newState.order.orderState) {
            OrderState.FINISHED, // Funds orders are getting finished right after confirmation
            OrderState.AWAITING_FUNDS -> {
                if (newState.confirmationActionRequested) {
                    navigator().goToPaymentScreen()
                }
            }
            OrderState.FAILED -> {

                binding.buttonAction.isEnabled = false
            }
            OrderState.CANCELED -> {
                if (activity is SmallSimpleBuyNavigator) {
                    (activity as SmallSimpleBuyNavigator).exitSimpleBuyFlow()
                } else {
                    navigator().exitSimpleBuyFlow()
                }
            }
            else -> {
                // do nothing
            }
        }

        newState.googlePayTokenizationInfo?.let { tokenizationMap ->
            if (tokenizationMap.isNotEmpty()) {
                googlePayManager.requestPayment(
                    GooglePayRequestBuilder.buildForPaymentRequest(
                        allowedAuthMethods = allowedAuthMethods,
                        allowedCardNetworks = allowedCardNetworks,
                        gatewayTokenizationParameters = tokenizationMap,
                        totalPrice = newState.amount.toStringWithoutSymbol(),
                        countryCode = newState.googlePayMerchantBankCountryCode.orEmpty(),
                        currencyCode = newState.fiatCurrency.networkTicker,
                        allowPrepaidCards = newState.googlePayAllowPrepaidCards ?: true,
                        allowCreditCards = newState.googlePayAllowCreditCards ?: true
                    ),
                    requireActivity()
                )

                model.process(SimpleBuyIntent.ClearGooglePayInfo)
            }
        }

        newState.safeConnectTosPdf?.let {
            context.openPdfFile(it)
            model.process(SimpleBuyIntent.SafeConnectTosOpened)
        }
    }

    private fun downloadSafeConnectTos() {
        context?.let { context ->
            model.process(SimpleBuyIntent.DownloadSafeConnectTos(context.getFilePath(
                fileName = SAFECONNECT_TERMS_FILE_NAME, extension = SAFECONNECT_TERMS_FILE_EXTENSION
            )))
        }
    }

    private fun renderPrivateKeyLabel(selectedCryptoAsset: AssetInfo) {
        if (selectedCryptoAsset.isCustodialOnly) {
            val map = mapOf("learn_more_link" to Uri.parse(PRIVATE_KEY_EXPLANATION))
            val learnMoreLink = StringUtils.getStringWithMappedAnnotations(
                requireContext(),
                R.string.common_linked_learn_more,
                map
            )

            val sb = SpannableStringBuilder()
            val privateKeyExplanation =
                getString(
                    R.string.checkout_item_private_key_wallet_explanation_1,
                    selectedCryptoAsset.displayTicker
                )
            sb.append(privateKeyExplanation)
                .append(learnMoreLink)
                .setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(activity, R.color.blue_600)),
                    privateKeyExplanation.length, privateKeyExplanation.length + learnMoreLink.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

            binding.privateKeyExplanation.apply {
                setText(sb, TextView.BufferType.SPANNABLE)
                movementMethod = LinkMovementMethod.getInstance()
                visible()
            }
        }
    }

    private fun showWithdrawalPeriod(newState: SimpleBuyState) =
        newState.withdrawalLockPeriod.secondsToDays().takeIf { it > 0 }?.let {
            StringUtils.getResolvedStringWithAppendedMappedLearnMore(
                getString(R.string.security_locked_funds_bank_transfer_explanation_1, it.toString()),
                R.string.common_linked_learn_more, TRADING_ACCOUNT_LOCKS, requireActivity(), R.color.blue_600
            )
        } ?: getString(R.string.security_no_lock_bank_transfer_explanation)

    private fun showAmountForMethod(newState: SimpleBuyState) {
        binding.amount.text = newState.orderValue?.toStringWithSymbol()
        binding.amountFiat.text = newState.order.amount?.toStringWithSymbol()
    }

    private fun updateStatusPill(newState: SimpleBuyState) {
        with(binding.status) {
            when {
                isPendingOrAwaitingFunds(newState.orderState) -> {
                    text = getString(R.string.order_pending)
                    background =
                        ContextCompat.getDrawable(requireContext(), R.drawable.bkgd_status_unconfirmed)
                    setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.grey_800)
                    )
                }
                newState.orderState == OrderState.FINISHED -> {
                    text = getString(R.string.order_complete)
                    background =
                        ContextCompat.getDrawable(requireContext(), R.drawable.bkgd_green_100_rounded)
                    setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.green_600)
                    )
                }
                else -> {
                    gone()
                }
            }
        }
    }

    private fun getCheckoutFields(state: SimpleBuyState): List<SimpleBuyCheckoutItem> {

        require(state.selectedCryptoAsset != null)

        val priceExplanation = StringUtils.getResolvedStringWithAppendedMappedLearnMore(
            staticText = if (state.coinHasZeroMargin)
                getString(
                    R.string.checkout_item_price_blurb_zero_margin,
                    state.selectedCryptoAsset.displayTicker
                ) else getString(R.string.checkout_item_price_blurb),
            textToMap = R.string.learn_more_annotated,
            url = ORDER_PRICE_EXPLANATION,
            context = requireContext(),
            linkColour = R.color.blue_600
        )

        return listOfNotNull(
            SimpleBuyCheckoutItem.ExpandableCheckoutItem(
                getString(R.string.quote_price, state.selectedCryptoAsset.displayTicker),
                state.exchangeRate?.toStringWithSymbol().orEmpty(),
                priceExplanation
            ),
            buildPaymentMethodItem(state),
            if (state.recurringBuyFrequency != RecurringBuyFrequency.ONE_TIME) {
                SimpleBuyCheckoutItem.ComplexCheckoutItem(
                    getString(R.string.recurring_buy_frequency_label_1),
                    state.recurringBuyFrequency.toHumanReadableRecurringBuy(requireContext()),
                    state.recurringBuyFrequency.toHumanReadableRecurringDate(
                        requireContext(), ZonedDateTime.now()
                    )
                )
            } else null,

            SimpleBuyCheckoutItem.SimpleCheckoutItem(
                getString(R.string.purchase),
                state.purchasedAmount().toStringWithSymbol()
            ),
            buildPaymentFee(
                state,
                getString(R.string.checkout_item_price_fee)
            ),
            SimpleBuyCheckoutItem.SimpleCheckoutItem(
                getString(R.string.common_total),
                state.amount.toStringWithSymbol(),
                true
            )
        )
    }

    private fun buildPaymentMethodItem(state: SimpleBuyState): SimpleBuyCheckoutItem? =
        state.selectedPaymentMethod?.let {
            val paymentMethodType = if (state.selectedPaymentMethodDetails?.id == GOOGLE_PAY_PAYMENT_ID)
                PaymentMethodType.GOOGLE_PAY else it.paymentMethodType

            when (paymentMethodType) {
                PaymentMethodType.FUNDS -> SimpleBuyCheckoutItem.SimpleCheckoutItem(
                    getString(R.string.payment_method),
                    getString(R.string.fiat_currency_funds_wallet_name_1, state.fiatCurrency)
                )
                PaymentMethodType.BANK_TRANSFER,
                PaymentMethodType.BANK_ACCOUNT,
                PaymentMethodType.PAYMENT_CARD -> {
                    state.selectedPaymentMethodDetails?.let { details ->
                        SimpleBuyCheckoutItem.ComplexCheckoutItem(
                            getString(R.string.payment_method),
                            details.methodDetails(),
                            details.methodName()
                        )
                    }
                }
                PaymentMethodType.GOOGLE_PAY -> {
                    state.selectedPaymentMethodDetails?.let { details ->
                        SimpleBuyCheckoutItem.SimpleCheckoutItem(
                            getString(R.string.payment_method),
                            details.methodDetails()
                        )
                    }
                }
                PaymentMethodType.UNKNOWN -> null
            }
        }

    private fun buildPaymentFee(state: SimpleBuyState, feeExplanation: CharSequence): SimpleBuyCheckoutItem? =
        state.quote?.feeDetails?.let { feeDetails ->
            SimpleBuyCheckoutItem.ExpandableCheckoutItem(
                getString(R.string.blockchain_fee),
                feeDetails.fee.toStringWithSymbol(),
                feeExplanation,
                viewForPromo(feeDetails)
            )
        }

    private fun isPendingOrAwaitingFunds(orderState: OrderState) =
        isForPendingPayment || orderState == OrderState.AWAITING_FUNDS

    private fun configureButtons(state: SimpleBuyState) {
        val isOrderAwaitingFunds = state.orderState == OrderState.AWAITING_FUNDS
        val isGooglePay = state.selectedPaymentMethod?.id == GOOGLE_PAY_PAYMENT_ID &&
            !isForPendingPayment && !isOrderAwaitingFunds

        with(binding) {
            buttonAction.apply {
                if (!isForPendingPayment && !isOrderAwaitingFunds) {
                    text = getString(R.string.buy_asset_now, state.orderValue?.toStringWithSymbol())
                    setOnClickListener {
                        model.process(SimpleBuyIntent.ConfirmOrder)
                        analytics.logEvent(
                            eventWithPaymentMethod(
                                SimpleBuyAnalytics.CHECKOUT_SUMMARY_CONFIRMED,
                                state.selectedPaymentMethod?.paymentMethodType?.toAnalyticsString().orEmpty()
                            )
                        )
                    }
                } else {
                    text = if (isOrderAwaitingFunds && !isForPendingPayment) {
                        getString(R.string.complete_payment)
                    } else {
                        getString(R.string.common_ok)
                    }
                    setOnClickListener {
                        if (isForPendingPayment) {
                            navigator().exitSimpleBuyFlow()
                        } else {
                            navigator().goToPaymentScreen()
                        }
                    }
                }
                visibleIf { !showOnlyOrderData && !isGooglePay }
                isEnabled = !state.isLoading
            }

            buttonCancel.visibleIf {
                isOrderAwaitingFunds && state.selectedPaymentMethod?.isBank() == true
            }
            buttonCancel.setOnClickListenerDebounced {
                analytics.logEvent(SimpleBuyAnalytics.CHECKOUT_SUMMARY_PRESS_CANCEL)
                showBottomSheet(SimpleBuyCancelOrderBottomSheet.newInstance())
            }
            buttonGooglePay.apply {
                visibleIf { isGooglePay }
                setOnClickListener {
                    buttonGooglePay.showLoading()
                    model.process(SimpleBuyIntent.GooglePayInfoRequested)
                }
            }
        }
    }

    private fun showErrorState(errorState: ErrorState) {
        when (errorState) {
            ErrorState.DailyLimitExceeded -> showBottomSheet(
                ErrorSlidingBottomDialog.newInstance(
                    ErrorDialogData(
                        getString(R.string.sb_checkout_daily_limit_title),
                        getString(R.string.sb_checkout_daily_limit_blurb),
                        getString(R.string.common_ok)
                    )
                )
            )
            ErrorState.WeeklyLimitExceeded -> showBottomSheet(
                ErrorSlidingBottomDialog.newInstance(
                    ErrorDialogData(
                        getString(R.string.sb_checkout_weekly_limit_title),
                        getString(R.string.sb_checkout_weekly_limit_blurb),
                        getString(R.string.common_ok)
                    )
                )
            )
            ErrorState.YearlyLimitExceeded -> showBottomSheet(
                ErrorSlidingBottomDialog.newInstance(
                    ErrorDialogData(
                        getString(R.string.sb_checkout_yearly_limit_title),
                        getString(R.string.sb_checkout_yearly_limit_blurb),
                        getString(R.string.common_ok)
                    )
                )
            )
            ErrorState.ExistingPendingOrder -> showBottomSheet(
                ErrorSlidingBottomDialog.newInstance(
                    ErrorDialogData(
                        getString(R.string.sb_checkout_pending_order_title),
                        getString(R.string.sb_checkout_pending_order_blurb),
                        getString(R.string.common_ok)
                    )
                )
            )
            ErrorState.InsufficientCardFunds -> showBottomSheet(
                ErrorSlidingBottomDialog.newInstance(
                    ErrorDialogData(
                        getString(R.string.sb_checkout_card_insufficient_funds_title),
                        getString(R.string.sb_checkout_card_insufficient_funds_blurb),
                        getString(R.string.common_ok)
                    )
                )
            )
            ErrorState.DebitCardOnly -> showBottomSheet(
                ErrorSlidingBottomDialog.newInstance(
                    ErrorDialogData(
                        getString(R.string.sb_checkout_card_debit_only_title),
                        getString(R.string.sb_checkout_card_debit_only_blurb),
                        getString(R.string.sb_checkout_card_debit_only_cta)
                    )
                )
            )
            ErrorState.CardPaymentDeclined -> showBottomSheet(
                ErrorSlidingBottomDialog.newInstance(
                    ErrorDialogData(
                        getString(R.string.sb_checkout_card_declined_title),
                        getString(R.string.sb_checkout_card_declined_blurb),
                        getString(R.string.common_ok)
                    )
                )
            )
            is ErrorState.UnhandledHttpError -> showBottomSheet(
                ErrorSlidingBottomDialog.newInstance(
                    ErrorDialogData(
                        getString(
                            R.string.common_http_error_with_message, errorState.nabuApiException.getErrorDescription()
                        ),
                        getString(R.string.something_went_wrong_try_again),
                        getString(R.string.common_ok)
                    )
                )
            )
            ErrorState.InternetConnectionError -> showBottomSheet(
                ErrorSlidingBottomDialog.newInstance(
                    ErrorDialogData(
                        getString(
                            R.string.executing_connection_error
                        ),
                        getString(R.string.something_went_wrong_try_again),
                        getString(R.string.common_ok)
                    )
                )
            )
            is ErrorState.ApprovedBankUndefinedError -> showBottomSheet(
                ErrorSlidingBottomDialog.newInstance(
                    ErrorDialogData(
                        getString(
                            R.string.payment_failed_title_with_reason
                        ),
                        getString(R.string.something_went_wrong_try_again),
                        getString(R.string.common_ok)
                    )
                )
            )
            ErrorState.ApproveBankInvalid,
            ErrorState.ApprovedBankAccountInvalid,
            ErrorState.ApprovedBankDeclined,
            ErrorState.ApprovedBankExpired,
            ErrorState.ApprovedBankFailed,
            ErrorState.ApprovedBankFailedInternal,
            ErrorState.ApprovedBankInsufficientFunds,
            ErrorState.ApprovedBankLimitedExceed,
            ErrorState.BankLinkingTimeout,
            ErrorState.ApprovedBankRejected,
            is ErrorState.PaymentFailedError,
            ErrorState.UnknownCardProvider,
            ErrorState.ProviderIsNotSupported,
            ErrorState.Card3DsFailed,
            ErrorState.LinkedBankNotSupported -> throw IllegalStateException(
                "Error $errorState should not be presented in the checkout screen"
            )
        }.exhaustive
    }

    override fun cancelOrderConfirmAction(cancelOrder: Boolean, orderId: String?) {
        if (cancelOrder) {
            model.process(SimpleBuyIntent.CancelOrder)
            analytics.logEvent(
                eventWithPaymentMethod(
                    SimpleBuyAnalytics.CHECKOUT_SUMMARY_CANCELLATION_CONFIRMED,
                    lastState?.selectedPaymentMethod?.paymentMethodType?.toAnalyticsString().orEmpty()
                )
            )
        } else {
            analytics.logEvent(SimpleBuyAnalytics.CHECKOUT_SUMMARY_CANCELLATION_GO_BACK)
        }
    }

    override fun onResume() {
        super.onResume()
        binding.buttonGooglePay.hideLoading()
    }

    override fun onPause() {
        super.onPause()
        model.process(SimpleBuyIntent.NavigationHandled)
    }

    override fun onSheetClosed() {
        binding.buttonGooglePay.hideLoading()
    }

    override fun onGooglePayTokenReceived(token: String) {
        model.process(SimpleBuyIntent.ConfirmGooglePayOrder(googlePayPayload = token))
        binding.buttonGooglePay.showLoading()
    }

    override fun onGooglePayCancelled() {
        binding.buttonGooglePay.isEnabled = true
    }

    override fun onGooglePaySheetClosed() {
        binding.buttonGooglePay.isEnabled = true
    }

    override fun onGooglePayError(e: Throwable) {
        // TODO Show error sheet here?
        binding.buttonGooglePay.isEnabled = true
    }

    private fun viewForPromo(buyFees: BuyFees): View? {
        return buyFees.takeIf { it.promo != Promo.NO_PROMO }?.let { promotedFees ->
            val promoBinding = PromoLayoutBinding.inflate(LayoutInflater.from(context), null, false)
            return when (promotedFees.promo) {
                Promo.NEW_USER -> configureNewUserPromo(promoBinding, buyFees)
                Promo.NO_PROMO -> throw IllegalStateException("No Promo available")
            }
        }
    }

    private fun configureNewUserPromo(promoBinding: PromoLayoutBinding, fees: BuyFees): View =
        promoBinding.apply {
            feeWaiverPromo.setBackgroundResource(R.drawable.bkgd_green_100_rounded)
            label.text = getString(R.string.new_user_fee_waiver)
            afterPromoFee.text = fees.fee.toStringWithSymbolOrFree()
            val strikedThroughFee = SpannableString(fees.feeBeforePromo.toStringWithSymbol()).apply {
                setSpan(StrikethroughSpan(), 0, this.length - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            beforePromoFee.text = strikedThroughFee
        }.root

    private fun FiatValue.toStringWithSymbolOrFree(): String =
        if (isPositive) toStringWithSymbol() else getString(R.string.common_free)

    companion object {
        private const val PENDING_PAYMENT_ORDER_KEY = "PENDING_PAYMENT_KEY"
        private const val SHOW_ONLY_ORDER_DATA = "SHOW_ONLY_ORDER_DATA"

        fun newInstance(
            isForPending: Boolean = false,
            showOnlyOrderData: Boolean = false
        ): SimpleBuyCheckoutFragment {
            val fragment = SimpleBuyCheckoutFragment()
            fragment.arguments = Bundle().apply {
                putBoolean(PENDING_PAYMENT_ORDER_KEY, isForPending)
                putBoolean(SHOW_ONLY_ORDER_DATA, showOnlyOrderData)
            }
            return fragment
        }
    }
}

private fun SimpleBuyState.purchasedAmount(): Money {
    val fee = quote?.feeDetails?.fee ?: FiatValue.zero(fiatCurrency)
    return amount - fee
}

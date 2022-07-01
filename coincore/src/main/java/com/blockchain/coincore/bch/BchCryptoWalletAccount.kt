package com.blockchain.coincore.bch

import com.blockchain.coincore.ActivitySummaryList
import com.blockchain.coincore.AddressResolver
import com.blockchain.coincore.AssetAction
import com.blockchain.coincore.CryptoAccount
import com.blockchain.coincore.ReceiveAddress
import com.blockchain.coincore.TransactionTarget
import com.blockchain.coincore.TxEngine
import com.blockchain.coincore.impl.AccountRefreshTrigger
import com.blockchain.coincore.impl.CryptoNonCustodialAccount
import com.blockchain.coincore.impl.transactionFetchCount
import com.blockchain.coincore.impl.transactionFetchOffset
import com.blockchain.core.chains.bitcoincash.BchDataManager
import com.blockchain.core.price.ExchangeRatesDataManager
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.preferences.WalletStatusPrefs
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.Money
import info.blockchain.wallet.bch.BchMainNetParams
import info.blockchain.wallet.bch.CashAddress
import info.blockchain.wallet.coin.GenericMetadataAccount
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import java.util.concurrent.atomic.AtomicBoolean
import org.bitcoinj.core.LegacyAddress
import piuk.blockchain.androidcore.data.fees.FeeDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.data.payments.SendDataManager
import piuk.blockchain.androidcore.utils.extensions.mapList
import piuk.blockchain.androidcore.utils.extensions.then

/*internal*/ class BchCryptoWalletAccount private constructor(
    private val payloadDataManager: PayloadDataManager,
    private val bchManager: BchDataManager,
    // Used to lookup the account in payloadDataManager to fetch receive address
    private val addressIndex: Int,
    override val exchangeRates: ExchangeRatesDataManager,
    private val feeDataManager: FeeDataManager,
    private val sendDataManager: SendDataManager,
    private val internalAccount: GenericMetadataAccount,
    private val walletPreferences: WalletStatusPrefs,
    private val custodialWalletManager: CustodialWalletManager,
    private val refreshTrigger: AccountRefreshTrigger,
    override val addressResolver: AddressResolver,
) : CryptoNonCustodialAccount(
    CryptoCurrency.BCH
) {

    private val hasFunds = AtomicBoolean(false)

    override val label: String
        get() = internalAccount.label

    override fun getOnChainBalance(): Observable<Money> =
        Single.fromCallable { internalAccount.xpubs() }
            .flatMap { xpub -> bchManager.getBalance(xpub) }
            .map { CryptoValue.fromMinor(currency, it) as Money }
            .doOnSuccess { hasFunds.set(it.isPositive) }
            .toObservable()

    override val isArchived: Boolean
        get() = internalAccount.isArchived

    override val isDefault: Boolean
        get() = addressIndex == bchManager.getDefaultAccountPosition()

    override val isFunded: Boolean
        get() = hasFunds.get()

    override val receiveAddress: Single<ReceiveAddress>
        get() = bchManager.getNextReceiveAddress(
            addressIndex
        ).map {
            val networkParams = BchMainNetParams.get()
            val address = LegacyAddress.fromBase58(networkParams, it)
            CashAddress.fromLegacyAddress(address)
        }.firstOrError()
            .map {
                BchAddress(address_ = it, label = label)
            }

    override val activity: Single<ActivitySummaryList>
        get() = bchManager.getAddressTransactions(
            xpubAddress,
            transactionFetchCount,
            transactionFetchOffset
        ).onErrorReturn { emptyList() }
            .mapList {
                BchActivitySummaryItem(
                    it,
                    exchangeRates,
                    account = this,
                    payloadDataManager = payloadDataManager
                )
            }.flatMap {
                appendTradeActivity(custodialWalletManager, currency, it)
            }.doOnSuccess { setHasTransactions(it.isNotEmpty()) }

    override fun createTxEngine(target: TransactionTarget, action: AssetAction): TxEngine =
        BchOnChainTxEngine(
            feeManager = feeDataManager,
            sendDataManager = sendDataManager,
            bchDataManager = bchManager,
            payloadDataManager = payloadDataManager,
            requireSecondPassword = payloadDataManager.isDoubleEncrypted,
            walletPreferences = walletPreferences,
            resolvedAddress = addressResolver.getReceiveAddress(currency, target, action)
        )

    override fun updateLabel(newLabel: String): Completable {
        require(newLabel.isNotEmpty())
        val revertLabel = label
        internalAccount.label = newLabel
        return bchManager.syncWithServer()
            .doOnError { internalAccount.label = revertLabel }
    }

    override fun archive(): Completable =
        if (!isArchived && !isDefault) {
            toggleArchived()
        } else {
            Completable.error(IllegalStateException("${currency.networkTicker} Account $label cannot be archived"))
        }

    override fun unarchive(): Completable =
        if (isArchived) {
            toggleArchived()
        } else {
            Completable.error(IllegalStateException("${currency.networkTicker} Account $label cannot be unarchived"))
        }

    private fun toggleArchived(): Completable {
        val isArchived = this.isArchived
        internalAccount.isArchived = !isArchived

        return bchManager.syncWithServer()
            .doOnError { internalAccount.isArchived = isArchived } // Revert
            .then { bchManager.updateTransactions() }
    }

    override fun setAsDefault(): Completable {
        require(!isDefault)
        val revertDefault = bchManager.getDefaultAccountPosition()
        bchManager.setDefaultAccountPosition(addressIndex)
        return bchManager.syncWithServer()
            .doOnError { bchManager.setDefaultAccountPosition(revertDefault) }
    }

    override val xpubAddress: String
        get() = internalAccount.xpubs().default.address

    override fun matches(other: CryptoAccount): Boolean =
        other is BchCryptoWalletAccount && other.xpubAddress == xpubAddress

    fun getReceiveAddressAtPosition(position: Int) =
        bchManager.getReceiveAddressAtPosition(addressIndex, position)

    internal fun forceRefresh() {
        refreshTrigger.forceAccountsRefresh()
    }

    override fun doesAddressBelongToWallet(address: String): Boolean =
        payloadDataManager.isOwnHDAddress(address)

    override val hasStaticAddress: Boolean = false

    companion object {
        fun createBchAccount(
            payloadManager: PayloadDataManager,
            jsonAccount: GenericMetadataAccount,
            bchManager: BchDataManager,
            addressIndex: Int,
            exchangeRates: ExchangeRatesDataManager,
            feeDataManager: FeeDataManager,
            sendDataManager: SendDataManager,
            walletPreferences: WalletStatusPrefs,
            custodialWalletManager: CustodialWalletManager,
            refreshTrigger: AccountRefreshTrigger,
            addressResolver: AddressResolver,
        ) = BchCryptoWalletAccount(
            bchManager = bchManager,
            payloadDataManager = payloadManager,
            addressIndex = addressIndex,
            exchangeRates = exchangeRates,
            feeDataManager = feeDataManager,
            sendDataManager = sendDataManager,
            internalAccount = jsonAccount,
            walletPreferences = walletPreferences,
            custodialWalletManager = custodialWalletManager,
            refreshTrigger = refreshTrigger,
            addressResolver = addressResolver
        )
    }
}

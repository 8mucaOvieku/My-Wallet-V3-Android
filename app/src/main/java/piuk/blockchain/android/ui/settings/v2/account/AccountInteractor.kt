package piuk.blockchain.android.ui.settings.v2.account

import com.blockchain.core.price.ExchangeRatesDataManager
import com.blockchain.preferences.CurrencyPrefs
import info.blockchain.balance.FiatCurrency
import info.blockchain.wallet.api.data.Settings
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import piuk.blockchain.androidcore.data.settings.SettingsDataManager
import thepit.PitLinking

class AccountInteractor internal constructor(
    private val settingsDataManager: SettingsDataManager,
    private val exchangeRates: ExchangeRatesDataManager,
    private val currencyPrefs: CurrencyPrefs,
    private val exchangeLinkingState: PitLinking,
    private val debitCardState: PitLinking
) {

    fun getWalletInfo(): Single<AccountInformation> =
        settingsDataManager.getSettings().firstOrError().map {
            AccountInformation(
                walletId = it.guid,
                userCurrency = FiatCurrency.fromCurrencyCode(it.currency)
            )
        }

    fun getAvailableFiatList(): Single<List<FiatCurrency>> =
        Single.just(exchangeRates.fiatAvailableForRates)

    fun updateSelectedCurrency(currency: FiatCurrency): Observable<Settings> =
        settingsDataManager.updateFiatUnit(currency)
            .doOnComplete {
                currencyPrefs.selectedFiatCurrency = currency
            }

    fun getExchangeState(): Single<ExchangeLinkingState> =
        exchangeLinkingState.state.firstOrError().map {
            if (it.isLinked) {
                ExchangeLinkingState.LINKED
            } else {
                ExchangeLinkingState.NOT_LINKED
            }
        }

    fun getDebitCardState(): Single<DebitCardState> {
        debitCardState.state.firstOrError().map {
            // TODO add logic to check if there are any available cards for this user. No cards -> means we should allow the user to order if eligible
            // if there are cards -> we should not allow the user to order but we should still show the option in the menu
        }

        return Single.just(null)
    }
}

package piuk.blockchain.android.ui.settings.v2

import com.blockchain.koin.payloadScopeQualifier
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import org.koin.dsl.module
import piuk.blockchain.android.ui.settings.v2.account.AccountInteractor
import piuk.blockchain.android.ui.settings.v2.account.AccountModel
import piuk.blockchain.android.ui.settings.v2.account.AccountState
import piuk.blockchain.android.ui.settings.v2.notifications.NotificationsInteractor
import piuk.blockchain.android.ui.settings.v2.notifications.NotificationsModel
import piuk.blockchain.android.ui.settings.v2.notifications.NotificationsState
import piuk.blockchain.android.ui.settings.v2.profile.ProfileInteractor
import piuk.blockchain.android.ui.settings.v2.profile.ProfileModel
import piuk.blockchain.android.ui.settings.v2.profile.ProfileState
import piuk.blockchain.android.util.AppUtil

val redesignSettingsModule = module {

    scope(payloadScopeQualifier) {
        factory {
            SettingsModel(
                initialState = SettingsState(),
                mainScheduler = AndroidSchedulers.mainThread(),
                interactor = get(),
                environmentConfig = get(),
                crashLogger = get()
            )
        }

        factory {
            SettingsInteractor(
                userIdentity = get(),
                database = get(),
                credentialsWiper = get(),
                custodialWalletManager = get(),
                currencyPrefs = get()
            )
        }

        scoped {
            ProfileModel(
                initialState = ProfileState(),
                mainScheduler = AndroidSchedulers.mainThread(),
                interactor = get(),
                _activityIndicator = lazy { get<AppUtil>().activityIndicator },
                environmentConfig = get(),
                crashLogger = get()
            )
        }

        scoped {
            ProfileInteractor(
                emailUpdater = get(),
                settingsDataManager = get(),
                prefs = get(),
                nabuUserSync = get()
            )
        }

        factory {
            NotificationsModel(
                initialState = NotificationsState(),
                mainScheduler = AndroidSchedulers.mainThread(),
                interactor = get(),
                environmentConfig = get(),
                crashLogger = get()
            )
        }

        factory {
            AccountModel(
                initialState = AccountState(),
                mainScheduler = AndroidSchedulers.mainThread(),
                interactor = get(),
                environmentConfig = get(),
                crashLogger = get()
            )
        }

        factory {
            NotificationsInteractor(
                notificationPrefs = get(),
                notificationTokenManager = get(),
                settingsDataManager = get(),
                payloadDataManager = get()
            )
        }

        factory {
            AccountInteractor(
                settingsDataManager = get(),
                exchangeRates = get(),
                currencyPrefs = get(),
                exchangeLinkingState = get()
            )
        }
    }
}

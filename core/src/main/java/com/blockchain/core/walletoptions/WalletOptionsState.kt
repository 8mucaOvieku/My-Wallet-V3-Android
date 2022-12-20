package com.blockchain.core.walletoptions

import com.blockchain.utils.InvalidatableLazy
import info.blockchain.wallet.api.data.Settings
import info.blockchain.wallet.api.data.WalletOptions
import io.reactivex.rxjava3.subjects.ReplaySubject

class WalletOptionsState {

    private val optionsInitializer = InvalidatableLazy { ReplaySubject.create<WalletOptions>(1) }
    private val settingsInitializer = InvalidatableLazy { ReplaySubject.create<Settings>(1) }

    val walletOptionsSource: ReplaySubject<WalletOptions> by optionsInitializer
    val walletSettingsSource: ReplaySubject<Settings>by settingsInitializer

    fun wipe() {
        optionsInitializer.invalidate()
        settingsInitializer.invalidate()
    }
}

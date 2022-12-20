package com.blockchain.core.settings

import io.reactivex.rxjava3.core.Single

interface PhoneNumberUpdater {

    fun smsNumber(): Single<String>

    fun updateSms(phoneNumber: PhoneNumber): Single<String>

    fun verifySms(code: String): Single<String>
}

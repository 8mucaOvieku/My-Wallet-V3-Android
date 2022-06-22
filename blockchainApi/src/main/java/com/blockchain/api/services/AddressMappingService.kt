package com.blockchain.api.services

import com.blockchain.api.ApiException
import com.blockchain.api.HttpStatus
import com.blockchain.api.addressmapping.AddressMappingApiInterface
import com.blockchain.api.addressmapping.data.AddressMapRequest
import io.reactivex.rxjava3.core.Single
import java.util.Locale
import retrofit2.HttpException

class DomainAddressNotFound : Exception()

class AddressMappingService internal constructor(
    private val addressApi: AddressMappingApiInterface
) {
    fun resolveAssetAddress(
        domainName: String,
        assetTicker: String
    ): Single<String> =
        addressApi.resolveAssetAddress(
            AddressMapRequest(
                domainName = domainName.toLowerCase(Locale.ROOT),
                assetTicker = assetTicker
            )
        ).map {
            check(it.assetTicker.compareTo(assetTicker, true) == 0) { "Asset ticker mismatch" }
            it.address
        }.onErrorResumeNext {
            when {
                it is HttpException && it.code() == HttpStatus.NOT_FOUND -> Single.error(DomainAddressNotFound())
                else -> Single.error(ApiException(it.message))
            }
        }
}

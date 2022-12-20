package com.blockchain.api

import io.reactivex.rxjava3.core.Observable
import okhttp3.ResponseBody
import retrofit2.http.GET

interface ConnectionEndpoint {

    @GET(".")
    fun pingExplorer(): Observable<ResponseBody>
}

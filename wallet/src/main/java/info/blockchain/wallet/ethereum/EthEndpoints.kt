package info.blockchain.wallet.ethereum

import info.blockchain.wallet.ethereum.data.EthAddressResponse
import info.blockchain.wallet.ethereum.data.EthPushTxRequest
import info.blockchain.wallet.ethereum.data.EthTransaction
import info.blockchain.wallet.ethereum.data.EthTransactionsResponse
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

internal interface EthEndpoints {
    @GET(EthUrls.ACCOUNT + "/{address}")
    fun getEthAccount(@Path("address") address: String): Observable<HashMap<String, EthAddressResponse>>

    @POST(EthUrls.PUSH_TX)
    fun pushTx(@Body ethPushTxRequest: EthPushTxRequest): Single<HashMap<String, String>>

    @Headers("Accept: application/json")
    @GET(EthUrls.V2_DATA_TRANSACTION + "/{hash}")
    fun getTransaction(@Path("hash") txHash: String): Observable<EthTransaction>

    @GET("${EthUrls.V2_DATA_ACCOUNT}/{address}/transactions")
    @Headers("Accept: application/json")
    fun getTransactions(
        @Path("address") address: String,
        @Query("size") size: Int = 50
    ): Single<EthTransactionsResponse>
}

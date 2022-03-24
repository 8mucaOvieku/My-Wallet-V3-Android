package com.blockchain.nabu.models

import com.blockchain.nabu.models.responses.nabu.KycTierState
import com.blockchain.nabu.models.responses.nabu.KycTierStateAdapter
import com.squareup.moshi.JsonDataException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should throw`
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test

class KycTierStateAdapterTest {

    private val jsonBuilder = Json {
        ignoreUnknownKeys = true
        explicitNulls = true
    }

    @Test
    fun `kycTierState serializer`() {
        @Serializable
        data class TestClass(
            val state: KycTierState
        )

        KycTierState::class.sealedSubclasses.map { it.objectInstance as KycTierState }.forEach { state ->
            println("Checking serialization for: ${state.javaClass.name}")
            val jsonString = jsonBuilder.encodeToString(TestClass(state))
            val testObject = jsonBuilder.decodeFromString<TestClass>(jsonString)

            testObject shouldBeEqualTo TestClass(state)
        }
    }

    @Test
    fun `from none`() {
        KycTierStateAdapter().fromJson("NONE") `should be equal to` KycTierState.None
    }

    @Test
    fun `from none mixed case`() {
        KycTierStateAdapter().fromJson("None") `should be equal to` KycTierState.None
    }

    @Test
    fun `from rejected`() {
        KycTierStateAdapter().fromJson("REJECTED") `should be equal to` KycTierState.Rejected
    }

    @Test
    fun `from rejected mixed case`() {
        KycTierStateAdapter().fromJson("RejectED") `should be equal to` KycTierState.Rejected
    }

    @Test
    fun `from pending`() {
        KycTierStateAdapter().fromJson("PENDING") `should be equal to` KycTierState.Pending
    }

    @Test
    fun `from pending lower case`() {
        KycTierStateAdapter().fromJson("pending") `should be equal to` KycTierState.Pending
    }

    @Test
    fun `from verified`() {
        KycTierStateAdapter().fromJson("VERIFIED") `should be equal to` KycTierState.Verified
    }

    @Test
    fun `from verified lower case`() {
        KycTierStateAdapter().fromJson("verified") `should be equal to` KycTierState.Verified
    }

    @Test
    fun `from unknown should throw exception`() {
        {
            KycTierStateAdapter().fromJson("malformed")
        } `should throw` JsonDataException::class
    }

    @Test
    fun `to none`() {
        KycTierStateAdapter().toJson(KycTierState.None) `should be equal to` "NONE"
    }

    @Test
    fun `to rejected`() {
        KycTierStateAdapter().toJson(KycTierState.Rejected) `should be equal to` "REJECTED"
    }

    @Test
    fun `to pending`() {
        KycTierStateAdapter().toJson(KycTierState.Pending) `should be equal to` "PENDING"
    }

    @Test
    fun `to verified`() {
        KycTierStateAdapter().toJson(KycTierState.Verified) `should be equal to` "VERIFIED"
    }
}

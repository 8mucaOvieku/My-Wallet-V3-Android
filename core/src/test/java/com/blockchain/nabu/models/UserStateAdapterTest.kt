package com.blockchain.nabu.models

import com.blockchain.nabu.models.responses.nabu.UserState
import com.blockchain.nabu.models.responses.nabu.UserStateAdapter
import com.squareup.moshi.JsonDataException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should throw`
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test

class UserStateAdapterTest {

    private val jsonBuilder = Json {
        ignoreUnknownKeys = true
        explicitNulls = true
    }

    @Test
    fun `userState serializer`() {
        @Serializable
        data class TestClass(
            val state: UserState
        )

        UserState::class.sealedSubclasses.map { it.objectInstance as UserState }.forEach { state ->
            println("Checking serialization for: ${state.javaClass.name}")
            val jsonString = jsonBuilder.encodeToString(TestClass(state))
            val testObject = jsonBuilder.decodeFromString<TestClass>(jsonString)

            testObject shouldBeEqualTo TestClass(state)
        }
    }

    @Test
    fun `from none`() {
        UserStateAdapter().fromJson("NONE") `should be equal to` UserState.None
    }

    @Test
    fun `from created`() {
        UserStateAdapter().fromJson("CREATED") `should be equal to` UserState.Created
    }

    @Test
    fun `from active`() {
        UserStateAdapter().fromJson("ACTIVE") `should be equal to` UserState.Active
    }

    @Test
    fun `from blocked`() {
        UserStateAdapter().fromJson("BLOCKED") `should be equal to` UserState.Blocked
    }

    @Test
    fun `from unknown should throw exception`() {
        {
            UserStateAdapter().fromJson("malformed")
        } `should throw` JsonDataException::class
    }

    @Test
    fun `to none`() {
        UserStateAdapter().toJson(UserState.None) `should be equal to` "NONE"
    }

    @Test
    fun `to created`() {
        UserStateAdapter().toJson(UserState.Created) `should be equal to` "CREATED"
    }

    @Test
    fun `to active`() {
        UserStateAdapter().toJson(UserState.Active) `should be equal to` "ACTIVE"
    }

    @Test
    fun `to blocked`() {
        UserStateAdapter().toJson(UserState.Blocked) `should be equal to` "BLOCKED"
    }
}

package piuk.blockchain.android.ui.kyc.address

import com.blockchain.android.testutils.rxInit
import com.blockchain.nabu.api.getuser.domain.UserService
import com.blockchain.nabu.models.responses.nabu.TierLevels
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.rxjava3.core.Single
import org.amshove.kluent.`should be equal to`
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import piuk.blockchain.android.ui.getBlankNabuUser

class EligibilityForFreeEthAdapterTest {

    private lateinit var eligibilityForFreeEthAdapter: EligibilityForFreeEthAdapter
    private val userService: UserService = mock()

    @get:Rule
    val rx = rxInit {
        mainTrampoline()
    }

    @Before
    fun setUp() {
        eligibilityForFreeEthAdapter = EligibilityForFreeEthAdapter(userService)
    }

    @Test
    fun `should not be eligible, if tier is lower than 2 and no tag contained`() {
        whenever(userService.getUser())
            .thenReturn(
                Single.just(
                    getBlankNabuUser()
                        .copy(
                            tiers = TierLevels(1, 1, 2)
                        )
                        .copy(tags = mapOf())
                )
            )

        val testEligibleObserver = eligibilityForFreeEthAdapter.isEligible().test()

        testEligibleObserver.values().single().apply {
            this `should be equal to` false
        }
    }

    @Test
    fun `should not be eligible, if tier is lower than 2 and tag contained`() {
        whenever(userService.getUser())
            .thenReturn(
                Single.just(
                    getBlankNabuUser()
                        .copy(tiers = TierLevels(1, 1, 2))
                        .copy(tags = mapOf("POWER_PAX" to mapOf("some key" to "some key")))
                )
            )

        val testEligibleObserver = eligibilityForFreeEthAdapter.isEligible().test()

        testEligibleObserver.values().single().apply {
            this `should be equal to` false
        }
    }

    @Test
    fun `should not be eligible, if tier is 2 and tag contained`() {
        whenever(userService.getUser())
            .thenReturn(
                Single.just(
                    getBlankNabuUser()
                        .copy(tiers = TierLevels(2, 2, 2))
                        .copy(tags = mapOf("POWER_PAX" to mapOf("some key" to "some key")))
                )
            )

        val testEligibleObserver = eligibilityForFreeEthAdapter.isEligible().test()

        testEligibleObserver.values().single().apply {
            this `should be equal to` false
        }
    }

    @Test
    fun `should be eligible, if tier is 2 and no tag contained`() {
        whenever(userService.getUser())
            .thenReturn(
                Single.just(
                    getBlankNabuUser()
                        .copy(tiers = TierLevels(2, 2, 2))
                        .copy(tags = mapOf())
                )
            )

        val testEligibleObserver = eligibilityForFreeEthAdapter.isEligible().test()

        testEligibleObserver.values().single().apply {
            this `should be equal to` true
        }
    }
}

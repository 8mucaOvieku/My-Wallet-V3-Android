package info.blockchain.wallet

import com.blockchain.api.services.NonCustodialBitcoinService
import com.nhaarman.mockitokotlin2.mock
import info.blockchain.wallet.BitcoinCashWallet.Companion.create
import info.blockchain.wallet.BitcoinCashWallet.Companion.restore
import info.blockchain.wallet.bch.BchMainNetParams
import info.blockchain.wallet.test_data.TestVectorBip39
import info.blockchain.wallet.test_data.TestVectorBip39List
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import org.bitcoinj.core.NetworkParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BitcoinCashWalletTest {
    private val params: NetworkParameters = BchMainNetParams.get()

    private val bitcoinApi: NonCustodialBitcoinService = mock()

    private fun getTestVectors(): TestVectorBip39List {
        val uri = javaClass.classLoader.getResource("hd/test_EN_BIP39.json").toURI()
        val response = String(Files.readAllBytes(Paths.get(uri)), StandardCharsets.UTF_8)
        return TestVectorBip39List.fromJson(response)
    }

    @Test
    fun getPrivB58() {
        val vector = getTestVectors().vectors!![24]
        val subject = restore(
            bitcoinApi,
            BitcoinCashWallet.BITCOINCASH_COIN_PATH,
            split(vector.mnemonic!!),
            vector.passphrase!!
        )
        subject.addAccount()
        assertNotNull(subject.getAccountPrivB58(0))
    }

    @Test(expected = IndexOutOfBoundsException::class)
    fun getPrivB58_badIndex() {
        val subject = create(
            bitcoinApi,
            params,
            BitcoinCashWallet.BITCOINCASH_COIN_PATH
        )

        subject.getAccountPrivB58(1)
    }

    @Test
    fun testAddressDerivations() {
        val vector: TestVectorBip39 = getTestVectors().vectors!![24]

        val subject = restore(
            bitcoinApi,
            BitcoinCashWallet.BITCOINCASH_COIN_PATH,
            split(vector.mnemonic!!),
            vector.passphrase!!
        )

        // m / purpose' / coin_type' / account' / change / address_index
        // m/44H/0H/0H/0/0
        val coin = vector.getCoinTestVectors(subject.uriScheme, subject.path)

        coin.accountList!!.forEachIndexed { accountIndex, account ->
            subject.addAccount()
            account.addresses!!.forEachIndexed { addressIndex, address ->
                assertEquals(
                    address.receiveLegacy,
                    subject.getReceiveAddressAtArbitraryPosition(accountIndex, addressIndex)
                )
                assertEquals(
                    address.changeLegacy,
                    subject.getChangeAddressAtArbitraryPosition(accountIndex, addressIndex)
                )
                assertEquals(
                    address.receiveCashAddress,
                    subject.getReceiveCashAddressAt(accountIndex, addressIndex)
                )
                assertEquals(
                    address.changeCashAddress,
                    subject.getChangeCashAddressAt(accountIndex, addressIndex)
                )
            }
        }
    }

    companion object {
        private fun split(words: String): List<String> = words.split("\\s+".toRegex())
    }
}

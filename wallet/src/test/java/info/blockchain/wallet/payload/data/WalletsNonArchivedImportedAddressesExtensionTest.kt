package info.blockchain.wallet.payload.data

import org.amshove.kluent.`should be equal to`
import org.junit.Test

class WalletsNonArchivedImportedAddressesExtensionTest {

    private fun importedAddressWithPrivateKey(address: String, privateKey: String = "PRIVATE_KEY") =
        ImportedAddress(
            privateKey = privateKey,
            address = address
        )

    @Test
    fun `empty list`() {
        Wallet().nonArchivedImportedAddressStrings() `should be equal to` emptyList()
    }

    @Test
    fun `one spendable`() {
        Wallet().apply {
            importedAddressList.add(importedAddressWithPrivateKey("Address1"))
        }.nonArchivedImportedAddressStrings() `should be equal to` listOf("Address1")
    }

    @Test
    fun `one archived`() {
        Wallet().apply {
            importedAddressList.add(importedAddressWithPrivateKey("Address1").apply { archive() })
        }.nonArchivedImportedAddressStrings() `should be equal to` emptyList()
    }

    @Test
    fun `one without private key`() {
        Wallet().apply {
            importedAddressList.add(
                ImportedAddress(address = "Address1")
            )
        }.nonArchivedImportedAddressStrings() `should be equal to` listOf("Address1")
    }

    @Test
    fun `two spendable`() {
        Wallet().apply {
            importedAddressList.add(importedAddressWithPrivateKey("Address1", "PRIVATE_KEY1"))
            importedAddressList.add(importedAddressWithPrivateKey("Address2", "PRIVATE_KEY2"))
        }.nonArchivedImportedAddressStrings() `should be equal to` listOf("Address1", "Address2")
    }

    @Test
    fun `repeated address`() {
        Wallet().apply {
            importedAddressList.add(importedAddressWithPrivateKey("Address1", "PRIVATE_KEY1"))
            importedAddressList.add(importedAddressWithPrivateKey("Address1", "PRIVATE_KEY2"))
        }.nonArchivedImportedAddressStrings() `should be equal to` listOf("Address1")
    }
}

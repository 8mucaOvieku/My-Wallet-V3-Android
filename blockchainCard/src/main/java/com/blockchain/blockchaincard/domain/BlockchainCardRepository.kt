package com.blockchain.blockchaincard.domain

import com.blockchain.blockchaincard.domain.models.BlockchainCardError
import com.blockchain.blockchaincard.domain.models.BlockchainDebitCard
import com.blockchain.blockchaincard.domain.models.BlockchainDebitCardProduct
import com.blockchain.outcome.Outcome

interface BlockchainCardRepository {
    suspend fun getProducts(): Outcome<BlockchainCardError, List<BlockchainDebitCardProduct>>

    suspend fun getCards(): Outcome<BlockchainCardError, List<BlockchainDebitCard>>

    suspend fun createCard(productCode: String, ssn: String): Outcome<BlockchainCardError, BlockchainDebitCard>

    suspend fun deleteCard(cardId: String): Outcome<BlockchainCardError, BlockchainDebitCard>
}
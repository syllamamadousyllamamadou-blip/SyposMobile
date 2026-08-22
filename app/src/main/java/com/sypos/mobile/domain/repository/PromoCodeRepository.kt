package com.sypos.mobile.domain.repository

import com.sypos.mobile.data.local.entity.PromoCodeEntity
import kotlinx.coroutines.flow.Flow

interface PromoCodeRepository {
    fun getAllPromoCodes(): Flow<List<PromoCodeEntity>>
    suspend fun getActivePromoByCode(code: String): PromoCodeEntity?
    suspend fun insertPromoCode(promo: PromoCodeEntity)
    suspend fun updatePromoCode(promo: PromoCodeEntity)
    suspend fun deletePromoCode(promo: PromoCodeEntity)
    suspend fun incrementUsage(promoId: String)
}

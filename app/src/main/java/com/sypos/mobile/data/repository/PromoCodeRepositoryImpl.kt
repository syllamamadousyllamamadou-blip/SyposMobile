package com.sypos.mobile.data.repository

import com.sypos.mobile.data.local.dao.PromoCodeDao
import com.sypos.mobile.data.local.entity.PromoCodeEntity
import com.sypos.mobile.domain.repository.PromoCodeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PromoCodeRepositoryImpl @Inject constructor(
    private val promoCodeDao: PromoCodeDao
) : PromoCodeRepository {

    override fun getAllPromoCodes(): Flow<List<PromoCodeEntity>> {
        return promoCodeDao.getAllPromoCodes()
    }

    override suspend fun getActivePromoByCode(code: String): PromoCodeEntity? {
        val promo = promoCodeDao.getActivePromoByCode(code.trim().uppercase())
        return if (promo != null && promo.currentUsage < promo.maxUsage) promo else null
    }

    override suspend fun insertPromoCode(promo: PromoCodeEntity) {
        promoCodeDao.insertPromoCode(promo.copy(code = promo.code.trim().uppercase()))
    }

    override suspend fun updatePromoCode(promo: PromoCodeEntity) {
        promoCodeDao.updatePromoCode(promo.copy(code = promo.code.trim().uppercase()))
    }

    override suspend fun deletePromoCode(promo: PromoCodeEntity) {
        promoCodeDao.deletePromoCode(promo)
    }

    override suspend fun incrementUsage(promoId: String) {
        val all = promoCodeDao.getAllPromoCodes().first()
        val promo = all.find { it.id == promoId }
        if (promo != null) {
            val newUsage = promo.currentUsage + 1
            val updated = promo.copy(
                currentUsage = newUsage,
                isActive = if (newUsage >= promo.maxUsage) false else promo.isActive
            )
            promoCodeDao.updatePromoCode(updated)
        }
    }
}

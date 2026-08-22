package com.sypos.mobile.data.local.dao

import androidx.room.*
import com.sypos.mobile.data.local.entity.PromoCodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PromoCodeDao {
    @Query("SELECT * FROM promo_codes ORDER BY createdAt DESC")
    fun getAllPromoCodes(): Flow<List<PromoCodeEntity>>

    @Query("SELECT * FROM promo_codes WHERE code = :code AND isActive = 1 LIMIT 1")
    suspend fun getActivePromoByCode(code: String): PromoCodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPromoCode(promo: PromoCodeEntity)

    @Update
    suspend fun updatePromoCode(promo: PromoCodeEntity)

    @Delete
    suspend fun deletePromoCode(promo: PromoCodeEntity)
}

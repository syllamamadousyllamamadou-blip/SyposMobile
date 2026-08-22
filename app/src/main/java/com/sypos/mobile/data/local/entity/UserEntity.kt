package com.sypos.mobile.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val username: String,
    val pinCode: String,
    val role: UserRole
)

enum class UserRole {
    ADMIN, CASHIER
}

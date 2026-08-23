package com.keluargakendali.data

/**
 * Model data yang mengikuti persis field JSON yang dikembalikan backend (server.js).
 * Jangan menambah/menghapus field tanpa mengecek ulang backend terlebih dahulu.
 */

data class UserDto(
    val id: String,
    val role: String, // "parent" atau "child"
    val name: String,
    val familyId: String
)

data class FamilyDto(
    val id: String,
    val name: String,
    // Hanya terisi kalau yang meminta adalah orang tua; backend mengirim undefined untuk anak.
    val code: String? = null
)

data class TaskDto(
    val id: String,
    val familyId: String,
    val childId: String,
    val title: String,
    val description: String,
    val rewardMinutes: Int,
    val status: String, // "assigned" | "submitted" | "approved" | "rejected"
    val createdAt: String,
    val evidence: String? = null,
    val submittedAt: String? = null,
    val decisionNote: String? = null,
    val decidedAt: String? = null
)

data class AuthResult(
    val token: String,
    val user: UserDto,
    val family: FamilyDto? = null // hanya ada pada respons register-parent
)

data class AddChildResult(
    val child: UserDto,
    val familyCode: String
)

data class FamilyResult(
    val family: FamilyDto,
    val children: List<UserDto>
)

data class BalanceResult(
    val minutes: Int,
    val approvedTaskCount: Int
)

fun statusLabel(status: String): String = when (status) {
    "assigned" -> "Belum dikirim"
    "submitted" -> "Menunggu persetujuan"
    "approved" -> "Disetujui"
    "rejected" -> "Ditolak"
    else -> status
}

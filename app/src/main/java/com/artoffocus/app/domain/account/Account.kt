package com.artoffocus.app.domain.account

enum class AccountStatus { UNCONFIGURED, SIGNED_OUT, LINK_SENT, SIGNED_IN }

/** Deliberately permissive — the magic link itself is the real validator, this only rejects obvious garbage. */
fun isPlausibleEmail(raw: String): Boolean {
    if (raw.length > 254) return false
    if (raw.startsWith(".") || raw.endsWith(".")) return false
    val atCount = raw.count { it == '@' }
    if (atCount != 1) return false
    val domain = raw.substringAfter('@')
    return domain.contains('.')
}

enum class SyncStatus { IDLE, SYNCING, SYNCED, FAILED }

/** Drives the "what would be lost" clear-cache warning — a concrete check, not a vague confirm dialog. */
fun hasUnsyncedWork(paintingCount: Int, lastSyncedAt: Long?, syncStatus: SyncStatus): Boolean {
    if (paintingCount == 0) return false
    return lastSyncedAt == null || syncStatus == SyncStatus.FAILED
}

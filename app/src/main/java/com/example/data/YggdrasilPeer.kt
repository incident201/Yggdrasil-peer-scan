package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Locale

@Entity(tableName = "yggdrasil_peers")
data class YggdrasilPeer(
    @PrimaryKey val uri: String,
    val countryCode: String,     // Folder name, e.g., "russia", "continental-us"
    val countryName: String,     // Prettified name, e.g., "Russia", "Continental US"
    val protocol: String,        // tcp, tls, quic, ws, etc.
    val host: String,
    val port: Int,
    val queryOptions: String?,
    val latency: Long = -1L,     // -1 means untested or offline
    val isOnline: Boolean = false,
    val lastTestedTime: Long = 0L,
    val isFavorite: Boolean = false,
    val userNotes: String? = null
) {
    // Helper to check if a peer is tested
    val isTested: Boolean
        get() = lastTestedTime > 0L

    // Helper to get a cleanly displayed title/address
    val displayAddress: String
        get() = "$protocol://$host:$port"

    companion object {
        // Prettify directory names into human readable country/region labels
        fun prettifyCountry(folderName: String): String {
            return folderName.split("-", "_")
                .joinToString(" ") { part ->
                    part.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                }
        }
    }
}

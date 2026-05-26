package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PeerDao {
    @Query("SELECT * FROM yggdrasil_peers")
    fun getAllPeers(): Flow<List<YggdrasilPeer>>

    @Query("SELECT * FROM yggdrasil_peers WHERE isFavorite = 1")
    fun getFavoritePeers(): Flow<List<YggdrasilPeer>>

    @Query("SELECT DISTINCT countryCode, countryName FROM yggdrasil_peers ORDER BY countryName ASC")
    fun getAvailableCountries(): Flow<List<CountryInfo>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPeers(peers: List<YggdrasilPeer>): List<Long>

    @Update
    suspend fun updatePeer(peer: YggdrasilPeer)

    @Query("UPDATE yggdrasil_peers SET latency = :latency, isOnline = :isOnline, lastTestedTime = :lastTestedTime WHERE uri = :uri")
    suspend fun updateLatency(uri: String, latency: Long, isOnline: Boolean, lastTestedTime: Long)

    @Query("UPDATE yggdrasil_peers SET isFavorite = :isFavorite WHERE uri = :uri")
    suspend fun updateFavorite(uri: String, isFavorite: Boolean)

    @Query("UPDATE yggdrasil_peers SET userNotes = :notes WHERE uri = :uri")
    suspend fun updateNotes(uri: String, notes: String?)

    @Query("DELETE FROM yggdrasil_peers WHERE isFavorite = 0")
    suspend fun deleteNonFavorites()

    @Query("DELETE FROM yggdrasil_peers")
    suspend fun deleteAllPeers()
}

data class CountryInfo(
    val countryCode: String,
    val countryName: String
)

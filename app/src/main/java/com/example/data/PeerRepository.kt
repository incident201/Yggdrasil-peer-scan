package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

class PeerRepository(private val peerDao: PeerDao) {
    private val TAG = "PeerRepository"

    val allPeers: Flow<List<YggdrasilPeer>> = peerDao.getAllPeers()
    val favoritePeers: Flow<List<YggdrasilPeer>> = peerDao.getFavoritePeers()
    val availableCountries: Flow<List<CountryInfo>> = peerDao.getAvailableCountries()

    /**
     * Downloads/refreshes the complete public peer directory from the GitHub repository.
     */
    suspend fun refreshPeersFromGitHub(onProgress: (current: Int, total: Int) -> Unit) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting refresh of public Yggdrasil peers")
            val paths = PeersFetcher.fetchPeerPaths()
            val total = paths.size
            if (total == 0) return@withContext

            paths.forEachIndexed { index, path ->
                val peers = PeersFetcher.fetchPeersFromPath(path)
                if (peers.isNotEmpty()) {
                    peerDao.insertPeers(peers)
                }
                // Update progress callback on main/caller context
                onProgress(index + 1, total)
            }
            Log.d(TAG, "Successfully updated public Yggdrasil peers. Total categories compiled: $total")
        } catch (e: Exception) {
            Log.e(TAG, "Error in peer list refresh task", e)
            throw e
        }
    }

    /**
     * Tests connectivity to a single peer and updates its database model with the result.
     */
    suspend fun testSinglePeer(peer: YggdrasilPeer): YggdrasilPeer = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var socket: Socket? = null
        var isOnline = false
        var latency = -1L

        try {
            socket = Socket()
            // 2.5 seconds connection timeout
            val socketAddress = InetSocketAddress(peer.host, peer.port)
            socket.connect(socketAddress, 2500)
            
            // Connection succeeded
            val endTime = System.currentTimeMillis()
            latency = endTime - startTime
            isOnline = true
        } catch (e: Exception) {
            // Unreachable or connection refused
            Log.w(TAG, "Peer unreachable: ${peer.uri} - ${e.localizedMessage}")
        } finally {
            try {
                socket?.close()
            } catch (ex: Exception) {
                // Ignore closing error
            }
        }

        val updated = peer.copy(
            latency = latency,
            isOnline = isOnline,
            lastTestedTime = System.currentTimeMillis()
        )
        
        peerDao.updateLatency(
            uri = updated.uri,
            latency = updated.latency,
            isOnline = updated.isOnline,
            lastTestedTime = updated.lastTestedTime
        )

        return@withContext updated
    }

    /**
     * Tests a list of peers in parallel using a controlled coroutine thread pool,
     * reporting updates as each batch completes.
     */
    suspend fun testMultiplePeersInParallel(
        peers: List<YggdrasilPeer>,
        progressCallback: (index: Int, total: Int, updatedPeer: YggdrasilPeer) -> Unit
    ) = withContext(Dispatchers.IO) {
        val total = peers.size
        if (total == 0) return@withContext

        // Run with concurrency limit of (e.g., 10) to prevent network socket congestion or system thrashing
        val limit = 10
        val tempExecutor = Executors.newFixedThreadPool(limit)
        val testingDispatcher = tempExecutor.asCoroutineDispatcher()

        try {
            var completedCount = 0
            
            // Chunk peers to test them concurrently
            peers.chunked(limit).forEach { chunk ->
                val deferreds = chunk.map { peer ->
                    async(testingDispatcher) {
                        val updated = testSinglePeer(peer)
                        synchronized(this) {
                            completedCount++
                            progressCallback(completedCount, total, updated)
                        }
                    }
                }
                deferreds.awaitAll()
            }
        } finally {
            tempExecutor.shutdown()
        }
    }

    suspend fun toggleFavorite(uri: String, isFavorite: Boolean) {
        peerDao.updateFavorite(uri, isFavorite)
    }

    suspend fun updateNotes(uri: String, notes: String?) {
        peerDao.updateNotes(uri, notes)
    }

    suspend fun deleteNonFavorites() {
        peerDao.deleteNonFavorites()
    }

    suspend fun clearAll() {
        peerDao.deleteAllPeers()
    }
}

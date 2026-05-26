package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SortOrder {
    LATENCY_ASC,
    FAVORITES_FIRST,
    COUNTRY_ASC,
    PROTOCOL_ASC
}

enum class ProtocolFilter {
    ALL, TCP, TLS, WEBSOCKET, OTHER
}

data class UiState(
    val peersList: List<YggdrasilPeer> = emptyList(),
    val favoritesList: List<YggdrasilPeer> = emptyList(),
    val countries: List<CountryInfo> = emptyList(),
    val isRefreshing: Boolean = false,
    val refreshProgress: Float = 0f,
    val refreshText: String = "",
    val isScanning: Boolean = false,
    val scanProgress: Float = 0f,
    val scanText: String = "",
    val searchQuery: String = "",
    val selectedCountryCode: String? = null,
    val protocolFilter: ProtocolFilter = ProtocolFilter.ALL,
    val sortOrder: SortOrder = SortOrder.LATENCY_ASC,
    val showFavoritesOnly: Boolean = false
)

class PeersViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: PeerRepository
    private var scanJob: Job? = null
    private var fetchJob: Job? = null

    // Filter and Sort inputs
    private val _searchQuery = MutableStateFlow("")
    private val _selectedCountryCode = MutableStateFlow<String?>(value = null)
    private val _protocolFilter = MutableStateFlow(ProtocolFilter.ALL)
    private val _sortOrder = MutableStateFlow(SortOrder.LATENCY_ASC)
    private val _showFavoritesOnly = MutableStateFlow(false)

    // Task statuses
    private val _isRefreshing = MutableStateFlow(false)
    private val _refreshProgress = MutableStateFlow(0f)
    private val _refreshText = MutableStateFlow("")
    private val _isScanning = MutableStateFlow(false)
    private val _scanProgress = MutableStateFlow(0f)
    private val _scanText = MutableStateFlow("")

    val uiState: StateFlow<UiState>

    init {
        val database = AppDatabase.getDatabase(application)
        repository = PeerRepository(database.peerDao())

        // Combine flow queries reactively!
        uiState = combine(
            repository.allPeers,
            repository.favoritePeers,
            repository.availableCountries,
            _searchQuery,
            _selectedCountryCode,
            _protocolFilter,
            _sortOrder,
            _showFavoritesOnly,
            _isRefreshing,
            _refreshProgress,
            _refreshText,
            _isScanning,
            _scanProgress,
            _scanText
        ) { combined ->
            @Suppress("UNCHECKED_CAST")
            val allPeers = combined[0] as List<YggdrasilPeer>
            @Suppress("UNCHECKED_CAST")
            val favoritePeers = combined[1] as List<YggdrasilPeer>
            @Suppress("UNCHECKED_CAST")
            val countriesList = combined[2] as List<CountryInfo>
            val search = combined[3] as String
            val countryCode = combined[4] as String?
            val proto = combined[5] as ProtocolFilter
            val order = combined[6] as SortOrder
            val favsOnly = combined[7] as Boolean
            
            val isRef = combined[8] as Boolean
            val refProg = combined[9] as Float
            val refText = combined[10] as String
            val isScan = combined[11] as Boolean
            val scanProg = combined[12] as Float
            val scanText = combined[13] as String

            // Apply search & filters
            var filtered = if (favsOnly) favoritePeers else allPeers

            if (search.isNotBlank()) {
                filtered = filtered.filter { peer ->
                    peer.uri.contains(search, ignoreCase = true) ||
                    peer.host.contains(search, ignoreCase = true) ||
                    peer.countryName.contains(search, ignoreCase = true)
                }
            }

            if (countryCode != null) {
                filtered = filtered.filter { it.countryCode == countryCode }
            }

            filtered = when (proto) {
                ProtocolFilter.ALL -> filtered
                ProtocolFilter.TCP -> filtered.filter { it.protocol == "tcp" }
                ProtocolFilter.TLS -> filtered.filter { it.protocol == "tls" }
                ProtocolFilter.WEBSOCKET -> filtered.filter { it.protocol == "ws" || it.protocol == "wss" }
                ProtocolFilter.OTHER -> filtered.filter { it.protocol != "tcp" && it.protocol != "tls" && it.protocol != "ws" && it.protocol != "wss" }
            }

            // Apply customized sort orders
            val sorted = when (order) {
                SortOrder.LATENCY_ASC -> {
                    // Sort tested online peers first, ascending by latency; then untested or offline (-1) at the end
                    filtered.sortedWith(
                        compareBy<YggdrasilPeer> { !it.isOnline }
                            .thenBy { if (it.latency == -1L) Long.MAX_VALUE else it.latency }
                            .thenBy { it.countryName }
                    )
                }
                SortOrder.FAVORITES_FIRST -> {
                    filtered.sortedWith(
                        compareByDescending<YggdrasilPeer> { it.isFavorite }
                            .thenBy { !it.isOnline }
                            .thenBy { if (it.latency == -1L) Long.MAX_VALUE else it.latency }
                    )
                }
                SortOrder.COUNTRY_ASC -> {
                    filtered.sortedWith(
                        compareBy<YggdrasilPeer> { it.countryName }
                            .thenBy { !it.isOnline }
                            .thenBy { if (it.latency == -1L) Long.MAX_VALUE else it.latency }
                    )
                }
                SortOrder.PROTOCOL_ASC -> {
                    filtered.sortedWith(
                        compareBy<YggdrasilPeer> { it.protocol }
                            .thenBy { !it.isOnline }
                            .thenBy { if (it.latency == -1L) Long.MAX_VALUE else it.latency }
                    )
                }
            }

            UiState(
                peersList = sorted,
                favoritesList = favoritePeers,
                countries = countriesList,
                isRefreshing = isRef,
                refreshProgress = refProg,
                refreshText = refText,
                isScanning = isScan,
                scanProgress = scanProg,
                scanText = scanText,
                searchQuery = search,
                selectedCountryCode = countryCode,
                protocolFilter = proto,
                sortOrder = order,
                showFavoritesOnly = favsOnly
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UiState()
        )
    }

    /**
     * Starts downloading new peer paths and file descriptions recursively from GitHub.
     */
    fun refreshPeers() {
        if (_isRefreshing.value || _isScanning.value) return
        
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _isRefreshing.value = true
            _refreshProgress.value = 0f
            _refreshText.value = "Fetching repository tree..."
            try {
                repository.refreshPeersFromGitHub { current, total ->
                    _refreshProgress.value = current.toFloat() / total
                    _refreshText.value = "Reading data lists: $current/$total"
                }
            } catch (e: Exception) {
                _refreshText.value = "Sync failed: ${e.localizedMessage}"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Scans and tests latency in parallel for all current filtered peers.
     */
    fun scanFilteredPeers() {
        if (_isScanning.value || _isRefreshing.value) return

        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _isScanning.value = true
            _scanProgress.value = 0f
            _scanText.value = "Initializing scan..."

            val targets = uiState.value.peersList
            val total = targets.size

            if (total == 0) {
                _isScanning.value = false
                _scanText.value = "No peers match filter criteria"
                return@launch
            }

            _scanText.value = "Scanning 0/$total peers..."
            try {
                repository.testMultiplePeersInParallel(targets) { current, count, peer ->
                    _scanProgress.value = current.toFloat() / count
                    _scanText.value = "Checking socket: $current/$count | ${peer.host}"
                }
            } catch (e: Exception) {
                _scanText.value = "Scan encountered error: ${e.localizedMessage}"
            } finally {
                _isScanning.value = false
            }
        }
    }

    /**
     * Cancels any active connection scanner or downloader.
     */
    fun cancelOperations() {
        if (_isScanning.value) {
            scanJob?.cancel()
            _isScanning.value = false
            _scanText.value = "Scan cancelled"
        }
        if (_isRefreshing.value) {
            fetchJob?.cancel()
            _isRefreshing.value = false
            _refreshText.value = "Download cancelled"
        }
    }

    fun toggleFavorite(peer: YggdrasilPeer) {
        viewModelScope.launch {
            repository.toggleFavorite(peer.uri, !peer.isFavorite)
        }
    }

    fun updateNotes(peer: YggdrasilPeer, notes: String?) {
        viewModelScope.launch {
            repository.updateNotes(peer.uri, notes)
        }
    }

    fun clearNonFavorites() {
        viewModelScope.launch {
            repository.deleteNonFavorites()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCountry(countryCode: String?) {
        _selectedCountryCode.value = countryCode
    }

    fun setProtocolFilter(filter: ProtocolFilter) {
        _protocolFilter.value = filter
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun setShowFavoritesOnly(favsOnly: Boolean) {
        _showFavoritesOnly.value = favsOnly
    }
}

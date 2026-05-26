package com.example.data

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object PeersFetcher {
    private const val TAG = "PeersFetcher"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Key fallback folders in public-peers repo if GitHub API rate limits recursive trees
    private val FALLBACK_CATEGORIES = listOf(
        "additional",
        "continental-us",
        "czech-republic",
        "finland",
        "france",
        "germany",
        "italy",
        "luxembourg",
        "netherlands",
        "moldova",
        "russia",
        "sweden",
        "ukraine",
        "united-kingdom"
    )

    /**
     * Fetches paths to all country/region Markdown files in yggdrasil-network/public-peers.
     */
    fun fetchPeerPaths(): List<String> {
        val paths = mutableListOf<String>()
        val url = "https://api.github.com/repos/yggdrasil-network/public-peers/git/trees/master?recursive=1"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()
                    if (!bodyString.isNullOrBlank()) {
                        val json = JSONObject(bodyString)
                        val treeArray = json.optJSONArray("tree")
                        if (treeArray != null) {
                            for (i in 0 until treeArray.length()) {
                                val item = treeArray.getJSONObject(i)
                                val path = item.optString("path", "")
                                val type = item.optString("type", "")
                                // We are looking for markdown files containing peers (usually README.md files inside directories)
                                if (type == "blob" && path.endsWith(".md", ignoreCase = true)) {
                                    paths.add(path)
                                }
                            }
                        }
                    }
                } else {
                    Log.w(TAG, "Failed to fetch Git tree recursively: Code ${response.code}. Using fallback paths.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching recursive Git tree from GitHub API", e)
        }

        // If Github APIs fail or return empty, load fallbacks to keep the app functional
        if (paths.isEmpty()) {
            for (category in FALLBACK_CATEGORIES) {
                paths.add("$category/README.md")
            }
        }
        return paths
    }

    /**
     * Downloads markdown from a specific path and parses any Yggdrasil peer addresses found.
     */
    fun fetchPeersFromPath(path: String): List<YggdrasilPeer> {
        val peers = mutableListOf<YggdrasilPeer>()
        val cleanPath = path.trim().replace("\\", "/")
        val folders = cleanPath.split("/")
        
        // If the path is not a folder/sub-file, we treat "global" as folder
        val folderName = if (folders.size > 1) folders[folders.size - 2] else "global"
        val countryName = YggdrasilPeer.prettifyCountry(folderName)

        val url = "https://raw.githubusercontent.com/yggdrasil-network/public-peers/master/$cleanPath"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    peers.addAll(parsePeersFromMarkdown(body, folderName, countryName))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading peers from raw path: $cleanPath", e)
        }
        return peers
    }

    /**
     * Extracts peer URIs from raw Markdown content.
     */
    fun parsePeersFromMarkdown(content: String, folderName: String, countryName: String): List<YggdrasilPeer> {
        val peers = mutableListOf<YggdrasilPeer>()
        
        // Split content by standard token boundaries: spaces, newlines, quotes, code symbols, brackets, backticks
        val tokens = content.split(Regex("[\\s\"'*`<>(){},\\[\\]|\\\\]+"))
        
        for (token in tokens) {
            val trimmedToken = cleanToken(token)
            if (trimmedToken.startsWith("tcp://") || trimmedToken.startsWith("tls://") ||
                trimmedToken.startsWith("quic://") || trimmedToken.startsWith("ws://") ||
                trimmedToken.startsWith("wss://") || trimmedToken.startsWith("socks://")
            ) {
                val parsed = parseUri(trimmedToken, folderName, countryName)
                if (parsed != null) {
                    peers.add(parsed)
                }
            }
        }
        
        // Dedup by lowercased URI
        return peers.distinctBy { it.uri.lowercase() }
    }

    private fun cleanToken(token: String): String {
        var clean = token.trim()
        // Strip trailing punctuation like dot, comma, semicolons, brackets, or markdown accents
        while (clean.isNotEmpty() && (clean.endsWith(".") || clean.endsWith(",") || clean.endsWith(";") || clean.endsWith(")") || clean.endsWith("?"))) {
            clean = clean.substring(0, clean.length - 1)
        }
        return clean
    }

    private fun parseUri(uri: String, folderName: String, countryName: String): YggdrasilPeer? {
        try {
            val schemeEnd = uri.indexOf("://")
            if (schemeEnd == -1) return null
            val scheme = uri.substring(0, schemeEnd).lowercase()
            var body = uri.substring(schemeEnd + 3)

            // Split parameters
            val qIndex = body.indexOf('?')
            val queryOptions = if (qIndex != -1) body.substring(qIndex + 1) else null
            if (qIndex != -1) {
                body = body.substring(0, qIndex)
            }

            val host: String
            val port: Int
            if (body.startsWith("[")) {
                val closingBracket = body.indexOf("]")
                if (closingBracket == -1) return null
                host = body.substring(1, closingBracket)
                val colonAfterBracket = body.indexOf(":", closingBracket)
                if (colonAfterBracket == -1) return null
                port = body.substring(colonAfterBracket + 1).toIntOrNull() ?: return null
            } else {
                val lastColon = body.lastIndexOf(":")
                if (lastColon == -1) return null
                host = body.substring(0, lastColon)
                port = body.substring(lastColon + 1).toIntOrNull() ?: return null
            }

            return YggdrasilPeer(
                uri = uri,
                countryCode = folderName,
                countryName = countryName,
                protocol = scheme,
                host = host,
                port = port,
                queryOptions = queryOptions
            )
        } catch (e: Exception) {
            return null
        }
    }
}

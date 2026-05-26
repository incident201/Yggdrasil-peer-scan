# Yggdrasil Peers — Public Peer Finder & Latency Tester

A modern, fluid, and powerful Android utility application designed to find, check, and test public peers within the **Yggdrasil Network**. Built entirely in Kotlin with Jetpack Compose, implementing Material Design 3 guidelines, and featuring robust local persistence powered by SQLite and Room.

---

## 🚀 Key Features

*   **Dynamic GitHub Integration**: Fetches real-time public Yggdrasil peer configurations directly from the official repository `yggdrasil-network/public-peers`.
*   **Offline Fallback Mode**: Contains built-in fallback peer configurations so the app remains fully functional even when offline or during GitHub API rate-limiting events.
*   **Multi-Protocol Parser**: Automatically extracts and standardizes peer connections from markdown, supporting multiple protocols:
    *   `tcp` (Transmission Control Protocol)
    *   `tls` (Transport Layer Security)
    *   `quic` (Quick UDP Internet Connections)
    *   `ws` & `wss` (WebSockets)
    *   `socks` (Proxy routing)
*   **Real-time Latency & Status Evaluator**: Checks server availability and measures actual connection round-trip latency to each node.
*   **Room Database Cache**: Persists crawled nodes locally to enable instant launches, offline searches, and persistent favorite states.
*   **Favorites & Annotations**: Allows bookmarking important or closest peers and adding custom user notes per node.
*   **Polished Material Design 3 Dashboard**:
    *   Interactive filter chips for switching between country clusters or regions.
    *   Integrated searching, sorting (by latency, country, protocol, or favorites).
    *   Glowing dark-theme UI featuring precise geometric details, custom ripple effects, and responsive layout ratios.
*   **Adaptive App Launcher Icon**: Modern high-contrast vector design presenting a neon-green server-node geometric mesh with dynamic background layering.

---

## 🛠️ Built with Modern Android Stack

*   **Language**: [Kotlin](https://kotlinlang.org/) — 100% type-safe and expressive.
*   **UI Framework**: [Jetpack Compose](https://developer.android.com/compose) — Modern declarative UI.
*   **Local Database**: [Room Persistence Library](https://developer.android.com/training/data-storage/room) — Highly optimized SQLite layer utilizing Kotlin Symbol Processing (KSP).
*   **Asynchronous Engine**: [Kotlin Coroutines & Flow](https://kotlinlang.org/docs/coroutines-overview.html) — Structured concurrency for non-blocking latency checks and DB streaming.
*   **Network Client**: [OkHttp3](https://square.github.io/okhttp/) — Lightweight HTTP request engine for raw peer parsing.
*   **Unit & Local Testing**: [Robolectric](https://robolectric.org/) — Fast JVM-based integration checks verifying the resource catalog.
*   **CI/CD Automation**: [GitHub Actions](https://github.com/features/actions) — Built-in automated integration environment.

---

## 📂 Project Structure

```text
├── .github/workflows/          # CI/CD Workflows
│   └── android.yml             # Automates builds, test execution, decoding debug.keystore, and archiving APK
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/
│   │   │   │   ├── MainActivity.kt      # Main hosting activity and Material 3 Screens
│   │   │   │   ├── data/
│   │   │   │   │   ├── AppDatabase.kt   # Room Database class definition
│   │   │   │   │   ├── PeerDao.kt       # Local transactions, queries, CRUD methods
│   │   │   │   │   ├── PeerRepository.kt# Repository pattern separating DB & Network sources
│   │   │   │   │   ├── PeersFetcher.kt  # OkHttp crawl engine for markdown parsing
│   │   │   │   │   └── YggdrasilPeer.kt # Principal database model entity
│   │   │   │   └── ui/                  # Composables, themes, and Material 3 color states
│   │   │   └── res/
│   │   │       ├── drawable/            # Vector drawables (Adaptive launcher foreground/background)
│   │   │       └── values/strings.xml   # User-facing string catalogs
│   │   └── test/                        # JVM Unit, Robolectric, and integration tests
├── build.gradle.kts            # Project-level Gradle orchestration
└── metadata.json               # AI Studio project representation descriptor
```

---

## 🤖 CI/CD Pipeline (GitHub Actions)

The repository provides a pre-configured, production-grade GitHub Actions CD/CI pipeline located in `.github/workflows/android.yml`.

### Automated Pipeline Pipeline Steps:
1.  **Checkout & Cache Initialization**: Code clone using `actions/checkout@v4` and caching dependencies via Zulu JDK 17 setup.
2.  **Debug Keystore Decoding**: Safely extracts the required development build key from `debug.keystore.base64` before triggering the build process.
3.  **Local Unit Tests**: Runs all local unit tests (specifically Room, repository bindings, and parser validations under Robolectric sandbox configuration.
4.  **Debug Build Assembly**: Builds the optimized development application bundle `app-debug.apk`.
5.  **Artifact Archiving**: Saves the output APK directly as a compiled platform artifact with a customized retention period (7 days).

---

## 🏗️ Development & Local Environment Compilation

To build and compile the Yggdrasil Peers app locally, clone the repository and run the following commands through your terminal:

### Run Local Unit & Robolectric Tests
```bash
gradle :app:testDebugUnitTest
```

### Build Debug APK Bundle
```bash
gradle :app:assembleDebug
```
Once the compilation successfully finishes, find your built package at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 🔒 Security & Privacy Policy

*   **No Camera / Storage Access Needed**: The application operates without requesting high-risk system permissions.
*   **100% Client-Side Evaluation**: All socket reachability tests, latency lookups, and database persistence are handled exclusively on the user's terminal.
*   **Strictly Offline SQLite Database**: Bookmarks, favorites, and notes are cached 100% locally inside the sandboxed Room Database, never leaving the local device.

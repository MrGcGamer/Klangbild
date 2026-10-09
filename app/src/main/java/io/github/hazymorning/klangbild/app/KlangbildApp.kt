package io.github.hazymorning.klangbild.app

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import io.github.hazymorning.klangbild.config.SettingsStore
import io.github.hazymorning.klangbild.data.ha.LocalNetwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual dependency graph – the app is small enough not to need a DI framework. */
class AppGraph(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        // WebSocket keep-alive is handled by HA pings; OkHttp answers server pings itself.
        .addInterceptor(LocalNetwork.CleartextGuard())
        // …and every redirect hop, too: a picture link must not lead to plain HTTP outside the home.
        .addNetworkInterceptor(LocalNetwork.CleartextGuard())
        .build()

    val settings = SettingsStore(context.applicationContext)

    val controller = NowPlayingController(scope, http, settings)
}

class KlangbildApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        // When the network comes back (Wi-Fi hiccup), reconnect right away instead of
        // waiting for the backoff timer.
        getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    graph.controller.networkAvailable()
                }
            },
        )
    }
}

val Context.graph: AppGraph get() = (applicationContext as KlangbildApp).graph

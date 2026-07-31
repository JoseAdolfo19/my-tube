package com.miappvideos.api.innertube

import okhttp3.OkHttpClient
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Pool de proxies para rotacion. VACIO a proposito: no uses proxies
 * publicos gratuitos (inseguros y bloqueados por YouTube). Si no tienes
 * proxies propios (VPS con Squid, etc.), dejalo vacio: se usa conexion
 * directa, que sigue funcionando.
 */
object ProxyPool {
    val proxies: List<Proxy> = emptyList()
}

object RotatingHttpClient {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .proxySelector(object : java.net.ProxySelector() {
            override fun select(uri: java.net.URI?): List<Proxy> =
                if (ProxyPool.proxies.isEmpty()) listOf(Proxy.NO_PROXY)
                else listOf(ProxyPool.proxies.random())

            override fun connectFailed(
                uri: java.net.URI?,
                sa: java.net.SocketAddress?,
                ioe: java.io.IOException?
            ) {
            }
        })
        .build()

    fun client(): OkHttpClient = client
}

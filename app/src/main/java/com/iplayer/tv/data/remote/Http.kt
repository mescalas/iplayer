package com.iplayer.tv.data.remote

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

const val DEFAULT_USER_AGENT = "VLC/3.0.21 LibVLC/3.0.21"

object Http {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .dispatcher(Dispatcher().apply { maxRequestsPerHost = 8 })
            .build()
    }

    fun get(url: String, userAgent: String?): Response {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent?.takeIf { it.isNotBlank() } ?: DEFAULT_USER_AGENT)
            .header("Accept", "*/*")
            .build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) {
            val code = resp.code
            resp.close()
            throw IOException(
                when (code) {
                    401, 403 -> "Accès refusé par le serveur ($code). Vérifiez vos identifiants."
                    404 -> "Adresse introuvable (404)."
                    else -> "Erreur du serveur ($code)."
                }
            )
        }
        return resp
    }
}

package com.aniyomi.cross.network

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.util.concurrent.TimeUnit

object NetworkClient {

    // DNS personalizado con resolución directa contra 8.8.8.8 y 1.1.1.1
    // Esto garantiza que la app funcione incluso si el ISP o el router bloquean o fallan con dominios como raw.githubusercontent.com
    private val fallbackDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return try {
                val systemAddresses = Dns.SYSTEM.lookup(hostname)
                if (systemAddresses.isNotEmpty()) systemAddresses else resolvePublicDns(hostname)
            } catch (e: Exception) {
                resolvePublicDns(hostname)
            }
        }

        private fun resolvePublicDns(hostname: String): List<InetAddress> {
            return try {
                // Mapeo conocido y resolución directa para repositorios críticos
                when (hostname) {
                    "raw.githubusercontent.com" -> listOf(
                        InetAddress.getByName("185.199.108.133"),
                        InetAddress.getByName("185.199.109.133"),
                        InetAddress.getByName("185.199.110.133"),
                        InetAddress.getByName("185.199.111.133")
                    )
                    "github.com" -> listOf(
                        InetAddress.getByName("140.82.121.4")
                    )
                    else -> Dns.SYSTEM.lookup(hostname)
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .dns(fallbackDns)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36 AniyomiCross/1.0")
                .header("Accept", "*/*")
                .build()
            chain.proceed(request)
        }
        .build()

    fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> requestBuilder.header(k, v) }
        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Error HTTP ${response.code} en $url")
            return response.body?.string() ?: ""
        }
    }

    fun postJson(url: String, jsonBody: String, headers: Map<String, String> = emptyMap()): String {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonBody.toRequestBody(mediaType)
        val requestBuilder = Request.Builder().url(url).post(body)
        headers.forEach { (k, v) -> requestBuilder.header(k, v) }
        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Error HTTP ${response.code} en $url")
            return response.body?.string() ?: ""
        }
    }

    fun downloadToFile(url: String, destinationFile: File, onProgress: (Float) -> Unit = {}): Boolean {
        destinationFile.parentFile?.mkdirs()
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return false
            val body = response.body ?: return false
            val contentLength = body.contentLength()

            body.byteStream().use { input: InputStream ->
                FileOutputStream(destinationFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            onProgress(totalRead.toFloat() / contentLength.toFloat())
                        }
                    }
                    output.flush()
                }
            }
        }
        return true
    }
}

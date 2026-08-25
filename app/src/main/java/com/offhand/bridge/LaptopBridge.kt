package com.offhand.bridge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The bridge is an interface so the bet on the daemon is isolated: if a real
 * Office Kit API ever materialises, an OfficeKitBridge implements this and
 * nothing else changes. (As of this build its API surface is UNKNOWN, so the
 * HTTP daemon is the implementation.)
 */
interface LaptopBridge {
    suspend fun health(): Boolean
    suspend fun clipboard(): String?
    suspend fun listFiles(): List<String>
    suspend fun fetchFile(name: String, destDir: File): File?
}

class HttpBridgeClient(host: String, port: Int) : LaptopBridge {

    private val base = "http://$host:$port"
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        try {
            client.newCall(Request.Builder().url("$base/health").build()).execute()
                .use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun clipboard(): String? = withContext(Dispatchers.IO) {
        try {
            client.newCall(Request.Builder().url("$base/clipboard").build()).execute()
                .use { response ->
                    if (!response.isSuccessful) return@withContext null
                    JSONObject(response.body!!.string()).optString("text")
                }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun listFiles(): List<String> = withContext(Dispatchers.IO) {
        try {
            client.newCall(Request.Builder().url("$base/files").build()).execute()
                .use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    val arr = JSONObject(response.body!!.string()).optJSONArray("files")
                        ?: return@withContext emptyList()
                    buildList { for (i in 0 until arr.length()) add(arr.getString(i)) }
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun fetchFile(name: String, destDir: File): File? =
        withContext(Dispatchers.IO) {
            try {
                val url = "$base/file?path=" + java.net.URLEncoder.encode(name, "UTF-8")
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    destDir.mkdirs()
                    val dest = File(destDir, name)
                    response.body!!.byteStream().use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                    dest
                }
            } catch (e: Exception) {
                null
            }
        }
}

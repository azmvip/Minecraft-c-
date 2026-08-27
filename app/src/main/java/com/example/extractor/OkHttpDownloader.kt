package com.example.extractor

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.concurrent.TimeUnit

class OkHttpDownloader private constructor(
    private val client: OkHttpClient
) : Downloader() {

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        @Volatile
        private var instance: OkHttpDownloader? = null

        fun getInstance(client: OkHttpClient = createDefaultClient()): OkHttpDownloader {
            return instance ?: synchronized(this) {
                instance ?: OkHttpDownloader(client).also { instance = it }
            }
        }

        private fun createDefaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }
    }

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBuilder = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)

        headers?.forEach { (name, values) ->
            if (values.isNotEmpty()) {
                requestBuilder.removeHeader(name)
                values.forEach { value ->
                    requestBuilder.addHeader(name, value)
                }
            }
        }

        when (httpMethod) {
            "GET" -> requestBuilder.get()
            "HEAD" -> requestBuilder.head()
            "POST" -> {
                val body = dataToSend?.toRequestBody() ?: ByteArray(0).toRequestBody()
                requestBuilder.post(body)
            }
            "PUT" -> {
                val body = dataToSend?.toRequestBody() ?: ByteArray(0).toRequestBody()
                requestBuilder.put(body)
            }
            "DELETE" -> requestBuilder.delete()
            else -> requestBuilder.get()
        }

        val okResponse = client.newCall(requestBuilder.build()).execute()
        val responseBody = okResponse.body?.string() ?: ""
        val responseHeaders = mutableMapOf<String, List<String>>()

        okResponse.headers.names().forEach { headerName ->
            responseHeaders[headerName] = okResponse.headers.values(headerName)
        }

        return Response(
            okResponse.code,
            okResponse.message,
            responseHeaders,
            responseBody,
            okResponse.request.url.toString()
        )
    }
}

package work.ranjit.nfctags

import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class NetworkManager {
    private val client = OkHttpClient()

    suspend fun sendNfcData(url: String, data: String, isPost: Boolean): String = suspendCoroutine { continuation ->
        try {
            val requestBuilder = Request.Builder()
            
            if (isPost) {
                // Send as form-urlencoded (or adjust to JSON if needed)
                val formBody = FormBody.Builder()
                    .add("nfc", data)
                    .build()
                requestBuilder.url(url).post(formBody)
            } else {
                // Append to query string
                val httpUrl = url.toHttpUrlOrNull()
                if (httpUrl == null) {
                    continuation.resume("Invalid URL format.")
                    return@suspendCoroutine
                }
                
                val urlWithParam = httpUrl.newBuilder()
                    .addQueryParameter("nfc", data)
                    .build()
                requestBuilder.url(urlWithParam).get()
            }

            val request = requestBuilder.build()
            
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resume("Network Error: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string() ?: "Empty response"
                    val code = response.code
                    continuation.resume("HTTP $code: $responseBody")
                }
            })
        } catch (e: Exception) {
            continuation.resume("Exception: ${e.message}")
        }
    }
}

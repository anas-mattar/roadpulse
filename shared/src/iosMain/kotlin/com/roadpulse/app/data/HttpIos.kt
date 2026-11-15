package com.roadpulse.app.data

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.dataUsingEncoding
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual open class IOException actual constructor(message: String) : Exception(message)

actual fun platformTransport(): HttpTransport = UrlSessionTransport

/** iPhone: URLSession. */
object UrlSessionTransport : HttpTransport {
    @Suppress("CAST_NEVER_SUCCEEDS")
    override suspend fun send(method: String, url: String, body: String?, headers: Map<String, String>): HttpResult =
        suspendCancellableCoroutine { cont ->
            val nsUrl = NSURL.URLWithString(url)
            if (nsUrl == null) {
                cont.resumeWithException(IOException("Bad address"))
                return@suspendCancellableCoroutine
            }
            val request = NSMutableURLRequest.requestWithURL(nsUrl)
            request.setHTTPMethod(method)
            request.setTimeoutInterval(12.0)
            headers.forEach { (k, v) -> request.setValue(v, forHTTPHeaderField = k) }
            if (body != null) {
                request.setValue("application/json", forHTTPHeaderField = "Content-Type")
                request.setHTTPBody((body as NSString).dataUsingEncoding(NSUTF8StringEncoding))
            }
            val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
                if (error != null) {
                    cont.resumeWithException(IOException(error.localizedDescription))
                } else {
                    val code = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
                    val text = data?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding)?.toString() }.orEmpty()
                    cont.resume(HttpResult(code, text))
                }
            }
            cont.invokeOnCancellation { task.cancel() }
            task.resume()
        }
}

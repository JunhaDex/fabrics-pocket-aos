package com.fabrics.pocket.bridge

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * 웹뷰와 네이티브 사이의 브릿지. 와이어 형식과 규칙은 브릿지 계약 문서를 따른다.
 *
 * capability 목록은 [handlers]의 키에서 만든다. 구현되지 않은 메서드는 목록에 들어갈 수 없다.
 */
class Bridge(
    context: Context,
    private val handlers: Map<String, BridgeHandler>,
    private val allowedHosts: Set<String>,
    runtimeVersion: String,
) {
    // 이벤트는 웹이 먼저 보낸 메시지의 reply proxy로만 보낼 수 있다. 메인 프레임 문서가
    // 시작될 때 보내는 ready 메시지의 proxy를 보관한다.
    private var eventProxy: JavaScriptReplyProxy? = null

    private val script: String = context.assets.open("bridge.js").bufferedReader().use { it.readText() }
        .replace(
            "/*BRIDGE_CONFIG*/",
            JSONObject()
                .put("capabilities", JSONArray(handlers.keys.sorted()))
                .put("runtimeVersion", runtimeVersion)
                .put("hosts", JSONArray(allowedHosts.toList()))
                .toString()
        )

    /**
     * 웹뷰가 첫 페이지를 로드하기 전에 호출한다. [startUrl]의 origin은 포트까지 규칙에 넣는다.
     * origin 규칙이 포트 와일드카드를 지원하지 않기 때문이다.
     */
    fun attach(webView: WebView, startUrl: String) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        ) return
        val rules = allowedHosts.map { "https://$it" }.toMutableSet()
        originOf(startUrl)?.let(rules::add)
        if (rules.isEmpty()) return
        WebViewCompat.addWebMessageListener(webView, "nativeBridgeChannel", rules) { _, message, sourceOrigin, isMainFrame, proxy ->
            onMessage(message, sourceOrigin, isMainFrame, proxy)
        }
        WebViewCompat.addDocumentStartJavaScript(webView, script, rules)
    }

    fun emit(event: String, data: JSONObject = JSONObject()) {
        eventProxy?.postMessage(JSONObject().put("event", event).put("data", data).toString())
    }

    private fun onMessage(message: WebMessageCompat, sourceOrigin: Uri, isMainFrame: Boolean, proxy: JavaScriptReplyProxy) {
        if (!isMainFrame || sourceOrigin.host !in allowedHosts) return
        val body = try {
            JSONObject(message.data ?: return)
        } catch (e: JSONException) {
            return
        }
        if (body.optBoolean("ready")) {
            eventProxy = proxy
            return
        }
        val id = body.opt("id") as? String ?: return
        val reply = { result: BridgeResult -> proxy.postMessage(encode(id, result)) }
        val handler = handlers[body.opt("method") as? String]
            ?: return reply(BridgeResult.Error(ErrorCode.UNSUPPORTED))
        val params = when (val raw = body.opt("params")) {
            null -> JSONObject()
            is JSONObject -> raw
            else -> return reply(BridgeResult.Error(ErrorCode.INVALID_PARAMS))
        }
        try {
            handler.handle(params, reply)
        } catch (e: Exception) {
            reply(BridgeResult.Error(ErrorCode.INTERNAL))
        }
    }

    private fun encode(id: String, result: BridgeResult): String {
        val json = JSONObject().put("id", id)
        when (result) {
            is BridgeResult.Ok -> json.put("status", "ok").put("data", result.data)
            is BridgeResult.Canceled -> json.put("status", "canceled").put("code", result.code.name)
            is BridgeResult.Error -> json.put("status", "error").put("code", result.code.name)
        }
        return json.toString()
    }

    private fun originOf(url: String): String? {
        val uri = Uri.parse(url)
        val scheme = uri.scheme ?: return null
        val host = uri.host ?: return null
        if (scheme != "http" && scheme != "https") return null
        return if (uri.port == -1) "$scheme://$host" else "$scheme://$host:${uri.port}"
    }
}

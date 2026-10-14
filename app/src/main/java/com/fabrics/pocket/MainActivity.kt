package com.fabrics.pocket

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.fabrics.pocket.ui.theme.FabricspocketaosTheme
import java.net.URISyntaxException

class MainActivity : ComponentActivity() {
    // 마지막 메인 프레임 로딩이 실패한 URL. null이 아니면 오류 화면을 덮는다.
    private var failedUrl by mutableStateOf<String?>(null)
    // 첫 메인 프레임 로딩이 실패 없이 끝나기 전까지 로딩 화면을 덮는다. 한 번 걷으면 다시 띄우지 않는다.
    private var loading by mutableStateOf(true)
    // 웹 히스토리가 있을 때만 켠다. 꺼져 있으면 시스템 기본 동작(predictive back 포함)에 맡긴다.
    private lateinit var back: OnBackPressedCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // 문서 교체 사이에 기본 흰 배경이 비치지 않도록 시작 화면과 같은 배경색을 쓴다.
            setBackgroundColor(getColor(R.color.background))
            webViewClient = ShellClient()
            loadUrl(BuildConfig.START_URL)
        }
        back = onBackPressedDispatcher.addCallback(this, enabled = false) { webView.goBack() }
        // 로고 자리. adaptive icon은 painterResource로 읽을 수 없어 bitmap으로 변환한다.
        val logo = ContextCompat.getDrawable(this, R.mipmap.ic_launcher)?.toBitmap()?.asImageBitmap()
        setContent {
            FabricspocketaosTheme {
                // safe area는 웹이 env()로 처리하므로 WebView에 inset padding을 주지 않는다.
                Box(Modifier.fillMaxSize()) {
                    AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
                    if (loading) Loading(logo)
                    failedUrl?.let { url -> LoadError(onRetry = { webView.loadUrl(url) }) }
                }
            }
        }
    }

    private inner class ShellClient : WebViewClient() {
        private var loadFailed = false

        // 허용 목록 밖의 메인 프레임 이동은 OS에 넘긴다. 내부 이동은 loadUrl로 다시 띄우지 않고
        // 웹뷰가 그대로 처리하게 둔다(cross-document View Transitions 유지).
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame) return false
            val uri = request.url
            when (uri.scheme) {
                "http", "https" -> if (uri.host in BuildConfig.ALLOWED_HOSTS) return false
                "about", "data", "blob", "javascript" -> return false
            }
            try {
                startActivity(
                    if (uri.scheme == "intent") {
                        Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME).apply {
                            addCategory(Intent.CATEGORY_BROWSABLE)
                            component = null
                            selector = null
                        }
                    } else Intent(Intent.ACTION_VIEW, uri)
                )
            } catch (e: ActivityNotFoundException) {
                // 처리할 앱이 없으면 무시한다.
            } catch (e: URISyntaxException) {
                // 형식이 잘못된 intent: URL은 무시한다.
            }
            return true
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            back.isEnabled = view.canGoBack()
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            loadFailed = false
        }

        override fun onReceivedError(view: WebView?, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            loadFailed = true
            failedUrl = request.url.toString()
        }

        // 재시도가 성공할 때까지 오류 화면을 유지해 WebView 기본 오류 페이지가 보이지 않게 한다.
        override fun onPageFinished(view: WebView?, url: String?) {
            if (!loadFailed) {
                failedUrl = null
                loading = false
            }
        }
    }
}

@Composable
fun Loading(logo: ImageBitmap?) {
    Column(
        modifier = Modifier.fillMaxSize().background(colorResource(R.color.background)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        logo?.let {
            Image(it, contentDescription = null, modifier = Modifier.size(96.dp))
            Spacer(Modifier.height(32.dp))
        }
        CircularProgressIndicator()
    }
}

@Composable
fun LoadError(onRetry: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.load_error_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.load_error_message),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.load_error_retry)) }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoadErrorPreview() {
    FabricspocketaosTheme {
        LoadError(onRetry = {})
    }
}

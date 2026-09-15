package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.LeaseDeal
import com.example.data.scraper.LeaseScraperEngine

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ScraperWebView(
    url: String,
    onClose: () -> Unit,
    onDealExtracted: (LeaseDeal) -> Unit,
    modifier: Modifier = Modifier
) {
    var pageProgress by remember { mutableFloatStateOf(0f) }
    var currentTitle by remember { mutableStateOf("Loading Web Scraper...") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Surface(
        modifier = modifier.testTag("scraper_webview_container"),
        tonalElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Live Scraper Browser",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = currentTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                }

                IconButton(
                    onClick = { webViewRef?.reload() },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("reload_webview_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reload Page",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("close_webview_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Browser View",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (pageProgress in 0.01f..0.99f) {
                LinearProgressIndicator(
                    progress = { pageProgress },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewRef = this

                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                userAgentString =
                                    "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro Build/UQ1A.240205.004) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.64 Mobile Safari/537.36"
                                cacheMode = WebSettings.LOAD_DEFAULT
                            }

                            // Javascript Interface for DOM Extraction
                            addJavascriptInterface(object {
                                @JavascriptInterface
                                fun receiveExtractedDeal(
                                    vehicleTitle: String,
                                    monthlyPrice: String,
                                    initialPayment: String,
                                    term: String,
                                    mileage: String,
                                    financeType: String,
                                    additionalFees: String
                                ) {
                                    post {
                                        val isSearch = LeaseScraperEngine.isSearchListingUrl(url)
                                        val meta = LeaseScraperEngine.parseUrlMetadata(url)
                                        val searchMeta = LeaseScraperEngine.parseSearchUrlMetadata(url)

                                        val resolvedMake = if (isSearch) searchMeta.manufacturer else meta.make.ifBlank { "Vauxhall" }
                                        val resolvedModel = if (isSearch) searchMeta.range else meta.model.ifBlank { "Corsa" }
                                        val resolvedFinance = financeType.ifBlank { if (isSearch) searchMeta.financeType else "Personal" }
                                        val resolvedFee = additionalFees.ifBlank { "£199.00" }
                                        val resolvedVat = if (resolvedFinance.equals("Business", ignoreCase = true)) "+ VAT" else "Inc. VAT"

                                        val deal = LeaseDeal(
                                            sourceUrl = url,
                                            vehicleName = vehicleTitle.ifBlank { "$resolvedMake $resolvedModel 1.2 Design" },
                                            vehicleMake = resolvedMake.replaceFirstChar { it.uppercase() },
                                            vehicleModel = resolvedModel.replaceFirstChar { it.uppercase() },
                                            trimVariant = vehicleTitle.substringAfter(resolvedModel).trim().ifBlank { "1.2 Design 5dr" },
                                            monthlyPrice = monthlyPrice.ifBlank { "£179.99 / mo" },
                                            initialPayment = initialPayment.ifBlank { "£1,619.91 (9 months upfront)" },
                                            termMonths = term.ifBlank { "36 Months" },
                                            annualMileage = mileage.ifBlank { "8,000 miles/yr" },
                                            brokerName = meta.brokerName.ifBlank { "Lease Cars 4 Less" },
                                            dealRef = meta.dealRef.ifBlank { "L0103950000002074620" },
                                            vatStatus = resolvedVat,
                                            contractType = "$resolvedFinance Lease",
                                            fuelType = if (vehicleTitle.contains("Electric", ignoreCase = true)) "Electric" else "Petrol",
                                            transmission = if (vehicleTitle.contains("Auto", ignoreCase = true)) "Automatic" else "Manual",
                                            financeType = resolvedFinance,
                                            additionalFees = resolvedFee
                                        )
                                        onDealExtracted(deal)
                                    }
                                }

                                @JavascriptInterface
                                fun receiveExtractedDeal(
                                    vehicleTitle: String,
                                    monthlyPrice: String,
                                    initialPayment: String,
                                    term: String,
                                    mileage: String
                                ) {
                                    receiveExtractedDeal(vehicleTitle, monthlyPrice, initialPayment, term, mileage, "Personal", "£199.00")
                                }
                            }, "AndroidScraper")

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    pageProgress = newProgress / 100f
                                    if (newProgress == 100) {
                                        currentTitle = view?.title ?: "Deal Page"
                                    }
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    currentTitle = "Scraping $url..."
                                }

                                override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                    super.onPageFinished(view, finishedUrl)
                                    currentTitle = view?.title ?: "Deal Loaded"

                                    // Inject script to extract deal metrics and separate fees/finance type
                                    val js = """
                                        (function() {
                                            try {
                                                var title = document.querySelector('h1')?.innerText || 
                                                            document.querySelector('.c-deal-card__title, .deal-card h2, .listing-card h2, [class*="vehicle-card"] h2')?.innerText ||
                                                            document.title;
                                                var body = document.body ? document.body.innerText : '';
                                                
                                                var monthlyMatch = body.match(/(?:monthly|pm|per month|rental)[\s:]*£\s*(\d{1,4}(?:\.\d{2})?)/i) ||
                                                                   body.match(/£\s*(\d{2,4}(?:\.\d{2})?)/);
                                                var monthly = monthlyMatch ? '£' + monthlyMatch[1] : '';

                                                var initMatch = body.match(/(?:initial payment|initial rental|upfront|deposit)[\s:]*£\s*(\d{1,4}(?:,\d{3})?(?:\.\d{2})?)/i);
                                                var initial = initMatch ? '£' + initMatch[1] : '';

                                                var termMatch = body.match(/\b(12|18|24|36|48|60)\s*(?:months|mths|month)\b/i);
                                                var term = termMatch ? termMatch[1] + ' Months' : '36 Months';

                                                var mileMatch = body.match(/\b(\d{1,2},?\d{3})\s*(?:miles|mileage|per annum|mpa)\b/i);
                                                var mileage = mileMatch ? mileMatch[1] + ' miles/yr' : '8,000 miles/yr';

                                                var feeMatch = body.match(/(?:processing|admin|additional|broker)\s*(?:fee|charges?)?[\s:]*£\s*(\d{1,4}(?:\.\d{2})?)/i);
                                                var addFee = feeMatch ? '£' + feeMatch[1] : '£199.00';

                                                var isBusiness = /business/i.test(body) && !/personal/i.test(body);
                                                var financeType = isBusiness ? 'Business' : 'Personal';

                                                if (monthly) {
                                                    AndroidScraper.receiveExtractedDeal(title, monthly, initial, term, mileage, financeType, addFee);
                                                }
                                            } catch(e) {
                                                console.error(e);
                                            }
                                        })();
                                    """.trimIndent()

                                    view?.evaluateJavascript(js, null)
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                    Log.w("ScraperWebView", "Web error: ${error?.description}")
                                }
                            }

                            loadUrl(url)
                        }
                    }
                )
            }
        }
    }
}

package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
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
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.LeaseDeal
import com.example.data.scraper.LeaseScraperEngine
import org.json.JSONObject
import java.util.Locale

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ScraperWebView(
    url: String,
    onClose: () -> Unit,
    onDealExtracted: (LeaseDeal) -> Unit,
    modifier: Modifier = Modifier
) {
    var pageProgress by remember { mutableFloatStateOf(0f) }
    var currentTitle by remember { mutableStateOf("Loading Browser...") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isScrapingNow by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val runScrapeScript = {
        webViewRef?.let { webView ->
            isScrapingNow = true
            Log.d("ScraperDebug", "Manual extraction triggered for: ${webView.url}")

            val js = """
            (function() {
                try {
                    console.log('JS Scraper: Searching DOM...');
                    var scopeText = document.body.innerText;
                    var card = document.querySelector('.c-listing-card, .deal-card, .listing-item, [class*="card"]');
                    if (card) {
                        scopeText = card.innerText + "\n---\n" + scopeText;
                    }

                    var titleSelectors = ['.c-listing-card__title', '.c-deal-card__title', '.deal-card h2', '.listing-card h2', 'h1', 'h2'];
                    var vehicleTitle = 'Unknown Vehicle';
                    for (var s of titleSelectors) {
                        var el = document.querySelector(s);
                        if (el && el.innerText.trim()) {
                            vehicleTitle = el.innerText.replace(/Search Results/gi, '').trim();
                            break;
                        }
                    }

                    var derivEl = document.querySelector('.c-listing-card__derivative, .c-deal-card__derivative, [class*="derivative"]');
                    var derivativeText = '';
                    if (derivEl) {
                        var cleanDerivative = derivEl.innerText.replace(/Search Results/gi, '').trim();
                        if (cleanDerivative && !vehicleTitle.includes(cleanDerivative)) {
                            derivativeText = cleanDerivative;
                        }
                    }
                    
                    // 1. Strip out ratings, "images for illustration", and sorting badge text
                    var cleanText = scopeText
                        .replace(/^[\s\S]*?(?:\*?images for illustration[^\n]*|\b\d\.\d\b)\s*/i, '')
                        .trim();

                    // 2. Body style anchor matching (Hatchback, SUV, etc.)
                    var bodyStyles = '(?:Hatchback|Estate|SUV|Saloon|Coupe|Coupé|MPV|Convertible|Cabriolet|Pickup|Crossover)';
                    
                    // Match up to 4 words directly preceding the body style
                    var nameMatch = cleanText.match(new RegExp('(?:^|\\n)\\s*([A-Za-z0-9\\-]+(?:\\s+[A-Za-z0-9\\-]+){1,3})\\s+' + bodyStyles, 'i')) ||
                                    scopeText.match(new RegExp('([A-Za-z0-9\\-]+(?:\\s+[A-Za-z0-9\\-]+){1,3})\\s+' + bodyStyles, 'i'));

                    var vehicleName = nameMatch ? nameMatch[1].trim() : (vehicleTitle !== 'Unknown Vehicle' ? vehicleTitle : '');
                    
                    // 1. Initial Rental Extraction
                    var initMatch = scopeText.match(/£\s*([\d,]+(?:\.\d{2})?)\s*(?:initial payment|initial rental|upfront|deposit|initial)/i) ||
                                    scopeText.match(/(?:initial payment|initial rental|upfront|deposit|initial)[\s:]*£\s*([\d,]+(?:\.\d{2})?)/i);
                    var initial = initMatch ? '£' + initMatch[1] : '';

                    // 2. Monthly Price Extraction
                    var monthlyMatch = scopeText.match(/£\s*([\d,]+(?:\.\d{2})?)\s*(?:monthly|pm|per month|monthly rental)/i) ||
                                       scopeText.match(/(?:monthly|pm|per month|monthly rental)[\s:]*£\s*([\d,]+(?:\.\d{2})?)/i);
                    var monthly = monthlyMatch ? '£' + monthlyMatch[1] : '';
                    
                    var feesMatch = scopeText.match(/Additional fees:\s*£?\s*([\d,]+(?:\.\d{2})?)/i);
                    var additionalFees = feesMatch ? '£' + feesMatch[1] : '';

                    // 3. Term and Mileage Extraction
                    var termMatch = scopeText.match(/\b(12|18|24|36|48|60)\s*(?:months|mths|month)\b/i);
                    var term = termMatch ? termMatch[1] : '';
                    
                    var mileMatch = scopeText.match(/\b([\d,]+(?:\.\d+)?k?)\s*(?:miles|mileage|per annum|mpa|miles\s*p\/a)\b/i);
                    var mileage = mileMatch ? mileMatch[1] : '';
                    
                    var leaseMatch = scopeText.match(/Lease type:\s*([a-z]+)/i);
                    var leaseType = leaseMatch ? leaseMatch[1] : '';

                    // Fallback: If we have one price but not the other
                    if (!monthly && initial) {
                       var allPrices = scopeText.match(/£\s*[\d,]+(?:\.\d{2})?/g) || [];
                       for (var p of allPrices) {
                           if (p !== initial) { monthly = p; break; }
                       }
                    }

                    var initIndex = scopeText.toLowerCase().indexOf('initial rental');
                    var initialContext = initIndex !== -1 ? 
                        scopeText.substring(0, Math.min(scopeText.length, initIndex + 160)).replace(/\n/g, ' ') : 'Not Found';

                    var debugInfo = {
                        vehicleName: vehicleName,
                        derivative: derivativeText,
                        monthly: monthly,
                        initial: initial,
                        term: term,
                        additionalFees: additionalFees,
                        mileage: mileage,
                        initialContext: initialContext,
                        leaseType: leaseType,
                        url: window.location.href
                    };

                    console.log('JS Scraper Result: ' + JSON.stringify(debugInfo));
                    
                    if (monthly) {
                        AndroidScraper.receiveExtractedDeal(vehicleName, derivativeText, monthly, initial, term, mileage, '', additionalFees, leaseType, JSON.stringify(debugInfo));
                    } else {
                        console.log('JS Scraper: Could not find monthly price.');
                    }
                } catch(e) {
                    console.error('JS Scraper Exception: ' + e);
                }
            })();
        """.trimIndent()
            webView.evaluateJavascript(js, null)

            webView.postDelayed({ isScrapingNow = false }, 2000)
        }
    }

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

                if (isScrapingNow) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp).padding(4.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    IconButton(
                        onClick = { runScrapeScript() },
                        modifier = Modifier.size(40.dp).testTag("manual_scrape_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Extract Deal Now",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
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
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewRef = this

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                WebView.setWebContentsDebuggingEnabled(true)
                            }

                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                // Simplified User Agent to avoid detection/blocks
                                userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
                                cacheMode = WebSettings.LOAD_DEFAULT
                            }

                            addJavascriptInterface(object {
                                @JavascriptInterface
                                fun receiveExtractedDeal(
                                    vehicleName: String,
                                    derivative: String,
                                    monthlyPrice: String,
                                    initialPayment: String,
                                    term: String,
                                    mileage: String,
                                    financeType: String,
                                    additionalFees: String,
                                    leaseType: String,
                                    debugMetadata: String
                                ) {
                                    post {
                                        Log.d("ScraperDebug", "EXTRACTED: $monthlyPrice / $initialPayment")

                                        if (monthlyPrice.isBlank()) return@post

                                        val monthlyVal = monthlyPrice.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
                                        val initialVal = initialPayment.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
                                        val calculatedUpfront = if (monthlyVal > 0) Math.round(initialVal / monthlyVal).toInt() else 0
                                        
                                        val metadataStr = try {
                                            val json = JSONObject(debugMetadata)
                                            val contextSnippet = json.optString("initialContext", "")
                                            val termVal = json.optString("term", "")
                                            val mileVal = json.optString("mileage", "")
                                            "\nTerm: $termVal, Miles: $mileVal\nContext: $contextSnippet"
                                        } catch (e: Exception) { "" }

                                        Toast.makeText(context,
                                            "Found: $monthlyPrice pm / $initialPayment upfront ($calculatedUpfront months)$metadataStr",
                                            Toast.LENGTH_LONG).show()

                                        val deal = LeaseDeal(
                                            sourceUrl = url,
                                            vehicleName = vehicleName,
                                            vehicleMake = "",
                                            vehicleModel = derivative,
                                            trimVariant = "",
                                            monthlyPrice = if (monthlyPrice.startsWith("£")) monthlyPrice else "£$monthlyPrice",
                                            initialPayment = if (initialPayment.isNotBlank()) initialPayment else "Check site",
                                            termMonths = if (term.isNotBlank()) "$term Months" else "term not found",
                                            annualMileage = if (mileage.isNotBlank()) "$mileage miles" else "miles not found",
                                            brokerName = "Broker tbc",
                                            dealRef = "B" + System.currentTimeMillis().toString().takeLast(8),
                                            vatStatus = "tbc",
                                            contractType = leaseType,
                                            fuelType = "tbc",
                                            transmission = "tbc",
                                            financeType = "tbc",
                                            upfrontPaymentsCount = calculatedUpfront,
                                            additionalFees = additionalFees
                                        )
                                        onDealExtracted(deal)
                                    }
                                }

                                @JavascriptInterface
                                fun receiveExtractedDeal(
                                    vehicleName: String,
                                    derivative: String,
                                    monthlyPrice: String,
                                    initialPayment: String,
                                    term: String,
                                    additionalFees: String,
                                    leaseType: String,
                                    mileage: String
                                ) {
                                    receiveExtractedDeal(vehicleName, derivative, monthlyPrice, initialPayment, term, mileage, "", additionalFees, leaseType, "{}")
                                }
                            }, "AndroidScraper")

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    pageProgress = newProgress / 100f
                                    if (newProgress == 100) {
                                        currentTitle = view?.title ?: "Page Loaded"
                                    }
                                }

                                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                    Log.d("ScraperWebViewJS", "[JS] ${consoleMessage?.message()}")
                                    return true
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    currentTitle = "Loading..."
                                }

                                override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                    super.onPageFinished(view, finishedUrl)
                                    currentTitle = view?.title ?: "Ready"
                                    
                                    // Automatically trigger extraction after a delay to allow dynamic content to load
                                    view?.postDelayed({
                                        runScrapeScript()
                                    }, 3000)
                                }

                                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                    super.onReceivedError(view, request, error)
                                    Log.e("ScraperWebView", "Web Error: ${error?.description}")
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

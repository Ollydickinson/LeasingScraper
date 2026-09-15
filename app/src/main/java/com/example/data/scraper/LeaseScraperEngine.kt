package com.example.data.scraper

import android.util.Log
import com.example.data.model.LeaseDeal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.TimeUnit

object LeaseScraperEngine {

    private const val TAG = "LeaseScraperEngine"
    const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    data class ScrapeResult(
        val isSuccess: Boolean,
        val deals: List<LeaseDeal> = emptyList(),
        val message: String = "",
        val isCloudflareProtected: Boolean = false,
        val rawHtmlSnippet: String = ""
    )

    suspend fun scrapeUrl(url: String): ScrapeResult = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) {
            return@withContext ScrapeResult(false, message = "URL cannot be empty")
        }

        val searchMeta = parseSearchUrlMetadata(cleanUrl)

        try {
            val request = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-GB,en;q=0.9")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val statusCode = response.code
            val htmlBody = response.body?.string() ?: ""

            val isCloudflare = statusCode == 403 || 
                htmlBody.contains("Just a moment...", ignoreCase = true) || 
                htmlBody.contains("challenge-platform", ignoreCase = true)

            if (isCloudflare || !response.isSuccessful) {
                return@withContext ScrapeResult(
                    isSuccess = false,
                    message = "Blocked by Cloudflare or HTTP Status $statusCode. In-app WebView engine required.",
                    isCloudflareProtected = isCloudflare,
                    rawHtmlSnippet = htmlBody.take(200)
                )
            }

            // Extract via __NEXT_DATA__
            val doc = Jsoup.parse(htmlBody, cleanUrl)
            val nextDataScript = doc.select("script#__NEXT_DATA__").first()?.data()

            if (!nextDataScript.isNullOrBlank()) {
                val deals = extractDealsFromNextData(nextDataScript, cleanUrl, searchMeta)
                if (deals.isNotEmpty()) {
                    return@withContext ScrapeResult(
                        isSuccess = true,
                        deals = deals,
                        message = "Successfully scraped ${deals.size} live deals via __NEXT_DATA__ payload.",
                        rawHtmlSnippet = doc.title()
                    )
                }
            }

            ScrapeResult(
                isSuccess = false,
                message = "Failed to parse JSON state payload from target page.",
                rawHtmlSnippet = doc.title()
            )

        } catch (e: Exception) {
            Log.e(TAG, "Scraping failed for $cleanUrl", e)
            ScrapeResult(
                isSuccess = false,
                message = "Scraper Exception: ${e.localizedMessage ?: "Unknown Error"}"
            )
        }
    }

    fun extractDealsFromNextData(
        jsonString: String,
        sourceUrl: String,
        searchMeta: SearchMetadata
    ): List<LeaseDeal> {
        val deals = mutableListOf<LeaseDeal>()
        try {
            val root = JSONObject(jsonString)
            val pageProps = root.optJSONObject("props")?.optJSONObject("pageProps") ?: return emptyList()
            
            // Navigate to results array inside Next.js state tree
            val searchResults = pageProps.optJSONObject("searchResults") 
                ?: pageProps.optJSONObject("initialState")?.optJSONObject("search")
            val itemsArray = searchResults?.optJSONArray("items") 
                ?: searchResults?.optJSONArray("results")
                ?: pageProps.optJSONArray("listings")

            if (itemsArray != null) {
                for (i in 0 until itemsArray.length()) {
                    val item = itemsArray.optJSONObject(i) ?: continue
                    val deal = parseDealFromJsonObject(item, sourceUrl, searchMeta)
                    if (deal != null) deals.add(deal)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON payload: ${e.message}", e)
        }
        return deals
    }

    private fun parseDealFromJsonObject(
        obj: JSONObject,
        sourceUrl: String,
        searchMeta: SearchMetadata
    ): LeaseDeal? {
        val vehicleObj = obj.optJSONObject("vehicle") ?: obj
        val make = vehicleObj.optString("make", vehicleObj.optString("manufacturer", searchMeta.manufacturer)).replaceFirstChar { it.uppercase() }
        val model = vehicleObj.optString("model", vehicleObj.optString("range", searchMeta.range)).replaceFirstChar { it.uppercase() }
        val derivative = vehicleObj.optString("derivative", vehicleObj.optString("trim", "Standard Spec"))
        val title = "$make $model $derivative".trim()

        // Extract precise pricing (Personal Inc. VAT vs Business Ex. VAT)
        val pricingObj = obj.optJSONObject("pricing") ?: obj
        val personalPricing = pricingObj.optJSONObject("personal") ?: pricingObj
        
        val rawMonthly = when {
            personalPricing.has("monthlyIncVat") -> personalPricing.optDouble("monthlyIncVat")
            personalPricing.has("monthlyPrice") -> personalPricing.optDouble("monthlyPrice")
            else -> personalPricing.optDouble("monthlyRental", -1.0)
        }

        if (rawMonthly <= 0.0) return null

        val termMonths = obj.optInt("term", searchMeta.term)
        val upfrontCount = obj.optInt("upfront", searchMeta.upfront)
        val mileage = obj.optInt("mileage", searchMeta.mileage)

        val rawInitial = personalPricing.optDouble("initialRental", rawMonthly * upfrontCount)
        val rawFee = obj.optDouble("processingFee", 199.00)

        val totalContractVal = (upfrontCount + (termMonths - 1)) * rawMonthly
        val totalIncFeesVal = totalContractVal + rawFee

        return LeaseDeal(
            sourceUrl = sourceUrl,
            vehicleName = title,
            vehicleMake = make,
            vehicleModel = model,
            trimVariant = derivative,
            monthlyPrice = "£${String.format(Locale.UK, "%.2f", rawMonthly)} / mo",
            initialPayment = "£${String.format(Locale.UK, "%,.2f", rawInitial)} ($upfrontCount months upfront)",
            termMonths = "$termMonths Months",
            annualMileage = "${String.format(Locale.UK, "%,d", mileage)} miles/yr",
            brokerName = obj.optJSONObject("broker")?.optString("name", "Leasing Provider") ?: "Leasing Provider",
            dealRef = obj.optString("dealRef", "REF-${(100000..999999).random()}"),
            vatStatus = "Inc. VAT",
            contractType = "${searchMeta.financeType} Lease",
            fuelType = vehicleObj.optString("fuelType", searchMeta.fuel),
            transmission = vehicleObj.optString("transmission", "Automatic"),
            financeType = searchMeta.financeType,
            additionalFees = "£${String.format(Locale.UK, "%.2f", rawFee)}",
            upfrontPaymentsCount = upfrontCount,
            totalPayable = "£${String.format(Locale.UK, "%,.2f", totalContractVal)}",
            totalPayableIncFees = "£${String.format(Locale.UK, "%,.2f", totalIncFeesVal)}"
        )
    }

    data class SearchMetadata(
        val financeType: String = "Personal",
        val manufacturer: String = "Omoda",
        val range: String = "5",
        val fuel: String = "Electric",
        val term: Int = 36,
        val upfront: Int = 6,
        val mileage: Int = 10000
    )

    fun parseSearchUrlMetadata(url: String): SearchMetadata {
        return try {
            val query = if (url.contains("?")) url.substringAfter("?") else ""
            val params = query.split("&").filter { it.contains("=") }.associate {
                val parts = it.split("=", limit = 2)
                URLDecoder.decode(parts[0], "UTF-8").lowercase() to URLDecoder.decode(parts[1], "UTF-8")
            }
            SearchMetadata(
                financeType = params["finance"]?.replaceFirstChar { it.uppercase() } ?: "Personal",
                manufacturer = params["manufacturer"]?.replaceFirstChar { it.uppercase() } ?: "Omoda",
                range = params["range"]?.replaceFirstChar { it.uppercase() } ?: "5",
                fuel = params["fuel"]?.replaceFirstChar { it.uppercase() } ?: "Electric",
                term = params["term"]?.toIntOrNull() ?: 36,
                upfront = params["upfront"]?.toIntOrNull() ?: 6,
                mileage = params["mileage"]?.toIntOrNull() ?: 10000
            )
        } catch (_: Exception) {
            SearchMetadata()
        }
    }
}

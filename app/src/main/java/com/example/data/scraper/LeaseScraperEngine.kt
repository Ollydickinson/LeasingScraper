package com.example.data.scraper

import android.content.Context
import com.example.data.model.LeaseDeal

/**
 * Utility engine for lease deal data processing.
 * Note: Background scraping via Jsoup is disabled in favor of interactive browser extraction.
 */
object LeaseScraperEngine {

    fun init(context: Context) {}

    // Stubs for binary compatibility with minimal changes
    fun parseUrlMetadata(url: String): DealMetadata {
        val broker = if (url.contains("leasing.com")) "Lease Cars 4 Less" else "Independent Broker"
        
        // Try to extract make/model from URL if it's a search URL
        var manufacturer = ""
        var range = ""
        if (isSearchListingUrl(url)) {
            val search = parseSearchUrlMetadata(url)
            manufacturer = search.manufacturer
            range = search.range
        } else if (url.contains("/car-leasing/")) {
            // Simple extraction for direct deal URLs like /car-leasing/vauxhall/corsa/...
            val segments = url.substringAfter("/car-leasing/").split("/")
            if (segments.size >= 2) {
                manufacturer = segments[0]
                range = segments[1]
            }
        }

        return DealMetadata(
            brokerName = broker, 
            dealRef = "L" + url.hashCode().toString().take(8),
            manufacturer = manufacturer,
            range = range,
            make = manufacturer,
            model = range
        )
    }
    
    data class DealMetadata(val make: String = "", val model: String = "", val manufacturer: String = "", val range: String = "", val brokerName: String = "", val dealRef: String = "")
    
    fun isSearchListingUrl(url: String): Boolean = url.contains("/search/")
    
    data class ScrapeResult(
        val deals: List<LeaseDeal> = emptyList(),
        val isSuccess: Boolean = false,
        val message: String = ""
    )

    fun scrapeUrl(url: String): ScrapeResult {
        // Background Jsoup scraping is disabled. 
        // We return a result that informs the user to use the browser.
        return ScrapeResult(
            isSuccess = false,
            message = "Background scraping disabled. Use the 'Browser' button to extract prices interactively."
        )
    }

    data class SearchMetadata(val manufacturer: String = "", val range: String = "", val financeType: String = "Personal")
    
    fun parseSearchUrlMetadata(url: String): SearchMetadata {
        // Simple extraction from query params
        val manufacturer = if (url.contains("manufacturer=")) url.substringAfter("manufacturer=").substringBefore("&") else ""
        val range = if (url.contains("range=")) url.substringAfter("range=").substringBefore("&") else ""
        val finance = if (url.contains("finance=")) url.substringAfter("finance=").substringBefore("&") else "Personal"
        return SearchMetadata(manufacturer, range, finance)
    }

    fun extractMileageInt(deal: LeaseDeal): Int = extractMileageInt(deal.annualMileage)
    fun extractMileageInt(text: String): Int {
        val cleaned = text.replace(",", "").replace(" ", "").lowercase()
        val match = Regex("(\\d+)").find(cleaned)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    fun extractTermMonths(deal: LeaseDeal): Int = extractTermMonths(deal.termMonths)
    fun extractTermMonths(text: String): Int {
        val match = Regex("(\\d+)").find(text)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    fun generateTermAndUpfrontVariants(baseDeal: LeaseDeal, mileages: List<Int>? = null, upfronts: List<Int> = emptyList()) = listOf(baseDeal)
    val DEFAULT_MILEAGE_PROFILES = listOf(0)
}

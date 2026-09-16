package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.LeaseDeal
import com.example.data.model.SavedUrl
import com.example.data.repository.LeaseDealRepository
import com.example.data.scraper.LeaseScraperEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ScraperTab(val title: String) {
    DEALS("Deals"),
    CONTROLS("Controls")
}

class LeaseScraperViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: LeaseDealRepository

    companion object {
        const val TARGET_URL =
            "https://leasing.com/car-leasing/search/?finance=Personal&grouped=false&manufacturer=Vauxhall&range=Corsa&sort=total&page=2"

        val DEFAULT_PRESETS = listOf(
            SavedUrl(
                url = "https://leasing.com/car-leasing/search/?finance=Personal&grouped=false&manufacturer=Vauxhall&range=Corsa&sort=total&page=2",
                description = "Vauxhall Corsa Search Results (Page 2)"
            )
        )

        fun normalizeUrl(url: String): String {
            return url.trim().lowercase().removeSuffix("/")
        }
    }

    init {
        LeaseScraperEngine.init(application)
        val db = AppDatabase.getDatabase(application)
        repository = LeaseDealRepository(db.leaseDealDao(), db.savedUrlDao())
    }

    // Deals Flow
    val deals: StateFlow<List<LeaseDeal>> = repository.allDeals
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Saved URLs Flow
    val savedUrls: StateFlow<List<SavedUrl>> = repository.allSavedUrls
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current Tab
    private val _currentTab = MutableStateFlow(ScraperTab.DEALS)
    val currentTab: StateFlow<ScraperTab> = _currentTab.asStateFlow()

    private val _urlInput = MutableStateFlow(TARGET_URL)
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _isScraping = MutableStateFlow(false)
    val isScraping: StateFlow<Boolean> = _isScraping.asStateFlow()

    private val _scrapingUrl = MutableStateFlow<String?>(null)
    val scrapingUrl: StateFlow<String?> = _scrapingUrl.asStateFlow()

    private val _statusMessage = MutableStateFlow("Ready to scrape leasing deal")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _isCloudflareEncountered = MutableStateFlow(false)
    val isCloudflareEncountered: StateFlow<Boolean> = _isCloudflareEncountered.asStateFlow()

    private val _showBrowserPreview = MutableStateFlow(false)
    val showBrowserPreview: StateFlow<Boolean> = _showBrowserPreview.asStateFlow()

    private val _activeBrowserUrl = MutableStateFlow(TARGET_URL)
    val activeBrowserUrl: StateFlow<String> = _activeBrowserUrl.asStateFlow()

    private val _selectedDeal = MutableStateFlow<LeaseDeal?>(null)
    val selectedDeal: StateFlow<LeaseDeal?> = _selectedDeal.asStateFlow()

    private val _selectedUpfrontFilter = MutableStateFlow<Int?>(null)
    val selectedUpfrontFilter: StateFlow<Int?> = _selectedUpfrontFilter.asStateFlow()

    private val _selectedTermFilter = MutableStateFlow<Int?>(null)
    val selectedTermFilter: StateFlow<Int?> = _selectedTermFilter.asStateFlow()

    private val _selectedVehicleFilter = MutableStateFlow<String?>(null)
    val selectedVehicleFilter: StateFlow<String?> = _selectedVehicleFilter.asStateFlow()

    private val _selectedMileageFilter = MutableStateFlow<Int?>(null)
    val selectedMileageFilter: StateFlow<Int?> = _selectedMileageFilter.asStateFlow()

    init {
        viewModelScope.launch {
            delay(200)
            if (savedUrls.value.isEmpty()) {
                repository.insertSavedUrls(DEFAULT_PRESETS)
            }
        }
    }

    fun setTab(tab: ScraperTab) {
        _currentTab.value = tab
    }

    fun onUrlChange(newUrl: String) {
        _urlInput.value = newUrl
    }

    fun resetToTargetUrl() {
        _urlInput.value = TARGET_URL
    }

    fun setUpfrontFilter(upfrontCount: Int?) {
        _selectedUpfrontFilter.value = upfrontCount
    }

    fun setTermFilter(termMonths: Int?) {
        _selectedTermFilter.value = termMonths
    }

    fun setVehicleFilter(vehicleName: String?) {
        _selectedVehicleFilter.value = vehicleName
    }

    fun setMileageFilter(mileage: Int?) {
        _selectedMileageFilter.value = mileage
    }

    fun resetAllFilters() {
        _selectedUpfrontFilter.value = null
        _selectedTermFilter.value = null
        _selectedVehicleFilter.value = null
        _selectedMileageFilter.value = null
    }

    fun toggleBrowserPreview() {
        _activeBrowserUrl.value = _urlInput.value
        _showBrowserPreview.value = !_showBrowserPreview.value
    }

    fun setBrowserPreview(show: Boolean) {
        _showBrowserPreview.value = show
    }

    fun openWebViewForUrl(url: String) {
        _activeBrowserUrl.value = url.ifBlank { TARGET_URL }
        _showBrowserPreview.value = true
    }

    fun selectDeal(deal: LeaseDeal?) {
        _selectedDeal.value = deal
    }

    /**
     * Check if a specific URL exists in the current scraped deals list
     */
    fun isUrlScraped(url: String): Boolean {
        val targetNorm = normalizeUrl(url)
        return deals.value.any { normalizeUrl(it.sourceUrl) == targetNorm }
    }

    /**
     * Count how many deals/tiers currently exist for this URL
     */
    fun getScrapedCountForUrl(url: String): Int {
        val targetNorm = normalizeUrl(url)
        return deals.value.count { normalizeUrl(it.sourceUrl) == targetNorm }
    }

    // --- Saved URLs CRUD ---
    fun addSavedUrl(url: String, description: String) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) return

        val finalDesc = description.trim().ifBlank {
            val meta = LeaseScraperEngine.parseUrlMetadata(trimmedUrl)
            if (meta.manufacturer.isNotBlank() || meta.range.isNotBlank()) {
                "${meta.manufacturer.replaceFirstChar { it.uppercase() }} ${meta.range.replaceFirstChar { it.uppercase() }} Lease Deal".trim()
            } else {
                "Vehicle Lease Target"
            }
        }

        viewModelScope.launch {
            repository.insertSavedUrl(
                SavedUrl(
                    url = trimmedUrl,
                    description = finalDesc
                )
            )
            _statusMessage.value = "Added \"$finalDesc\" to Controls list"
        }
    }

    fun updateSavedUrl(savedUrl: SavedUrl, newDescription: String, newUrl: String) {
        viewModelScope.launch {
            repository.updateSavedUrl(
                savedUrl.copy(
                    url = newUrl.trim(),
                    description = newDescription.trim()
                )
            )
            _statusMessage.value = "Updated \"${newDescription.trim()}\""
        }
    }

    fun deleteSavedUrl(savedUrl: SavedUrl) {
        viewModelScope.launch {
            repository.deleteSavedUrl(savedUrl)
            _statusMessage.value = "Removed \"${savedUrl.description}\" from Controls"
        }
    }

    // --- Scraping Actions ---
    fun scrapeDeal(urlToScrape: String = _urlInput.value, isInitialAutoRun: Boolean = false) {
        if (_isScraping.value) return

        viewModelScope.launch {
            _isScraping.value = true
            _scrapingUrl.value = urlToScrape
            _statusMessage.value = "Searching for lowest price on ${urlToScrape.take(30)}..."

            delay(300)
            val result = LeaseScraperEngine.scrapeUrl(urlToScrape)

            repository.deleteDealsBySourceUrl(urlToScrape)

            if (result.isSuccess && result.deals.isNotEmpty()) {
                repository.insertDeals(result.deals)
                _statusMessage.value = result.message
            } else {
                _statusMessage.value = result.message.ifBlank { "No deals found to extract." }
            }

            _isScraping.value = false
            _scrapingUrl.value = null
        }
    }

    /**
     * Scrapes all saved URLs in sequence
     */
    fun scrapeAllSavedUrls() {
        val targets = savedUrls.value
        if (targets.isEmpty() || _isScraping.value) return

        viewModelScope.launch {
            _isScraping.value = true
            var count = 0

            for (target in targets) {
                count++
                _scrapingUrl.value = target.url
                _statusMessage.value = "Scraping ($count/${targets.size}): ${target.description.take(25)}..."

                val result = LeaseScraperEngine.scrapeUrl(target.url)
                repository.deleteDealsBySourceUrl(target.url)

                if (result.deals.isNotEmpty()) {
                    repository.insertDeals(result.deals)
                }

                delay(200)
            }

            _isScraping.value = false
            _scrapingUrl.value = null
            _statusMessage.value = "Finished scraping all ${targets.size} saved vehicles across 6k–12k miles!"
        }
    }

    fun onDealExtractedFromBrowser(deal: LeaseDeal) {
        viewModelScope.launch {
            repository.deleteDealsBySourceUrl(deal.sourceUrl)
            repository.insertDeal(deal)
            _statusMessage.value = "Extracted deal from live browser session!"
            _isScraping.value = false
        }
    }

    fun deleteDeal(deal: LeaseDeal) {
        viewModelScope.launch {
            repository.deleteDeal(deal)
            if (_selectedDeal.value?.id == deal.id) {
                _selectedDeal.value = null
            }
            _statusMessage.value = "Removed ${deal.vehicleName} (${deal.upfrontPaymentsCount} upfront) from list"
        }
    }

    fun clearDealsForUrl(url: String) {
        viewModelScope.launch {
            repository.deleteDealsBySourceUrl(url)
            _statusMessage.value = "Removed scraped deals for this URL"
        }
    }

    fun clearAllDeals() {
        viewModelScope.launch {
            repository.clearAll()
            _selectedDeal.value = null
            _statusMessage.value = "Data cleared! Clean slate ready to scrape."
        }
    }
}

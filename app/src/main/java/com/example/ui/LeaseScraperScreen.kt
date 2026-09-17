package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.LeaseDeal
import com.example.data.scraper.LeaseScraperEngine
import com.example.ui.components.ControlsTabContent
import com.example.ui.components.DealCard
import com.example.ui.components.ScraperControls
import com.example.ui.components.ScraperWebView
import com.example.util.CsvExporter

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun LeaseScraperScreen(
    viewModel: LeaseScraperViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val deals by viewModel.deals.collectAsStateWithLifecycle()
    val savedUrls by viewModel.savedUrls.collectAsStateWithLifecycle()
    val urlInput by viewModel.urlInput.collectAsStateWithLifecycle()
    val isScraping by viewModel.isScraping.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val showBrowserPreview by viewModel.showBrowserPreview.collectAsStateWithLifecycle()
    val activeBrowserUrl by viewModel.activeBrowserUrl.collectAsStateWithLifecycle()
    val selectedUpfrontFilter by viewModel.selectedUpfrontFilter.collectAsStateWithLifecycle()
    val selectedTermFilter by viewModel.selectedTermFilter.collectAsStateWithLifecycle()
    val selectedVehicleFilter by viewModel.selectedVehicleFilter.collectAsStateWithLifecycle()
    val selectedMileageFilter by viewModel.selectedMileageFilter.collectAsStateWithLifecycle()

    val distinctVehicles = remember(deals) {
        deals.map { it.vehicleName }.distinct().sorted()
    }

    val distinctMileages = remember(deals) {
        val extracted = deals.map { deal: LeaseDeal -> LeaseScraperEngine.extractMileageInt(deal) }.filter { it > 0 }.distinct()
        (listOf(6000, 8000, 10000, 12000) + extracted).distinct().sorted()
    }

    val distinctTerms = remember(deals) {
        val extracted = deals.map { deal: LeaseDeal -> LeaseScraperEngine.extractTermMonths(deal) }.filter { it > 0 }.distinct()
        (listOf(24, 36, 48) + extracted).distinct().sorted()
    }

    val distinctUpfronts = remember(deals) {
        val extracted = deals.map { deal: LeaseDeal -> deal.upfrontPaymentsCount }.filter { it > 0 }.distinct()
        (listOf(1, 3, 6, 9, 12) + extracted).distinct().sorted()
    }

    val isAnyFilterActive = selectedUpfrontFilter != null ||
        selectedTermFilter != null ||
        selectedVehicleFilter != null ||
        selectedMileageFilter != null

    val filteredDeals = remember(deals, selectedVehicleFilter, selectedTermFilter, selectedUpfrontFilter, selectedMileageFilter) {
        deals.filter { deal ->
            val matchesVehicle = selectedVehicleFilter == null ||
                deal.vehicleName.equals(selectedVehicleFilter, ignoreCase = true) ||
                deal.vehicleName.contains(selectedVehicleFilter!!, ignoreCase = true)

            val matchesTerm = selectedTermFilter == null ||
                deal.termMonths.contains("$selectedTermFilter", ignoreCase = true)

            val matchesUpfront = selectedUpfrontFilter == null ||
                deal.upfrontPaymentsCount == selectedUpfrontFilter

            val matchesMileage = selectedMileageFilter == null ||
                LeaseScraperEngine.extractMileageInt(deal) == selectedMileageFilter

            matchesVehicle && matchesTerm && matchesUpfront && matchesMileage
        }
    }

    var showClearDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsCar,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Extracting monthly rentals for verification",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (deals.isNotEmpty()) {
                        IconButton(
                            onClick = { CsvExporter.shareCsv(context, filteredDeals.ifEmpty { deals }) },
                            modifier = Modifier.testTag("topbar_share_csv_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share CSV",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        TextButton(
                            onClick = { showClearDialog = true },
                            modifier = Modifier.testTag("topbar_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Clear",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = { showInfoDialog = true },
                        modifier = Modifier.testTag("info_dialog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "About Scraper",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main Navigation TabRow: "Deals" and "Controls"
            TabRow(
                selectedTabIndex = if (currentTab == ScraperTab.DEALS) 0 else 1,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[if (currentTab == ScraperTab.DEALS) 0 else 1]),
                        color = MaterialTheme.colorScheme.primary,
                        height = 3.dp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("main_tabs_row")
            ) {
                Tab(
                    selected = currentTab == ScraperTab.DEALS,
                    onClick = { viewModel.setTab(ScraperTab.DEALS) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Deals (${deals.size})",
                                fontWeight = if (currentTab == ScraperTab.DEALS) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    },
                    modifier = Modifier.testTag("tab_deals")
                )
                Tab(
                    selected = currentTab == ScraperTab.CONTROLS,
                    onClick = { viewModel.setTab(ScraperTab.CONTROLS) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Controls (${savedUrls.size})",
                                fontWeight = if (currentTab == ScraperTab.CONTROLS) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    },
                    modifier = Modifier.testTag("tab_controls")
                )
            }

            // Tab Content Rendering
            when (currentTab) {
                ScraperTab.DEALS -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("deals_lazy_column"),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Scraper Controls Card
                        item {
                            ScraperControls(
                                urlInput = urlInput,
                                onUrlChange = viewModel::onUrlChange,
                                onScrapeClick = { viewModel.scrapeDeal() },
                                onResetToTarget = viewModel::resetToTargetUrl,
                                onToggleBrowserPreview = viewModel::toggleBrowserPreview,
                                onClearData = { showClearDialog = true },
                                onShareCsv = { CsvExporter.shareCsv(context, filteredDeals.ifEmpty { deals }) },
                                hasDeals = deals.isNotEmpty(),
                                isScraping = isScraping,
                                statusMessage = statusMessage
                            )
                        }

                        // Filters Card (Vehicles, Terms, Upfront Payments)
                        if (deals.isNotEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("deals_filter_card"),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                    ) {
                                        // Header Row with count and actions
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.FilterList,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Filter Deals",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.primaryContainer
                                                ) {
                                                    Text(
                                                        text = "${filteredDeals.size} of ${deals.size} shown",
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (isAnyFilterActive) {
                                                    TextButton(
                                                        onClick = { viewModel.resetAllFilters() },
                                                        modifier = Modifier.testTag("reset_all_filters_button")
                                                    ) {
                                                        Text(
                                                            text = "Reset All",
                                                            color = MaterialTheme.colorScheme.error,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }

                                                TextButton(
                                                    onClick = { CsvExporter.shareCsv(context, filteredDeals.ifEmpty { deals }) },
                                                    modifier = Modifier.testTag("section_share_csv_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Share,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Share CSV",
                                                        color = MaterialTheme.colorScheme.primary,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // 1. Vehicles Filter
                                        if (distinctVehicles.isNotEmpty()) {
                                            Text(
                                                text = "Vehicle",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            androidx.compose.foundation.layout.FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                FilterChip(
                                                    selected = selectedVehicleFilter == null,
                                                    onClick = { viewModel.setVehicleFilter(null) },
                                                    label = { Text("All Vehicles (${deals.size})") },
                                                    modifier = Modifier.testTag("filter_chip_vehicle_all")
                                                )

                                                distinctVehicles.forEach { vehicleName ->
                                                    val count = deals.count { it.vehicleName == vehicleName }
                                                    val words = vehicleName.split(" ")
                                                    val shortLabel = if (words.size > 3) words.take(3).joinToString(" ") else vehicleName
                                                    val isSelected = selectedVehicleFilter == vehicleName
                                                    FilterChip(
                                                        selected = isSelected,
                                                        onClick = {
                                                            if (isSelected) {
                                                                viewModel.setVehicleFilter(null)
                                                            } else {
                                                                viewModel.setVehicleFilter(vehicleName)
                                                            }
                                                        },
                                                        label = { Text("$shortLabel ($count)") },
                                                        modifier = Modifier.testTag("filter_chip_vehicle_${shortLabel.take(10).replace(" ", "_")}")
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(10.dp))
                                        }

                                        // 2. Terms Filter (24, 36, 48 Months)
                                        Text(
                                            text = "Contract Term",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        androidx.compose.foundation.layout.FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            FilterChip(
                                                selected = selectedTermFilter == null,
                                                onClick = { viewModel.setTermFilter(null) },
                                                label = { Text("All Terms (${deals.size})") },
                                                modifier = Modifier.testTag("filter_chip_term_all")
                                            )

                                            distinctTerms.forEach { termMonths ->
                                                val count = deals.count { it.termMonths.contains("$termMonths") }
                                                val isSelected = selectedTermFilter == termMonths
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = {
                                                        if (isSelected) {
                                                            viewModel.setTermFilter(null)
                                                        } else {
                                                            viewModel.setTermFilter(termMonths)
                                                        }
                                                    },
                                                    label = { Text("${termMonths}m ($count)") },
                                                    modifier = Modifier.testTag("filter_chip_term_$termMonths")
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // 3. Upfront Payments Filter
                                        Text(
                                            text = "Upfront Payments",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        androidx.compose.foundation.layout.FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            FilterChip(
                                                selected = selectedUpfrontFilter == null,
                                                onClick = { viewModel.setUpfrontFilter(null) },
                                                label = { Text("All Upfront (${deals.size})") },
                                                modifier = Modifier.testTag("filter_chip_upfront_all")
                                            )

                                            distinctUpfronts.forEach { upfrontTier ->
                                                val count = deals.count { it.upfrontPaymentsCount == upfrontTier }
                                                val isSelected = selectedUpfrontFilter == upfrontTier
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = {
                                                        if (isSelected) {
                                                            viewModel.setUpfrontFilter(null)
                                                        } else {
                                                            viewModel.setUpfrontFilter(upfrontTier)
                                                        }
                                                    },
                                                    label = { Text("$upfrontTier Upfront ($count)") },
                                                    modifier = Modifier.testTag("filter_chip_upfront_$upfrontTier")
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // 4. Annual Mileage Filter (6000, 8000, 10000, 12000)
                                        Text(
                                            text = "Annual Mileage",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        androidx.compose.foundation.layout.FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            FilterChip(
                                                selected = selectedMileageFilter == null,
                                                onClick = { viewModel.setMileageFilter(null) },
                                                label = { Text("All Miles (${deals.size})") },
                                                modifier = Modifier.testTag("filter_chip_mileage_all")
                                            )

                                            distinctMileages.forEach { mileage ->
                                                val count = deals.count { LeaseScraperEngine.extractMileageInt(it) == mileage }
                                                val isSelected = selectedMileageFilter == mileage
                                                val labelText = when (mileage) {
                                                    6000 -> "6,000 mi"
                                                    8000 -> "8,000 mi"
                                                    10000 -> "10,000 mi"
                                                    12000 -> "12,000 mi"
                                                    else -> "${String.format(java.util.Locale.UK, "%,d", mileage)} mi"
                                                }
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = {
                                                        if (isSelected) {
                                                            viewModel.setMileageFilter(null)
                                                        } else {
                                                            viewModel.setMileageFilter(mileage)
                                                        }
                                                    },
                                                    label = { Text("$labelText ($count)") },
                                                    modifier = Modifier.testTag("filter_chip_mileage_$mileage")
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // When filters result in zero deals
                        if (deals.isNotEmpty() && filteredDeals.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("no_matching_filters_card"),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "No deals match the selected filter combination",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Try clearing one of the filters or tapping below to view all deals.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        FilledTonalButton(
                                            onClick = { viewModel.resetAllFilters() },
                                            modifier = Modifier.testTag("clear_filter_combination_button")
                                        ) {
                                            Text("Clear Filters")
                                        }
                                    }
                                }
                            }
                        }

                        // Empty State (Clean Slate)
                        if (deals.isEmpty() && !isScraping) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("empty_deals_state"),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DirectionsCar,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "Basic Scraper — Ready to Search",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Tap below to search the target URL and find the lowest monthly leasing price available on the page.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            FilledTonalButton(
                                                onClick = { viewModel.scrapeDeal(LeaseScraperViewModel.TARGET_URL) },
                                                modifier = Modifier.testTag("scrape_default_corsa_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AutoAwesome,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Scrape Target Deal")
                                            }

                                            TextButton(
                                                onClick = { viewModel.setTab(ScraperTab.CONTROLS) },
                                                modifier = Modifier.testTag("go_to_controls_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Tune,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Controls")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Scraped Deals List
                        items(
                            items = filteredDeals,
                            key = { it.id }
                        ) { deal ->
                            DealCard(
                                deal = deal,
                                onDelete = { viewModel.deleteDeal(deal) }
                            )
                        }
                    }
                }

                ScraperTab.CONTROLS -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("controls_lazy_column"),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item {
                            ControlsTabContent(
                                savedUrls = savedUrls,
                                deals = deals,
                                onAddUrl = viewModel::addSavedUrl,
                                onUpdateUrl = viewModel::updateSavedUrl,
                                onDeleteUrl = viewModel::deleteSavedUrl,
                                onOpenWebView = viewModel::openWebViewForUrl,
                                onNavigateToDeals = { viewModel.setTab(ScraperTab.DEALS) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Interactive Browser Bottom Sheet
    if (showBrowserPreview) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setBrowserPreview(false) },
            sheetState = bottomSheetState,
            modifier = Modifier.fillMaxSize()
        ) {
            ScraperWebView(
                url = activeBrowserUrl,
                onClose = { viewModel.setBrowserPreview(false) },
                onDealExtracted = { extractedDeal ->
                    viewModel.onDealExtractedFromBrowser(extractedDeal)
                    viewModel.setBrowserPreview(false)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    // Clear Confirmation Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Deals?") },
            text = { Text("This will remove all saved scraped vehicle deals from your local list.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllDeals()
                        showClearDialog = false
                    },
                    modifier = Modifier.testTag("confirm_clear_deals")
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Info Dialog
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("About Basic Scraper") },
            text = {
                Column {
                    Text(
                        text = "Step 1: Focus on parsing the search results page to find the lowest monthly price.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Scrapes all deals from the provided Vauxhall Corsa search URL")
                    Text("• Identifies the lowest monthly price on the page")
                    Text("• Displays extracted deals in a list for verification")
                    Text("• Export options to CSV for further analysis")
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Got It")
                }
            }
        )
    }
}

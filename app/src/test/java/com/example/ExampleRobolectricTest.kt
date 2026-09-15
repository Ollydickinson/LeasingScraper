package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.LeaseDeal
import com.example.data.scraper.LeaseScraperEngine
import com.example.ui.LeaseScraperViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun read_string_from_context() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Lease Scraper", appName)
    }

    @Test
    fun test_target_url_metadata_parsing() {
        val url = "https://leasing.com/car-leasing/search/?finance=Personal&manufacturer=Omoda&range=5&fuel=Electric&page=4"
        val meta = LeaseScraperEngine.parseSearchUrlMetadata(url)
        assertEquals("Omoda", meta.manufacturer)
        assertEquals("5", meta.range)
        assertEquals("Electric", meta.fuel)
        assertEquals("Personal", meta.financeType)
        assertEquals(4, meta.page)

        val fallbackListings = LeaseScraperEngine.buildSearchListingsFallback(url, meta)
        org.junit.Assert.assertTrue(fallbackListings.isNotEmpty())
        val firstDeal = fallbackListings.first()
        org.junit.Assert.assertTrue(firstDeal.vehicleName.contains("Omoda 5"))
        org.junit.Assert.assertTrue(firstDeal.trimVariant.isNotBlank())
        org.junit.Assert.assertTrue(firstDeal.brokerName.isNotBlank())
        org.junit.Assert.assertTrue(firstDeal.additionalFees.isNotBlank())
        org.junit.Assert.assertTrue(firstDeal.totalPayable.isNotBlank())
    }

    @Test
    fun test_csv_generation() {
        val url = "https://leasing.com/car-leasing/search/?finance=Personal&manufacturer=Omoda&range=5&fuel=Electric&page=4"
        val meta = LeaseScraperEngine.parseSearchUrlMetadata(url)
        val deals = LeaseScraperEngine.buildSearchListingsFallback(url, meta)

        val csv = com.example.util.CsvExporter.generateCsv(deals)
        org.junit.Assert.assertTrue(csv.contains("Vehicle Name,Vehicle Derivative,Leasing Company"))
        org.junit.Assert.assertTrue(csv.contains("Omoda 5"))
        org.junit.Assert.assertTrue(csv.contains("Admin Fee"))
        org.junit.Assert.assertTrue(csv.contains("Total Contract Payable"))
    }

    @Test
    fun test_saved_url_and_status_check() {
        val omodaUrl = "https://leasing.com/car-leasing/search/?finance=Personal&manufacturer=Omoda&range=5&fuel=Electric&page=4"
        val meta = LeaseScraperEngine.parseSearchUrlMetadata(omodaUrl)
        val deals = LeaseScraperEngine.buildSearchListingsFallback(omodaUrl, meta)

        val isOmodaScraped = deals.any {
            LeaseScraperViewModel.normalizeUrl(it.sourceUrl) == LeaseScraperViewModel.normalizeUrl(omodaUrl)
        }
        org.junit.Assert.assertTrue(isOmodaScraped)
    }

    @Test
    fun test_term_variants_generation_and_filtering() {
        val testDeal = LeaseDeal(
            sourceUrl = "https://leasing.com/test",
            vehicleName = "Omoda 5 Estate 150kW Comfort 61kWh 5dr Auto",
            vehicleMake = "Omoda",
            vehicleModel = "5",
            trimVariant = "150kW Comfort 61kWh 5dr Auto",
            monthlyPrice = "£200.00 / mo",
            initialPayment = "£1,800.00 (9 months upfront)",
            termMonths = "36 Months",
            annualMileage = "8,000 miles/yr",
            brokerName = "Select Car Leasing",
            dealRef = "L0104880000003011420",
            vatStatus = "Inc. VAT",
            contractType = "Personal Lease",
            fuelType = "Electric",
            transmission = "Automatic",
            financeType = "Personal",
            additionalFees = "£199.00",
            upfrontPaymentsCount = 9
        )

        // Scraped contract term upfront variants
        val exactTermVariants = LeaseScraperEngine.generateTermAndUpfrontVariants(testDeal)
        assertEquals(5, exactTermVariants.size)
        assertEquals(listOf("36 Months"), exactTermVariants.map { it.termMonths }.distinct())
        assertEquals(listOf(1, 3, 6, 9, 12), exactTermVariants.map { it.upfrontPaymentsCount })

        // Check 1 and 9 upfront rates for the contract term
        val deal1Upfront = exactTermVariants.first { it.upfrontPaymentsCount == 1 }
        assertEquals("£244.44 / mo", deal1Upfront.monthlyPrice)
        val deal9Upfront = exactTermVariants.first { it.upfrontPaymentsCount == 9 }
        assertEquals("£200.00 / mo", deal9Upfront.monthlyPrice)

        // Test explicit multi-term variants when requested
        val multiTermVariants = LeaseScraperEngine.generateTermAndUpfrontVariants(testDeal, terms = listOf(24, 36, 48))
        assertEquals(15, multiTermVariants.size)

        // Check terms
        val terms = multiTermVariants.map { it.termMonths }.distinct().sorted()
        assertEquals(listOf("24 Months", "36 Months", "48 Months"), terms)

        // Filter by Term 36 + Upfront 9
        val filtered36and9 = multiTermVariants.filter { it.termMonths.contains("36") && it.upfrontPaymentsCount == 9 }
        assertEquals(1, filtered36and9.size)
        assertEquals("£200.00 / mo", filtered36and9.first().monthlyPrice)
    }

    @Test
    fun test_next_data_json_extraction() {
        val fakeNextDataJson = """
        {
          "props": {
            "pageProps": {
              "initialState": {
                "search": {
                  "results": [
                    {
                      "manufacturer": "Vauxhall",
                      "model": "Corsa",
                      "derivative": "1.2 Turbo GS 5dr Manual",
                      "monthlyRental": 195.50,
                      "initialRental": 1759.50,
                      "upfrontMonths": 9,
                      "term": 36,
                      "annualMileage": 8000,
                      "advertiser": { "name": "Select Car Leasing" },
                      "dealRef": "L010395000000999123",
                      "processingFee": 199.00
                    }
                  ]
                }
              }
            }
          }
        }
        """.trimIndent()

        val sourceUrl = "https://leasing.com/car-leasing/search/?finance=personal&manufacturer=vauxhall&range=corsa"
        val deals = LeaseScraperEngine.extractDealsFromNextData(fakeNextDataJson, sourceUrl)

        assertEquals(1, deals.size)
        val deal = deals.first()
        assertEquals("Vauxhall", deal.vehicleMake)
        assertEquals("Corsa", deal.vehicleModel)
        assertEquals("£195.50 / mo", deal.monthlyPrice)
        assertEquals("36 Months", deal.termMonths)
        assertEquals("8,000 miles/yr", deal.annualMileage)
        assertEquals("Select Car Leasing", deal.brokerName)
        assertEquals("L010395000000999123", deal.dealRef)
    }

    @Test
    fun test_dynamic_dom_selectors_fallback() {
        val html = """
        <html>
            <body>
                <div class="SearchResultsTile">
                    <h2 class="VehicleName">Vauxhall Corsa 1.2 Design 5dr</h2>
                    <div class="PriceContainer">
                        <span class="MonthlyRental">£184.99</span>
                    </div>
                    <div class="SpecList">
                        <span class="Badge">36 Months</span>
                        <span class="Badge">8,000 miles</span>
                        <span class="Badge">£1,664.91 upfront</span>
                        <span class="Badge">Processing fee £199.00</span>
                    </div>
                    <span class="Advertiser">Lease Cars 4 Less</span>
                </div>
            </body>
        </html>
        """.trimIndent()

        val doc = org.jsoup.Jsoup.parse(html)
        val meta = LeaseScraperEngine.parseSearchUrlMetadata("https://leasing.com/car-leasing/search/?finance=personal&manufacturer=vauxhall&range=corsa")
        val deals = LeaseScraperEngine.extractDealsFromListingsDocument(doc, "https://leasing.com", meta)

        assertEquals(1, deals.size)
        val deal = deals.first()
        assertEquals("£184.99 / mo", deal.monthlyPrice)
        assertEquals("36 Months", deal.termMonths)
        assertEquals("8,000 miles/yr", deal.annualMileage)
        assertEquals("Lease Cars 4 Less", deal.brokerName)
    }

    @Test
    fun test_mileage_variants_generation_and_filtering() {
        val testDeal = LeaseDeal(
            sourceUrl = "https://leasing.com/test",
            vehicleName = "Omoda 5 Estate 150kW Comfort 61kWh 5dr Auto",
            vehicleMake = "Omoda",
            vehicleModel = "5",
            trimVariant = "150kW Comfort 61kWh 5dr Auto",
            monthlyPrice = "£200.00 / mo",
            initialPayment = "£1,800.00 (9 months upfront)",
            termMonths = "36 Months",
            annualMileage = "8,000 miles/yr",
            brokerName = "Select Car Leasing",
            dealRef = "L0104880000003011420",
            vatStatus = "Inc. VAT",
            contractType = "Personal Lease",
            fuelType = "Electric",
            transmission = "Automatic",
            financeType = "Personal",
            additionalFees = "£199.00",
            upfrontPaymentsCount = 9
        )

        // Verify baseline extraction
        assertEquals(8000, LeaseScraperEngine.extractMileageInt(testDeal))
        assertEquals(6000, LeaseScraperEngine.extractMileageInt("6,000 miles/yr"))
        assertEquals(10000, LeaseScraperEngine.extractMileageInt("10,000 miles/yr"))
        assertEquals(12000, LeaseScraperEngine.extractMileageInt("12,000 miles/yr"))

        // Generate variants across all 4 requested mileage profiles: 6000, 8000, 10000 & 12000 miles
        val mileageVariants = LeaseScraperEngine.generateTermAndUpfrontVariants(
            testDeal,
            mileages = listOf(6000, 8000, 10000, 12000)
        )

        // 4 mileages x 5 upfront payments (1, 3, 6, 9, 12) = 20 variants
        assertEquals(20, mileageVariants.size)

        val distinctMileages = mileageVariants.map { it.annualMileage }.distinct()
        assertEquals(
            listOf("6,000 miles/yr", "8,000 miles/yr", "10,000 miles/yr", "12,000 miles/yr"),
            distinctMileages
        )

        // Check rates at 9 months upfront for each mileage profile
        val deal6k = mileageVariants.first { it.annualMileage == "6,000 miles/yr" && it.upfrontPaymentsCount == 9 }
        val deal8k = mileageVariants.first { it.annualMileage == "8,000 miles/yr" && it.upfrontPaymentsCount == 9 }
        val deal10k = mileageVariants.first { it.annualMileage == "10,000 miles/yr" && it.upfrontPaymentsCount == 9 }
        val deal12k = mileageVariants.first { it.annualMileage == "12,000 miles/yr" && it.upfrontPaymentsCount == 9 }

        assertEquals("£192.00 / mo", deal6k.monthlyPrice)
        assertEquals("£200.00 / mo", deal8k.monthlyPrice)
        assertEquals("£210.00 / mo", deal10k.monthlyPrice)
        assertEquals("£220.00 / mo", deal12k.monthlyPrice)

        // Filter by 10,000 miles
        val filtered10k = mileageVariants.filter { LeaseScraperEngine.extractMileageInt(it) == 10000 }
        assertEquals(5, filtered10k.size)
        assertEquals(listOf(1, 3, 6, 9, 12), filtered10k.map { it.upfrontPaymentsCount })
        filtered10k.forEach {
            assertEquals("10,000 miles/yr", it.annualMileage)
        }
    }
}

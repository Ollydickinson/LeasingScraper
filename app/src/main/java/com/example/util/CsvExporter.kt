package com.example.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.LeaseDeal
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {

    private val HEADERS = listOf(
        "Vehicle Name",
        "Vehicle Derivative",
        "Leasing Company",
        "Monthly Price",
        "Initial Payment",
        "Upfront Payments (x)",
        "Term Months",
        "Annual Mileage",
        "Admin Fee",
        "Total Contract Payable",
        "Total Payable Inc Fees",
        "Deal Reference",
        "VAT Status",
        "Contract Type",
        "Fuel Type",
        "Transmission",
        "Source URL",
        "Scraped Date"
    )

    fun generateCsv(deals: List<LeaseDeal>): String {
        val sb = StringBuilder()
        sb.append(HEADERS.joinToString(",") { escapeCsv(it) }).append("\n")

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.UK)

        for (deal in deals) {
            val dateStr = dateFormat.format(Date(deal.scrapedAt))
            val row = listOf(
                deal.vehicleName,
                deal.trimVariant,
                deal.brokerName,
                deal.monthlyPrice,
                deal.initialPayment,
                deal.upfrontPaymentsCount.toString(),
                deal.termMonths,
                deal.annualMileage,
                deal.additionalFees,
                deal.totalPayable,
                deal.totalPayableIncFees,
                deal.dealRef,
                deal.vatStatus,
                deal.contractType,
                deal.fuelType,
                deal.transmission,
                deal.sourceUrl,
                dateStr
            )
            sb.append(row.joinToString(",") { escapeCsv(it) }).append("\n")
        }

        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        return if (needsQuotes) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    fun shareCsv(context: Context, deals: List<LeaseDeal>) {
        if (deals.isEmpty()) {
            Toast.makeText(context, "No lease deals to export. Scrape pricing first!", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val csvDir = File(context.cacheDir, "csv")
            if (!csvDir.exists()) {
                csvDir.mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.UK).format(Date())
            val csvFile = File(csvDir, "lease_deals_$timestamp.csv")

            FileWriter(csvFile).use { writer ->
                writer.write(generateCsv(deals))
            }

            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, csvFile)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Leasing Deals Export (${deals.size} options)")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Attached is the exported vehicle lease pricing data with 1, 3, 6, 9 & 12 upfront payments."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Lease Deals CSV")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Error sharing CSV: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_LONG).show()
        }
    }
}

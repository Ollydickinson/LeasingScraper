package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a scraped vehicle leasing deal.
 */
@Entity(tableName = "lease_deals")
data class LeaseDeal(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceUrl: String,
    val vehicleName: String,
    val vehicleMake: String = "",
    val vehicleModel: String = "",
    val trimVariant: String = "",
    val monthlyPrice: String,
    val initialPayment: String,
    val termMonths: String,
    val annualMileage: String,
    val brokerName: String = "Lease Cars 4 Less",
    val dealRef: String = "",
    val vatStatus: String = "Inc. VAT",
    val contractType: String = "Personal Lease",
    val fuelType: String = "Petrol",
    val transmission: String = "Manual",
    val upfrontPaymentsCount: Int = 9,
    val totalPayable: String = "",
    val financeType: String = "Personal",
    val additionalFees: String = "£199.00",
    val totalPayableIncFees: String = "",
    val scrapedAt: Long = System.currentTimeMillis()
)

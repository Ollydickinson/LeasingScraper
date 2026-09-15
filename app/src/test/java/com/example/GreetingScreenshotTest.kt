package com.example

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.data.model.LeaseDeal
import com.example.ui.components.DealCard
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val sampleDeal = LeaseDeal(
      id = 1,
      sourceUrl = "https://leasing.com/independent-brokers/lease-cars-4-less/vauxhall/corsa/L0103950000002074620",
      vehicleName = "Vauxhall Corsa Hatchback 1.2 Design 5dr",
      vehicleMake = "Vauxhall",
      vehicleModel = "Corsa",
      trimVariant = "1.2 Design 5dr Manual",
      monthlyPrice = "£179.99 / mo",
      initialPayment = "£1,619.91",
      termMonths = "36 Months",
      annualMileage = "8,000 miles/yr",
      brokerName = "Lease Cars 4 Less",
      dealRef = "L0103950000002074620"
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        DealCard(
          deal = sampleDeal,
          onDelete = {},
          modifier = Modifier.padding(16.dp)
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}

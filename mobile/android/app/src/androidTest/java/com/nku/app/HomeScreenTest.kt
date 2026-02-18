package com.nku.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nku.app.screens.HomeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * HomeScreenTest — Compose UI tests for the HomeScreen composable.
 *
 * Tests: step card rendering, localized text, tappable navigation callback,
 * and progress display updates with screening results.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val defaultStrings = LocalizedStrings.UiStrings()

    // ── Rendering Tests ──────────────────────────────────────────

    @Test
    fun homeScreen_showsAllThreeStepCards() {
        composeTestRule.setContent {
            HomeScreen(
                rppgResult = RPPGResult(),
                pallorResult = PallorResult(),
                jaundiceResult = JaundiceResult(),
                edemaResult = EdemaResult(),
                respiratoryResult = RespiratoryResult(),
                strings = defaultStrings,
                selectedLanguage = "en",
                onLanguageChange = {}
            )
        }

        composeTestRule.onNodeWithText("Heart Rate").assertIsDisplayed()
        composeTestRule.onNodeWithText("Anemia Screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Preeclampsia Screen").assertIsDisplayed()
    }

    @Test
    fun homeScreen_showsTapPrompts_whenNoDataCollected() {
        composeTestRule.setContent {
            HomeScreen(
                rppgResult = RPPGResult(),
                pallorResult = PallorResult(),
                jaundiceResult = JaundiceResult(),
                edemaResult = EdemaResult(),
                respiratoryResult = RespiratoryResult(),
                strings = defaultStrings,
                selectedLanguage = "en",
                onLanguageChange = {}
            )
        }

        composeTestRule.onNodeWithText("Tap here to measure heart rate").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tap here to capture eyelid").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tap here to capture face").assertIsDisplayed()
    }

    @Test
    fun homeScreen_showsProgressText_zeroScreenings() {
        composeTestRule.setContent {
            HomeScreen(
                rppgResult = RPPGResult(),
                pallorResult = PallorResult(),
                jaundiceResult = JaundiceResult(),
                edemaResult = EdemaResult(),
                respiratoryResult = RespiratoryResult(),
                strings = defaultStrings,
                selectedLanguage = "en",
                onLanguageChange = {}
            )
        }

        composeTestRule.onNodeWithText("0 of 5 screenings complete").assertIsDisplayed()
        composeTestRule.onNodeWithText("Follow the steps below to screen a patient").assertIsDisplayed()
    }

    // ── Navigation Tests ─────────────────────────────────────────

    @Test
    fun homeScreen_tappingHeartRateCard_navigatesToCardioTab() {
        var navigatedTab = -1
        composeTestRule.setContent {
            HomeScreen(
                rppgResult = RPPGResult(),
                pallorResult = PallorResult(),
                jaundiceResult = JaundiceResult(),
                edemaResult = EdemaResult(),
                respiratoryResult = RespiratoryResult(),
                strings = defaultStrings,
                selectedLanguage = "en",
                onLanguageChange = {},
                onNavigateToTab = { navigatedTab = it }
            )
        }

        composeTestRule.onNodeWithText("Heart Rate").performClick()
        assert(navigatedTab == 1) { "Expected tab 1 (Cardio), got $navigatedTab" }
    }

    @Test
    fun homeScreen_tappingAnemiaCard_navigatesToAnemiaTab() {
        var navigatedTab = -1
        composeTestRule.setContent {
            HomeScreen(
                rppgResult = RPPGResult(),
                pallorResult = PallorResult(),
                jaundiceResult = JaundiceResult(),
                edemaResult = EdemaResult(),
                respiratoryResult = RespiratoryResult(),
                strings = defaultStrings,
                selectedLanguage = "en",
                onLanguageChange = {},
                onNavigateToTab = { navigatedTab = it }
            )
        }

        composeTestRule.onNodeWithText("Anemia Screen").performClick()
        assert(navigatedTab == 2) { "Expected tab 2 (Anemia), got $navigatedTab" }
    }

    @Test
    fun homeScreen_tappingPreECard_navigatesToPreETab() {
        var navigatedTab = -1
        composeTestRule.setContent {
            HomeScreen(
                rppgResult = RPPGResult(),
                pallorResult = PallorResult(),
                jaundiceResult = JaundiceResult(),
                edemaResult = EdemaResult(),
                respiratoryResult = RespiratoryResult(),
                strings = defaultStrings,
                selectedLanguage = "en",
                onLanguageChange = {},
                onNavigateToTab = { navigatedTab = it }
            )
        }

        composeTestRule.onNodeWithText("Preeclampsia Screen").performClick()
        assert(navigatedTab == 4) { "Expected tab 4 (Pre-E), got $navigatedTab" }
    }

    // ── Localization Tests ────────────────────────────────────────

    @Test
    fun homeScreen_displayHausaStrings() {
        val hausaStrings = LocalizedStrings.forLanguage("ha")
        composeTestRule.setContent {
            HomeScreen(
                rppgResult = RPPGResult(),
                pallorResult = PallorResult(),
                jaundiceResult = JaundiceResult(),
                edemaResult = EdemaResult(),
                respiratoryResult = RespiratoryResult(),
                strings = hausaStrings,
                selectedLanguage = "ha",
                onLanguageChange = {}
            )
        }

        composeTestRule.onNodeWithText("Bugun zuciya").assertIsDisplayed()
        composeTestRule.onNodeWithText("Gwajin rashin jini").assertIsDisplayed()
    }

    @Test
    fun homeScreen_displayYorubaStrings() {
        val yorubaStrings = LocalizedStrings.forLanguage("yo")
        composeTestRule.setContent {
            HomeScreen(
                rppgResult = RPPGResult(),
                pallorResult = PallorResult(),
                jaundiceResult = JaundiceResult(),
                edemaResult = EdemaResult(),
                respiratoryResult = RespiratoryResult(),
                strings = yorubaStrings,
                selectedLanguage = "yo",
                onLanguageChange = {}
            )
        }

        composeTestRule.onNodeWithText("Ìlù ọkàn").assertIsDisplayed()
        composeTestRule.onNodeWithText("Àyẹ̀wò ẹ̀jẹ̀").assertIsDisplayed()
    }

}

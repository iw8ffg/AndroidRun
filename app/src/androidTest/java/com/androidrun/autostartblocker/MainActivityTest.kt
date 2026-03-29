package com.androidrun.autostartblocker

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.androidrun.autostartblocker.ui.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun toolbarIsDisplayed() {
        onView(withId(R.id.toolbar))
            .check(matches(isDisplayed()))
    }

    @Test
    fun recyclerViewIsDisplayed() {
        onView(withId(R.id.rv_apps))
            .check(matches(isDisplayed()))
    }

    @Test
    fun searchViewIsDisplayed() {
        onView(withId(R.id.search_view))
            .check(matches(isDisplayed()))
    }

    @Test
    fun systemAppsToggleIsDisplayed() {
        onView(withId(R.id.switch_system_apps))
            .check(matches(isDisplayed()))
    }

    @Test
    fun systemAppsToggleHasCorrectText() {
        onView(withId(R.id.switch_system_apps))
            .check(matches(withText(R.string.show_system_apps)))
    }
}

package com.musync.app

import com.musync.app.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NavigationTest {

    @Test
    fun testBottomNavItemsNotNull() {
        val items = Screen.bottomNavItems
        assertEquals(5, items.size)
        for (item in items) {
            assertNotNull("Bottom nav item must not be null", item)
            assertNotNull("Route must not be null", item.route)
            assertNotNull("Title must not be null", item.title)
        }
    }
}

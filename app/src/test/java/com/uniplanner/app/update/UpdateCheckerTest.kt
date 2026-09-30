package com.uniplanner.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun parsesPublishedVersion() {
        val update = UpdateChecker.parse("""{"versionCode": 12, "versionName": "0.2.12"}""")
        assertEquals(AvailableUpdate(12, "0.2.12"), update)
    }

    @Test
    fun onlyHigherBuildsCount() {
        val update = AvailableUpdate(12, "0.2.12")
        assertTrue(UpdateChecker.isNewer(update, 11))
        assertFalse(UpdateChecker.isNewer(update, 12))
    }
}

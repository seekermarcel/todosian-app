package com.isotjs.todosian.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusChoicesTest {

    @Test
    fun alwaysStartsWithOpenAndDone() {
        assertEquals(listOf(' ', 'x'), AppSettings().statusChoices())
    }

    @Test
    fun addsEachCustomCharacterOnce() {
        assertEquals(
            listOf(' ', 'x', '/', 'w', 's', '-'),
            AppSettings(customStatuses = "/ w s - x ] w").statusChoices(),
        )
    }

    @Test
    fun statusLabelsReadsOneLabelPerLineAndSkipsBlankOnes() {
        assertEquals(
            mapOf('w' to "Wartet", '/' to "In Arbeit"),
            AppSettings(customStatusLabels = "wWartet\n/ In Arbeit \ns\n\n-   ").statusLabels(),
        )
        assertEquals(emptyMap<Char, String>(), AppSettings().statusLabels())
    }
}

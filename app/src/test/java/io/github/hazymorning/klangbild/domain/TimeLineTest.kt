package io.github.hazymorning.klangbild.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TimeLineTest {
    @Test
    fun `times read like a clock`() {
        assertThat(TimeLine.formatClock(7_000)).isEqualTo("0:07")
        assertThat(TimeLine.formatClock(221_400)).isEqualTo("3:41")
        assertThat(TimeLine.formatClock(3_725_000)).isEqualTo("1:02:05")
        assertThat(TimeLine.formatClock(-500)).isEqualTo("0:00")
    }

    @Test
    fun `the separator stays with the word before it`() {
        assertThat(TimeLine.SEPARATOR).isEqualTo(" · ")
    }
}

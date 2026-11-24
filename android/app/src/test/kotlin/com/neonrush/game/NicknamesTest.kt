package com.neonrush.game

import com.neonrush.game.game.Nicknames
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NicknamesTest {

    @Test
    fun acceptsValidNames() {
        assertTrue(Nicknames.isValid("Nova"))
        assertTrue(Nicknames.isValid("neon_runner"))
        assertTrue(Nicknames.isValid("Ace Runner 7"))
        assertTrue(Nicknames.isValid("abc"))
    }

    @Test
    fun rejectsTooShortAndTooLong() {
        assertFalse(Nicknames.isValid("ab"))
        assertFalse(Nicknames.isValid("a".repeat(17)))
    }

    @Test
    fun rejectsSpecialCharacters() {
        assertFalse(Nicknames.isValid("bad<script>"))
        assertFalse(Nicknames.isValid("emoji{name}"))
        assertFalse(Nicknames.isValid("dash-dash"))
    }

    @Test
    fun rejectsBlankAndWhitespaceOnly() {
        assertFalse(Nicknames.isValid("   "))
        assertFalse(Nicknames.isValid(""))
    }
}

package app.winters.octo.theme

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextStyle
import app.winters.octo.design.LocalOctoFonts
import app.winters.octo.design.MusicSpaceBodyFamily
import app.winters.octo.design.MusicSpaceDisplayFamily
import app.winters.octo.design.OctoType
import app.winters.octo.design.bodyType
import app.winters.octo.design.displayType
import app.winters.octo.design.fontsFor
import app.winters.octo.testing.RobolectricComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// The accessors the screens read their type through (displayType for page
// titles, shelf headers and hero names; bodyType for song rows and body
// text) follow the theme provided at the root: under the five themes that
// name no face they hand back the very style they were given, so those
// themes draw exactly as before; under Music Space only the face changes.
@RunWith(RobolectricTestRunner::class)
class ThemeTypeTest {
    @get:Rule
    val ui = RobolectricComposeRule()

    private val styles = listOf(OctoType.display, OctoType.title, OctoType.section, OctoType.body, OctoType.bodySmall, OctoType.caption, OctoType.label)

    @Test
    fun theAccessorsFollowTheThemeProvided() {
        val seen = mutableMapOf<ThemeChoice, List<Pair<TextStyle, TextStyle>>>()
        var theme by mutableStateOf(ThemeChoice.entries.first())
        ui.compose.setContent {
            CompositionLocalProvider(LocalOctoFonts provides fontsFor(theme)) {
                seen[theme] = styles.map { displayType(it) to bodyType(it) }
                BasicText(theme.name)
            }
        }
        for (choice in ThemeChoice.entries) {
            ui.compose.runOnIdle { theme = choice }
            ui.compose.waitForIdle()
        }
        assertEquals(ThemeChoice.entries.toSet(), seen.keys)
        for ((choice, pairs) in seen) {
            pairs.forEachIndexed { i, (display, body) ->
                val style = styles[i]
                if (choice == ThemeChoice.MusicSpace) {
                    assertEquals(style.copy(fontFamily = MusicSpaceDisplayFamily), display)
                    assertEquals(style.copy(fontFamily = MusicSpaceBodyFamily), body)
                } else {
                    assertSame(choice.name, style, display)
                    assertSame(choice.name, style, body)
                }
            }
        }
    }
}

package app.winters.octo.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// The floating bar steps aside over every sign-in page and under the full
// player, and the shell reads that one rule at both of its sites.
class BarShownTest {
    private val signInRoutes = mapOf(
        "SignInRoute" to SignInRoute,
        "EditConnectionRoute" to EditConnectionRoute,
        "ServerFormRoute" to ServerFormRoute(ServerForm.Add),
        "FamilyJoinRoute" to FamilyJoinRoute("octo://join"),
    )

    // Red when a sign-in page leaves the bar up, or another page loses it.
    @Test
    fun theBarHidesOverSignInPagesAndUnderThePlayer() {
        assertTrue(barShown(playerOpen = false, topRoute = SettingsRoute))
        assertTrue(barShown(playerOpen = false, topRoute = null))
        assertFalse(barShown(playerOpen = true, topRoute = SettingsRoute))
        for (route in signInRoutes.values) assertFalse("$route", barShown(playerOpen = false, topRoute = route))
    }

    private val shell = File(System.getProperty("octo.mainSources"), "app/winters/octo/ui/nav/MainShell.kt").readText().replace("\r\n", "\n")

    // Red when a page that draws the sign-in screen is missing from the set
    // (its bar would show over it), a bar site (the bar, the downloads pill,
    // Music Space's player bar) stops reading the rule, or a
    // sign-in page pushed under the full player stays under it.
    @Test
    fun everySignInPageIsInTheSetAndBothSitesReadIt() {
        val drawn = Regex("""entry<(\w+)> \{[^\n]*SignInScreen\(""").findAll(shell).map { it.groupValues[1] }.toSet()
        assertEquals(signInRoutes.keys, drawn)
        // The bar, the downloads pill and Music Space's bottom player bar.
        assertEquals(3, Regex(Regex.escape("visible = barShown(playerOpen, stack.lastOrNull()),")).findAll(shell).count())
        assertFalse(shell.contains("visible = !playerOpen,"))
        assertTrue(shell.contains("LaunchedEffect(top) { if (isSignInRoute(top)) playerOpen = false }"))
    }
}

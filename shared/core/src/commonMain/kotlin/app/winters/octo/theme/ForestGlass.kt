package app.winters.octo.theme

// The Music Space's "Forest Glass" look, as its stylesheet writes it
// (music-space-ui-reference/client-src/theme.css), each CSS value as an
// ARGB int: a CSS alpha a is the byte round(a * 255). These are the source
// values; the Music Space theme (ThemeChoice.MusicSpace's base) is built
// from them. The ones the theme system has no role for yet are kept here
// for the components round and listed in PHASE2-FOUNDATION-NOTES.md.
object ForestGlass {
    // --bg: deep forest green-black.
    const val Bg: Int = 0xFF131A10.toInt()

    // --surface: frosted olive glass, rgba(26, 33, 22, 0.58).
    const val Surface: Int = 0x941A2116.toInt()

    // --surface-alt: a cream pane, rgba(238, 232, 210, 0.13).
    const val SurfaceAlt: Int = 0x21EEE8D2

    // --text: cream ink; --dim: its quiet form.
    const val Text: Int = 0xFFF4EEDD.toInt()
    const val Dim: Int = 0xFFCDC7AE.toInt()

    // --border: the hairline light edge, rgba(244, 238, 221, 0.18).
    const val Border: Int = 0x2EF4EEDD

    // --accent: sage; --accent-soft, rgba(125, 145, 104, 0.28); --accent2:
    // the light sage.
    const val Accent: Int = 0xFF7D9168.toInt()
    const val AccentSoft: Int = 0x477D9168
    const val Accent2: Int = 0xFFAEBFA5.toInt()

    // --hand: the warm tan of handwriting; --note-ink: margin notes.
    const val Hand: Int = 0xFFEECFA3.toInt()
    const val NoteInk: Int = 0xFFF0DFBA.toInt()

    // --amber, and --amber-soft, rgba(238, 194, 122, 0.18).
    const val Amber: Int = 0xFFEEC27A.toInt()
    const val AmberSoft: Int = 0x2EEEC27A

    // --danger.
    const val Danger: Int = 0xFFF0927F.toInt()

    // .player-bar: near-opaque dark olive, rgba(19, 26, 16, 0.94).
    const val PlayerBar: Int = 0xF0131A10.toInt()

    // .glass: rgba(24, 31, 20, 0.55) fill, rgba(244, 238, 221, 0.16) edge,
    // 0 8px 28px rgba(5, 8, 4, 0.35) shadow, blur(16px) saturate(1.25).
    const val GlassFill: Int = 0x8C181F14.toInt()
    const val GlassEdge: Int = 0x29F4EEDD
    const val GlassShadow: Int = 0x59050804
    const val GlassBlur: Float = 16f
    const val GlassSaturation: Float = 1.25f
    const val GlassShadowBlur: Float = 28f
    const val GlassShadowY: Float = 8f

    // --radius, in dp.
    const val Radius: Float = 18f
}

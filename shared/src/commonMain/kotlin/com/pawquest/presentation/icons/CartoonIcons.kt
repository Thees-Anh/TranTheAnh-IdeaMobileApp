package com.pawquest.presentation.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Original layered pastel vectors. Render with Color.Unspecified to keep their colors. */
object CartoonIcons {
    val Fire by lazy {vector("StreakFlame") {
        part("M17 2C19 10 26 11 26 20A10 10 0 0 1 6 20C6 14 10 12 11 7Q14 11 15 13Q19 8 17 2Z",Color(0xFFFB923C))
        part("M16 15Q23 23 18 27Q10 29 11 22Q15 20 16 15Z",Color(0xFFFACC15))
        part("M12 18V19M20 18V19M14 22Q16 24 18 22",null)
    }}
    private val cocoa = Color(0xFF592512)
    private val pink = Color(0xFFF9A8D4)
    private val cream = Color(0xFFFFF0C2)
    private val lavender = Color(0xFFC4B5FD)
    private val white = Color.White
    private fun ImageVector.Builder.part(path: String, fill: Color?, outline: Boolean = true) = addPath(
        pathData = addPathNodes(path), fill = fill?.let(::SolidColor),
        stroke = if (outline) SolidColor(cocoa) else null,
        strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    )
    private fun vector(name: String, draw: ImageVector.Builder.() -> Unit) =
        ImageVector.Builder(name, 32.dp, 32.dp, 32f, 32f).apply(draw).build()

    val Food = vector("CuteFoodBowl") {
        part("M7 17Q5 10 10 10Q11 6 16 9Q21 6 23 11Q28 11 25 18Z", Color(0xFFFDB874))
        part("M4 17H28L25 27Q16 30 7 27Z", pink)
        part("M3 15Q16 12 29 15V18H3Z", cream)
        part("M13 22Q16 19 19 22L18 25H14Z", white)
        part("M9 21H10M22 21H23", null)
        part("M7 6L8 3M25 6L27 4", null)
    }
    val Water = vector("CuteWaterGlass") {
        part("M7 6H25L23 28H9Z", Color(0xFFE0F2FE))
        part("M8 16Q12 13 16 16Q20 19 24 15L23 28H9Z", Color(0xFF93D5F3))
        part("M16 2Q10 9 16 11Q22 9 16 2Z", Color(0xFF71C7ED))
        part("M12 20V21M20 20V21M14 24Q16 26 18 24", null)
    }
    val Heart = vector("CuteHappyHeart") {
        part("M16 28C12 25 3 19 3 11C3 3 12 2 16 8C20 2 29 3 29 11C29 19 20 25 16 28Z", pink)
        part("M8 10Q8 7 11 7", null)
        part("M11 14V16M21 14V16M13 19Q16 22 19 19", null)
        part("M7 18H10M22 18H25", Color(0xFFFB7185), false)
    }
    val Energy = vector("CuteEnergyStar") {
        part("M18 2L5 18H14L12 30L28 12H19Z", Color(0xFFFACC15))
        part("M17 7L11 15", null)
        part("M15 17H16M21 15H22M17 20Q19 22 21 19", null)
        part("M4 7L6 8M27 25L29 26", null)
    }
    val Ball = vector("CutePlayBall") {
        part("M16 3A13 13 0 1 1 16 29A13 13 0 1 1 16 3Z", lavender)
        part("M6 8Q16 13 26 8M3 18Q16 11 29 18M12 4Q8 16 17 29", null)
        part("M18 18L19 21L23 21L20 23L21 26L18 24L15 26L16 22L13 20H17Z", cream)
        part("M18 6L21 7", null)
    }
    val Moon = vector("CuteSleepMoon") {
        part("M22 3C12 6 12 18 26 20C20 32 4 26 4 15C4 7 12 1 22 3Z", cream)
        part("M8 15Q10 18 12 15M13 20Q15 22 17 20", null)
        part("M25 5L26 8L29 9L26 10L25 13L24 10L21 9L24 8Z", lavender)
        part("M8 20H10", pink, false)
    }
    val Home = vector("CuteCatHouse") {
        part("M6 13H26V28H6Z", cream)
        part("M3 14L16 3L29 14L26 17L16 9L6 17Z", pink)
        part("M11 28V19L14 17L16 20L18 17L21 19V28Z", white)
        part("M14 23H14.5M18 23H18.5M15 25Q16 26 17 25", null)
        part("M23 4V8", null)
    }
    val Quest = vector("CuteQuestBook") {
        part("M6 5H26V29H6Q3 29 3 26V8Q3 5 6 5Z", lavender)
        part("M7 5H27V27H7Z", cream)
        part("M12 4H22V9H12Z", pink)
        part("M11 16L14 19L21 12M12 23H22", null)
        part("M4 25H7", null)
    }
    val Shop = vector("CuteShopBag") {
        part("M6 11H26L29 28H3Z", pink)
        part("M10 12V8C10 1 22 1 22 8V12", null)
        part("M12 19L14 16L16 19L18 16L21 19L20 24H13Z", cream)
        part("M15 21H15.5M18 21H18.5", null)
    }
    val Inventory = vector("CuteInventoryBackpack") {
        part("M12 6V3H20V6", null)
        part("M7 8Q16 2 25 8L28 27Q16 32 4 27Z", lavender)
        part("M8 13Q16 17 24 13L23 7Q16 3 9 7Z", pink)
        part("M15 11H18V16H15Z", cream)
        part("M10 20H23V26H10Z", cream)
        part("M13 22H20", null)
    }
    val Profile = vector("CuteCatProfile") {
        part("M16 2A14 14 0 1 1 16 30A14 14 0 1 1 16 2Z", Color(0xFFFFDFB0))
        part("M7 13L8 6L14 10H19L25 6L26 16Q28 26 16 26Q4 26 7 13Z", white)
        part("M8 7L14 10L11 14L7 14Z", Color(0xFFFDB874), false)
        part("M11 17V18M21 17V18M13 21Q16 24 19 21", null)
        part("M8 20H10M22 20H24", pink, false)
    }
}

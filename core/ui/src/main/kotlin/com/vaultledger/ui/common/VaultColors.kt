package com.vaultledger.ui.common

import androidx.compose.ui.graphics.Color

object VaultColors {

    val Teal = Color(0xFF006D77)
    val Amber = Color(0xFFE29578)
    val SoftBlue = Color(0xFF83C5BE)
    val Sage = Color(0xFF6B8F71)
    val Rose = Color(0xFFD4A5A5)
    val Lavender = Color(0xFFB8A9C9)
    val Coral = Color(0xFFE5989B)
    val Slate = Color(0xFF6C757D)

    val all: List<Color> = listOf(
        Teal, Amber, SoftBlue, Sage, Rose, Lavender, Coral, Slate,
    )

    val default: Color = Teal

    fun fromHex(hex: String): Color {
        val colorLong = hex.removePrefix("#").toLong(16)
        return Color(0xFF000000 or colorLong)
    }

    val hexStrings: List<String> = all.map { it.toHexString() }

    fun defaultHex(): String = default.toHexString()

    private fun Color.toHexString(): String {
        val red = (this.red * 255).toInt()
        val green = (this.green * 255).toInt()
        val blue = (this.blue * 255).toInt()
        return "#%02X%02X%02X".format(red, green, blue)
    }
}

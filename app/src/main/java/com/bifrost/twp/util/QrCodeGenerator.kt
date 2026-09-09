package com.bifrost.twp.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * High-performance QR Code generator utility using ZXing core.
 */
object QrCodeGenerator {

    /**
     * Generates a sleek, high-contrast QR Code Bitmap for the given text.
     *
     * @param content The text content (e.g. twp:// link).
     * @param size Width and height in pixels (e.g. 512).
     * @param fgColor Color of the QR modules (default clean white).
     * @param bgColor Color of the quiet zone & background.
     * @return [Bitmap] or null if encoding fails.
     */
    fun generateQrBitmap(
        content: String,
        size: Int = 512,
        fgColor: Int = Color.WHITE,
        bgColor: Int = Color.TRANSPARENT
    ): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1
            )
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)

            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) fgColor else bgColor
                }
            }

            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

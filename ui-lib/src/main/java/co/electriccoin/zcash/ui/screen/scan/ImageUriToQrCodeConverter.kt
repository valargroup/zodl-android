package co.electriccoin.zcash.ui.screen.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import co.electriccoin.zcash.spackle.Twig
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class ImageToQrCodeResult {
    data class SingleCode(
        val text: String
    ) : ImageToQrCodeResult()

    data object MultipleCodes : ImageToQrCodeResult()

    data object NoCode : ImageToQrCodeResult()
}

class ImageUriToQrCodeConverter {
    suspend operator fun invoke(
        context: Context,
        uri: Uri
    ): ImageToQrCodeResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val binaryBitmap =
                    uri
                        .toBitmap(context)
                        .toBinaryBitmap()

                val reader = QRCodeMultiReader()

                val results = reader.decodeMultiple(binaryBitmap)

                when (results?.size) {
                    null, 0 -> ImageToQrCodeResult.NoCode
                    1 -> ImageToQrCodeResult.SingleCode(results[0].text)
                    else -> ImageToQrCodeResult.MultipleCodes
                }
            }.onFailure {
                Twig.error(it) { "Failed to convert Uri to QR code" }
            }.getOrDefault(ImageToQrCodeResult.NoCode)
        }

    private fun Uri.toBitmap(context: Context): Bitmap =
        context.contentResolver
            .openInputStream(this)
            .use {
                BitmapFactory.decodeStream(it)
            }

    private fun Bitmap.toBinaryBitmap(): BinaryBitmap {
        val width = this.width
        val height = this.height
        val pixels = IntArray(width * height)
        this.getPixels(pixels, 0, width, 0, 0, width, height)
        this.recycle()
        val source = RGBLuminanceSource(width, height, pixels)
        return BinaryBitmap(HybridBinarizer(source))
    }
}

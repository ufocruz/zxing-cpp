/*
* Copyright 2021 Axel Waggershauser
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.example.zxingcpp

import android.graphics.*
import androidx.camera.core.ImageProxy


class BarcodeReader {

    // Enumerates barcode formats known to this package.
    // Note that this has to be kept synchronized with native (C++/JNI) side.
    enum class Format {
        NONE, AZTEC, CODABAR, CODE_39, CODE_93, CODE_128, DATA_BAR, DATA_BAR_EXPANDED,
        DATA_MATRIX, EAN_8, EAN_13, ITF, MAXICODE, PDF_417, QR_CODE, UPC_A, UPC_E,
    }

    data class Options(
        val formats: Set<Format> = setOf(),
        val tryHarder: Boolean = false,
        val tryRotate: Boolean = false
    )

    data class Result(
        val format: Format = Format.NONE,
        val text: String? = null,
        val time: String? = null, // for development/debug purposes only
    )

    private lateinit var bitmapBuffer: Bitmap
    var options : Options = Options()

    fun read(image: ImageProxy, invert:Boolean = false): Result? {
        if (!::bitmapBuffer.isInitialized || bitmapBuffer.width != image.width || bitmapBuffer.height != image.height) {
            if (image.format != ImageFormat.YUV_420_888) {
                error("invalid image format")
            }
            bitmapBuffer = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ALPHA_8)
        }
        image.use { bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer) }

        if(invert)
            bitmapBuffer = invertBitmap(bitmapBuffer)
        return read(bitmapBuffer, image.cropRect, image.imageInfo.rotationDegrees)
    }

    private fun invertBitmap(src: Bitmap): Bitmap {
        val height = src.height
        val width = src.width
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        val matrixGrayscale = ColorMatrix()
        matrixGrayscale.setSaturation(0f)
        val matrixInvert = ColorMatrix()
        matrixInvert.set(
            floatArrayOf(
                -1.0f, 0.0f, 0.0f, 0.0f, 255.0f,
                0.0f, -1.0f, 0.0f, 0.0f, 255.0f,
                0.0f, 0.0f, -1.0f, 0.0f, 255.0f,
                0.0f, 0.0f, 0.0f, 1.0f, 0.0f
            )
        )
        matrixInvert.preConcat(matrixGrayscale)
        val filter = ColorMatrixColorFilter(matrixInvert)
        paint.colorFilter = filter

        canvas.drawBitmap(src, 0f, 0f, paint)

        bitmap.reconfigure(width, height, Bitmap.Config.ALPHA_8)
        return bitmap
    }

    fun read(bitmap: Bitmap, cropRect: Rect = Rect(), rotation: Int = 0): Result? {
        return read(bitmap, options, cropRect, rotation)
    }

    fun read(bitmap: Bitmap, options: Options, cropRect: Rect = Rect(), rotation: Int = 0): Result? {
        var result = Result()
        val status = with(options) {
            read(bitmap, cropRect.left, cropRect.top, cropRect.width(), cropRect.height(), rotation,
                    formats.joinToString(), tryHarder, tryRotate, result)
        }
        return try {
            result.copy(format = Format.valueOf(status!!))
        } catch (e: Throwable) {
            if (status == "NotFound") null else throw RuntimeException(status!!)
        }
    }

    // setting the format enum from inside the JNI code is a hassle -> use returned String instead
    private external fun read(
        bitmap: Bitmap, left: Int, top: Int, width: Int, height: Int, rotation: Int,
        formats: String, tryHarder: Boolean, tryRotate: Boolean,
        result: Result,
    ): String?

    init {
        System.loadLibrary("zxing_android")
    }
}

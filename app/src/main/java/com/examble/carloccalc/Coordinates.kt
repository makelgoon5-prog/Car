package com.example.carloccalc

import kotlin.math.abs

object Coordinates {

    enum class Format { DD, DDMM, DDMMSS }

    sealed class Result {
        data class Success(val value: Double) : Result()
        data class Error(val message: String) : Result()
    }

    data class LatLon(val lat: Double, val lon: Double)
    sealed class PairResult {
        data class Success(val value: LatLon) : PairResult()
        data class Error(val message: String) : PairResult()
    }

    fun detectFormat(input: String): Format {
        val v = input.trim().replace(',', '.')
        if (v.isEmpty() || v == "-" || v == "." || v == "-.") return Format.DD
        val withoutSign = v.removePrefix("-")
        val intPart = withoutSign.substringBefore('.')
        return when {
            intPart.length <= 3 -> Format.DD
            intPart.length <= 5 -> Format.DDMM
            else -> Format.DDMMSS
        }
    }

    fun parse(input: String, format: Format, isLatitude: Boolean): Result {
        val raw = input.trim().replace(',', '.').replace(" ", "")
        if (raw.isEmpty() || raw == "-" || raw == "." || raw == "-.") {
            return Result.Error("قيمة غير صالحة")
        }
        if (!raw.matches(Regex("^-?\\d*(\\.\\d*)?$"))) {
            return Result.Error("صيغة رقمية غير صحيحة")
        }
        val negative = raw.startsWith("-")
        val absStr = raw.removePrefix("-")
        if (absStr.isEmpty()) return Result.Error("قيمة غير صالحة")

        val parsed = when (format) {
            Format.DD -> parseDD(absStr)
            Format.DDMM -> parseDDMM(absStr)
            Format.DDMMSS -> parseDDMMSS(absStr)
        }
        return when (parsed) {
            is Result.Error -> parsed
            is Result.Success -> {
                var v = parsed.value
                if (negative) v = -v
                val limit = if (isLatitude) 90.0 else 180.0
                if (v.isNaN() || v.isInfinite()) Result.Error("قيمة غير صالحة")
                else if (v < -limit || v > limit) Result.Error(
                    if (isLatitude) "خط العرض يجب أن يكون بين -90 و 90"
                    else "خط الطول يجب أن يكون بين -180 و 180"
                )
                else Result.Success(v)
            }
        }
    }

    private fun parseDD(s: String): Result {
        val v = s.toDoubleOrNull() ?: return Result.Error("قيمة غير صالحة")
        return Result.Success(v)
    }

    private fun parseDDMM(s: String): Result {
        val intPart = s.substringBefore('.')
        val fracPart = s.substringAfter('.', "")
        if (intPart.length < 3) return Result.Error("صيغة DDMM غير صحيحة")
        val deg = intPart.dropLast(2).toIntOrNull() ?: return Result.Error("درجات غير صالحة")
        val minInt = intPart.takeLast(2).toIntOrNull() ?: return Result.Error("دقائق غير صالحة")
        val frac = if (fracPart.isEmpty()) 0.0
                   else ("0.$fracPart").toDoubleOrNull() ?: return Result.Error("قيمة عشرية غير صالحة")
        val minutes = minInt + frac
        if (minutes >= 60.0) return Result.Error("الدقائق يجب أن تكون أقل من 60")
        return Result.Success(deg + minutes / 60.0)
    }

    private fun parseDDMMSS(s: String): Result {
        val intPart = s.substringBefore('.')
        val fracPart = s.substringAfter('.', "")
        if (intPart.length < 5) return Result.Error("صيغة DDMMSS غير صحيحة")
        val secInt = intPart.takeLast(2).toIntOrNull() ?: return Result.Error("ثواني غير صالحة")
        val minInt = intPart.dropLast(2).takeLast(2).toIntOrNull() ?: return Result.Error("دقائق غير صالحة")
        val deg = intPart.dropLast(4).toIntOrNull() ?: return Result.Error("درجات غير صالحة")
        val frac = if (fracPart.isEmpty()) 0.0
                   else ("0.$fracPart").toDoubleOrNull() ?: return Result.Error("قيمة عشرية غير صالحة")
        val seconds = secInt + frac
        if (seconds >= 60.0) return Result.Error("الثواني يجب أن تكون أقل من 60")
        if (minInt >= 60) return Result.Error("الدقائق يجب أن تكون أقل من 60")
        return Result.Success(deg + minInt / 60.0 + seconds / 3600.0)
    }

    fun parsePairDD(input: String): PairResult {
        val tokens = input
            .trim()
            .split(Regex("[\\s,;\\n\\r\\t]+"))
            .filter { it.isNotEmpty() }

        if (tokens.isEmpty()) return PairResult.Error("PASTE_EMPTY")
        if (tokens.size == 1) return PairResult.Error("PASTE_NEED_TWO")
        if (tokens.size > 2) return PairResult.Error("PASTE_MORE_THAN_TWO")

        val lat = tokens[0].toDoubleOrNull() ?: return PairResult.Error("PASTE_INVALID")
        val lon = tokens[1].toDoubleOrNull() ?: return PairResult.Error("PASTE_INVALID")

        if (lat < -90.0 || lat > 90.0)
            return PairResult.Error("خط العرض يجب أن يكون بين -90 و 90")
        if (lon < -180.0 || lon > 180.0)
            return PairResult.Error("خط الطول يجب أن يكون بين -180 و 180")

        return PairResult.Success(LatLon(lat, lon))
    }

    fun formatDD(v: Double): String = String.format(java.util.Locale.US, "%.6f", v)

    fun formatDDMM(v: Double, isLat: Boolean): String {
        val neg = v < 0
        val a = abs(v)
        var deg = a.toInt()
        var min = (a - deg) * 60.0
        if (min >= 59.99995) { deg += 1; min = 0.0 }
        val degFmt = if (isLat) "%02d" else "%03d"
        return (if (neg) "-" else "") + String.format(java.util.Locale.US, degFmt, deg) +
                String.format(java.util.Locale.US, "%07.4f", min)
    }

    fun formatDDMMSS(v: Double, isLat: Boolean): String {
        val neg = v < 0
        val a = abs(v)
        var deg = a.toInt()
        val minTotal = (a - deg) * 60.0
        var min = minTotal.toInt()
        var sec = (minTotal - min) * 60.0
        if (sec >= 59.995) { sec = 0.0; min += 1 }
        if (min >= 60) { min = 0; deg += 1 }
        val degFmt = if (isLat) "%02d" else "%03d"
        return (if (neg) "-" else "") + String.format(java.util.Locale.US, degFmt, deg) +
                String.format(java.util.Locale.US, "%02d", min) +
                String.format(java.util.Locale.US, "%05.2f", sec)
    }
}

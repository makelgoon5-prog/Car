package com.example.carloccalc

import kotlin.math.*

object Geodesic {
    private const val A = 6378137.0
    private const val F = 1.0 / 298.257223563
    private val B = A * (1 - F)

    data class Result(val lat: Double, val lon: Double)

    fun direct(lat1: Double, lon1: Double, bearing: Double, distanceMeters: Double): Result {
        if (distanceMeters == 0.0) return Result(lat1, lon1)
        val phi1 = Math.toRadians(lat1)
        val lambda1 = Math.toRadians(lon1)
        val alpha1 = Math.toRadians(bearing)
        val s = distanceMeters

        val sinAlpha1 = sin(alpha1)
        val cosAlpha1 = cos(alpha1)

        val tanU1 = (1 - F) * tan(phi1)
        val cosU1 = 1.0 / sqrt(1 + tanU1 * tanU1)
        val sinU1 = tanU1 * cosU1

        val sigma1 = atan2(tanU1, cosAlpha1)
        val sinAlpha = cosU1 * sinAlpha1
        val cosSqAlpha = 1 - sinAlpha * sinAlpha
        val uSq = cosSqAlpha * (A * A - B * B) / (B * B)

        val aCoef = 1 + uSq / 16384.0 * (4096 + uSq * (-768 + uSq * (320 - 175 * uSq)))
        val bCoef = uSq / 1024.0 * (256 + uSq * (-128 + uSq * (74 - 47 * uSq)))

        var sigma = s / (B * aCoef)
        var sinSigma: Double
        var cosSigma: Double
        var cos2SigmaM: Double
        var deltaSigma: Double
        var sigmaPrev: Double
        var iter = 0

        do {
            cos2SigmaM = cos(2 * sigma1 + sigma)
            sinSigma = sin(sigma)
            cosSigma = cos(sigma)
            deltaSigma = bCoef * sinSigma * (cos2SigmaM + bCoef / 4.0 * (
                    cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM) -
                            bCoef / 6.0 * cos2SigmaM * (-3 + 4 * sinSigma * sinSigma) *
                            (-3 + 4 * cos2SigmaM * cos2SigmaM)))
            sigmaPrev = sigma
            sigma = s / (B * aCoef) + deltaSigma
            iter++
        } while (abs(sigma - sigmaPrev) > 1e-12 && iter < 200)

        val tmp = sinU1 * sinSigma - cosU1 * cosSigma * cosAlpha1
        val phi2 = atan2(
            sinU1 * cosSigma + cosU1 * sinSigma * cosAlpha1,
            (1 - F) * sqrt(sinAlpha * sinAlpha + tmp * tmp)
        )
        val lambda = atan2(
            sinSigma * sinAlpha1,
            cosU1 * cosSigma - sinU1 * sinSigma * cosAlpha1
        )
        val C = F / 16.0 * cosSqAlpha * (4 + F * (4 - 3 * cosSqAlpha))
        val L = lambda - (1 - C) * F * sinAlpha * (
                sigma + C * sinSigma * (
                cos2SigmaM + C * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)))

        var lambda2 = lambda1 + L
        lambda2 = ((lambda2 + PI) % (2 * PI) + 2 * PI) % (2 * PI) - PI

        return Result(Math.toDegrees(phi2), Math.toDegrees(lambda2))
    }
}

package com.roadpulse.app.data

/** Decoder for Google-style encoded polylines. Valhalla uses precision 6. */
object Polyline {
    fun decode(encoded: String, precision: Int = 6): List<GeoPoint> {
        var factor = 1.0
        repeat(precision) { factor *= 10.0 }
        val points = ArrayList<GeoPoint>(encoded.length / 4)
        var index = 0
        var lat = 0L
        var lon = 0L
        while (index < encoded.length) {
            var result = 0L
            var shift = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f).toLong() shl shift)
                shift += 5
            } while (b >= 0x20)
            lat += if (result and 1L != 0L) (result shr 1).inv() else result shr 1

            result = 0L
            shift = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f).toLong() shl shift)
                shift += 5
            } while (b >= 0x20)
            lon += if (result and 1L != 0L) (result shr 1).inv() else result shr 1

            points.add(GeoPoint(lat / factor, lon / factor))
        }
        return points
    }
}

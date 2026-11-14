package com.roadpulse.app.data

/** Encoder/decoder for Google-style encoded polylines. Valhalla uses precision 6. */
object Polyline {
    fun encode(points: List<GeoPoint>, precision: Int = 6): String {
        var factor = 1.0
        repeat(precision) { factor *= 10.0 }
        val out = StringBuilder()
        var prevLat = 0L
        var prevLon = 0L
        for (p in points) {
            val lat = Math.round(p.lat * factor)
            val lon = Math.round(p.lon * factor)
            encodeValue(lat - prevLat, out)
            encodeValue(lon - prevLon, out)
            prevLat = lat
            prevLon = lon
        }
        return out.toString()
    }

    private fun encodeValue(value: Long, out: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            out.append(((0x20 or (v and 0x1f).toInt()) + 63).toChar())
            v = v shr 5
        }
        out.append((v.toInt() + 63).toChar())
    }

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

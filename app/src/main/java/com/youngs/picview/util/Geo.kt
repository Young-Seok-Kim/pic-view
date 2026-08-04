package com.youngs.picview.util

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 위경도 좌표. TourAPI 의 mapx(경도)/mapy(위도) 문자열에서 만듭니다. */
data class LatLng(val lat: Double, val lng: Double) {
    companion object {
        /** 한반도 범위를 벗어나거나 값이 없으면 null. */
        fun parseOrNull(mapy: String?, mapx: String?): LatLng? {
            val lat = mapy?.toDoubleOrNull() ?: return null
            val lng = mapx?.toDoubleOrNull() ?: return null
            if (lat !in 33.0..39.0 || lng !in 124.0..132.0) return null
            return LatLng(lat, lng)
        }
    }
}

private const val EARTH_RADIUS_KM = 6371.0

/** 두 지점 사이 대권 거리(km). */
fun LatLng.distanceKmTo(other: LatLng): Double {
    val dLat = Math.toRadians(other.lat - lat)
    val dLng = Math.toRadians(other.lng - lng)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat)) * cos(Math.toRadians(other.lat)) *
            sin(dLng / 2) * sin(dLng / 2)
    return 2 * EARTH_RADIUS_KM * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

/**
 * 직선거리를 실제 이동 거리(km)로 환산합니다.
 *
 * 카카오 모빌리티 길찾기를 붙이기 전까지 쓰는 근사치입니다.
 *
 * 화면에 보여 주는 거리와 소요 시간이 **같은 값에서 나와야** 합니다.
 * 예전에는 거리는 직선(16.7km)으로 보여 주면서 시간은 우회 반영(22.5km 기준
 * 35분)으로 계산해서, 두 숫자의 기준이 서로 달랐습니다.
 */
fun roadDistanceKm(straightLineKm: Double): Double = straightLineKm * ROAD_DETOUR_FACTOR

/**
 * 이동 소요 시간 추정(분).
 *
 * @param straightLineKm 직선거리. 내부에서 [roadDistanceKm] 로 환산해 계산합니다.
 */
fun estimateTravelMinutes(straightLineKm: Double, mode: TravelMode): Int {
    val minutes = roadDistanceKm(straightLineKm) / mode.averageKmh * 60.0
    // 주차·도보 접근 같은 고정 비용
    return (minutes + mode.fixedOverheadMinutes).toInt().coerceAtLeast(mode.minMinutes)
}

/**
 * 직선거리 대비 실제 도로 거리 배수.
 * 정읍 시내·근교 기준으로 잡은 값이라 시외로 나가면 오차가 커집니다.
 */
const val ROAD_DETOUR_FACTOR = 1.35

enum class TravelMode(
    val label: String,
    val averageKmh: Double,
    val fixedOverheadMinutes: Int,
    val minMinutes: Int
) {
    CAR("자가용", 45.0, 5, 5),
    WALK("도보", 4.5, 0, 3),
    TRANSIT("대중교통", 22.0, 12, 15)
}

/**
 * [from] 에서 [to] 를 바라보는 방위각(0~360, 북쪽 0도).
 * 스팟이 서향인지 동향인지 판단할 때 씁니다.
 */
fun LatLng.bearingTo(to: LatLng): Double {
    val lat1 = Math.toRadians(lat)
    val lat2 = Math.toRadians(to.lat)
    val dLng = Math.toRadians(to.lng - lng)
    val y = sin(dLng) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}

package com.youngs.picview.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.youngs.picview.R

/**
 * 앱 전체가 함께 쓰는 "내 위치".
 *
 * 거리·이동 시간·포토스코어의 접근성 항목은 모두 이 한 점에서 잽니다.
 * 예전에는 탐색 목록·코스 장소 고르기는 시내 고정 좌표, 지도 시트만
 * 내 위치를 써서 같은 장소가 화면마다 다른 거리로 나왔습니다.
 *
 * 위치를 모르는 동안(권한 없음·아직 측위 전)에는 [CITY_CENTER] 로 물러나고,
 * 문구도 "시내에서" 로 기준을 밝힙니다. 값이 들어오면 [latLng] 를 보고
 * 있는 화면이 스스로 다시 그립니다.
 */
object UserLocation {

    /** 위치를 모를 때의 기준점 — 정읍 시내(시청 인근). */
    val CITY_CENTER = LatLng(35.5699, 126.8559)

    private val _latLng = MutableLiveData<LatLng?>(null)

    /** 마지막으로 알아낸 내 위치. 모르면 null. */
    val latLng: LiveData<LatLng?> get() = _latLng

    /** 지금 알고 있는 내 위치. 화면을 그릴 때 바로 읽는 용도입니다. */
    fun current(): LatLng? = _latLng.value

    /** 거리를 잴 출발점. 내 위치를 알면 그곳, 모르면 시내. */
    fun origin(): LatLng = current() ?: CITY_CENTER

    /** 지금 거리 기준이 내 위치인지(true) 시내인지(false). */
    fun isFromMe(): Boolean = current() != null

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** 다른 곳(지도의 위치 소스 등)에서 받은 좌표를 나눠 씁니다. */
    fun update(lat: Double, lng: Double) {
        val next = LatLng(lat, lng)
        if (_latLng.value != next) _latLng.postValue(next)
    }

    /**
     * 위치를 새로 읽습니다. 권한이 없으면 아무 일도 하지 않습니다.
     *
     * 마지막 위치를 먼저 받아 화면을 빨리 채우고, 이어서 현재 위치를
     * 한 번 더 물어 갱신합니다. 한 번 읽는 것이라 고정밀(GPS)로 묻습니다 —
     * 균형 모드는 네트워크 측위가 없는 기기(무무 등)에서 아무 값도 못 받습니다.
     * 측위가 아예 안 되면 둘 다 null 이 와서 시내 기준 그대로 남습니다.
     */
    @SuppressLint("MissingPermission")
    fun refresh(context: Context) {
        if (!hasPermission(context)) return
        val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)
        client.lastLocation.addOnSuccessListener { loc ->
            loc?.let { update(it.latitude, it.longitude) }
        }
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc -> loc?.let { update(it.latitude, it.longitude) } }
    }

    /**
     * "내 위치에서 차로 8분" / "시내에서 차로 1시간 20분".
     * 카드·장소 고르기·지도 시트가 모두 이 한 문장을 씁니다.
     */
    fun travelLine(context: Context, to: LatLng, mode: TravelMode = TravelMode.CAR): String {
        val minutes = estimateTravelMinutes(origin().distanceKmTo(to), mode)
        val duration = formatTravelMinutes(context, minutes)
        return context.getString(
            if (isFromMe()) R.string.spot_travel_from_me else R.string.spot_travel_from_city,
            duration
        )
    }

    /** 분을 "8분" 또는 "1시간 20분" 으로. 시외에서 보면 몇 백 분이 나와 시간으로 접습니다. */
    fun formatTravelMinutes(context: Context, minutes: Int): String {
        if (minutes < 60) return context.getString(R.string.travel_minutes, minutes)
        val h = minutes / 60
        val m = minutes % 60
        return if (m == 0) context.getString(R.string.travel_hours, h)
        else context.getString(R.string.travel_hours_minutes, h, m)
    }
}

package com.hiability.lifekit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import java.util.Locale

class MainActivity : ComponentActivity(), LocationListener, SensorEventListener {
    private val bg = Color.rgb(7, 17, 31)
    private val card = Color.rgb(12, 28, 46)
    private val border = Color.rgb(31, 54, 78)
    private val blue = Color.rgb(87, 183, 255)
    private val green = Color.rgb(85, 223, 154)
    private val red = Color.rgb(255, 107, 122)
    private val muted = Color.rgb(142, 164, 188)
    private val white = Color.rgb(237, 245, 255)

    private lateinit var locationManager: LocationManager
    private lateinit var sensorManager: SensorManager

    private var speedText: TextView? = null
    private var avgText: TextView? = null
    private var maxText: TextView? = null
    private var accuracyText: TextView? = null
    private var distanceText: TextView? = null
    private var timeText: TextView? = null
    private var coordsText: TextView? = null
    private var compassText: TextView? = null
    private var statusText: TextView? = null

    private var lastLocation: Location? = null
    private var maxSpeed = 0f
    private var speedSum = 0.0
    private var speedSamples = 0
    private var totalMeters = 0.0
    private var tracking = false
    private var startTime = 0L
    private var lastCompass = 0f
    private var timerRunnable: Runnable? = null

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val fineGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
            if (fineGranted) {
                startGps()
            } else {
                statusText?.text = "● GPS 권한 필요"
                statusText?.setTextColor(red)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        setContentView(buildRoot())

        requestGps()
        registerSensors()
    }

    private fun buildRoot(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(12))
            setBackgroundColor(bg)
        }

        content.addView(header(), lp(match(), wrap(), 0f))
        content.addView(speedCard(), lp(match(), 0, 1f).also { it.topMargin = dp(10) })
        content.addView(metrics(), lp(match(), wrap(), 0f).also { it.topMargin = dp(10) })
        content.addView(infoCard(), lp(match(), wrap(), 0f).also { it.topMargin = dp(10) })
        content.addView(actionBar(), lp(match(), wrap(), 0f).also { it.topMargin = dp(10) })

        return ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
            addView(content)
        }
    }

    private fun header(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        row.addView(TextView(this).apply {
            text = "LIFEKIT"
            textSize = 22f
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
        }, lp(0, wrap(), 1f))

        statusText = TextView(this).apply {
            text = "● GPS 준비"
            textSize = 12f
            setTextColor(muted)
        }
        row.addView(statusText, lp(wrap(), wrap(), 0f))
        return row
    }

    private fun speedCard(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(22), dp(18), dp(22))
        }
        rounded(box)

        box.addView(TextView(this).apply {
            text = "GPS SPEED"
            textSize = 12f
            setTextColor(muted)
        })

        speedText = TextView(this).apply {
            text = "0.0"
            textSize = 78f
            setTextColor(blue)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        box.addView(speedText, lp(match(), dp(110), 0f).also { it.topMargin = dp(4) })

        box.addView(TextView(this).apply {
            text = "km/h"
            textSize = 17f
            setTextColor(white)
        })

        coordsText = TextView(this).apply {
            text = "위치 대기 중…"
            textSize = 11f
            setTextColor(muted)
            gravity = Gravity.CENTER
        }
        box.addView(coordsText, lp(match(), wrap(), 0f).also { it.topMargin = dp(8) })
        return box
    }

    private fun metrics(): View {
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(metric("평균 속도") { avgText = it }, cellLp().also { it.marginEnd = dp(5) })
        row1.addView(metric("최고 속도") { maxText = it }, cellLp().also { it.marginStart = dp(5) })
        outer.addView(row1)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(metric("GPS 정확도") { accuracyText = it }, cellLp().also { it.marginEnd = dp(5); it.topMargin = dp(10) })
        row2.addView(metric("이동 거리") { distanceText = it }, cellLp().also { it.marginStart = dp(5); it.topMargin = dp(10) })
        outer.addView(row2)

        return outer
    }

    private fun metric(label: String, save: (TextView) -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(11), dp(12), dp(11))
        }
        rounded(box)

        box.addView(TextView(this).apply {
            text = label
            textSize = 10f
            setTextColor(muted)
        })

        val value = TextView(this).apply {
            text = "—"
            textSize = 16f
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
        }
        box.addView(value, lp(match(), wrap(), 0f).also { it.topMargin = dp(4) })
        save(value)
        return box
    }

    private fun infoCard(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
        }
        rounded(box)

        box.addView(TextView(this).apply {
            text = "현재 방향"
            textSize = 11f
            setTextColor(muted)
        })
        compassText = TextView(this).apply {
            text = "방위각 0° · 북"
            textSize = 18f
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
        }
        box.addView(compassText, lp(match(), wrap(), 0f).also { it.topMargin = dp(4) })

        box.addView(TextView(this).apply {
            text = "GPS는 실외에서 더 안정적인 값을 제공합니다."
            textSize = 10f
            setTextColor(muted)
        }, lp(match(), wrap(), 0f).also { it.topMargin = dp(8) })
        return box
    }

    private fun actionBar(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        row.addView(actionButton("초기화") { resetStats() }, cellLp().also { it.marginEnd = dp(5) })
        row.addView(actionButton("GPS 다시 연결") { requestGps() }, cellLp().also { it.marginStart = dp(5) })
        return row
    }

    private fun actionButton(label: String, click: () -> Unit): View {
        return TextView(this).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(white)
            setPadding(dp(8), dp(13), dp(8), dp(13))
            rounded(this)
            setOnClickListener { click() }
        }
    }

    private fun requestGps() {
        val fine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }
        startGps()
    }

    private fun startGps() {
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            statusText?.text = "● GPS 켜주세요"
            statusText?.setTextColor(red)
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        try {
            locationManager.removeUpdates(this)
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                500L,
                0.5f,
                this
            )
            statusText?.text = "● GPS 연결됨"
            statusText?.setTextColor(green)
        } catch (_: SecurityException) {
            statusText?.text = "● GPS 권한 필요"
            statusText?.setTextColor(red)
        }
    }

    private fun registerSensors() {
        val rotation = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotation != null) {
            sensorManager.registerListener(this, rotation, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onLocationChanged(location: Location) {
        val kmh = if (location.hasSpeed() && location.speed.isFinite()) {
            (location.speed * 3.6f).coerceAtLeast(0f)
        } else {
            0f
        }

        speedText?.text = String.format(Locale.US, "%.1f", kmh)
        maxSpeed = maxOf(maxSpeed, kmh)
        speedSum += kmh
        speedSamples++
        avgText?.text = String.format(Locale.US, "%.1f km/h", speedSum / speedSamples.toDouble())
        maxText?.text = String.format(Locale.US, "%.1f km/h", maxSpeed)
        accuracyText?.text = String.format(Locale.US, "±%.1f m", location.accuracy)
        coordsText?.text = String.format(Locale.US, "%.6f, %.6f", location.latitude, location.longitude)

        lastLocation?.let { previous ->
            val meters = previous.distanceTo(location).toDouble()
            if (meters in 0.0..500.0) totalMeters += meters
        }
        lastLocation = Location(location)
        distanceText?.text = if (totalMeters >= 1000.0) {
            String.format(Locale.US, "%.2f km", totalMeters / 1000.0)
        } else {
            String.format(Locale.US, "%.0f m", totalMeters)
        }

        if (!tracking) {
            tracking = true
            startTime = SystemClock.elapsedRealtime()
            startTimer()
        }
    }

    private fun startTimer() {
        timerRunnable?.let { window.decorView.removeCallbacks(it) }
        val timer = object : Runnable {
            override fun run() {
                if (!tracking) return
                val elapsed = (SystemClock.elapsedRealtime() - startTime) / 1000L
                val h = elapsed / 3600
                val m = (elapsed % 3600) / 60
                val s = elapsed % 60
                timeText?.text = String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
                window.decorView.postDelayed(this, 1000L)
            }
        }
        timerRunnable = timer
        window.decorView.post(timer)
    }

    private fun resetStats() {
        lastLocation = null
        maxSpeed = 0f
        speedSum = 0.0
        speedSamples = 0
        totalMeters = 0.0
        tracking = false
        startTime = 0L
        timerRunnable?.let { window.decorView.removeCallbacks(it) }
        timerRunnable = null

        speedText?.text = "0.0"
        avgText?.text = "—"
        maxText?.text = "—"
        accuracyText?.text = "—"
        distanceText?.text = "0 m"
        timeText?.text = "00:00:00"
        coordsText?.text = "위치 대기 중…"
        Toast.makeText(this, "측정값을 초기화했습니다", Toast.LENGTH_SHORT).show()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)

        var degrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
        if (degrees < 0f) degrees += 360f
        lastCompass = degrees

        val direction = when {
            degrees < 22.5f || degrees >= 337.5f -> "북"
            degrees < 67.5f -> "북동"
            degrees < 112.5f -> "동"
            degrees < 157.5f -> "남동"
            degrees < 202.5f -> "남"
            degrees < 247.5f -> "남서"
            degrees < 292.5f -> "서"
            else -> "북서"
        }
        compassText?.text = "방위각 ${degrees.toInt()}° · $direction"
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) {
            statusText?.text = "● GPS 꺼짐"
            statusText?.setTextColor(red)
        }
    }

    override fun onProviderEnabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) startGps()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.removeUpdates(this)
        }
    }

    override fun onResume() {
        super.onResume()
        registerSensors()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startGps()
        }
    }

    private fun rounded(view: View) {
        view.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(card)
            setStroke(dp(1), border)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun match() = LinearLayout.LayoutParams.MATCH_PARENT
    private fun wrap() = LinearLayout.LayoutParams.WRAP_CONTENT

    private fun lp(width: Int, height: Int, weight: Float) =
        LinearLayout.LayoutParams(width, height, weight)

    private fun cellLp() =
        LinearLayout.LayoutParams(0, dp(64), 1f)
}

package com.hiability.lifekit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
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
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import java.util.Locale
import kotlin.math.abs

class MainActivity : ComponentActivity(), LocationListener, SensorEventListener {
    private val bg = Color.rgb(7, 17, 31)
    private val card = Color.rgb(12, 28, 46)
    private val line = Color.rgb(31, 54, 78)
    private val blue = Color.rgb(87, 183, 255)
    private val green = Color.rgb(85, 223, 154)
    private val muted = Color.rgb(142, 164, 188)
    private val white = Color.rgb(237, 245, 255)

    private lateinit var locationManager: LocationManager
    private lateinit var sensorManager: SensorManager
    private var speedText: TextView? = null
    private var avgText: TextView? = null
    private var maxText: TextView? = null
    private var accuracyText: TextView? = null
    private var coordText: TextView? = null
    private var compassText: TextView? = null
    private var statusText: TextView? = null
    private var timerText: TextView? = null
    private var startTime = 0L
    private var totalMeters = 0.0
    private var maxSpeed = 0f
    private var speedSum = 0.0
    private var speedSamples = 0
    private var lastLocation: Location? = null
    private var tracking = false
    private var lastCompass = 0f
    private var timerView: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        setContentView(buildRoot())
        requestGps()
        setupSensors()
    }

    private fun buildRoot(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(16), dp(14), dp(16), dp(10))
        }
        root.addView(header())
        root.addView(speedCard(), lp(match(), 0, 1f))
        root.addView(metrics(), lp(match(), wrap(), 0f).also { it.topMargin = dp(10) })
        root.addView(bottomNav(), lp(match(), wrap(), 0f).also { it.topMargin = dp(10) })
        return root
    }

    private fun header(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(this@MainActivity).apply {
            text = "LIFEKIT"
            textSize = 20f
            setTextColor(white)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, lp(0, wrap(), 1f))
        statusText = TextView(this@MainActivity).apply {
            text = "● GPS 준비"
            textSize = 12f
            setTextColor(muted)
        }
        addView(statusText, lp(wrap(), wrap(), 0f))
    }

    private fun speedCard(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(22), dp(18), dp(22))
            setBackgroundColor(card)
        }
        val caption = TextView(this).apply { text = "GPS SPEED"; textSize = 12f; setTextColor(muted) }
        box.addView(caption)
        speedText = TextView(this).apply {
            text = "0"
            textSize = 82f
            setTextColor(blue)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        box.addView(speedText, lp(match(), dp(120), 0f))
        box.addView(TextView(this).apply {
            text = "km/h"
            textSize = 17f
            setTextColor(white)
        })
        coordText = TextView(this).apply {
            text = "위치 대기 중…"
            textSize = 11f
            setTextColor(muted)
            gravity = Gravity.CENTER
        }
        box.addView(coordText, lp(match(), wrap(), 0f).also { it.topMargin = dp(8) })
        return rounded(box)
    }

    private fun metrics(): View {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(metric("평균 속도") { avgText = it }, lp(0, dp(86), 1f).also { it.marginEnd = dp(5) })
        row1.addView(metric("최고 속도") { maxText = it }, lp(0, dp(86), 1f).also { it.marginStart = dp(5) })
        wrap.addView(row1)
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(metric("GPS 정확도") { accuracyText = it }, lp(0, dp(86), 1f).also { it.marginEnd = dp(5); it.topMargin = dp(10) })
        row2.addView(metric("이동 시간") { timerText = it }, lp(0, dp(86), 1f).also { it.marginStart = dp(5); it.topMargin = dp(10) })
        wrap.addView(row2)
        return wrap
    }

    private fun metric(label: String, save: (TextView) -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(card)
        }
        box.addView(TextView(this).apply { text = label; textSize = 10f; setTextColor(muted) })
        val v = TextView(this).apply { text = "—"; textSize = 17f; setTextColor(white); typeface = android.graphics.Typeface.DEFAULT_BOLD }
        box.addView(v, lp(match(), wrap(), 1f).also { it.topMargin = dp(4) })
        save(v)
        return rounded(box)
    }

    private fun bottomNav(): View {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setBackgroundColor(card)
        }
        nav.addView(navButton("속도계", true) { showHome() }, lp(0, dp(54), 1f))
        nav.addView(navButton("나침반", false) { showCompass() }, lp(0, dp(54), 1f))
        nav.addView(navButton("도구", false) { showTools() }, lp(0, dp(54), 1f))
        return rounded(nav)
    }

    private fun navButton(text: String, active: Boolean, click: () -> Unit) = TextView(this).apply {
        this.text = text
        textSize = 12f
        gravity = Gravity.CENTER
        setTextColor(if (active) blue else muted)
        setOnClickListener { click() }
    }

    private fun showHome() {
        toast("속도계 화면")
    }

    private fun showCompass() {
        toast("방위각 ${lastCompass.toInt()}°")
    }

    private fun showTools() {
        toast("생활 도구: 스톱워치 · 거리 · 수평계 준비")
    }

    private fun requestGps() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 100)
            return
        }
        startGps()
    }

    private fun startGps() {
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0.5f, this)
            statusText?.text = "● GPS 연결됨"
            statusText?.setTextColor(green)
        } catch (_: SecurityException) {
            statusText?.text = "● GPS 권한 필요"
        }
    }

    private fun setupSensors() {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor != null) sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startGps()
        else statusText?.text = "● GPS 권한 거부"
    }

    override fun onLocationChanged(location: Location) {
        val kmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
        speedText?.text = String.format(Locale.US, "%.1f", kmh)
        maxSpeed = maxOf(maxSpeed, kmh)
        speedSum += kmh
        speedSamples++
        avgText?.text = String.format(Locale.US, "%.1f km/h", speedSum / speedSamples)
        maxText?.text = String.format(Locale.US, "%.1f km/h", maxSpeed)
        accuracyText?.text = String.format(Locale.US, "±%.1f m", location.accuracy)
        coordText?.text = String.format(Locale.US, "%.6f, %.6f", location.latitude, location.longitude)
        lastLocation?.let { prev ->
            val d = prev.distanceTo(location)
            if (d >= 0f && d < 500f) totalMeters += d
        }
        lastLocation = Location(location)
        if (!tracking) {
            tracking = true
            startTime = SystemClock.elapsedRealtime()
            startTimer()
        }
    }

    private fun startTimer() {
        val r = object : Runnable {
            override fun run() {
                if (tracking) {
                    val s = (SystemClock.elapsedRealtime() - startTime) / 1000
                    val h = s / 3600
                    val m = (s % 3600) / 60
                    val sec = s % 60
                    timerText?.text = String.format(Locale.US, "%02d:%02d:%02d", h, m, sec)
                    timerView = this
                    window.decorView.postDelayed(this, 1000)
                }
            }
        }
        timerView = r
        window.decorView.post(r)
    }

    override fun onProviderDisabled(provider: String) { statusText?.text = "● GPS가 꺼져 있음"; statusText?.setTextColor(Color.rgb(255, 107, 122)) }
    override fun onProviderEnabled(provider: String) { startGps() }
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        val rotation = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotation, orientation)
        var deg = Math.toDegrees(orientation[0].toDouble()).toFloat()
        if (deg < 0) deg += 360f
        lastCompass = deg
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onResume() {
        super.onResume()
        setupSensors()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) startGps()
    }

    private fun rounded(view: View): View {
        val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(card)
            setStroke(dp(1), line)
        }
        view.background = bgDrawable
        return view
    }

    private fun toast(text: String) = android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show()
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun match() = LinearLayout.LayoutParams.MATCH_PARENT
    private fun wrap() = LinearLayout.LayoutParams.WRAP_CONTENT
    private fun lp(w: Int, h: Int, weight: Float) = LinearLayout.LayoutParams(w, h, weight)
}

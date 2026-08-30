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
    private var darkMode = false
    private var selectedTab = 0

    private lateinit var locationManager: LocationManager
    private lateinit var sensorManager: SensorManager

    private var root: LinearLayout? = null
    private var contentContainer: LinearLayout? = null
    private var navContainer: LinearLayout? = null

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

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
            if (fine) {
                startGps()
            } else {
                statusText?.text = "● GPS 권한 필요"
                statusText?.setTextColor(red())
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        renderApp()
        requestGps()
        registerSensors()
    }

    private fun renderApp() {
        val pageBg = bg()
        window.statusBarColor = pageBg
        window.navigationBarColor = pageBg

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageBg)
        }

        root?.addView(header(), lp(match(), wrap(), 0f))

        val scroll = ScrollView(this).apply {
            setBackgroundColor(pageBg)
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        contentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(pageBg)
        }
        scroll.addView(contentContainer)
        root?.addView(scroll, lp(match(), 0, 1f))

        navContainer = bottomNav()
        root?.addView(navContainer, lp(match(), wrap(), 0f))

        setContentView(root)
        renderSelectedTab()
    }

    private fun header(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(surface())
        }

        val brandIcon = TextView(this).apply {
            text = "✦"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = gradientDrawable(intArrayOf(Color.rgb(99, 102, 241), Color.rgb(139, 92, 246)), 18)
        }
        row.addView(brandIcon, lp(dp(42), dp(42), 0f))

        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        titleBlock.addView(TextView(this).apply {
            text = "LifeKit"
            textSize = 18f
            setTextColor(textPrimary())
            typeface = Typeface.DEFAULT_BOLD
        })
        titleBlock.addView(TextView(this).apply {
            text = "스마트 라이프 유틸리티"
            textSize = 10f
            setTextColor(textMuted())
            setPadding(0, dp(2), 0, 0)
        })
        row.addView(titleBlock, lp(0, wrap(), 1f))

        val status = TextView(this).apply {
            text = "● 준비"
            textSize = 10f
            setTextColor(textMuted())
        }
        statusText = status
        row.addView(status, lp(wrap(), wrap(), 0f).also { it.marginEnd = dp(8) })

        row.addView(iconButton(if (darkMode) "☀" else "☾") { toggleTheme() }, lp(dp(40), dp(40), 0f))
        return row
    }

    private fun renderSelectedTab() {
        val container = contentContainer ?: return
        container.removeAllViews()
        when (selectedTab) {
            0 -> renderSpeedPage(container)
            1 -> renderCompassPage(container)
            else -> renderToolsPage(container)
        }
        updateNavAppearance()
    }

    private fun renderSpeedPage(container: LinearLayout) {
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = gradientDrawable(intArrayOf(Color.rgb(79, 70, 229), Color.rgb(124, 58, 237)), 28)
            elevation = dp(4).toFloat()
        }

        val heroTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val greetingBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        greetingBlock.addView(TextView(this).apply {
            text = "현재 속도"
            textSize = 12f
            setTextColor(Color.rgb(224, 231, 255))
        })
        greetingBlock.addView(TextView(this).apply {
            text = "실시간 GPS 속도계"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(3), 0, 0)
        })
        heroTop.addView(greetingBlock, lp(0, wrap(), 1f))
        heroTop.addView(TextView(this).apply {
            text = "LIVE"
            textSize = 10f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = gradientDrawable(intArrayOf(Color.rgb(129, 140, 248), Color.rgb(167, 139, 250)), 99)
            setPadding(dp(9), dp(5), dp(9), dp(5))
        }, lp(wrap(), wrap(), 0f))
        hero.addView(heroTop)

        speedText = TextView(this).apply {
            text = "0.0"
            textSize = 74f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        hero.addView(speedText, lp(match(), dp(100), 0f).also { it.topMargin = dp(7) })

        hero.addView(TextView(this).apply {
            text = "km/h"
            textSize = 15f
            setTextColor(Color.rgb(238, 242, 255))
            gravity = Gravity.CENTER
        })

        coordsText = TextView(this).apply {
            text = "위치 대기 중…"
            textSize = 10f
            setTextColor(Color.rgb(224, 231, 255))
            gravity = Gravity.CENTER
        }
        hero.addView(coordsText, lp(match(), wrap(), 0f).also { it.topMargin = dp(8) })
        container.addView(hero, lp(match(), wrap(), 0f))

        container.addView(sectionTitle("오늘의 기록", "GPS 상태와 이동 정보를 한눈에"), lp(match(), wrap(), 0f).also { it.topMargin = dp(16) })
        container.addView(metricGrid(), lp(match(), wrap(), 0f).also { it.topMargin = dp(9) })
        container.addView(statusCard(), lp(match(), wrap(), 0f).also { it.topMargin = dp(10) })
        container.addView(shortcutRow(), lp(match(), wrap(), 0f).also { it.topMargin = dp(10) })
    }

    private fun sectionTitle(title: String, subtitle: String): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        box.addView(TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(textPrimary())
            typeface = Typeface.DEFAULT_BOLD
        })
        box.addView(TextView(this).apply {
            text = subtitle
            textSize = 10f
            setTextColor(textMuted())
            setPadding(0, dp(3), 0, 0)
        })
        return box
    }

    private fun metricGrid(): View {
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(metricCard("평균 속도", "—", "≈", Color.rgb(99, 102, 241)) { avgText = it }, cellLp().also { it.marginEnd = dp(5) })
        row1.addView(metricCard("최고 속도", "—", "↗", Color.rgb(16, 185, 129)) { maxText = it }, cellLp().also { it.marginStart = dp(5) })
        outer.addView(row1)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(metricCard("GPS 정확도", "—", "⌖", Color.rgb(6, 182, 212)) { accuracyText = it }, cellLp().also { it.marginEnd = dp(5); it.topMargin = dp(10) })
        row2.addView(metricCard("이동 거리", "0 m", "↔", Color.rgb(245, 158, 11)) { distanceText = it }, cellLp().also { it.marginStart = dp(5); it.topMargin = dp(10) })
        outer.addView(row2)
        return outer
    }

    private fun metricCard(label: String, initial: String, symbol: String, accent: Int, save: (TextView) -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(12), dp(13), dp(12))
        }
        roundedCard(box)

        val icon = TextView(this).apply {
            text = symbol
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(accent)
            background = tintedDrawable(accent, 0.14f, 12)
        }
        box.addView(icon, lp(dp(32), dp(32), 0f))

        box.addView(TextView(this).apply {
            text = label
            textSize = 9f
            setTextColor(textMuted())
        }, lp(match(), wrap(), 0f).also { it.topMargin = dp(8) })

        val value = TextView(this).apply {
            text = initial
            textSize = 15f
            setTextColor(textPrimary())
            typeface = Typeface.DEFAULT_BOLD
        }
        box.addView(value, lp(match(), wrap(), 0f).also { it.topMargin = dp(3) })
        save(value)
        return box
    }

    private fun statusCard(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        roundedCard(box)
        box.addView(TextView(this).apply {
            text = "현재 방향"
            textSize = 10f
            setTextColor(textMuted())
        })
        compassText = TextView(this).apply {
            text = "방위각 0° · 북"
            textSize = 18f
            setTextColor(textPrimary())
            typeface = Typeface.DEFAULT_BOLD
        }
        box.addView(compassText, lp(match(), wrap(), 0f).also { it.topMargin = dp(4) })
        box.addView(TextView(this).apply {
            text = "GPS는 실외에서 더 안정적인 값을 제공합니다."
            textSize = 10f
            setTextColor(textMuted())
        }, lp(match(), wrap(), 0f).also { it.topMargin = dp(7) })
        return box
    }

    private fun shortcutRow(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(actionButton("↻", "초기화") { resetStats() }, cellLp().also { it.marginEnd = dp(5) })
        row.addView(actionButton("⌁", "GPS 다시 연결") { requestGps() }, cellLp().also { it.marginStart = dp(5) })
        return row
    }

    private fun actionButton(symbol: String, label: String, click: () -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(11), dp(10), dp(11))
            setOnClickListener { click() }
        }
        roundedCard(box)
        box.addView(TextView(this).apply {
            text = symbol
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(99, 102, 241))
        })
        box.addView(TextView(this).apply {
            text = label
            textSize = 9f
            setTextColor(textPrimary())
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }, lp(match(), wrap(), 0f).also { it.topMargin = dp(4) })
        return box
    }

    private fun renderCompassPage(container: LinearLayout) {
        container.addView(sectionTitle("나침반", "휴대폰 센서로 현재 방향을 확인하세요"))

        val compassCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(26), dp(20), dp(26))
        }
        roundedCard(compassCard)

        compassText = TextView(this).apply {
            text = "방위각 ${lastCompass.toInt()}°"
            textSize = 34f
            setTextColor(Color.rgb(99, 102, 241))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        compassCard.addView(compassText, lp(match(), wrap(), 0f))

        compassCard.addView(TextView(this).apply {
            text = directionName(lastCompass)
            textSize = 18f
            setTextColor(textPrimary())
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }, lp(match(), wrap(), 0f).also { it.topMargin = dp(6) })

        val guide = TextView(this).apply {
            text = "휴대폰을 평평하게 들면 더 안정적입니다."
            textSize = 10f
            setTextColor(textMuted())
            gravity = Gravity.CENTER
        }
        compassCard.addView(guide, lp(match(), wrap(), 0f).also { it.topMargin = dp(10) })

        container.addView(compassCard, lp(match(), wrap(), 0f).also { it.topMargin = dp(12) })
        container.addView(actionButton("⌖", "속도계 화면으로 돌아가기") { selectTab(0) }, lp(match(), dp(82), 0f).also { it.topMargin = dp(10) })
    }

    private fun renderToolsPage(container: LinearLayout) {
        container.addView(sectionTitle("생활 도구", "자주 쓰는 기능을 빠르게 실행"))

        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(toolCard("↻", "측정 초기화", "현재 기록을 처음부터") { resetStats() }, cellLp().also { it.marginEnd = dp(5) })
        row1.addView(toolCard("⌁", "GPS 연결", "위치 권한 및 수신 상태") { requestGps() }, cellLp().also { it.marginStart = dp(5) })
        grid.addView(row1)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(toolCard("◷", "이동 시간", "현재 측정 시간") { Toast.makeText(this@MainActivity, timeText?.text ?: "00:00:00", Toast.LENGTH_SHORT).show() }, cellLp().also { it.marginEnd = dp(5); it.topMargin = dp(10) })
        row2.addView(toolCard("↔", "이동 거리", "누적 이동 거리") { Toast.makeText(this@MainActivity, distanceText?.text ?: "0 m", Toast.LENGTH_SHORT).show() }, cellLp().also { it.marginStart = dp(5); it.topMargin = dp(10) })
        grid.addView(row2)

        container.addView(grid, lp(match(), wrap(), 0f).also { it.topMargin = dp(12) })

        val note = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        roundedCard(note)
        note.addView(TextView(this).apply {
            text = "💡 사용 팁"
            textSize = 12f
            setTextColor(textPrimary())
            typeface = Typeface.DEFAULT_BOLD
        })
        note.addView(TextView(this).apply {
            text = "GPS 속도는 이동 중에 더 정확하게 표시됩니다. 나침반은 기기 센서가 지원될 때만 동작합니다."
            textSize = 10f
            setTextColor(textMuted())
        }, lp(match(), wrap(), 0f).also { it.topMargin = dp(6) })
        container.addView(note, lp(match(), wrap(), 0f).also { it.topMargin = dp(10) })
    }

    private fun toolCard(symbol: String, title: String, subtitle: String, click: () -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(12), dp(13), dp(12))
            setOnClickListener { click() }
        }
        roundedCard(box)
        box.addView(TextView(this).apply {
            text = symbol
            textSize = 18f
            setTextColor(Color.rgb(99, 102, 241))
        })
        box.addView(TextView(this).apply {
            text = title
            textSize = 11f
            setTextColor(textPrimary())
            typeface = Typeface.DEFAULT_BOLD
        }, lp(match(), wrap(), 0f).also { it.topMargin = dp(7) })
        box.addView(TextView(this).apply {
            text = subtitle
            textSize = 9f
            setTextColor(textMuted())
        }, lp(match(), wrap(), 0f).also { it.topMargin = dp(3) })
        return box
    }

    private fun bottomNav(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(surface())
            addView(navButton("⌁", "속도계", 0), navLp())
            addView(navButton("⌖", "나침반", 1), navLp())
            addView(navButton("✦", "도구", 2), navLp())
        }
    }

    private fun navButton(symbol: String, label: String, tab: Int): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setOnClickListener { selectTab(tab) }
            tag = tab
        }
        val icon = TextView(this).apply {
            text = symbol
            textSize = 17f
            gravity = Gravity.CENTER
        }
        box.addView(icon, lp(match(), dp(24), 0f))
        box.addView(TextView(this).apply {
            text = label
            textSize = 9f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(2), 0, 0)
        }, lp(match(), wrap(), 0f))
        return box
    }

    private fun updateNavAppearance() {
        val nav = navContainer ?: return
        for (i in 0 until nav.childCount) {
            val item = nav.getChildAt(i) as LinearLayout
            val selected = i == selectedTab
            item.background = if (selected) tintedDrawable(Color.rgb(99, 102, 241), if (darkMode) 0.22f else 0.12f, 18) else null
            val icon = item.getChildAt(0) as TextView
            val label = item.getChildAt(1) as TextView
            icon.setTextColor(if (selected) Color.rgb(99, 102, 241) else textMuted())
            label.setTextColor(if (selected) Color.rgb(79, 70, 229) else textMuted())
        }
    }

    private fun selectTab(tab: Int) {
        selectedTab = tab
        renderSelectedTab()
    }

    private fun toggleTheme() {
        darkMode = !darkMode
        renderApp()
        Toast.makeText(this, if (darkMode) "다크 모드" else "라이트 모드", Toast.LENGTH_SHORT).show()
    }

    private fun requestGps() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(
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
            statusText?.setTextColor(red())
            return
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        try {
            locationManager.removeUpdates(this)
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0.5f, this)
            statusText?.text = "● GPS 연결됨"
            statusText?.setTextColor(green())
        } catch (_: SecurityException) {
            statusText?.text = "● GPS 권한 필요"
            statusText?.setTextColor(red())
        }
    }

    private fun registerSensors() {
        val rotation = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotation != null) sensorManager.registerListener(this, rotation, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onLocationChanged(location: Location) {
        val kmh = if (location.hasSpeed() && location.speed.isFinite()) (location.speed * 3.6f).coerceAtLeast(0f) else 0f

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
        distanceText?.text = if (totalMeters >= 1000.0) String.format(Locale.US, "%.2f km", totalMeters / 1000.0) else String.format(Locale.US, "%.0f m", totalMeters)

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

        val direction = directionName(degrees)
        compassText?.text = "방위각 ${degrees.toInt()}° · $direction"
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) {
            statusText?.text = "● GPS 꺼짐"
            statusText?.setTextColor(red())
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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) startGps()
    }

    private fun directionName(degrees: Float): String = when {
        degrees < 22.5f || degrees >= 337.5f -> "북"
        degrees < 67.5f -> "북동"
        degrees < 112.5f -> "동"
        degrees < 157.5f -> "남동"
        degrees < 202.5f -> "남"
        degrees < 247.5f -> "남서"
        degrees < 292.5f -> "서"
        else -> "북서"
    }

    private fun bg() = if (darkMode) Color.rgb(15, 23, 42) else Color.rgb(248, 250, 252)
    private fun surface() = if (darkMode) Color.rgb(17, 24, 39) else Color.WHITE
    private fun card() = if (darkMode) Color.rgb(30, 41, 59) else Color.rgb(248, 250, 252)
    private fun border() = if (darkMode) Color.rgb(51, 65, 85) else Color.rgb(226, 232, 240)
    private fun textPrimary() = if (darkMode) Color.rgb(248, 250, 252) else Color.rgb(30, 41, 59)
    private fun textMuted() = if (darkMode) Color.rgb(148, 163, 184) else Color.rgb(100, 116, 139)
    private fun green() = Color.rgb(16, 185, 129)
    private fun red() = Color.rgb(244, 63, 94)

    private fun roundedCard(view: View) {
        view.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(22).toFloat()
            setColor(card())
            setStroke(dp(1), border())
        }
    }

    private fun gradientDrawable(colors: IntArray, radiusDp: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            colors
        ).apply { cornerRadius = dp(radiusDp).toFloat() }

    private fun tintedDrawable(color: Int, alpha: Float, radiusDp: Int): android.graphics.drawable.GradientDrawable {
        val a = (255 * alpha).toInt().coerceIn(0, 255)
        val tinted = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
        return android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(radiusDp).toFloat()
            setColor(tinted)
        }
    }

    private fun iconButton(symbol: String, click: () -> Unit): View = TextView(this).apply {
        text = symbol
        textSize = 16f
        gravity = Gravity.CENTER
        setTextColor(textPrimary())
        background = tintedDrawable(Color.rgb(100, 116, 139), if (darkMode) 0.16f else 0.10f, 14)
        setOnClickListener { click() }
    }

    private fun cellLp() = LinearLayout.LayoutParams(0, dp(115), 1f)
    private fun navLp() = LinearLayout.LayoutParams(0, dp(58), 1f)
    private fun lp(width: Int, height: Int, weight: Float) = LinearLayout.LayoutParams(width, height, weight)
    private fun match() = LinearLayout.LayoutParams.MATCH_PARENT
    private fun wrap() = LinearLayout.LayoutParams.WRAP_CONTENT
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

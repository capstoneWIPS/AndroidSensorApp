package com.example.final_demo

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.OutputStream
import java.net.Socket

class MainActivity2 : AppCompatActivity(), SensorEventListener {
    private lateinit var mSensorManager: SensorManager
    private var mAccelerometer: Sensor? = null
    private var mGyroscope: Sensor? = null
    private var mMagneticfield: Sensor? = null
    private var mrotation: Sensor? = null

    private var resume = true
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null

    private lateinit var wifiManager: WifiManager
    private lateinit var tableLayout: TableLayout
    private val PERMISSION_REQUEST_CODE = 100

    private val handler = Handler(Looper.getMainLooper())
    private val scanInterval = 300L // 5 seconds between scans
    private var isScanning = false

    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE
    )

    private val scanRunnable = object : Runnable {
        override fun run() {
            if (isScanning) {
                startWifiScan()
                handler.postDelayed(this, scanInterval)
            }
        }
    }

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            if (success) {
                scanSuccess()
            } else {
                scanFailure()
            }
        }
    }

//    private fun sendDataToServer(pitch: Float, roll: Float, yaw: Float) {
//        Thread {
//            try {
//                // Ensure socket and outputStream are initialized before attempting to send data
////                socket?.let { socket ->
////                    outputStream?.let { outputStream ->
////                        val data = "$pitch,$roll,$yaw"
////                        outputStream.write(data.toByteArray())
////                        outputStream.flush()
////                    }
////                }
//            } catch (e: Exception) {
//                runOnUiThread {
//                    Toast.makeText(applicationContext, "Error sending data", Toast.LENGTH_SHORT).show()
//                }
//            }
//        }.start()
//    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        return
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null && resume) {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                findViewById<TextView>(R.id.textView12).text = event.values[0].toString()
                findViewById<TextView>(R.id.textView17).text = event.values[1].toString()
                findViewById<TextView>(R.id.textView16).text = event.values[2].toString()
            }
            if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                findViewById<TextView>(R.id.textView18).text = event.values[0].toString()
                findViewById<TextView>(R.id.textView14).text = event.values[1].toString()
                findViewById<TextView>(R.id.textView19).text = event.values[2].toString()
            }
            if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                findViewById<TextView>(R.id.textView21).text = event.values[0].toString()
                findViewById<TextView>(R.id.textView22).text = event.values[1].toString()
                findViewById<TextView>(R.id.textView23).text = event.values[2].toString()
            }
            if (event.sensor.type == Sensor.TYPE_ORIENTATION) {
                findViewById<TextView>(R.id.textView3).text = event.values[0].toString()
                findViewById<TextView>(R.id.textView6).text = event.values[1].toString()
                findViewById<TextView>(R.id.textView8).text = event.values[2].toString()
                val pitch = event.values[1]  // Y axis (pitch)
                val roll = event.values[2]   // Z axis (roll)
                val yaw = event.values[0]    // X axis (yaw)
//                sendDataToServer(pitch, roll, yaw)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        mSensorManager.registerListener(this, mGyroscope, SensorManager.SENSOR_DELAY_NORMAL)
        mSensorManager.registerListener(this, mMagneticfield, SensorManager.SENSOR_DELAY_NORMAL)
        mSensorManager.registerListener(this, mrotation, SensorManager.SENSOR_DELAY_NORMAL)

        val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        registerReceiver(wifiScanReceiver, intentFilter)

        if (checkIfPermissionsGranted()) {
            startContinuousScanning()
        }
    }

    override fun onPause() {
        super.onPause()
        mSensorManager.unregisterListener(this)
        try {
            unregisterReceiver(wifiScanReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered, ignore
        }

        stopContinuousScanning()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        wifiManager = getSystemService(WIFI_SERVICE) as WifiManager

        tableLayout = findViewById(R.id.wifiTable)

//        Thread {
//            try {
//                socket = Socket("192.168.0.115", 12345)  // Replace with the IP address of the Python server
//                outputStream = socket?.getOutputStream()
//            } catch (e: Exception) {
//                runOnUiThread {
//                    Toast.makeText(applicationContext, "Error connecting to server", Toast.LENGTH_SHORT).show()
//                }
//            }
//        }.start()

        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        mMagneticfield = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        mrotation = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION) // note this is deprecated

        if (checkAndRequestPermissions()) {
            startContinuousScanning()
        }
    }

    private fun checkIfPermissionsGranted(): Boolean {
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    private fun checkAndRequestPermissions(): Boolean {
        val permissionsToRequest = ArrayList<String>()

        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
            return false
        }

        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // All permissions have been granted
                startContinuousScanning()
            } else {
                Toast.makeText(
                    this,
                    "Permission denied. Cannot scan WiFi networks without required permissions.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun startContinuousScanning() {
        if (!isScanning) {
            isScanning = true
            val statusText = findViewById<TextView>(R.id.textView)
            statusText.text = "WiFi scanning active..."

            handler.post(scanRunnable)
        }
    }

    private fun stopContinuousScanning() {
        isScanning = false
        handler.removeCallbacks(scanRunnable)
    }

    private fun startWifiScan() {
        try {
            val success = wifiManager.startScan()
            if (!success) {
                scanFailure()
            }
        } catch (e: SecurityException) {
            Toast.makeText(
                this,
                "Permission denied: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            stopContinuousScanning()
        }
    }

    private fun scanSuccess() {
        val results = wifiManager.scanResults

        val statusText = findViewById<TextView>(R.id.textView)
        val currentTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        statusText.text = "Last scan: $currentTime"

        tableLayout.removeAllViews()

        val headerRow = TableRow(this)
        headerRow.setBackgroundResource(android.R.color.darker_gray)

        val ssidHeader = TextView(this)
        ssidHeader.text = "SSID"
        ssidHeader.setPadding(16, 8, 16, 8)
        ssidHeader.setTextColor(resources.getColor(android.R.color.white, null))
        ssidHeader.setTypeface(null, android.graphics.Typeface.BOLD)

        val rssiHeader = TextView(this)
        rssiHeader.text = "RSSI (dBm)"
        rssiHeader.setPadding(16, 8, 16, 8)
        rssiHeader.setTextColor(resources.getColor(android.R.color.white, null))
        rssiHeader.setTypeface(null, android.graphics.Typeface.BOLD)

        headerRow.addView(ssidHeader)
        headerRow.addView(rssiHeader)
        tableLayout.addView(headerRow)

        val sortedResults = results.sortedByDescending { it.level }

        for (result in sortedResults) {
            val row = TableRow(this)

            val ssidText = TextView(this)
            ssidText.text = if (result.SSID.isNotEmpty()) result.SSID else "(Hidden Network)"
            ssidText.setPadding(16, 12, 16, 12)

            val rssiText = TextView(this)
            rssiText.text = "${result.level} dBm"
            rssiText.setPadding(16, 12, 16, 12)
            rssiText.gravity = Gravity.END

            row.addView(ssidText)
            row.addView(rssiText)
            tableLayout.addView(row)
        }

        if (results.isEmpty()) {
            val row = TableRow(this)
            val noNetworksText = TextView(this)
            noNetworksText.text = "No WiFi networks found"
            noNetworksText.setPadding(16, 12, 16, 12)
            row.addView(noNetworksText)
            tableLayout.addView(row)
        }
    }

    private fun scanFailure() {
        Toast.makeText(this, "WiFi scan failed. Will try again.", Toast.LENGTH_SHORT).show()
    }
}
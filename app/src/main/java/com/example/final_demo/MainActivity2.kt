package com.example.final_demo

import android.Manifest
import com.google.android.material.button.MaterialButton
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
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity2 : AppCompatActivity(), SensorEventListener {
    private lateinit var fabMap: FloatingActionButton
    private lateinit var mSensorManager: SensorManager
    private var mAccelerometer: Sensor? = null
    private var mGyroscope: Sensor? = null
    private var mMagneticfield: Sensor? = null
    private var mrotation: Sensor? = null

    private var resume = true

    private lateinit var wifiManager: WifiManager
    private lateinit var tableLayout: TableLayout
    private lateinit var roomNameEditText: EditText
    private lateinit var saveButton: Button

    private val PERMISSION_REQUEST_CODE = 100

    private val handler = Handler(Looper.getMainLooper())
    private val scanInterval = 300L // 5 seconds between scans
    private var isScanning = false
    private var latestScanResults: List<android.net.wifi.ScanResult> = emptyList()

    // Updated permissions based on Android version
    private val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.NEARBY_WIFI_DEVICES
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )
    }

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

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        return
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null && resume) {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                findViewById<TextView>(R.id.textView12).text = String.format("%.2f", event.values[0])
                findViewById<TextView>(R.id.textView17).text = String.format("%.2f", event.values[1])
                findViewById<TextView>(R.id.textView16).text = String.format("%.2f", event.values[2])
            }
            if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                findViewById<TextView>(R.id.textView18).text = String.format("%.2f", event.values[0])
                findViewById<TextView>(R.id.textView14).text = String.format("%.2f", event.values[1])
                findViewById<TextView>(R.id.textView19).text = String.format("%.2f", event.values[2])
            }
            if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                findViewById<TextView>(R.id.textView21).text = String.format("%.2f", event.values[0])
                findViewById<TextView>(R.id.textView22).text = String.format("%.2f", event.values[1])
                findViewById<TextView>(R.id.textView23).text = String.format("%.2f", event.values[2])
            }
            if (event.sensor.type == Sensor.TYPE_ORIENTATION) {
                findViewById<TextView>(R.id.textView3).text = String.format("%.2f", event.values[0])
                findViewById<TextView>(R.id.textView6).text = String.format("%.2f", event.values[1])
                findViewById<TextView>(R.id.textView8).text = String.format("%.2f", event.values[2])
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

        // Initialize all views including fabMap
        wifiManager = getSystemService(WIFI_SERVICE) as WifiManager
        tableLayout = findViewById(R.id.wifiTable)
        roomNameEditText = findViewById(R.id.statusTextView)
        saveButton = findViewById(R.id.actionButton)
        fabMap = findViewById(R.id.fabMap)

        fabMap.setOnClickListener {
            navigateToMap()
        }

        findViewById<MaterialButton>(R.id.btnSwitchToMap).setOnClickListener {
            navigateToMap()
        }

        saveButton.setOnClickListener {
            saveRoomData()
        }

        // Sensor setup
        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        mMagneticfield = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        mrotation = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION)

        if (checkAndRequestPermissions()) {
            startContinuousScanning()
        }
    }

    private fun navigateToMap() {
        val intent = Intent(this, MapActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    private fun saveRoomData() {
        val roomName = roomNameEditText.text.toString().trim()

        if (roomName.isEmpty()) {
            Toast.makeText(this, "Please enter a room name", Toast.LENGTH_SHORT).show()
            return
        }

        if (latestScanResults.isEmpty()) {
            Toast.makeText(this, "No WiFi networks found. Please wait for scan to complete.", Toast.LENGTH_SHORT).show()
            return
        }

        // Find the strongest signal (highest RSSI value)
        val strongestNetwork = latestScanResults.maxByOrNull { it.level }

        if (strongestNetwork != null) {
            val bssid = strongestNetwork.BSSID
            val rssi = strongestNetwork.level
            val ssid = if (strongestNetwork.SSID.isNotEmpty()) strongestNetwork.SSID else "Hidden Network"

            // Save to CSV
            if (saveToCsv(roomName, bssid, rssi, ssid)) {
                val csvFile = getCsvFile()
                Toast.makeText(this, "Room data saved to: ${csvFile.absolutePath}", Toast.LENGTH_LONG).show()
                roomNameEditText.setText("") // Clear the input
            } else {
                Toast.makeText(this, "Failed to save room data", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "No valid network data available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveToCsv(roomName: String, bssid: String, rssi: Int, ssid: String): Boolean {
        return try {
            val csvFile = getCsvFile()
            val fileExists = csvFile.exists()

            // Ensure parent directory exists
            csvFile.parentFile?.mkdirs()

            FileWriter(csvFile, true).use { writer ->
                // Write header if file is new
                if (!fileExists) {
                    writer.append("Room Name,BSSID,RSSI,SSID,Timestamp\n")
                }

                // Write data row
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                writer.append("\"$roomName\",\"$bssid\",$rssi,\"$ssid\",\"$timestamp\"\n")
                writer.flush()
            }

            // Log the file path for debugging
            android.util.Log.d("CSV_SAVE", "File saved to: ${csvFile.absolutePath}")
            true
        } catch (e: IOException) {
            e.printStackTrace()
            android.util.Log.e("CSV_SAVE", "Error saving file: ${e.message}")
            false
        }
    }

    private fun getCsvFile(): File {
        // Try multiple locations based on Android version
        return when {
            // For Android 10+ (API 29+) - Use public Downloads directory (most accessible)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // This saves to Downloads folder which is easily accessible
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                File(downloadsDir, "room_bssid_rssi.csv")
            }
            // For older versions - Use external storage if available
            Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED -> {
                val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                File(documentsDir, "room_bssid_rssi.csv")
            }
            // Fallback to app's external files directory
            else -> {
                val appDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                File(appDir, "room_bssid_rssi.csv")
            }
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

        // Add storage permission for older Android versions
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
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
            // Check if WiFi is enabled
            if (!wifiManager.isWifiEnabled) {
                Toast.makeText(this, "WiFi is disabled. Please enable WiFi to scan.", Toast.LENGTH_LONG).show()
                return
            }

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
        try {
            val results = wifiManager.scanResults
            latestScanResults = results // Store the latest results

            val statusText = findViewById<TextView>(R.id.textView)
            val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
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
        } catch (e: SecurityException) {
            Toast.makeText(this, "Security exception during scan: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun scanFailure() {
        Toast.makeText(this, "WiFi scan failed. Will try again.", Toast.LENGTH_SHORT).show()
    }
}
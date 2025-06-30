package com.example.final_demo

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.view.MotionEvent
import android.widget.Button
import io.getstream.photoview.PhotoView
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log

class MapActivity : AppCompatActivity() {

    private lateinit var mapImageView: PhotoView
    private lateinit var floorChipGroup: ChipGroup
    private lateinit var fabSensors: FloatingActionButton
    private lateinit var saveButtonVar: Button
    private lateinit var wifiManager: WifiManager
    private var bitmapX = 0
    private var bitmapY = 0
    private var scanIndex = 0
    private var latestScanResults: List<ScanResult> = emptyList()
    private var isScanning = false

    // Data structure to store all scan data
    private val outermostmap = mutableMapOf<Int, MutableMap<String, Any?>>()

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

    private val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)

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

    private val PERMISSION_REQUEST_CODE = 1001

    private val floorPlans: Map<String, Int> = mapOf(
        "Ground Floor" to R.drawable.f_0_updatd,
        "Floor 1" to R.drawable.f_1_updatd,
        "Floor 2" to R.drawable.f_2_updatd,
        "Floor 3" to R.drawable.f_3_updated,
        "Floor 4" to R.drawable.f_4_updatd,
        "Floor 5" to R.drawable.f_5_updatd,
        "Floor 6" to R.drawable.f_6_updatd
    )

    private var currentFloor = "Ground Floor"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mapMain)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initializeViews()
        initializeWifi()
        setupFloorSelector()
        setupFab()
        checkPermissions()

        // Load initial floor
        loadFloorPlan(currentFloor)
        saveButtonVar = findViewById(R.id.button3) //savejson button

        saveButtonVar.setOnClickListener {
            if (bitmapX == 0 && bitmapY == 0) {
                Toast.makeText(this, "Please tap on the map first to set position", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            performWifiScanAndSave()
        }

        mapImageView.setOnPhotoTapListener { view, x, y ->
            // x and y are relative (0.0 to 1.0) — normalized coordinates
            bitmapX = (x * view!!.drawable.intrinsicWidth).toInt()
            bitmapY = (y * view.drawable.intrinsicHeight).toInt()

            Toast.makeText(this, "Tapped at image coords: X=$bitmapX, Y=$bitmapY", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializeViews() {
        mapImageView = findViewById(R.id.mapImageView)
        floorChipGroup = findViewById(R.id.floorChipGroup)
        fabSensors = findViewById(R.id.fabSensors)
    }

    private fun initializeWifi() {
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    private fun checkPermissions() {
        val missingPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (!allGranted) {
                Toast.makeText(this, "Permissions required for WiFi scanning", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupFloorSelector() {
        floorPlans.keys.forEachIndexed { index, floorName ->
            val chip = Chip(this).apply {
                text = floorName
                isCheckable = true
                isChecked = floorName == currentFloor

                setOnClickListener {
                    if (isChecked) {
                        currentFloor = floorName
                        loadFloorPlan(floorName)

                        for (i in 0 until floorChipGroup.childCount) {
                            val otherChip = floorChipGroup.getChildAt(i) as Chip
                            if (otherChip != this) {
                                otherChip.isChecked = false
                            }
                        }
                    } else {
                        isChecked = true
                    }
                }
            }

            floorChipGroup.addView(chip)
        }
    }

    private fun setupFab() {
        fabSensors.setOnClickListener {
            // Go back to sensors activity
            finish()
        }
    }

    private fun loadFloorPlan(floorName: String) {
        val resourceId = floorPlans[floorName] ?: run {
            Toast.makeText(this, "Floor plan not available", Toast.LENGTH_SHORT).show()
            return
        }

        mapImageView.setImageResource(resourceId)
        mapImageView.scaleType = ImageView.ScaleType.FIT_CENTER
        mapImageView.adjustViewBounds = true
    }

    private fun performWifiScanAndSave() {
        if (isScanning) {
            Toast.makeText(this, "Scan already in progress", Toast.LENGTH_SHORT).show()
            return
        }

        if (!hasRequiredPermissions()) {
            Toast.makeText(this, "Missing required permissions", Toast.LENGTH_SHORT).show()
            return
        }

        isScanning = true
        Toast.makeText(this, "Starting WiFi scan...", Toast.LENGTH_SHORT).show()

        // Register receiver
        registerReceiver(wifiScanReceiver, intentFilter)

        // Start scan
        val success = wifiManager.startScan()
        if (!success) {
            scanFailure()
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun scanSuccess() {
        try {
            latestScanResults = wifiManager.scanResults
            Toast.makeText(this, "Scan completed. Found ${latestScanResults.size} networks", Toast.LENGTH_SHORT).show()

            // Process and save scan results
            processScanResults()
            saveRoomDataJson()

        } catch (e: SecurityException) {
            Toast.makeText(this, "Permission denied for WiFi scan", Toast.LENGTH_SHORT).show()
        } finally {
            isScanning = false
            try {
                unregisterReceiver(wifiScanReceiver)
            } catch (e: IllegalArgumentException) {
                // Receiver was not registered
            }
        }
    }

    private fun scanFailure() {
        Toast.makeText(this, "WiFi scan failed", Toast.LENGTH_SHORT).show()
        isScanning = false
        try {
            unregisterReceiver(wifiScanReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered
        }
    }

    private fun processScanResults() {
        val positionk = mutableMapOf<String, Int>()
        val readingsk = mutableMapOf<String, MutableMap<String, Int>>()

        // Initialize the map for this scan index
        outermostmap[scanIndex] = mutableMapOf<String, Any?>()

        // Set position values
        positionk["x"] = bitmapX
        positionk["y"] = bitmapY

        // Process scan results
        for (result in latestScanResults) {
            if (result.SSID.isNotEmpty()) { // Filter out empty SSIDs
                if (result.SSID !in readingsk) {
                    readingsk[result.SSID] = mutableMapOf<String, Int>()
                }
                readingsk[result.SSID]!![result.BSSID] = result.level
            }
        }

        // Store the data in the outermost map
        outermostmap[scanIndex]!!["position"] = positionk
        outermostmap[scanIndex]!!["readings"] = readingsk
        outermostmap[scanIndex]!!["floor"] = currentFloor
        outermostmap[scanIndex]!!["timestamp"] = System.currentTimeMillis()

        // Print to console
        printOutermostMap()

        scanIndex++
    }

    private fun saveRoomDataJson() {
        try {
            val jsonObject = JSONObject()

            // Add metadata
            jsonObject.put("total_scans", outermostmap.size)
            jsonObject.put("created_at", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

            // Convert outermostmap to JSON
            val scansArray = JSONObject()
            for ((index, scanData) in outermostmap) {
                val scanJson = JSONObject()

                // Add position
                val position = scanData["position"] as? Map<String, Int>
                if (position != null) {
                    val positionJson = JSONObject()
                    positionJson.put("x", position["x"])
                    positionJson.put("y", position["y"])
                    scanJson.put("position", positionJson)
                }

                // Add readings
                val readings = scanData["readings"] as? Map<String, Map<String, Int>>
                if (readings != null) {
                    val readingsJson = JSONObject()
                    for ((ssid, bssidMap) in readings) {
                        val bssidJson = JSONObject()
                        for ((bssid, rssi) in bssidMap) {
                            bssidJson.put(bssid, rssi)
                        }
                        readingsJson.put(ssid, bssidJson)
                    }
                    scanJson.put("readings", readingsJson)
                }

                // Add other metadata
                scanJson.put("floor", scanData["floor"])
                scanJson.put("timestamp", scanData["timestamp"])

                scansArray.put(index.toString(), scanJson)
            }

            jsonObject.put("scans", scansArray)

            // Save to file
            val fileName = "${currentFloor}.json"
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)

            FileWriter(file).use { writer ->
                writer.write(jsonObject.toString(2)) // Pretty print with indent
            }

            Toast.makeText(this, "Data saved to Downloads/$fileName", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Error saving data: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (isScanning) {
                unregisterReceiver(wifiScanReceiver)
            }
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered
        }
    }

    private fun printOutermostMap() {
        Log.d("MapActivity", "=== OUTERMOST MAP CONTENTS ===")
        Log.d("MapActivity", "Total entries: ${outermostmap.size}")

        for ((index, scanData) in outermostmap) {
            Log.d("MapActivity", "--- Scan Index: $index ---")

            // Print position
            val position = scanData["position"] as? Map<String, Int>
            if (position != null) {
                Log.d("MapActivity", "Position: x=${position["x"]}, y=${position["y"]}")
            }

            // Print floor and timestamp
            Log.d("MapActivity", "Floor: ${scanData["floor"]}")
            Log.d("MapActivity", "Timestamp: ${scanData["timestamp"]}")

            // Print readings
            val readings = scanData["readings"] as? Map<String, Map<String, Int>>
            if (readings != null) {
                Log.d("MapActivity", "WiFi Networks found: ${readings.size}")
                for ((ssid, bssidMap) in readings) {
                    Log.d("MapActivity", "  SSID: $ssid")
                    for ((bssid, rssi) in bssidMap) {
                        Log.d("MapActivity", "    BSSID: $bssid, RSSI: $rssi dBm")
                    }
                }
            }
            Log.d("MapActivity", "------------------------")
        }
        Log.d("MapActivity", "=== END MAP CONTENTS ===")
    }
}
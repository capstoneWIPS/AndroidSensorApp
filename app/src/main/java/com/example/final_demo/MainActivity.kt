package com.example.final_demo

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.ImageSource
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

class MapActivity : AppCompatActivity() {

    private lateinit var mapImageView: SubsamplingScaleImageView
    private lateinit var floorChipGroup: ChipGroup
    private lateinit var fabSensors: FloatingActionButton
    private lateinit var saveButtonVar: Button
    private lateinit var wifiManager: WifiManager
    private lateinit var gestureDetector: GestureDetector
    private var bitmapX = 0
    private var bitmapY = 0
    private var scanIndex = 0
    private var isScanning = false
    private val handler = Handler(Looper.getMainLooper())
    private val scanInterval = 500L // 0.5 seconds between scans
    private var latestScanResults: List<android.net.wifi.ScanResult> = emptyList()

    // Data structure to store all scan data
    private val outermostmap = mutableMapOf<Int, MutableMap<String, Any?>>()

    // NEW: Separate storage for modified bitmaps with markers
    private var modifiedFloorPlans = mutableMapOf<String, Bitmap>()
    private var baseBitmap: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    private var baseBitmap2: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)


    val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }


    val markerRadius = 50f // Slightly larger for better visibility
    var canvas = Canvas(baseBitmap)


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

    private val PERMISSION_REQUEST_CODE = 100

    private val floorPlans: MutableMap<String, Int> = mutableMapOf(
        "Ground Floor" to R.drawable.ground_floor,
        "Floor 1" to R.drawable.first_floor,
        "Floor 2" to R.drawable.second_floor,
        "Floor 3" to R.drawable.third_floor,
        "Floor 4" to R.drawable.fourth_floor,
        "Floor 5" to R.drawable.fifth_floor,
        "Floor 6" to R.drawable.sixth_floor
    )

    private var currentFloor = "Ground Floor"

    override fun onResume() {
        super.onResume()
        val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        registerReceiver(wifiScanReceiver, intentFilter)

        if (checkIfPermissionsGranted()) {
            startContinuousScanning()
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(wifiScanReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered, ignore
        }

        stopContinuousScanning()
    }

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
        setupMapInteraction()

        // Load initial floor
        loadFloorPlan(currentFloor)
        saveButtonVar = findViewById(R.id.button3) //savejson button

        if (checkAndRequestPermissions()) {
            startContinuousScanning()
        }

        saveButtonVar.setOnClickListener {
            if (bitmapX == 0 && bitmapY == 0) {
                Toast.makeText(this, "Please tap on the map first to set position", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            processScanResults()
            saveRoomDataJson()
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

    private fun setupMapInteraction() {
        // Create gesture detector for single tap detection
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // Convert screen coordinates to image coordinates
                val point = mapImageView.viewToSourceCoord(e.x, e.y)
                if (point != null) {
                    bitmapX = point.x.toInt()
                    bitmapY = point.y.toInt()

                    // Show position update with red marker
                    Log.e("MapActivity", "brr before drawmarkeronapp function")
                    //drawMarkerOnMap()
                    //ogdrawMarkerOnMap()
                    Toast.makeText(this@MapActivity, "Tapped at: X=$bitmapX, Y=$bitmapY", Toast.LENGTH_SHORT).show()

                    processScanResults()
                    saveRoomDataJson()
                    Log.e("MapActivity", "brr after drawmarkeronapp function")
                }
                return true
            }
        })

        // Set up touch listener that allows both pan/zoom and tap detection
        mapImageView.setOnTouchListener { v, event ->
            // Let the gesture detector handle single taps
            val handled = gestureDetector.onTouchEvent(event)

            // If not handled by gesture detector, let the SubsamplingScaleImageView handle it
            // This allows pan and zoom to work normally
            if (!handled) {
                false // Return false to let SubsamplingScaleImageView handle the touch
            } else {
                true
            }
        }
    }


    private fun drawMarkerOnMap() {
        val originalBitmap: Bitmap = when {
            modifiedFloorPlans.containsKey(currentFloor) -> {
                modifiedFloorPlans[currentFloor]!!.copy(Bitmap.Config.ARGB_8888, true)
            }
            else -> {
                val resBitmap = BitmapFactory.decodeResource(
                    resources,
                    floorPlans[currentFloor] ?: R.drawable.ground_floor
                )
                resBitmap.copy(Bitmap.Config.ARGB_8888, true)
            }
        }

        val canvas = Canvas(originalBitmap)
        canvas.drawCircle(bitmapX.toFloat(), bitmapY.toFloat(), markerRadius, paint)

        baseBitmap = originalBitmap
        modifiedFloorPlans[currentFloor] = baseBitmap


        mapImageView.setImage(ImageSource.bitmap(baseBitmap))

    }






    // NEW: Helper function to get current floor image (modified or original)
    private fun getCurrentFloorImage(floorName: String): ImageSource {
        return if (modifiedFloorPlans.containsKey(floorName)) {
            ImageSource.bitmap(modifiedFloorPlans[floorName]!!)
        } else {
            ImageSource.resource(floorPlans[floorName] ?: R.drawable.ground_floor)
        }
    }

    // NEW: Function to clear markers from current floor
    private fun clearMarkersFromCurrentFloor() {
        modifiedFloorPlans.remove(currentFloor)
        loadFloorPlan(currentFloor) // Reload original
    }

    private fun checkPermissions() {
        val missingPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
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
        } catch (e: SecurityException) {
            Toast.makeText(this, "Security exception during scan: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun scanFailure() {
        Toast.makeText(this, "WiFi scan failed. Will try again.", Toast.LENGTH_SHORT).show()
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
        try {
            // MODIFIED: Use the helper function to get the appropriate image source

            mapImageView.setImage(getCurrentFloorImage(floorName))


            // Configure the scale settings
            mapImageView.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE)
            mapImageView.setMaxScale(10.0f)
            mapImageView.setMinScale(0.1f)
            mapImageView.setDoubleTapZoomScale(2.0f)
            mapImageView.setDoubleTapZoomDpi(160)

            // Enable gestures
            mapImageView.setPanEnabled(true)
            mapImageView.setZoomEnabled(true)
            mapImageView.setQuickScaleEnabled(true)

        } catch (e: Exception) {
            Toast.makeText(this, "Error loading floor plan: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("MapActivity", "Error loading floor plan", e)
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

        // MODIFIED: Ensure the marker is stored in the modified bitmap when saving
        // This will create and store the modified bitmap
        drawMarkerOnMap()




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
            val fileName = "${currentFloor.replace(" ", "_")}_${System.currentTimeMillis()}.json"
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
                        Log.d("MapActivity", "    BSSID: $rssi dBm")
                    }
                }
            }
            Log.d("MapActivity", "------------------------")
        }
        Log.d("MapActivity", "=== END MAP CONTENTS ===")
    }
}
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
import org.json.JSONArray

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
    private val outermostmap2 = mutableMapOf<String, Any?>()

    private var readMap = mutableMapOf<String,MutableMap<Int, MutableMap<String, Any?>>>()
    var scanIndexMap = mutableMapOf<String, Int>()

    // NEW: Separate storage for modified bitmaps with markers
    private var modifiedFloorPlans = mutableMapOf<String, Bitmap>()


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
        "Floor One" to R.drawable.first_floor,
        "Floor Two" to R.drawable.second_floor,
        "Floor Three" to R.drawable.third_floor,
        "Floor Four" to R.drawable.fourth_floor,
        "Floor Five" to R.drawable.fifth_floor,
        "Floor Six" to R.drawable.sixth_floor
    )

    private var currentFloor = "Ground Floor"
    private lateinit var originalBitmap: Bitmap
    private lateinit var baseBitmap: Bitmap

    val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }


    val markerRadius = 50f // Slightly larger for better visibility
    lateinit var canvas:Canvas

    private fun initializeBitmaps() {
        try {
            // Start with the original resource bitmap
            val resBitmap = BitmapFactory.decodeResource(
                resources,
                floorPlans[currentFloor] ?: R.drawable.ground_floor
            )
            originalBitmap = resBitmap.copy(Bitmap.Config.ARGB_8888, true)

            // Read JSON file and mark existing points
            val jsonFile2 = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "${currentFloor.replace(" ", "_")}.json")
            //val jsonFile2 = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "${currentFloor}.json")

            val existingJson2 = if (jsonFile2.exists()) {
                try {
                    val existingContent2 = jsonFile2.readText()
                    JSONObject(existingContent2)
                } catch (e: Exception) {
                    // If file exists but is corrupted, create new structure
                    JSONObject()
                }
            } else {
                //file.createNewFile()
                JSONObject()

            }

            // Get or create the scans array
            val allScansArray2 = if (existingJson2.has("all_scans")) {
                existingJson2.getJSONArray("all_scans")
            } else {
                JSONArray()

            }

            if (jsonFile2.exists()) {
                for (i in 0 until allScansArray2.length()) {
                    val jsonObjectnew2 = allScansArray2.getJSONObject(i)
                    if (jsonObjectnew2.has("position")) {
                        val position2 = jsonObjectnew2.getJSONObject("position")
                        val x2 = position2.getInt("x")
                        val y2 = position2.getInt("y")
                        canvas = Canvas(originalBitmap)

                        // Draw marker at saved position
                        canvas.drawCircle(x2.toFloat(), y2.toFloat(), markerRadius, paint)

                        Log.d("MapActivity", "Loaded marker at: x=$x2, y=$y2 for floor $currentFloor")
                    } else {
                        Log.d("MapActivity","position not found")
                    }
                }
            } else {
                Log.d("MapActivity", "No JSON file found for floor $currentFloor, using original image")
            }




            baseBitmap = originalBitmap
            //this.canvas = Canvas(baseBitmap)
            canvas=Canvas(baseBitmap)
            // Store the bitmap with markers (if any were loaded)
            modifiedFloorPlans[currentFloor] = baseBitmap

        } catch (e: Exception) {
            Log.e("MapActivity", "Error initializing bitmaps: ${e.message}", e)
            Toast.makeText(this, "brr2 Error initializing bitmaps:", Toast.LENGTH_SHORT).show()
            // Fallback to original resource
            val resBitmap = BitmapFactory.decodeResource(
                resources,
                floorPlans[currentFloor] ?: R.drawable.ground_floor
            )
            originalBitmap = resBitmap.copy(Bitmap.Config.ARGB_8888, true)
            baseBitmap = originalBitmap
            this.canvas = Canvas(baseBitmap)
        }
    }




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
        initializeBitmaps()
        //loadExistingData()

        // Load initial floor
        loadFloorPlan(currentFloor)
        saveButtonVar = findViewById(R.id.button3) //savejson button

        if (checkAndRequestPermissions()) {
            startContinuousScanning()
        }

        //NOTE : SAVEBUTTON IS NOW DELETE BUTTON,IT DELETES THE LAST POSITION
        saveButtonVar.setOnClickListener {

            val jsonFile3 = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "${currentFloor.replace(" ", "_")}.json")
            if (jsonFile3.exists()){
                val existingJson3 = if (jsonFile3.exists()) {
                    try {
                        val existingContent3 = jsonFile3.readText()
                        JSONObject(existingContent3)
                    } catch (e: Exception) {
                        // If file exists but is corrupted, create new structure
                        JSONObject()
                    }
                } else {
                    //file.createNewFile()
                    JSONObject()

                }

                try {

                // Get or create the scans array
                val allScansArray3 = if (existingJson3.has("all_scans")) {
                    existingJson3.getJSONArray("all_scans")
                } else {
                    JSONArray()

                }
                    if (allScansArray3.length()>=1) {
                        allScansArray3.remove(allScansArray3.length() - 1)
                    } else {
                        Toast.makeText(this, "No scans to delete", Toast.LENGTH_SHORT).show()
                    }
                existingJson3.put("all_scans", allScansArray3)
                existingJson3.put("total_scan_sessions", allScansArray3.length())
                existingJson3.put("last_updated", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

                // Write the complete JSON back to file
                FileWriter(jsonFile3, false).use { writer -> // false = overwrite, not append
                    writer.write(existingJson3.toString(2))
                }

                   loadFloorPlan(currentFloor)
                    //Toast.makeText(this, "Point deleted", Toast.LENGTH_LONG).show()


                } catch (e: Exception) {
            Toast.makeText(this, "Error saving data: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("MapActivity", "brr Error loading floor plan ${e.message} ", e)

            e.printStackTrace()
        }



            } else {
                Toast.makeText(this, "No JSON file found for floor $currentFloor", Toast.LENGTH_SHORT).show()
            }

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
        try {
            // Create a completely fresh bitmap for drawing
            val sourceBitmap = if (modifiedFloorPlans.containsKey(currentFloor)) {
                modifiedFloorPlans[currentFloor]!!
            } else {
                BitmapFactory.decodeResource(
                    resources,
                    floorPlans[currentFloor] ?: R.drawable.ground_floor
                )
            }

            // Create a mutable copy
            val newBitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)

            // Create a new canvas for this specific bitmap
            val newCanvas = Canvas(newBitmap)

            // Draw the marker
            newCanvas.drawCircle(bitmapX.toFloat(), bitmapY.toFloat(), markerRadius, paint)

            // Update references
            originalBitmap = newBitmap
            baseBitmap = newBitmap
            canvas = newCanvas

            // Store the modified bitmap
            modifiedFloorPlans[currentFloor] = newBitmap

            // Update the image view
            mapImageView.setImage(ImageSource.bitmap(baseBitmap))

        } catch (e: Exception) {
            Log.e("MapActivity", "Error drawing marker: ${e.message}", e)
            Toast.makeText(this, "Error drawing marker", Toast.LENGTH_SHORT).show()
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
            currentFloor = floorName


            initializeBitmaps()
            //loadExistingData()
            mapImageView.setImage(ImageSource.bitmap(baseBitmap))
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
    private fun cleanupBitmaps() {
        try {
            // Clear all stored modified bitmaps to free memory
            modifiedFloorPlans.values.forEach { bitmap ->
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
            modifiedFloorPlans.clear()

            // Clean up current bitmaps
            if (::originalBitmap.isInitialized && !originalBitmap.isRecycled) {
                originalBitmap.recycle()
            }
            if (::baseBitmap.isInitialized && !baseBitmap.isRecycled && baseBitmap != originalBitmap) {
                baseBitmap.recycle()
            }
        } catch (e: Exception) {
            Log.e("MapActivity", "Error cleaning up bitmaps: ${e.message}")
        }
    }

    private fun processScanResults() {
        val positionk = mutableMapOf<String, Int>()
        val readingsk = mutableMapOf<String, MutableMap<String, Int>>()


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
        outermostmap2["position"] = positionk
        outermostmap2["readings"] = readingsk
        outermostmap2["floor"] = currentFloor
        outermostmap2["timestamp"] = System.currentTimeMillis()


        // Print to console
        printOutermostMap()

        scanIndex++
    }

    private fun saveRoomDataJson() {
        try {
            val fileName = "${currentFloor.replace(" ", "_")}.json"
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)

            val existingJson = if (file.exists()) {
                try {
                    val existingContent = file.readText()
                    JSONObject(existingContent)
                } catch (e: Exception) {
                    // If file exists but is corrupted, create new structure
                    JSONObject()
                }
            } else {
                //file.createNewFile()
                JSONObject()
            }

            // Get or create the scans array
            val allScansArray = if (existingJson.has("all_scans")) {
                existingJson.getJSONArray("all_scans")
            } else {
                JSONArray()
            }

            // Create new scan data
            //val newScanData = JSONObject()

            // Add metadata for this scan


            // Convert outermostmap to JSON

                val scanJson = JSONObject()

                // Add position
                val position = outermostmap2["position"] as? Map<String, Int>
                if (position != null) {
                    val positionJson = JSONObject()
                    positionJson.put("x", position["x"])
                    positionJson.put("y", position["y"])
                    scanJson.put("position", positionJson)

                }

                // Add readings
                val readings = outermostmap2["readings"] as? Map<String, Map<String, Int>>
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
            scanJson.put("index", allScansArray.length())
            scanJson.put("floor", outermostmap2["floor"])
            scanJson.put("scanned_at", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))



            // Add new scan data to the array
            //allScansArray.put(newScanData)
            allScansArray.put(allScansArray.length(),scanJson)


            // Update the main JSON object
            existingJson.put("all_scans", allScansArray)
            existingJson.put("total_scan_sessions", allScansArray.length())
            existingJson.put("last_updated", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

            // Write the complete JSON back to file
            FileWriter(file, false).use { writer -> // false = overwrite, not append
                writer.write(existingJson.toString(2))
            }


            Toast.makeText(this, "Data appended to Downloads/$fileName (${allScansArray.length()} scan sessions)", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Error saving data: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("MapActivity", "brr Error loading floor plan ${e.message} ", e)

            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupBitmaps()

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
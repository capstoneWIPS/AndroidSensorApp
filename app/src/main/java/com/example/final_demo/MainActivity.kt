package com.example.final_demo

import android.graphics.drawable.VectorDrawable
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MapActivity : AppCompatActivity() {

    private lateinit var mapImageView: ImageView
    private lateinit var floorChipGroup: ChipGroup
    private lateinit var fabSensors: FloatingActionButton

    private val floorPlans: Map<String, Int> = mapOf(
        "Ground Floor" to R.drawable.floor_0,
        "Floor 1" to R.drawable.floor_1,
        "Floor 2" to R.drawable.floor_2,
        "Floor 3" to R.drawable.floor_3,
        "Floor 4" to R.drawable.floor_4,
        "Floor 5" to R.drawable.floor_5,
        "Floor 6" to R.drawable.floor_6
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
        setupFloorSelector()
        setupFab()

        // Load initial floor
        loadFloorPlan(currentFloor)
    }

    private fun initializeViews() {
        mapImageView = findViewById(R.id.mapImageView)
        floorChipGroup = findViewById(R.id.floorChipGroup)
        fabSensors = findViewById(R.id.fabSensors)
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
}
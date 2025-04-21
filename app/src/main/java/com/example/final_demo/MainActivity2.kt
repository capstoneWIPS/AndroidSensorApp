package com.example.final_demo

import android.net.wifi.WifiManager
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.net.wifi.WifiInfo
import android.widget.Button
import android.widget.TextView
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.net.ServerSocket
import java.net.Socket
import android.widget.Toast
import java.io.OutputStream


class MainActivity2 : AppCompatActivity() ,SensorEventListener  {
    private lateinit var mSensorManager : SensorManager
    private var mAccelerometer : Sensor ?= null
    private var mGyroscope : Sensor ?= null
    private var mMagneticfield : Sensor ?= null
    private var mrotation : Sensor ?= null

    private var resume = true
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null

    private fun sendDataToServer(pitch: Float, roll: Float, yaw: Float) {
        Thread {
            try {
                // Ensure socket and outputStream are initialized before attempting to send data
                socket?.let { socket ->
                    outputStream?.let { outputStream ->
                        val data = "$pitch,$roll,$yaw"
                        outputStream.write(data.toByteArray())
                        outputStream.flush()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(applicationContext, "Error sending data", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
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
                sendDataToServer(pitch, roll, yaw)
            }
        }
    }
    override fun onResume() {
        super.onResume()
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        mSensorManager.registerListener(this, mGyroscope, SensorManager.SENSOR_DELAY_NORMAL)
        mSensorManager.registerListener(this, mMagneticfield, SensorManager.SENSOR_DELAY_NORMAL)
        mSensorManager.registerListener(this, mrotation, SensorManager.SENSOR_DELAY_NORMAL)

    }

    override fun onPause() {
        super.onPause()
        mSensorManager.unregisterListener(this)
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
        Thread {
            try {
                var socket = Socket("192.168.0.115", 12345)  // Replace with the IP address of the Python server
                outputStream = socket?.getOutputStream()
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(applicationContext, "Error connecting to server", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()

        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        mMagneticfield  = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        mrotation = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION) // note this is depriciated

        val wifiManager = getSystemService(WIFI_SERVICE) as WifiManager
        val info = wifiManager.connectionInfo //depriciated
        var rssi = info.rssi
        val t1: TextView = findViewById(R.id.textView)
        findViewById<Button>(R.id.b1).setOnClickListener {
            val updatedInfo = wifiManager.connectionInfo
            val updatedRssi = updatedInfo.rssi
            t1.text = "RSSI: $updatedRssi dBm"

        }
        }



    }

package com.newaimi.opusapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.newaimi.opus.Constants
import com.newaimi.opus.Opus
import com.newaimi.opusapp.mic.ControllerAudio

//
// Created by Loboda Alexey on 21.05.2020.
//

class MainActivity : AppCompatActivity() {

    private val TAG = "OpusActivity"
    private val audioPermission = android.Manifest.permission.RECORD_AUDIO
    private val readPermission = android.Manifest.permission.READ_EXTERNAL_STORAGE
    private val writePermission = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    private val permissionLauncher = this.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[audioPermission] ?: false

        if (audioGranted) {
            startLoop()
        } else {
            Toast.makeText(
                this,
                "Audio recording permission is required to continue",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    private lateinit var vSampleRateSeek: SeekBar
    private lateinit var vSampleRate: TextView
    private lateinit var vPlay: Button
    private lateinit var vStop: Button
    private lateinit var vBytes: RadioButton
    private lateinit var vShorts: RadioButton
    private lateinit var vMono: RadioButton
    private lateinit var vStereo: RadioButton
    private lateinit var vConvert: CheckBox

    private val codec = Opus()
    private val APPLICATION = Constants.Application.audio()
    private var CHUNK_SIZE = 0
    private lateinit var SAMPLE_RATE: Constants.SampleRate
    private lateinit var CHANNELS: Constants.Channels
    private lateinit var DEF_FRAME_SIZE: Constants.FrameSize
    private lateinit var FRAME_SIZE_SHORT: Constants.FrameSize
    private lateinit var FRAME_SIZE_BYTE: Constants.FrameSize

    private var runLoop = false
    private var needToConvert = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        vSampleRateSeek = findViewById(R.id.vSampleRateSeek)
        vSampleRate = findViewById(R.id.vSampleRate)

        vPlay = findViewById(R.id.vPlay)
        vStop = findViewById(R.id.vStop)

        vBytes = findViewById(R.id.vHandleBytes)
        vShorts = findViewById(R.id.vHandleShorts)
        vMono = findViewById(R.id.vMono)
        vStereo = findViewById(R.id.vStereo)
        vConvert = findViewById(R.id.vConvert)

        vSampleRateSeek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                SAMPLE_RATE = getSampleRate(progress)
                val lableText = "${SAMPLE_RATE.v} Hz"
                vSampleRate.text = lableText
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        vSampleRateSeek.progress = 0

        vPlay.setOnClickListener {
            vPlay.visibility = View.GONE
            vStop.visibility = View.VISIBLE
            requestPermissions()
        }

        vStop.setOnClickListener {
            stopRecording()
        }

        vConvert.setOnCheckedChangeListener { _, isChecked -> needToConvert = isChecked }
    }

    private fun recalculateCodecValues() {
        DEF_FRAME_SIZE = getDefaultFrameSize(SAMPLE_RATE.v)
        CHANNELS = if (vMono.isChecked) Constants.Channels.mono() else Constants.Channels.stereo()
        /** "CHUNK_SIZE = DEF_FRAME_SIZE.v * CHANNELS.v * 2" it's formula from opus.h "frame_size*channels*sizeof(opus_int16)" */
        CHUNK_SIZE = DEF_FRAME_SIZE.v * CHANNELS.v * 2                                              // bytes or shorts in a frame
        FRAME_SIZE_SHORT = Constants.FrameSize.fromValue(CHUNK_SIZE / CHANNELS.v)            // samples per channel
        FRAME_SIZE_BYTE = Constants.FrameSize.fromValue(CHUNK_SIZE / 2 / CHANNELS.v)         // samples per channel
    }

    private fun getSampleRate(v: Int): Constants.SampleRate {
        return when(v) {
            0 -> Constants.SampleRate._8000()
            1 -> Constants.SampleRate._12000()
            2 -> Constants.SampleRate._16000()
            3 -> Constants.SampleRate._24000()
            4 -> Constants.SampleRate._48000()
            else -> throw IllegalArgumentException()
        }
    }

    private fun getDefaultFrameSize(v: Int): Constants.FrameSize {
        return when (v) {
            8000 -> Constants.FrameSize._160()
            12000 -> Constants.FrameSize._240()
            16000 -> Constants.FrameSize._160()
            24000 -> Constants.FrameSize._240()
            48000 -> Constants.FrameSize._120()
            else -> throw IllegalArgumentException()
        }
    }

    private fun stopRecording() {
        vStop.visibility = View.GONE
        vPlay.visibility = View.VISIBLE
        stopLoop()
        ControllerAudio.stopRecord()
        ControllerAudio.stopTrack()
        vSampleRateSeek.isEnabled = true
        vBytes.isEnabled = true
        vShorts.isEnabled = true
        vMono.isEnabled = true
        vStereo.isEnabled = true
    }

    private fun startLoop() {
        stopLoop()

        vSampleRateSeek.isEnabled = false
        vBytes.isEnabled = false
        vShorts.isEnabled = false
        vMono.isEnabled = false
        vStereo.isEnabled = false

        val handleShorts = vShorts.isChecked
        recalculateCodecValues()

        codec.encoderInit(SAMPLE_RATE, CHANNELS, APPLICATION)
        codec.decoderInit(SAMPLE_RATE, CHANNELS)

        ControllerAudio.initRecorder(SAMPLE_RATE.v, CHUNK_SIZE, CHANNELS.v == 1)
        ControllerAudio.initTrack(SAMPLE_RATE.v, CHANNELS.v == 1)
        ControllerAudio.startRecord()
        runLoop = true
        Thread {
            while (runLoop) { if (handleShorts) handleShorts() else handleBytes() }
            if (!runLoop) {
                codec.encoderRelease()
                codec.decoderRelease()
            }
        }.start()
    }

    private fun stopLoop() {
        runLoop = false
    }

    private fun handleShorts() {
        val frame = ControllerAudio.getFrameShort() ?: return
        val encoded = codec.encode(frame, FRAME_SIZE_SHORT) ?: return
        Log.d(TAG, "encoded: ${frame.size} shorts of ${if (CHANNELS.v == 1) "MONO" else "STEREO"} audio into ${encoded.size} shorts")
        val decoded = codec.decode(encoded, FRAME_SIZE_SHORT) ?: return
        Log.d(TAG, "decoded: ${decoded.size} shorts")

        if (needToConvert) {
            val converted = codec.convert(decoded) ?: return
            Log.d(TAG, "converted: ${decoded.size} shorts into ${converted.size} bytes")
            ControllerAudio.write(converted)
        } else ControllerAudio.write(decoded)
        Log.d(TAG, "===========================================")
    }

    private fun handleBytes() {
        val frame = ControllerAudio.getFrame() ?: return
        val encoded = codec.encode(frame, FRAME_SIZE_BYTE) ?: return
        Log.d(TAG, "encoded: ${frame.size} bytes of ${if (CHANNELS.v == 1) "MONO" else "STEREO"} audio into ${encoded.size} bytes")
        val decoded = codec.decode(encoded, FRAME_SIZE_BYTE) ?: return
        Log.d(TAG, "decoded: ${decoded.size} bytes")

        if (needToConvert) {
            val converted = codec.convert(decoded) ?: return
            Log.d(TAG, "converted: ${decoded.size} bytes into ${converted.size} shorts")
            ControllerAudio.write(converted)
        } else ControllerAudio.write(decoded)
        Log.d(TAG, "===========================================")
    }

    private fun requestPermissions() {
        when {
            // Android 13+ (API 33+) - 只需要音频权限
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                if (ContextCompat.checkSelfPermission(this, audioPermission) == PackageManager.PERMISSION_GRANTED) {
                    startLoop()
                } else {
                    permissionLauncher.launch(arrayOf(audioPermission))
                }
            }

            // Android 7.0 到 12 (API 24-32) - 音频 + 存储权限
            else -> {
                val readPermission = Manifest.permission.READ_EXTERNAL_STORAGE
                val writePermission = Manifest.permission.WRITE_EXTERNAL_STORAGE

                val audioGranted = ContextCompat.checkSelfPermission(this, audioPermission) == PackageManager.PERMISSION_GRANTED
                val readGranted = ContextCompat.checkSelfPermission(this, readPermission) == PackageManager.PERMISSION_GRANTED
                val writeGranted = ContextCompat.checkSelfPermission(this, writePermission) == PackageManager.PERMISSION_GRANTED

                if (audioGranted && readGranted && writeGranted) {
                    startLoop()
                } else {
                    permissionLauncher.launch(arrayOf(audioPermission, readPermission, writePermission))
                }
            }
        }
    }


    override fun onPause() {
        super.onPause()
        stopRecording()
    }
}

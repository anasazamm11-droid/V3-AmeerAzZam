package com.example.trinitytuner

import android.content.Context
import android.graphics.Color
import android.media.midi.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var midiManager: MidiManager
    private var inputPort: MidiInputPort? = null
    private var outputPort: MidiOutputPort? = null
    private var openDevice: MidiDevice? = null

    private lateinit var tvStatus: TextView
    private lateinit var tvTransposeVal: TextView
    private lateinit var tvCentsVal: TextView
    private lateinit var notesGrid: GridLayout

    private val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val isTuned = BooleanArray(12) { false }
    private val centsValues = IntArray(12) { 0 }

    private var currentTranspose = 0
    private var selectedCentsStep = -50

    private val noteButtons = mutableListOf<Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvTransposeVal = findViewById(R.id.tvTransposeVal)
        tvCentsVal = findViewById(R.id.tvCentsVal)
        notesGrid = findViewById(R.id.notesGrid)

        setupControls()
        setupNotesGrid()
        initMidi()
    }

    private fun setupControls() {
        findViewById<Button>(R.id.btnTransMinus).setOnClickListener {
            if (currentTranspose > -12) {
                currentTranspose--
                tvTransposeVal.text = if (currentTranspose > 0) "+$currentTranspose" else "$currentTranspose"
            }
        }
        findViewById<Button>(R.id.btnTransPlus).setOnClickListener {
            if (currentTranspose < 12) {
                currentTranspose++
                tvTransposeVal.text = if (currentTranspose > 0) "+$currentTranspose" else "$currentTranspose"
            }
        }

        findViewById<Button>(R.id.btnCentsMinus).setOnClickListener {
            if (selectedCentsStep > -100) {
                selectedCentsStep -= 25
                tvCentsVal.text = "$selectedCentsStep"
            }
        }
        findViewById<Button>(R.id.btnCentsPlus).setOnClickListener {
            if (selectedCentsStep < 100) {
                selectedCentsStep += 25
                tvCentsVal.text = "$selectedCentsStep"
            }
        }

        findViewById<Button>(R.id.btnReset).setOnClickListener {
            resetAll()
        }

        findViewById<Button>(R.id.btnTest).setOnClickListener {
            sendTestNote()
        }
    }

    private fun setupNotesGrid() {
        notesGrid.removeAllViews()
        noteButtons.clear()

        for (i in 0 until 12) {
            val btn = Button(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = 0
                    columnSpec = GridLayout.spec(i % 3, 1f)
                    rowSpec = GridLayout.spec(i / 3, 1f)
                    setMargins(6, 6, 6, 6)
                }
                textSize = 16f
                isAllCaps = false
                setOnClickListener { toggleNoteTuning(i) }
            }
            noteButtons.add(btn)
            notesGrid.addView(btn)
            updateNoteButtonUI(i)
        }
    }

    private fun toggleNoteTuning(noteIndex: Int) {
        if (!isTuned[noteIndex]) {
            isTuned[noteIndex] = true
            centsValues[noteIndex] = selectedCentsStep
        } else {
            isTuned[noteIndex] = false
            centsValues[noteIndex] = 0
        }
        updateNoteButtonUI(noteIndex)
        sendPitchBendForNoteClass(noteIndex)
    }

    private fun updateNoteButtonUI(index: Int) {
        val btn = noteButtons[index]
        val name = noteNames[index]
        val cents = centsValues[index]

        if (isTuned[index]) {
            btn.text = "$name\n$cents cents"
            btn.setBackgroundColor(Color.parseColor("#00E5FF"))
            btn.setTextColor(Color.BLACK)
        } else {
            btn.text = "$name\n0"
            btn.setBackgroundColor(Color.parseColor("#2C2C2C"))
            btn.setTextColor(Color.WHITE)
        }
    }

    private fun resetAll() {
        currentTranspose = 0
        tvTransposeVal.text = "0"
        for (i in 0 until 12) {
            isTuned[i] = false
            centsValues[i] = 0
            updateNoteButtonUI(i)
            sendPitchBendForNoteClass(i)
        }
    }

    private fun initMidi() {
        midiManager = getSystemService(Context.MIDI_SERVICE) as MidiManager
        midiManager.registerDeviceCallback(object : MidiManager.DeviceCallback() {
            override fun onDeviceAdded(device: MidiDeviceInfo) {
                openMidiDevice(device)
            }

            override fun onDeviceRemoved(device: MidiDeviceInfo) {
                closeMidiDevice()
            }
        }, Handler(Looper.getMainLooper()))

        val devices = midiManager.devices
        if (devices.isNotEmpty()) {
            openMidiDevice(devices[0])
        } else {
            updateStatus(false)
        }
    }

    private fun openMidiDevice(info: MidiDeviceInfo) {
        midiManager.openDevice(info, { device ->
            if (device != null) {
                openDevice = device
                inputPort = device.openInputPort(0)
                outputPort = device.openOutputPort(0)

                outputPort?.connect(MidiProcessorReceiver())
                updateStatus(true)
                sendAllPitchBends()
            } else {
                updateStatus(false)
            }
        }, Handler(Looper.getMainLooper()))
    }

    private fun closeMidiDevice() {
        inputPort?.close()
        outputPort?.close()
        openDevice?.close()
        inputPort = null
        outputPort = null
        openDevice = null
        updateStatus(false)
    }

    private fun updateStatus(connected: Boolean) {
        runOnUiThread {
            if (connected) {
                tvStatus.text = "MIDI CONNECTED"
                tvStatus.setTextColor(Color.parseColor("#00E676"))
            } else {
                tvStatus.text = "MIDI DISCONNECTED"
                tvStatus.setTextColor(Color.parseColor("#FF5252"))
            }
        }
    }

    private fun sendPitchBendForNoteClass(noteClass: Int) {
        val port = inputPort ?: return
        val cents = if (isTuned[noteClass]) centsValues[noteClass] else 0

        val pbUnits = (cents * (8191.0 / 200.0)).toInt()
        var pbValue = 8192 + pbUnits
        pbValue = pbValue.coerceIn(0, 16383)

        val lsb = pbValue and 0x7F
        val msb = (pbValue shr 7) and 0x7F
        val channel = noteClass

        val msg = byteArrayOf((0xE0 or channel).toByte(), lsb.toByte(), msb.toByte())
        port.send(msg, 0, msg.size)
    }

    private fun sendAllPitchBends() {
        for (i in 0 until 12) {
            sendPitchBendForNoteClass(i)
        }
    }

    private inner class MidiProcessorReceiver : MidiReceiver() {
        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            val port = inputPort ?: return
            var i = offset
            while (i < offset + count) {
                val status = msg[i].toInt() and 0xFF
                val command = status and 0xF0

                if (command == 0x90 || command == 0x80) {
                    if (i + 2 < offset + count) {
                        val originalNote = msg[i + 1].toInt() and 0xFF
                        val velocity = msg[i + 2].toInt() and 0xFF

                        val transposedNote = (originalNote + currentTranspose).coerceIn(0, 127)
                        val noteClass = transposedNote % 12
                        val targetChannel = noteClass

                        val outStatus = (command or targetChannel).toByte()
                        val outMsg = byteArrayOf(outStatus, transposedNote.toByte(), velocity.toByte())

                        port.send(outMsg, 0, outMsg.size)
                        i += 3
                    } else break
                } else {
                    i++
                }
            }
        }
    }

    private fun sendTestNote() {
        val port = inputPort ?: return
        val noteOn = byteArrayOf(0x90.toByte(), 60.toByte(), 100.toByte())
        val noteOff = byteArrayOf(0x80.toByte(), 60.toByte(), 0.toByte())

        port.send(noteOn, 0, noteOn.size)
        Handler(Looper.getMainLooper()).postDelayed({
            port.send(noteOff, 0, noteOff.size)
        }, 500)
    }

    override fun onDestroy() {
        super.onDestroy()
        closeMidiDevice()
    }
}

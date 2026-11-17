package com.example.carloccalc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.example.carloccalc.databinding.ActivityMainBinding
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val prefs by lazy { getSharedPreferences("car_loc_calc_prefs", Context.MODE_PRIVATE) }
    private var lastResult: CalcResult? = null
    private var pastedPair: Coordinates.LatLon? = null

    data class CalcResult(
        val distanceMeters: Double,
        val durationSeconds: Double,
        val speedValue: Double,
        val speedUnit: String,
        val course: Double,
        val lat1: Double, val lon1: Double,
        val lat2: Double, val lon2: Double
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        setupSpinners()
        setupListeners()
        loadPrefs()
    }

    private fun setupSpinners() {
        fun adapter(arr: Int) = ArrayAdapter.createFromResource(
            this, arr, android.R.layout.simple_spinner_item
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        b.formatSpinner.adapter = adapter(R.array.coord_formats)
        b.latDirSpinner.adapter = adapter(R.array.lat_directions)
        b.lonDirSpinner.adapter = adapter(R.array.lon_directions)
        b.speedUnitSpinner.adapter = adapter(R.array.speed_units)
    }

    private fun setupListeners() {
        b.calculateButton.setOnClickListener { calculate() }
        b.resetButton.setOnClickListener { resetFields() }
        b.copyCoordsButton.setOnClickListener { copyCoordinates() }
        b.copyAllButton.setOnClickListener { copyAll() }
        b.pasteButton.setOnClickListener { pasteFromClipboard() }

        b.pasteSingleCheckbox.setOnCheckedChangeListener { _, isChecked ->
            togglePasteMode(isChecked)
        }

        b.singleCoordInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                if (!b.pasteSingleCheckbox.isChecked) return
                val txt = s?.toString().orEmpty()
                if (txt.isBlank()) { clearPasteStatus(); pastedPair = null; return }
                when (val r = Coordinates.parsePairDD(txt)) {
                    is Coordinates.PairResult.Success -> {
                        pastedPair = r.value
                        showPasteStatus(
                            "✓  Lat = ${Coordinates.formatDD(r.value.lat)}   Lon = ${Coordinates.formatDD(r.value.lon)}",
                            ok = true
                        )
                    }
                    is Coordinates.PairResult.Error -> {
                        pastedPair = null
                        showPasteStatus(localizePasteError(r.message), ok = false)
                    }
                }
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }

    private fun togglePasteMode(single: Boolean) {
        if (single) {
            b.separateCoordGroup.visibility = View.GONE
            b.singleCoordGroup.visibility = View.VISIBLE
            val lat = b.latInput.text.toString().trim()
            val lon = b.lonInput.text.toString().trim()
            if (lat.isNotEmpty() && lon.isNotEmpty()
                && Coordinates.parse(lat, Coordinates.Format.DD, true) is Coordinates.Result.Success
                && Coordinates.parse(lon, Coordinates.Format.DD, false) is Coordinates.Result.Success
            ) {
                b.singleCoordInput.setText("$lat $lon")
            }
            b.singleCoordInput.requestFocus()
        } else {
            pastedPair?.let {
                b.latInput.setText(Coordinates.formatDD(it.lat))
                b.lonInput.setText(Coordinates.formatDD(it.lon))
                b.latDirSpinner.setSelection(if (it.lat < 0) 1 else 0)
                b.lonDirSpinner.setSelection(if (it.lon < 0) 1 else 0)
            }
            b.singleCoordGroup.visibility = View.GONE
            b.separateCoordGroup.visibility = View.VISIBLE
            clearPasteStatus()
        }
        savePrefs()
    }

    private fun pasteFromClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        if (clip == null || clip.itemCount == 0) {
            Toast.makeText(this, R.string.paste_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val text = clip.getItemAt(0).coerceToText(this)?.toString().orEmpty()
        if (text.isBlank()) {
            Toast.makeText(this, R.string.paste_empty, Toast.LENGTH_SHORT).show()
            return
        }
        b.singleCoordInput.setText(text.trim())
        b.singleCoordInput.setSelection(b.singleCoordInput.text.length)
        when (val r = Coordinates.parsePairDD(text)) {
            is Coordinates.PairResult.Success -> {
                pastedPair = r.value
                Toast.makeText(this, R.string.paste_success, Toast.LENGTH_SHORT).show()
            }
            is Coordinates.PairResult.Error -> {
                pastedPair = null
                Toast.makeText(this, localizePasteError(r.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showPasteStatus(msg: String, ok: Boolean) {
        b.pasteStatus.visibility = View.VISIBLE
        b.pasteStatus.text = msg
        b.pasteStatus.setTextColor(if (ok) 0xFF2E7D32.toInt() else 0xFFC62828.toInt())
    }

    private fun clearPasteStatus() {
        b.pasteStatus.visibility = View.GONE
        b.pasteStatus.text = ""
    }

    private fun localizePasteError(key: String): String = when (key) {
        "PASTE_EMPTY"        -> getString(R.string.paste_empty)
        "PASTE_NEED_TWO"     -> getString(R.string.paste_need_two)
        "PASTE_MORE_THAN_TWO"-> getString(R.string.paste_more_than_two)
        "PASTE_INVALID"      -> getString(R.string.paste_invalid)
        else                 -> key
    }

    private fun loadPrefs() {
        b.formatSpinner.setSelection(prefs.getInt("coord_format", 0))
        b.speedUnitSpinner.setSelection(prefs.getInt("speed_unit", 0))
        b.latDirSpinner.setSelection(prefs.getInt("lat_dir", 0))
        b.lonDirSpinner.setSelection(prefs.getInt("lon_dir", 0))
        val single = prefs.getBoolean("paste_single_mode", false)
        b.pasteSingleCheckbox.isChecked = single
    }

    private fun savePrefs() {
        prefs.edit {
            putInt("coord_format", b.formatSpinner.selectedItemPosition)
            putInt("speed_unit", b.speedUnitSpinner.selectedItemPosition)
            putInt("lat_dir", b.latDirSpinner.selectedItemPosition)
            putInt("lon_dir", b.lonDirSpinner.selectedItemPosition)
            putBoolean("paste_single_mode", b.pasteSingleCheckbox.isChecked)
        }
    }

    private fun resolveFormat(input: String, spinnerPos: Int): Coordinates.Format {
        return when (spinnerPos) {
            0 -> Coordinates.detectFormat(input)
            1 -> Coordinates.Format.DD
            2 -> Coordinates.Format.DDMM
            3 -> Coordinates.Format.DDMMSS
            else -> Coordinates.Format.DD
        }
    }

    private fun calculate() {
        val lat: Double
        val lon: Double

        if (b.pasteSingleCheckbox.isChecked) {
            val txt = b.singleCoordInput.text.toString()
            when (val r = Coordinates.parsePairDD(txt)) {
                is Coordinates.PairResult.Error -> {
                    showError(b.singleCoordInput, localizePasteError(r.message))
                    return
                }
                is Coordinates.PairResult.Success -> {
                    lat = r.value.lat
                    lon = r.value.lon
                    pastedPair = r.value
                }
            }
        } else {
            val latStr = b.latInput.text.toString()
            val lonStr = b.lonInput.text.toString()

            val latRes = Coordinates.parse(
                latStr, resolveFormat(latStr, b.formatSpinner.selectedItemPosition), true
            )
            val lonRes = Coordinates.parse(
                lonStr, resolveFormat(lonStr, b.formatSpinner.selectedItemPosition), false
            )

            if (latRes is Coordinates.Result.Error) { showError(b.latInput, latRes.message); return }
            if (lonRes is Coordinates.Result.Error) { showError(b.lonInput, lonRes.message); return }

            var latV = (latRes as Coordinates.Result.Success).value
            var lonV = (lonRes as Coordinates.Result.Success).value
            latV = if (b.latDirSpinner.selectedItemPosition == 1) -abs(latV) else abs(latV)
            lonV = if (b.lonDirSpinner.selectedItemPosition == 1) -abs(lonV) else abs(lonV)
            lat = latV
            lon = lonV
        }

        val speedVal = parseNumber(b.speedInput.text.toString())
        if (speedVal == null || speedVal < 0) { showError(b.speedInput, "السرعة غير صالحة"); return }
        val speedMs = when (b.speedUnitSpinner.selectedItemPosition) {
            0 -> speedVal / 3.6
            1 -> speedVal
            2 -> speedVal * 0.514444
            else -> speedVal / 3.6
        }

        val courseRaw = parseNumber(b.courseInput.text.toString())
        if (courseRaw == null || courseRaw < 0 || courseRaw > 360) {
            showError(b.courseInput, "اتجاه الحركة يجب أن يكون بين 0 و 360"); return
        }
        val course = if (courseRaw == 360.0) 0.0 else courseRaw

        val h = parseNumberOrZero(b.hoursInput.text.toString())
        val m = parseNumberOrZero(b.minutesInput.text.toString())
        val s = parseNumberOrZero(b.secondsInput.text.toString())
        if (h == null || m == null || s == null || h < 0 || m < 0 || s < 0) {
            showError(b.hoursInput, "الزمن غير صالح"); return
        }
        val totalSec = h * 3600.0 + m * 60.0 + s
        if (totalSec <= 0.0) { showError(b.hoursInput, "الزمن يجب أن يكون أكبر من صفر"); return }

        val distanceMeters = speedMs * totalSec
        val dest = Geodesic.direct(lat, lon, course, distanceMeters)

        val r = CalcResult(
            distanceMeters, totalSec, speedVal,
            b.speedUnitSpinner.selectedItem.toString(), course,
            lat, lon, dest.lat, dest.lon
        )
        lastResult = r

        savePrefs()
        displayResults(r)
        hideKeyboard()
    }

    private fun parseNumber(s: String): Double? {
        val v = s.trim().replace(',', '.')
        if (v.isEmpty()) return null
        return v.toDoubleOrNull()
    }

    private fun parseNumberOrZero(s: String): Double? {
        val v = s.trim().replace(',', '.')
        if (v.isEmpty()) return 0.0
        return v.toDoubleOrNull()
    }

    private fun showError(v: View, msg: String) {
        if (v is EditText) v.error = msg
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun displayResults(r: CalcResult) {
        b.resultDistanceValue.text = String.format(
            Locale.US, "%.3f كم  (%.1f م)", r.distanceMeters / 1000.0, r.distanceMeters
        )
        b.resultTimeValue.text = formatDuration(r.durationSeconds)
        b.resultSpeedValue.text = String.format(Locale.US, "%.3f %s", r.speedValue, r.speedUnit)
        b.resultCourseValue.text = String.format(Locale.US, "%.2f°", r.course)

        val latDir = if (r.lat2 >= 0) "N" else "S"
        val lonDir = if (r.lon2 >= 0) "E" else "W"
        b.resultLatValue.text = String.format(Locale.US, "%.6f° %s", abs(r.lat2), latDir)
        b.resultLonValue.text = String.format(Locale.US, "%.6f° %s", abs(r.lon2), lonDir)

        val latDD = Coordinates.formatDD(r.lat2)
        val lonDD = Coordinates.formatDD(r.lon2)
        val latDDMM = Coordinates.formatDDMM(r.lat2, true)
        val lonDDMM = Coordinates.formatDDMM(r.lon2, false)
        val latDDMMSS = Coordinates.formatDDMMSS(r.lat2, true)
        val lonDDMMSS = Coordinates.formatDDMMSS(r.lon2, false)

        b.resultDDValue.text = "Lat: $latDD\nLon: $lonDD"
        b.resultDDMMValue.text = "Lat: $latDDMM\nLon: $lonDDMM"
        b.resultDDMMSSValue.text = "Lat: $latDDMMSS\nLon: $lonDDMMSS"

        b.resultsCard.visibility = View.VISIBLE
    }

    private fun formatDuration(totalSec: Double): String {
        val totalInt = totalSec.toLong()
        val hh = totalInt / 3600
        val mm = (totalInt % 3600) / 60
        val ss = totalInt % 60
        val frac = totalSec - totalInt
        val secStr = if (frac > 1e-6) String.format(Locale.US, "%.3f", ss + frac) else ss.toString()
        return "${hh}س ${mm}د ${secStr}ث"
    }

    private fun copyCoordinates() {
        val r = lastResult ?: return
        val text = "Lat: ${Coordinates.formatDD(r.lat2)}\nLon: ${Coordinates.formatDD(r.lon2)}"
        copyToClipboard(text)
    }

    private fun copyAll() {
        val r = lastResult ?: return
        val text = buildString {
            appendLine("نتائج حاسبة موقع السيارة")
            appendLine("المسافة: ${String.format(Locale.US, "%.3f", r.distanceMeters / 1000.0)} كم")
            appendLine("المدة: ${formatDuration(r.durationSeconds)}")
            appendLine("السرعة: ${r.speedValue} ${r.speedUnit}")
            appendLine("الاتجاه: ${r.course}°")
            appendLine("Lat: ${Coordinates.formatDD(r.lat2)}")
            appendLine("Lon: ${Coordinates.formatDD(r.lon2)}")
            appendLine("DD: ${Coordinates.formatDD(r.lat2)}, ${Coordinates.formatDD(r.lon2)}")
            appendLine("DDMM: ${Coordinates.formatDDMM(r.lat2, true)}, ${Coordinates.formatDDMM(r.lon2, false)}")
            append("DDMMSS: ${Coordinates.formatDDMMSS(r.lat2, true)}, ${Coordinates.formatDDMMSS(r.lon2, false)}")
        }
        copyToClipboard(text)
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("CarLocCalc", text))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    private fun resetFields() {
        b.latInput.setText("")
        b.lonInput.setText("")
        b.singleCoordInput.setText("")
        b.speedInput.setText("")
        b.courseInput.setText("")
        b.hoursInput.setText("")
        b.minutesInput.setText("")
        b.secondsInput.setText("")
        b.resultsCard.visibility = View.GONE
        lastResult = null
        pastedPair = null
        clearPasteStatus()
        b.latInput.requestFocus()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(b.root.windowToken, 0)
    }
}

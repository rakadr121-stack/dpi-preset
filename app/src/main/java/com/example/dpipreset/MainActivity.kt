package com.example.dpipreset

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var list: LinearLayout
    private val presets = mutableListOf<Pair<String, Int>>()

    private val permListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> updateStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadPresets()
        buildUi()
        Shizuku.addRequestPermissionResultListener(permListener)
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) updateStatus()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permListener)
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun toast(s: String) =
        Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun buildUi() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(32), dp(16), dp(16))

        val title = TextView(this)
        title.text = "DPI Preset"
        title.textSize = 24f
        root.addView(title)

        status = TextView(this)
        status.setPadding(0, dp(8), 0, dp(8))
        root.addView(status)

        val permBtn = Button(this)
        permBtn.text = "Beri izin Shizuku"
        permBtn.setOnClickListener {
            try {
                Shizuku.requestPermission(0)
            } catch (e: Throwable) {
                toast("Buka dan aktifkan Shizuku dulu")
            }
        }
        root.addView(permBtn)

        val resetBtn = Button(this)
        resetBtn.text = "RESET (DPI normal)"
        resetBtn.setBackgroundColor(Color.parseColor("#C62828"))
        resetBtn.setTextColor(Color.WHITE)
        resetBtn.setOnClickListener { applyDpi(0) }
        root.addView(resetBtn)

        val hint = TextView(this)
        hint.text = "Tahan lama sebuah preset untuk menghapusnya."
        hint.setPadding(0, dp(12), 0, dp(4))
        root.addView(hint)

        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        root.addView(list)

        val nameIn = EditText(this)
        nameIn.hint = "Nama preset"
        root.addView(nameIn)

        val dpiIn = EditText(this)
        dpiIn.hint = "Angka DPI (contoh 600)"
        dpiIn.inputType = InputType.TYPE_CLASS_NUMBER
        root.addView(dpiIn)

        val addBtn = Button(this)
        addBtn.text = "Tambah preset"
        addBtn.setOnClickListener {
            val n = nameIn.text.toString().trim().replace("|", "").replace(";", "")
            val d = dpiIn.text.toString().toIntOrNull()
            if (n.isEmpty() || d == null || d < 120 || d > 1000) {
                toast("Isi nama dan DPI (120-1000)")
            } else {
                presets.add(Pair(n, d))
                savePresets()
                refreshList()
                nameIn.text.clear()
                dpiIn.text.clear()
            }
        }
        root.addView(addBtn)

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
        refreshList()
    }

    private fun refreshList() {
        list.removeAllViews()
        presets.forEachIndexed { i, p ->
            val b = Button(this)
            b.text = if (p.second == 0) "${p.first} (normal)" else "${p.first} (${p.second})"
            b.setOnClickListener { applyDpi(p.second) }
            b.setOnLongClickListener {
                presets.removeAt(i)
                savePresets()
                refreshList()
                true
            }
            list.addView(b)
        }
    }

    private fun hasAccess(): Boolean = try {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false
    }

    private fun updateStatus() {
        status.text = try {
            when {
                !Shizuku.pingBinder() ->
                    "Shizuku belum aktif. Buka aplikasi Shizuku dan nyalakan dulu."
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED ->
                    "Shizuku aktif, tapi belum ada izin. Ketuk 'Beri izin Shizuku'."
                else -> "Siap dipakai. Ketuk preset untuk mengubah DPI."
            }
        } catch (e: Throwable) {
            "Shizuku belum terdeteksi."
        }
    }

    private fun applyDpi(dpi: Int) {
        if (!hasAccess()) {
            toast("Shizuku belum siap")
            return
        }
        val cmd = if (dpi == 0) "wm density reset" else "wm density $dpi"
        Thread {
            val r = try {
                runShell(cmd)
            } catch (e: Throwable) {
                "Gagal: ${e.message}"
            }
            runOnUiThread { toast(if (r.isEmpty()) "Berhasil" else r) }
        }.start()
    }

    private fun runShell(cmd: String): String {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val out = p.inputStream.bufferedReader().readText()
        val err = p.errorStream.bufferedReader().readText()
        p.waitFor()
        return (out + err).trim()
    }

    private fun loadPresets() {
        val s = getSharedPreferences("p", MODE_PRIVATE).getString("data", null)
        presets.clear()
        if (s.isNullOrEmpty()) {
            presets.add(Pair("Normal", 0))
            presets.add(Pair("Preset 2", 600))
        } else {
            s.split(";").forEach {
                val a = it.split("|")
                if (a.size == 2) presets.add(Pair(a[0], a[1].toIntOrNull() ?: 0))
            }
        }
    }

    private fun savePresets() {
        val s = presets.joinToString(";") { "${it.first}|${it.second}" }
        getSharedPreferences("p", MODE_PRIVATE).edit().putString("data", s).apply()
    }
}

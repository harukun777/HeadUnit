package com.andrerinas.headunitrevived.update

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.andrerinas.headunitrevived.App
import com.andrerinas.headunitrevived.R
import com.andrerinas.headunitrevived.app.BaseActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class UpdateActivity : BaseActivity() {
    private lateinit var status: TextView
    private lateinit var check: Button
    private lateinit var install: Button
    private var permissionPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.update_title)
        permissionPending = savedInstanceState?.getBoolean("permissionPending") ?: false
        val padding = (20 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        setContentView(ScrollView(this).apply { addView(layout) })
        layout.addView(TextView(this).apply { text = getString(R.string.update_description) })
        val prefs = AppUpdater.prefs(this)
        val enabled = Switch(this).apply {
            text = getString(R.string.update_auto)
            isChecked = prefs.getBoolean("enabled", true)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("enabled", checked).apply() }
        }
        layout.addView(enabled)
        val url = EditText(this).apply {
            hint = getString(R.string.update_url)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            setText(prefs.getString("url", ""))
        }
        layout.addView(url)
        layout.addView(Button(this).apply {
            text = getString(R.string.update_save)
            setOnClickListener {
                try {
                    val value = url.text.toString().trim()
                    if (value.isNotEmpty()) UpdatePolicy.requireHttps(value)
                    prefs.edit().putString("url", value).remove("last-check").apply()
                    status.text = getString(R.string.update_saved)
                } catch (_: Exception) { status.text = getString(R.string.update_invalid_url) }
            }
        })
        status = TextView(this)
        layout.addView(status)
        check = Button(this).apply {
            text = getString(R.string.update_check)
            setOnClickListener {
                if (prefs.getString("url", "").isNullOrBlank()) {
                    status.text = getString(R.string.update_no_url)
                    return@setOnClickListener
                }
                if (App.provide(this@UpdateActivity).commManager.isConnected) {
                    status.text = getString(R.string.update_connected)
                    return@setOnClickListener
                }
                isEnabled = false
                install.isEnabled = false
                status.text = getString(R.string.update_checking)
                lifecycleScope.launch {
                    try {
                        val file = AppUpdater.check(applicationContext, automatic = false)
                        status.text = getString(if (file != null) R.string.update_ready else R.string.update_current)
                        install.isEnabled = file != null
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { status.text = getString(R.string.update_failed, e.message ?: "") }
                    finally { check.isEnabled = true }
                }
            }
        }
        layout.addView(check)
        install = Button(this).apply {
            text = getString(R.string.update_install)
            isEnabled = AppUpdater.apk(this@UpdateActivity).exists()
            setOnClickListener { installUpdate() }
        }
        layout.addView(install)
        layout.addView(Button(this).apply {
            text = getString(R.string.update_back)
            setOnClickListener { finish() }
        })
        if (install.isEnabled) status.text = getString(R.string.update_ready)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("permissionPending", permissionPending)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (permissionPending && Build.VERSION.SDK_INT >= 26 && packageManager.canRequestPackageInstalls()) {
            permissionPending = false
            installUpdate()
        }
    }

    private fun installUpdate() {
        try {
            if (App.provide(this).commManager.isConnected) {
                status.text = getString(R.string.update_connected)
                return
            }
            val file = AppUpdater.apk(this)
            AppUpdater.verify(this, file)
            if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
                permissionPending = true
                status.text = getString(R.string.update_permission)
                startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName")))
                return
            }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (e: Exception) { status.text = getString(R.string.update_failed, e.message ?: "") }
    }
}

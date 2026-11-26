package com.custom.tvlauncher

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Environment
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.tvprovider.media.tv.TvContractCompat
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var ivPhotoWallpaper: ImageView
    private lateinit var tvVideoWallpaper: TextureView
    private lateinit var rvMainRows: RecyclerView
    private lateinit var rvDock: RecyclerView
    private lateinit var ivWifiStatus: TextView
    private lateinit var ivBtStatus: TextView

    private var exoPlayer: ExoPlayer? = null
    private val gson = Gson()
    private val prefs by lazy { getSharedPreferences("glass_launcher_prefs", Context.MODE_PRIVATE) }

    private var customRows = mutableListOf<CustomRow>()
    private var dockPackages = mutableListOf<String>()
    private lateinit var rowsAdapter: RowsAdapter
    private lateinit var dockAdapter: DockAdapter

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateConnectivityStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ivPhotoWallpaper = findViewById(R.id.ivPhotoWallpaper)
        tvVideoWallpaper = findViewById(R.id.tvVideoWallpaper)
        rvMainRows = findViewById(R.id.rvMainRows)
        rvDock = findViewById(R.id.rvDock)
        ivWifiStatus = findViewById(R.id.ivWifiStatus)
        ivBtStatus = findViewById(R.id.ivBtStatus)

        findViewById<Button>(R.id.btnLauncherSettings).setOnClickListener { showLauncherSettingsDialog() }
        findViewById<Button>(R.id.btnFireOsSettings).setOnClickListener { openFireOsSettings() }

        checkStoragePermission()
        loadSavedData()
        setupRecyclerViews()
        applySavedWallpaper()
        updateConnectivityStatus()
    }

    private fun checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 101)
        }
    }

    private fun loadSavedData() {
        val savedRowsJson = prefs.getString("custom_rows", null)
        if (savedRowsJson != null) {
            val type = object : TypeToken<MutableList<CustomRow>>() {}.type
            customRows = gson.fromJson(savedRowsJson, type)
        } else {
            val allApps = getInstalledApps().map { it.first }
            customRows = mutableListOf(CustomRow("all_apps", "All Applications", allApps.toMutableList()))
            saveData()
        }

        val savedDockJson = prefs.getString("dock_apps", null)
        if (savedDockJson != null) {
            val type = object : TypeToken<MutableList<String>>() {}.type
            dockPackages = gson.fromJson(savedDockJson, type)
        } else {
            dockPackages = getInstalledApps().take(5).map { it.first }.toMutableList()
            saveData()
        }
        injectLiveChannelRows()
    }

    private fun saveData() {
        val rowsToSave = customRows.filter { !it.isChannelRow }
        prefs.edit()
            .putString("custom_rows", gson.toJson(rowsToSave))
            .putString("dock_apps", gson.toJson(dockPackages))
            .apply()
    }

    private fun injectLiveChannelRows() {
        customRows.removeAll { it.isChannelRow }
        val channelCards = mutableListOf<ChannelCard>()
        try {
            val cursor = contentResolver.query(
                TvContractCompat.PreviewPrograms.CONTENT_URI,
                arrayOf(
                    TvContractCompat.PreviewPrograms.COLUMN_TITLE,
                    TvContractCompat.PreviewPrograms.COLUMN_POSTER_ART_URI,
                    TvContractCompat.PreviewPrograms.COLUMN_INTENT_URI,
                    TvContractCompat.PreviewPrograms.COLUMN_PACKAGE_NAME
                ),
                null, null, null
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val title = it.getString(0) ?: continue
                    channelCards.add(ChannelCard(title, it.getString(1), it.getString(2), it.getString(3) ?: ""))
                }
            }
        } catch (_: Exception) {}

        if (channelCards.isNotEmpty()) {
            customRows.add(0, CustomRow("channels_continue", "Continue Watching & Channels", isChannelRow = true, channelItems = channelCards))
        }
    }

    private fun setupRecyclerViews() {
        rvMainRows.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        rowsAdapter = RowsAdapter(customRows, packageManager) { launchApp(it) }
        rvMainRows.adapter = rowsAdapter

        rvDock.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        dockAdapter = DockAdapter(dockPackages, packageManager) { launchApp(it) }
        rvDock.adapter = dockAdapter
    }
        private fun showLauncherSettingsDialog() {
        val options = arrayOf(
            "1. Set Photo or Video Wallpaper (from Download folder)",
            "2. Create New Custom App Row",
            "3. Manage / Delete / Reorder Rows",
            "4. Pin Apps to Bottom Frosted Dock",
            "5. Refresh TV Channels & App List"
        )
        AlertDialog.Builder(this)
            .setTitle("Glass Launcher Settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showWallpaperPicker()
                    1 -> showCreateRowDialog()
                    2 -> showManageRowsDialog()
                    3 -> showEditDockDialog()
                    4 -> {
                        loadSavedData()
                        rowsAdapter.notifyDataSetChanged()
                        dockAdapter.notifyDataSetChanged()
                    }
                }
            }.show()
    }

    private fun showWallpaperPicker() {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val mediaFiles = downloadDir?.listFiles { file ->
            val n = file.name.lowercase()
            n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") ||
                    n.endsWith(".webp") || n.endsWith(".mp4") || n.endsWith(".mkv")
        }?.toList() ?: emptyList()

        if (mediaFiles.isEmpty()) {
            Toast.makeText(this, "Put a .jpg, .png, or .mp4 file into your Firestick's Download folder first!", Toast.LENGTH_LONG).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Select Wallpaper from Downloads")
            .setItems(mediaFiles.map { it.name }.toTypedArray()) { _, index ->
                prefs.edit().putString("wallpaper_path", mediaFiles[index].absolutePath).apply()
                applySavedWallpaper()
            }.show()
    }

    private fun applySavedWallpaper() {
        val path = prefs.getString("wallpaper_path", null) ?: return
        val file = File(path)
        if (!file.exists()) return

        exoPlayer?.release()
        exoPlayer = null
        val isVideo = path.lowercase().endsWith(".mp4") || path.lowercase().endsWith(".mkv")

        if (isVideo) {
            ivPhotoWallpaper.visibility = View.GONE
            tvVideoWallpaper.visibility = View.VISIBLE
            startVideoWallpaper(Uri.fromFile(file))
        } else {
            tvVideoWallpaper.visibility = View.GONE
            ivPhotoWallpaper.visibility = View.VISIBLE
            Glide.with(this).load(file).override(1280, 720).into(ivPhotoWallpaper)
        }
    }

    private fun startVideoWallpaper(uri: Uri) {
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            prepare()
            playWhenReady = true
        }
        if (tvVideoWallpaper.isAvailable) {
            exoPlayer?.setVideoSurface(Surface(tvVideoWallpaper.surfaceTexture))
        } else {
            tvVideoWallpaper.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(s: SurfaceTexture, w: Int, h: Int) {
                    exoPlayer?.setVideoSurface(Surface(s))
                }
                override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) {}
                override fun onSurfaceTextureDestroyed(s: SurfaceTexture): Boolean = true
                override fun onSurfaceTextureUpdated(s: SurfaceTexture) {}
            }
        }
    }

    private fun showCreateRowDialog() {
        val input = EditText(this).apply { hint = "e.g. Movies, Live TV, Sports" }
        AlertDialog.Builder(this)
            .setTitle("New Custom Row Name")
            .setView(input)
            .setPositiveButton("Next: Pick Apps") { _, _ ->
                val title = input.text.toString().trim().ifEmpty { "Custom Row" }
                showPickAppsForRowDialog(CustomRow("row_${System.currentTimeMillis()}", title), true)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPickAppsForRowDialog(row: CustomRow, isNew: Boolean) {
        val installed = getInstalledApps()
        val checked = installed.map { row.packages.contains(it.first) }.toBooleanArray()
        AlertDialog.Builder(this)
            .setTitle("Select Apps for ${row.title}")
            .setMultiChoiceItems(installed.map { it.second }.toTypedArray(), checked) { _, which, isChecked ->
                val pkg = installed[which].first
                if (isChecked) {
                    if (!row.packages.contains(pkg)) row.packages.add(pkg)
                } else {
                    row.packages.remove(pkg)
                }
            }
            .setPositiveButton("Save Row") { _, _ ->
                if (isNew) customRows.add(row)
                saveData()
                rowsAdapter.notifyDataSetChanged()
            }.show()
    }

    private fun showManageRowsDialog() {
        val editableRows = customRows.filter { !it.isChannelRow }
        if (editableRows.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Select Row to Edit / Move / Delete")
            .setItems(editableRows.map { it.title }.toTypedArray()) { _, idx ->
                val target = editableRows[idx]
                AlertDialog.Builder(this)
                    .setTitle(target.title)
                    .setItems(arrayOf("Edit Apps in Row", "Move Row Up", "Move Row Down", "Delete Row")) { _, act ->
                        val actualIdx = customRows.indexOf(target)
                        when (act) {
                            0 -> showPickAppsForRowDialog(target, false)
                            1 -> if (actualIdx > 0) { customRows.removeAt(actualIdx); customRows.add(actualIdx - 1, target) }
                            2 -> if (actualIdx < customRows.size - 1) { customRows.removeAt(actualIdx); customRows.add(actualIdx + 1, target) }
                            3 -> customRows.remove(target)
                        }
                        saveData()
                        rowsAdapter.notifyDataSetChanged()
                    }.show()
            }.show()
    }

    private fun showEditDockDialog() {
        val installed = getInstalledApps()
        val checked = installed.map { dockPackages.contains(it.first) }.toBooleanArray()
        AlertDialog.Builder(this)
            .setTitle("Select Apps for Bottom Frosted Dock")
            .setMultiChoiceItems(installed.map { it.second }.toTypedArray(), checked) { _, which, isChecked ->
                val pkg = installed[which].first
                if (isChecked) {
                    if (!dockPackages.contains(pkg)) dockPackages.add(pkg)
                } else {
                    dockPackages.remove(pkg)
                }
            }
            .setPositiveButton("Save Dock") { _, _ ->
                saveData()
                dockAdapter.notifyDataSetChanged()
            }.show()
    }

    private fun getInstalledApps(): List<Pair<String, String>> {
        val pm = packageManager
        val result = mutableMapOf<String, String>()
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0).forEach {
            val pkg = it.activityInfo.packageName
            if (pkg != packageName) result[pkg] = it.loadLabel(pm).toString()
        }
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).forEach {
            val pkg = it.activityInfo.packageName
            if (pkg != packageName && !result.containsKey(pkg)) result[pkg] = it.loadLabel(pm).toString()
        }
        return result.map { it.key to it.value }.sortedBy { it.second.lowercase() }
    }

    private fun launchApp(pkg: String) {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) startActivity(intent) else Toast.makeText(this, "Cannot launch $pkg", Toast.LENGTH_SHORT).show()
    }

    private fun openFireOsSettings() {
        val intent = packageManager.getLaunchIntentForPackage("com.amazon.tv.settings.v2")
            ?: Intent(android.provider.Settings.ACTION_SETTINGS)
        try { startActivity(intent) } catch (_: Exception) { Toast.makeText(this, "Settings unavailable", Toast.LENGTH_SHORT).show() }
    }

    private fun updateConnectivityStatus() {
        val wifiMgr = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        ivWifiStatus.setTextColor(if (wifiMgr?.isWifiEnabled == true) Color.parseColor("#4ADE80") else Color.parseColor("#64748B"))
        val btOn = BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
        ivBtStatus.setTextColor(if (btOn) Color.parseColor("#38BDF8") else Color.parseColor("#64748B"))
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(statusReceiver, IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        })
        exoPlayer?.playWhenReady = true
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(statusReceiver) } catch (_: Exception) {}
        exoPlayer?.playWhenReady = false
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }
}

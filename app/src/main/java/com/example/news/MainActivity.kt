package com.example.news

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.example.news.BuildConfig
import com.example.news.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Modern way to request permissions and handle the user's choice.
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d("MainActivity_Perms", "Permission GRANTED by user.")
                // If a share was pending, retry it now with the granted permission.
                checkAndShare(pendingShareFilename, pendingShareTitle)
            } else {
                Log.d("MainActivity_Perms", "Permission DENIED by user.")
                // Check if the user selected "Don't ask again".
                // If shouldShowRequestPermissionRationale is now false, it means they did.
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, getRequiredPermission())) {
                    Log.d("MainActivity_Perms", "Permission permanently denied. Showing settings dialog.")
                    showSettingsRedirectDialog()
                } else {
                    Toast.makeText(this, "Permission denied. Cannot share file.", Toast.LENGTH_SHORT).show()
                }
            }
            // Clear the pending request.
            clearPendingShare()
        }

    // --- State variables to hold share data while asking for permission ---
    private var pendingShareFilename: String? = null
    private var pendingShareTitle: String? = null

    // --- BroadcastReceiver for download completion ---
    private val onDownloadComplete: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                Toast.makeText(context, "Download Completed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        registerDownloadReceiver()
        setupOnBackPressed()
        setupWebView()
    }

    // This inner class is the "bridge" that JavaScript can call.
    inner class WebAppInterface {
        @JavascriptInterface
        fun shareVideoFile(filename: String, title: String) {
            Log.d("MainActivity_JS", "JavaScript called shareVideoFile with filename: $filename")
            // This function is called from a background thread by the WebView,
            // so we must switch to the main thread to interact with the UI (like showing dialogs).
            runOnUiThread {
                checkAndShare(filename, title)
            }
        }
    }

    // --- Permission and Sharing Logic ---

    private fun checkAndShare(filename: String?, title: String?) {
        if (filename == null || title == null) return
        // Store details in case we need to ask for permission.
        pendingShareFilename = filename
        pendingShareTitle = title

        val permission = getRequiredPermission()

        when {
            // Case 1: Permission is already granted.
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                Log.d("MainActivity_Perms", "Permission already granted. Sharing file.")
                shareVideoFile(filename, title)
                clearPendingShare()
            }
            // Case 2: User denied before. We should show an explanation.
            ActivityCompat.shouldShowRequestPermissionRationale(this, permission) -> {
                Log.d("MainActivity_Perms", "Showing permission rationale dialog.")
                showPermissionRationaleDialog()
            }
            // Case 3: First time asking OR permission was permanently denied.
            else -> {
                Log.d("MainActivity_Perms", "Requesting permission for the first time or was permanently denied.")
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private fun shareVideoFile(filename: String, title: String) {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val videoFile = File(downloadDir, filename)
        Log.d("MainActivity_Share", "Attempting to share file at path: ${videoFile.absolutePath}")

        if (!videoFile.exists()) {
            Log.e("MainActivity_Share", "SHARE FAILED: File does not exist!")
            Toast.makeText(this, "Error: Downloaded file not found.", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val fileUri: Uri = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.provider", videoFile)
            Log.d("MainActivity_Share", "Successfully got FileProvider URI: $fileUri")

            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val shareIntent = Intent.createChooser(sendIntent, "Share Video")
            startActivity(shareIntent)
            Log.d("MainActivity_Share", "Share Intent started!")
        } catch (e: Exception) {
            Log.e("MainActivity_Share", "SHARE FAILED: Error creating FileProvider URI.", e)
            Toast.makeText(this, "Error: Could not share file.", Toast.LENGTH_LONG).show()
        }
    }

    // --- Helper Dialogs and Functions ---

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Needed")
            .setMessage("To share the video, the app needs permission to access your device's video files.")
            .setPositiveButton("Continue") { _, _ ->
                // User agreed, now request the permission.
                requestPermissionLauncher.launch(getRequiredPermission())
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                clearPendingShare()
            }
            .show()
    }

    private fun showSettingsRedirectDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("You have permanently denied the file access permission. To share videos, please enable it in the app settings.")
            .setPositiveButton("Go to Settings") { _, _ ->
                // Create an intent that opens this app's specific settings screen.
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun getRequiredPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    private fun clearPendingShare() {
        pendingShareFilename = null
        pendingShareTitle = null
    }

    // --- Setup and Lifecycle ---

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun setupWebView() {
        binding.webView.apply {
            settings.javaScriptEnabled = true
            webViewClient = WebViewClient()
            addJavascriptInterface(WebAppInterface(), "AndroidInterface")
            setDownloadListener { url, _, contentDisposition, mimetype, _ ->
                val request = DownloadManager.Request(url.toUri())
                request.setMimeType(mimetype)
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                request.setTitle(fileName)
                request.setDescription("Downloading file...")
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                Toast.makeText(applicationContext, "Download Started...", Toast.LENGTH_SHORT).show()
            }
            loadUrl("https://tejas56789ce11.pythonanywhere.com")
        }
    }

    private fun registerDownloadReceiver() {
        val intentFilter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(onDownloadComplete, intentFilter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(onDownloadComplete, intentFilter)
        }
    }

    private fun setupOnBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(onDownloadComplete)
    }
}
package com.example.news

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.webkit.URLUtil
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.news.databinding.ActivityMainBinding
import android.database.Cursor
import android.util.Log // <-- Make sure you have this import

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private companion object {
        private const val STORAGE_PERMISSION_REQUEST_CODE = 101
    }

    private val onDownloadComplete: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Check if the received broadcast is for a download completion.
            Log.d("DownloadReceiver", "onReceive triggered! Action: ${intent.action}")

            if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {

                // Get the ID of the completed download
                val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (downloadId == -1L) {
                    return // Exit if the ID is not valid
                }

                // Get the DownloadManager system service
                val dm = context.getSystemService(DOWNLOAD_SERVICE) as DownloadManager

                // Use the ID to get the file's URI
                val fileUri = dm.getUriForDownloadedFile(downloadId)

                // Check if the download was successful and the URI is valid
                if (fileUri != null) {
                    Toast.makeText(context, "Download Completed", Toast.LENGTH_SHORT).show()

                    // --- THIS IS THE NEW SHARING LOGIC ---

                    // Create the share intent
                    val shareIntent = Intent(Intent.ACTION_SEND)
                    shareIntent.type = "video/mp4" // Set the MIME type for the video
                    shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri) // Add the video file URI

                    // Add flags to grant read permission to the receiving app
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                    // Create a chooser to show the user all available sharing apps
                    val chooserIntent = Intent.createChooser(shareIntent, "Share Video via...")

                    // Because we are starting an Activity from a BroadcastReceiver,
                    // we MUST add the FLAG_ACTIVITY_NEW_TASK flag.
                    chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    // Launch the share sheet
                    context.startActivity(chooserIntent)

                } else {
                    // Handle the case where the download failed
                    Toast.makeText(context, "Download Failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }



    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val intentFilter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            registerReceiver(onDownloadComplete, intentFilter, RECEIVER_NOT_EXPORTED)
        } else {
            // For older Android versions, no flag is needed.
            @Suppress("DEPRECATION")
            registerReceiver(onDownloadComplete, intentFilter)
        }


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

        checkStoragePermission()
    }
    @SuppressLint("UnspecifiedRegisterReceiverFlag")

    override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return super.registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            return super.registerReceiver(receiver, filter)
        }
    }

    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_PERMISSION_REQUEST_CODE)
        } else {
            setupWebView()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            // We can check if all permissions were granted, but for simplicity,
            // we will proceed to set up the WebView regardless. The app can still
            // function, just the downloads/sharing might not work.

            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permissions Granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permissions Denied. Features may be limited.", Toast.LENGTH_LONG).show()
            }
            setupWebView()
        }
    }


    private fun checkPermissions() {
        // List to hold the permissions we need to request
        val permissionsToRequest = mutableListOf<String>()

        // Notification permission is needed for Android 13 (API 33) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Storage permission is only needed for Android 9 (API 28) and below
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        // If we have permissions to request, ask the user
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), STORAGE_PERMISSION_REQUEST_CODE)
        } else {
            // All permissions are already granted, so we can proceed
            setupWebView()
        }
    }


    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.apply {

            settings.javaScriptEnabled = true
            webViewClient = WebViewClient()
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

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(onDownloadComplete)
    }


}
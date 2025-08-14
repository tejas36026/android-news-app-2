package com.example.news

import android.os.Bundle
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.example.news.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
    }

    private fun setupWebView() {
        binding.webView.apply {
            // Enable JavaScript for your website to function correctly
            settings.javaScriptEnabled = true
            // Ensures that links clicked within the WebView open inside the app
            webViewClient = WebViewClient()
            // Load your web page
            loadUrl("https://tejas56789ce11.pythonanywhere.com")
        }
    }

    // Handle the back button to navigate within the WebView's history
    @Deprecated("This method is deprecated in favor of OnBackPressedDispatcher.")
    override fun onBackPressed() {
        // If the WebView can go back, navigate to the previous page
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            // Otherwise, exit the app as usual
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
package com.example.eggplant

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager.LayoutParams.*
import android.widget.ImageButton
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.widget.Toast

class menu : AppCompatActivity() {
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_menu)
        this.window.setFlags(FLAG_FULLSCREEN, FLAG_FULLSCREEN)
        makeNavigationBarTransparent()
        val captureButton = findViewById<ImageButton>(R.id.imageButtonCapture)
        captureButton.setOnClickListener {
            val intent = Intent(this, CameraX::class.java)
            startActivity(intent)
        }

        val infoButton = findViewById<ImageButton>(R.id.infoimageButton)
        val promptImage = findViewById<ImageView>(R.id.promptImageView)

        // Initially, hide the prompt
        promptImage.visibility = View.INVISIBLE

        infoButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Show prompt when button is pressed
                    promptImage.visibility = View.VISIBLE
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Hide prompt when button is released
                    promptImage.visibility = View.INVISIBLE
                }
            }
            true
        }
        val galleryButton = findViewById<ImageButton>(R.id.imageButtonGallery)
        galleryButton.setOnClickListener {
            //val intent = Intent(this, Gallery::class.java)
            //startActivity(intent)
            galleryLauncher.launch("image/*") // Open gallery
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val intent = Intent(this, results::class.java)
            intent.putExtra("imageUri", it.toString()) // Pass image URI to results activity
            startActivity(intent)
            Toast.makeText(this, "Uploading Image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun makeNavigationBarTransparent() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
        } else {
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
    }
}
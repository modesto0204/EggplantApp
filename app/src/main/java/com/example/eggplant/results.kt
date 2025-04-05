package com.example.eggplant

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.Image
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class results : ComponentActivity() {

    private lateinit var interpreter: Interpreter
    private lateinit var selectedImageView: ImageView
    private lateinit var btnChooseImage: ImageButton
    private lateinit var textViewEdibilityLevel: TextView
    private var selectedBitmap: Bitmap? = null

    @SuppressLint("ClickableViewAccessibility", "MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_results)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val recaptureButton: ImageButton = findViewById(R.id.recaptureButton)
        val saveimageButton: ImageButton = findViewById(R.id.saveimagebutton)
        val backimagebutton: ImageButton = findViewById(R.id.backimagebutton)
        selectedImageView = findViewById(R.id.selectedImageView)
        btnChooseImage = findViewById(R.id.btnChooseImage)
        textViewEdibilityLevel = findViewById(R.id.edibilityplaceholder)

        // Load TensorFlow Lite model
        loadModel()

        // Button to choose an image
        btnChooseImage.setOnClickListener {
            galleryLauncher.launch("image/*") // Open gallery for reupload
        }

        // Get captured image path from intent
        val imagePath = intent.getStringExtra("imagePath")
        val imageUri = intent.getStringExtra("imageUri")

        if (imagePath != null) {
            // Load captured image
            val capturedBitmap = BitmapFactory.decodeFile(imagePath)
            Glide.with(this)
                .load(imagePath)
                .transform(RoundedCorners(dpToPx(30))) // Apply rounded corners
                .into(selectedImageView)
            selectedBitmap = capturedBitmap
        } else if (imageUri != null) {
            // Load uploaded image
            val uri = Uri.parse(imageUri)
            Glide.with(this)
                .load(uri)
                .transform(RoundedCorners(dpToPx(30))) // Apply rounded corners
                .into(selectedImageView)

            val inputStream = contentResolver.openInputStream(uri)
            selectedBitmap = BitmapFactory.decodeStream(inputStream)
        }

        // Automatically classify the image if available
        selectedBitmap?.let {
            autoClassify(it)
        }

        requestStoragePermissions()
        // Screenshot Button Report summary
        saveimageButton.setOnClickListener {
            selectedBitmap?.let { bitmap ->
                Toast.makeText(this, "Saving Image", Toast.LENGTH_SHORT).show()
                saveImageToGallery(bitmap)
            } ?: Toast.makeText(this, "No image to save", Toast.LENGTH_SHORT).show()
            captureScreenshot()
        }

        backimagebutton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        recaptureButton.setOnClickListener {
            // Navigate back to CameraActivity
            val intent = Intent(this, CameraX::class.java)
            startActivity(intent)
            finish()
        }

        window.decorView.post {
            findViewById<View>(android.R.id.content).invalidate()
        }

        val descriptionimageview : ImageView = findViewById(R.id.descriptionimageview)
        val labelimageview : ImageView = findViewById(R.id.labelimagview)
        val imageviewpositiveresult : ImageView = findViewById(R.id.imageViewPositiveResult)
        val saveimagebutton : ImageView = findViewById(R.id.saveimagebutton)
        Handler(Looper.getMainLooper()).postDelayed({
            btnChooseImage.setImageResource(R.drawable.rectangle3)
            recaptureButton.setImageResource(R.drawable.rectangle3)
            descriptionimageview.setImageResource(R.drawable.rectangle2)
            labelimageview.setImageResource(R.drawable.rectangle)
            imageviewpositiveresult.setImageResource(R.drawable.result1)
            backimagebutton.setImageResource(R.drawable.back)
            saveimagebutton.setImageResource(R.drawable.save)
        }, 300)

        val infoButton = findViewById<ImageButton>(R.id.resultsinfoImageButton)
        val promptImage = findViewById<ImageView>(R.id.imageView3)
        val prompttext = findViewById<TextView>(R.id.prompttext)

        // Initially, hide the prompt
        promptImage.visibility = View.INVISIBLE

        infoButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Show prompt when button is pressed
                    promptImage.visibility = View.VISIBLE
                    prompttext.visibility = View.VISIBLE
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Hide prompt when button is released
                    promptImage.visibility = View.INVISIBLE
                    prompttext.visibility = View.INVISIBLE
                }
            }
            true
        }
    }

    // Handle the result of the gallery selection
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val intent = Intent(this, results::class.java)
            intent.putExtra("imageUri", it.toString()) // Pass image URI to reload classification
            startActivity(intent)
            Toast.makeText(this, "Uploading Image", Toast.LENGTH_SHORT).show()
            finish() // Close the current activity to refresh
        }
    }

    fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun captureScreenshot() {
        val rootView = window.decorView.rootView
        val backButton: ImageButton = findViewById(R.id.backimagebutton)
        val saveButton: ImageButton = findViewById(R.id.saveimagebutton) // Update ID if needed

        // Hide buttons before capturing
        backButton.visibility = View.INVISIBLE
        saveButton.visibility = View.INVISIBLE

        // Create a full-screen bitmap
        val fullBitmap = Bitmap.createBitmap(rootView.width, rootView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(fullBitmap)
        rootView.draw(canvas)

        // Restore button visibility
        backButton.visibility = View.VISIBLE
        saveButton.visibility = View.VISIBLE

        // Define the cropped height (3/4 of the screen)
        val croppedHeight = (fullBitmap.height * 0.77).toInt()

        // Crop the bitmap from the top
        val croppedBitmap = Bitmap.createBitmap(fullBitmap, 0, 0, fullBitmap.width, croppedHeight)

        // Save the cropped image
        saveImageToGallery(croppedBitmap)
    }
    private fun requestStoragePermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(this, "Storage permission is required to save screenshots", Toast.LENGTH_LONG).show()
            }
        }
    private fun saveImageToGallery(bitmap: Bitmap) {
        val filename = "IMG_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/EggplantApp")
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        uri?.let {
            contentResolver.openOutputStream(it)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                Toast.makeText(this, "Image Saved!", Toast.LENGTH_SHORT).show()

                // Show notification with intent to open gallery
                showImageSavedNotification(it)
            }
        } ?: run {
            Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showImageSavedNotification(imageUri: Uri) {
        val channelId = "image_saved_channel"
        val notificationId = 1

        // Create an intent to open the saved image in the gallery
        val openImageIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(imageUri, "image/*")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, openImageIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create notification channel (for Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Image Saved", NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when an image is successfully saved"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        // Build and show the notification
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.eggplant) // Change to your app's icon
            .setContentTitle("Image Saved")
            .setContentText("Tap to view in gallery")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this).notify(notificationId, notification)

        triggerVibration(this)
    }

    private fun triggerVibration(context: Context) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        // Check if the device supports vibration
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrationEffect = VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(vibrationEffect)
            } else {
                vibrator.vibrate(500) // Vibrate for 500 milliseconds
            }
        }
    }


    // Convert the bitmap to ByteBuffer
    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(224 * 224 * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(224 * 224)
        bitmap.getPixels(pixels, 0, 224, 0, 0, 224, 224)
        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16 and 0xFF) - 103.939f)) // B
            buffer.putFloat(((pixel shr 8 and 0xFF) - 116.779f))  // G
            buffer.putFloat(((pixel and 0xFF) - 123.68f))        // R
        }
        buffer.rewind()
        return buffer
    }



    private fun autoClassify(bitmap: Bitmap) {
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        val byteBuffer = convertBitmapToByteBuffer(scaledBitmap)
        val output = TensorBuffer.createFixedSize(intArrayOf(1, 5), org.tensorflow.lite.DataType.FLOAT32)
        interpreter.run(byteBuffer, output.buffer)

        val predictions = output.floatArray
        val labels = listOf("Acceptable", "Moderate", "Not an Eggplant", "Optimal", "Unsafe")

        val result = labels[predictions.indices.maxByOrNull { predictions[it] } ?: -1]
        textViewEdibilityLevel.text = result

        val optimalplaceholder: TextView = findViewById(R.id.optimalplaceholder)
        val acceptableplaceholder: TextView = findViewById(R.id.acceptableplaceholder)
        val moderateplaceholder: TextView = findViewById(R.id.moderateplaceholder)
        val unsafeplaceholder: TextView = findViewById(R.id.unsafeplaceholder)
        val notaneggplantplaceholder: TextView = findViewById(R.id.notaneggplantplaceholder)
        val notaneggplantlabelimagview: ImageView = findViewById(R.id.notaneggplantlabelimagview)
        val notaneggplantdescriptionimageview: ImageView = findViewById(R.id.notaneggplantdescriptionimageview)

        val description = when (result) {
            "Optimal" -> {
                optimalplaceholder.visibility = View.VISIBLE
            }
            "Acceptable" -> {
                acceptableplaceholder.visibility = View.VISIBLE
            }
            "Moderate" -> {
                moderateplaceholder.visibility = View.VISIBLE
            }
            "Unsafe" -> {
                unsafeplaceholder.visibility = View.VISIBLE
            }
            "Not an Eggplant" -> {
                notaneggplantplaceholder.visibility = View.VISIBLE
            }
            else -> "Unable to determine the edibility of this item." // Default case
        }

        if (result == "Not an Eggplant") {
            notaneggplantlabelimagview.visibility = View.VISIBLE
            notaneggplantdescriptionimageview.visibility = View.VISIBLE

        } else {
            notaneggplantlabelimagview.visibility = View.INVISIBLE
            notaneggplantdescriptionimageview.visibility = View.INVISIBLE

        }
    }

    private fun loadModel() {
        // Load the model file as a MappedByteBuffer
        val assetFileDescriptor = assets.openFd("eggplant_model.tflite")
        val fileInputStream = assetFileDescriptor.createInputStream()
        val fileChannel = fileInputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength

        val modelByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        interpreter = Interpreter(modelByteBuffer) // Pass the ByteBuffer to the Interpreter
    }

}
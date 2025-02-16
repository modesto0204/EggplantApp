package com.example.eggplant

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.tensorflow.lite.support.common.FileUtil
import java.nio.channels.FileChannel

class Gallery : ComponentActivity() {

    private lateinit var interpreter: Interpreter
    private lateinit var selectedImageView: ImageView
    private lateinit var btnChooseImage: Button
    private lateinit var btnClassify: Button
    private var selectedBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_gallery)

        val recaptureButton: Button = findViewById(R.id.recaptureButton)
        selectedImageView = findViewById(R.id.selectedImageView)
        btnChooseImage = findViewById(R.id.btnChooseImage)
        btnClassify = findViewById(R.id.btnClassify)

        // Load TensorFlow Lite model
        loadModel()

        // Button to choose an image
        btnChooseImage.setOnClickListener {
            openGallery()
        }

        // Get the captured image path from the intent
        val imagePath = intent.getStringExtra("imagePath")
        val capturedBitmap = intent.getParcelableExtra<Bitmap>("capturedBitmap")
        // Load the image into the ImageView using Glide
        if (imagePath != null) {
            Glide.with(this).load(imagePath).into(selectedImageView)
        }

        // Button to classify the selected image
        btnClassify.setOnClickListener {
            // Check if there's a captured or selected image
            val bitmapToProcess = capturedBitmap ?: selectedBitmap

            bitmapToProcess?.let {
                val scaledBitmap = Bitmap.createScaledBitmap(it, 224, 224, true)
                val byteBuffer = convertBitmapToByteBuffer(scaledBitmap)
                val result = classifyImage(byteBuffer)
                Toast.makeText(this, "Classification Result: $result", Toast.LENGTH_LONG).show()
            } ?: run {
                Toast.makeText(this, "Please capture or select an image first!", Toast.LENGTH_SHORT).show()
            }
        }

        recaptureButton.setOnClickListener {
            // Navigate back to CameraActivity
            val intent = Intent(this, CameraX::class.java)
            startActivity(intent)
            finish()
        }
    }

    // Open gallery for image selection
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        galleryLauncher.launch(intent)
    }

    // Handle the result of the gallery selection
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            val inputStream = contentResolver.openInputStream(uri!!)
            selectedBitmap = BitmapFactory.decodeStream(inputStream)
            selectedImageView.setImageBitmap(selectedBitmap) // Display selected image
        } else {
            Toast.makeText(this, "Image selection canceled.", Toast.LENGTH_SHORT).show()
        }
    }

    // Convert the bitmap to ByteBuffer
    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(224 * 224 * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(224 * 224)
        bitmap.getPixels(pixels, 0, 224, 0, 0, 224, 224)
        for (pixel in pixels) {
            buffer.putFloat((pixel shr 16 and 0xFF) / 255.0f) // Red
            buffer.putFloat((pixel shr 8 and 0xFF) / 255.0f)  // Green
            buffer.putFloat((pixel and 0xFF) / 255.0f)       // Blue
        }
        buffer.rewind()
        return buffer
    }

    // Classify the image using the TensorFlow Lite model
    private fun classifyImage(byteBuffer: ByteBuffer): String {
        val output = TensorBuffer.createFixedSize(intArrayOf(1, 5), org.tensorflow.lite.DataType.FLOAT32)
        interpreter.run(byteBuffer, output.buffer)

        val predictions = output.floatArray
        val labels = listOf("Optimal Edibility", "Acceptable Edibility", "Moderate Edibility", "Unsafe Edibility", "Not an Eggplant")
        return labels[predictions.indices.maxByOrNull { predictions[it] } ?: -1]
    }

    // Load TensorFlow Lite model
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

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TFLiteModelHelper(context: Context, modelPath: String) {

    private val interpreter: Interpreter

    init {
        val modelFile = loadModelFile(context, modelPath)
        interpreter = Interpreter(modelFile)
    }

    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun predict(inputData: Array<FloatArray>): FloatArray {
        val outputData = Array(1) { FloatArray(4) }  // Adjust to match the output shape
        interpreter.run(inputData, outputData)
        return outputData[0]
    }

    fun close() {
        interpreter.close()
    }
}

val modelHelper = TFLiteModelHelper(context, "eggplant_model.tflite")

// Prepare input data
val inputData = Array(1) { FloatArray(28 * 28) }  // Adjust size for your model input
// Fill inputData with your preprocessed image data

// Run inference
val predictions = modelHelper.predict(inputData)
modelHelper.close()

// Find the class with the highest confidence
val predictedClass = predictions.indices.maxByOrNull { predictions[it] }
println("Predicted Class: $predictedClass")

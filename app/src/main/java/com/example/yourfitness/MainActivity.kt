package com.example.yourfitness

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.IOException
import android.annotation.SuppressLint
import android.graphics.Canvas
import android.util.Log
import android.graphics.Matrix
import android.graphics.Paint
import androidx.cardview.widget.CardView
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import android.graphics.Color
import android.widget.TextView
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.atan2

class MainActivity : AppCompatActivity() {


    var galleryCard: CardView? = null
    var cameraCard: CardView? = null
    var imageView: ImageView? = null
    var resultTV: TextView? = null
    var image_uri: Uri? = null
    val PERMISSION_CODE = 100

    // Gallery Activity Result
    private val galleryActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        ActivityResultCallback { result ->
            if (result != null && result.resultCode == RESULT_OK) {
                image_uri = result.data!!.data
                val inputImage = uriToBitmap(image_uri!!)
                val rotated = rotateBitmap(inputImage)
                imageView!!.setImageBitmap(rotated)
                performPoseDetection(rotated)
            }
        })

    // Camera Activity Result
    private val cameraActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        ActivityResultCallback { result ->
            if (result.resultCode == RESULT_OK) {
                val inputImage = uriToBitmap(image_uri!!)
                val rotated = rotateBitmap(inputImage)
                imageView!!.setImageBitmap(rotated)
                performPoseDetection(rotated)
            }
        })

    // Accurate pose detector on static images, when depending on the pose-detection-accurate sdk
    val options = AccuratePoseDetectorOptions.Builder()
        .setDetectorMode(AccuratePoseDetectorOptions.SINGLE_IMAGE_MODE)
        .build()
    val poseDetector = PoseDetection.getClient(options)

    fun performPoseDetection(inputBmp: Bitmap) {
        val image = InputImage.fromBitmap(inputBmp, 0)
        poseDetector.process(image)
            .addOnSuccessListener { results ->
                // Task completed successfully
                // ...
                Log.d("pose", results.allPoseLandmarks.size.toString())
                drawPose(inputBmp, results)
            }
            .addOnFailureListener { e ->
                // Task failed with an exception
                // ...
            }
    }

    fun drawPose(inputBmp: Bitmap, pose: Pose) {
        var mutable = inputBmp.copy(Bitmap.Config.ARGB_8888, true)
        var canvas = Canvas(mutable)

        var paint = Paint()
        paint.color = Color.RED
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f

        var paintLeft = Paint()
        paintLeft.color = Color.BLUE
        paintLeft.style = Paint.Style.STROKE
        paintLeft.strokeWidth = 5f

        var paintRight = Paint()
        paintRight.color = Color.YELLOW
        paintRight.style = Paint.Style.STROKE
        paintRight.strokeWidth = 5f

        pose.allPoseLandmarks.forEach{
            canvas.drawPoint(it.position.x, it.position.y, paint)
        }

        // DRAW ARMS
        drawPoseLines(pose, canvas,PoseLandmark.LEFT_WRIST, PoseLandmark.LEFT_ELBOW, paintLeft)
        drawPoseLines(pose, canvas,PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_SHOULDER, paintLeft)
        drawPoseLines(pose, canvas,PoseLandmark.RIGHT_WRIST, PoseLandmark.RIGHT_ELBOW, paintRight)
        drawPoseLines(pose, canvas,PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_SHOULDER, paintRight)

        // DRAW LEGS
        drawPoseLines(pose, canvas,PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, paintLeft)
        drawPoseLines(pose, canvas,PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE, paintLeft)
        drawPoseLines(pose, canvas,PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, paintRight)
        drawPoseLines(pose, canvas,PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE, paintRight)

        // DRAW BODY
        drawPoseLines(pose, canvas,PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER, paintLeft)
        drawPoseLines(pose, canvas,PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP, paintLeft)
        drawPoseLines(pose, canvas,PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_SHOULDER, paintLeft)
        drawPoseLines(pose, canvas,PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_SHOULDER, paintRight)

        imageView?.setImageBitmap(mutable);
        val result = validateDownwardDog(pose);
        resultTV?.setText(result)
        Log.d("tryRes",result)
    }

    fun drawPoseLines(pose: Pose, canvas: Canvas, startPoint: Int, endPoint: Int, paint: Paint) {
        var pointStart = pose.getPoseLandmark(startPoint)
        var pointEnd = pose.getPoseLandmark(endPoint)

        if (pointStart != null && pointEnd != null) {
            canvas.drawLine(pointStart.position.x, pointStart.position.y, pointEnd.position.x, pointEnd.position.y, paint)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize views
        galleryCard = findViewById(R.id.galleryCard)
        cameraCard = findViewById(R.id.cameraCard)
        imageView = findViewById(R.id.imageView)
        resultTV = findViewById(R.id.textViewRes)

        // Check and request permissions
        checkAndRequestPermissions()

        //TODO chose image from gallery
        // Pick from gallery
        galleryCard!!.setOnClickListener {
            val galleryIntent =
                Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            galleryActivityResultLauncher.launch(galleryIntent)
        }

        // Capture from camera
        cameraCard!!.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                openCamera()
            } else {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), PERMISSION_CODE)
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }

        // Android 13+ requires READ_MEDIA_IMAGES instead of READ_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            // For Android versions below Tiramisu, we request READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissions(permissionsToRequest.toTypedArray(), 1211)
        }
    }

    private fun openCamera() {
        val values = ContentValues()
        values.put(MediaStore.Images.Media.TITLE, "New Picture")
        values.put(MediaStore.Images.Media.DESCRIPTION, "From the Camera")
        image_uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, image_uri)
        cameraActivityResultLauncher.launch(cameraIntent)
    }

    //TODO takes URI of the image and returns bitmap
    private fun uriToBitmap(selectedFileUri: Uri): Bitmap? {
        return try {
            val parcelFileDescriptor = contentResolver.openFileDescriptor(selectedFileUri, "r")
            val fileDescriptor = parcelFileDescriptor!!.fileDescriptor
            val image = BitmapFactory.decodeFileDescriptor(fileDescriptor)
            parcelFileDescriptor.close()
            image
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
    //TODO rotate image if image captured on samsung devices
//TODO Most phone cameras are landscape, meaning if you take the photo in portrait, the resulting photos will be rotated 90 degrees.
    @SuppressLint("Range")
    fun rotateBitmap(input: Bitmap?): Bitmap {
        val orientationColumn = arrayOf(MediaStore.Images.Media.ORIENTATION)
        val cur = contentResolver.query(image_uri!!, orientationColumn, null, null, null)
        var orientation = -1
        if (cur != null && cur.moveToFirst()) {
            orientation = cur.getInt(cur.getColumnIndex(orientationColumn[0]))
            cur.close()
        }
        Log.d("tryOrientation", orientation.toString() + "")
        val rotationMatrix = Matrix()
        rotationMatrix.setRotate(orientation.toFloat())
        return Bitmap.createBitmap(
            input!!,
            0,
            0,
            input.width,
            input.height,
            rotationMatrix,
            true
        )
    }

    fun validateDownwardDog(pose: Pose): String {
        // Extract landmarks
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

        // Ensure all landmarks are detected
        val landmarks = listOf(
            leftWrist, rightWrist, leftElbow, rightElbow, leftShoulder,
            rightShoulder, leftHip, rightHip, leftKnee, rightKnee, leftAnkle, rightAnkle
        )

        if (landmarks.any { it == null }) {
            return "Unable to detect full body. Ensure hands and feet are visible."
        }

        // Calculate angles
        val leftArmAngle = calculateAngle(leftWrist!!, leftElbow!!, leftShoulder!!)
        val rightArmAngle = calculateAngle(rightWrist!!, rightElbow!!, rightShoulder!!)
        val leftLegAngle = calculateAngle(leftHip!!, leftKnee!!, leftAnkle!!)
        val rightLegAngle = calculateAngle(rightHip!!, rightKnee!!, rightAnkle!!)

        val armsStraight = (leftArmAngle in 130.0..200.0) && (rightArmAngle in 130.0..200.0)
        val legsStraight = (leftLegAngle in 130.0..190.0) && (rightLegAngle in 130.0..190.0)

        Log.d("PoseDebug", "Arms straight: $armsStraight | Left arm: $leftArmAngle | Right arm: $rightArmAngle")
        Log.d("PoseDebug", "Legs straight: $legsStraight | Left leg: $leftLegAngle | Right leg: $rightLegAngle")

        if (!armsStraight) {
            return "Great effort! Try straightening your arms a little more."
        }

        if (!legsStraight) {
            return "You're almost there! Straighten your legs a bit for a deeper stretch."
        }

        // Hip should be above shoulders
        if (leftHip!!.position.y > leftShoulder!!.position.y || rightHip!!.position.y > rightShoulder!!.position.y) {
            return "Nice work! Try lifting your hips higher to form an inverted V shape."
        }

        // Optional heel position check
        // val heelsTouching = abs(leftAnkle.position.y - leftKnee.position.y) < 20 &&
        //                     abs(rightAnkle.position.y - rightKnee.position.y) < 20
        // if (!heelsTouching) {
        //     return "Good job! Over time, work towards lowering your heels for a deeper stretch."
        // }

        return "Perfect! Your Downward Dog pose looks amazing!"
    }

    fun calculateAngle(firstPoint: PoseLandmark, midPoint: PoseLandmark, lastPoint: PoseLandmark): Double {
        val result = Math.toDegrees(
            (atan2(
                lastPoint.position.y - midPoint.position.y,
                lastPoint.position.x - midPoint.position.x
            ) -
                    atan2(
                        firstPoint.position.y - midPoint.position.y,
                        firstPoint.position.x - midPoint.position.x
                    )).toDouble()
        ).let { angle ->
            abs(angle).let {
                if (it > 180) 360 - it else it
            }
        }

        return result
    }
}


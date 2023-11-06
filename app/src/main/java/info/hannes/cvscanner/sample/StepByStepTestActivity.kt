package info.hannes.cvscanner.sample

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.*
import android.util.Log
import android.util.TimingLogger
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import info.hannes.cvscanner.util.CVProcessor
import info.hannes.cvscanner.util.Line
import info.hannes.cvscanner.util.Util
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.FileNotFoundException
import java.io.IOException
import java.util.*
import kotlin.math.*

class StepByStepTestActivity : AppCompatActivity() {

    private val REQ_PICK_IMAGE = 1
    private val REQ_STORAGE_PERM = 11
    private var scanType: CVCommand? = null
    private lateinit var contentView: RecyclerView
    private lateinit var imageAdapter: ImageAdapter
    private lateinit var fab: FloatingActionButton
    private var mData: Mat? = null
    private var baseLoaderCallback: BaseLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            super.onManagerConnected(status)
            fab.scaleX = 0.1f
            fab.scaleY = 0.1f
            fab.alpha = 0.4f
            fab.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .start()
        }
    }
    private var imgThread: HandlerThread? = null
    private var testRunner: CVTestRunner? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_step_by_step)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        if (intent.extras != null) scanType = intent.extras!!.getSerializable(EXTRA_SCAN_TYPE) as CVCommand?
        fab = findViewById(R.id.fab)
        fab.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            startActivityForResult(intent, REQ_PICK_IMAGE)
        }
        contentView = findViewById(R.id.image_list)
        contentView.layoutManager = LinearLayoutManager(this)
        //contentView.setHasFixedSize(true);
        imageAdapter = ImageAdapter()
        contentView.adapter = imageAdapter
        val result = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (result != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQ_STORAGE_PERM)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!OpenCVLoader.initDebug()) {
            //OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_1_0, getApplicationContext(), mCallback);
        } else
            baseLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
    }

    override fun onDestroy() {
        super.onDestroy()
        imageAdapter.clear()
        mData?.release()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            try {
                val image = BitmapFactory.decodeStream(contentResolver.openInputStream(data.data!!))
                mData?.release()
                mData = Mat()
                Utils.bitmapToMat(image, mData)
                image.recycle()
                startTests()
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode != REQ_STORAGE_PERM) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            return
        }
        if (grantResults.isEmpty() || grantResults[0] == PackageManager.PERMISSION_DENIED) {
            finish()
        }
    }

    private fun startTests() {
        imageAdapter.clear()
        if (imgThread == null || testRunner == null) {
            imgThread = HandlerThread("Image processor thread")
            imgThread!!.start()
            testRunner = CVTestRunner(imgThread!!.looper)
        }
        if (mData != null) {
            val msg = Message()
            msg.obj = CVTestMessage(scanType, mData!!)
            testRunner!!.sendMessage(msg)
        }
    }

    fun onNextStep(img: Mat?) {
        //Bitmap result = Bitmap.createBitmap(img.cols(), img.rows(), Bitmap.Config.ARGB_8888);
        //Utils.matToBitmap(img, result);
        //final String path = Utility.saveBitmapJPG(result, "cvsample_" + Calendar.getInstance().getTimeInMillis() + ".jpg");
        try {
            val imagePath = Util.saveImage(
                this, "cvsample_" + Calendar.getInstance().timeInMillis + ".jpg",
                img!!, true
            )
            val uri = Util.getUriFromPath(imagePath)
            runOnUiThread { imageAdapter.add(uri) }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun drawRect(img: Mat?, points: Array<Point?>?) {
        if (img == null || points == null || points.size != 4) return
        val sorted = CVProcessor.sortPoints(points)
        Imgproc.line(img, sorted[0], sorted[1], Scalar(250.0, 0.0, 0.0), 2)
        Imgproc.line(img, sorted[0], sorted[3], Scalar(250.0, 0.0, 0.0), 2)
        Imgproc.line(img, sorted[1], sorted[2], Scalar(250.0, 0.0, 0.0), 2)
        Imgproc.line(img, sorted[3], sorted[2], Scalar(250.0, 0.0, 0.0), 2)
    }

    fun findAngleBetweenLines(firstSrc: Point, firstDest: Point, secSrc: Point, secDest: Point): Double {
        var theta = Math.PI
        if (firstSrc.x == firstDest.x) //infinite slope
        {
            if (secSrc.x != secDest.x) {
                val secSlope = (secDest.y - secSrc.y) / (secDest.x - secSrc.x)
                theta = Math.atan2(1.0, Math.abs(secSlope))
            }
        } else if (secSrc.x == secDest.x) {
            if (firstSrc.x != firstDest.x) {
                val firstSlope = (firstDest.y - firstSrc.y) / (firstDest.x - firstSrc.x)
                theta = atan2(1.0, Math.abs(firstSlope))
            }
        } else {
            val firstSlope = (firstDest.y - firstSrc.y) / (firstDest.x - firstSrc.x)
            val secSlope = (secDest.y - secSrc.y) / (secDest.x - secSrc.x)
            theta = if (firstSlope == 0.0) Math.atan2(
                Math.abs(secDest.y - secSrc.y),
                Math.abs(secDest.x - secSrc.x)
            ) else if (secSlope == 0.0) Math.atan2(
                Math.abs(firstDest.y - firstSrc.y),
                Math.abs(firstDest.x - firstSrc.x)
            ) else Math.atan2(Math.abs(firstSlope - secSlope), Math.abs(1 + firstSlope * secSlope))
        }
        return Math.abs(theta)
    }

    fun buildSkeleton(img: Mat): Mat {
        val morph = Imgproc.getStructuringElement(Imgproc.CV_SHAPE_CROSS, Size(3.0, 3.0))
        val skel = Mat(img.size(), CvType.CV_8UC1, Scalar.all(0.0))
        val eroded = Mat()
        val temp = Mat()
        var done: Boolean
        do {
            Imgproc.morphologyEx(img, eroded, Imgproc.MORPH_ERODE, morph)
            Imgproc.morphologyEx(eroded, temp, Imgproc.MORPH_DILATE, morph)
            Core.subtract(img, temp, temp)
            Core.bitwise_or(skel, temp, skel)
            eroded.copyTo(img)
            done = Core.countNonZero(img) == 0
        } while (!done)
        return skel
    }

    internal inner class CVTestMessage(var command: CVCommand?, var input: Mat)

    inner class CVTestRunner(looper: Looper?) : Handler(looper) {
        val TAG = "CV-TEST"
        var foundPoints: Array<Point>? = null

        override fun handleMessage(msg: Message) {
            val FIXED_HEIGHT = 600
            if (msg.obj is CVTestMessage) {
                val data = msg.obj as CVTestMessage
                when (data.command) {
                    CVCommand.DOCUMENT_SCAN -> {
                        val timingLogger = TimingLogger(TAG, "Detect Document")
                        val img = data.input.clone()
                        data.input.release()
                        //find contours
                        val ratio = img.size().height / FIXED_HEIGHT
                        val width = (img.size().width / ratio).toInt()
                        val height = (img.size().height / ratio).toInt()
                        val newSize = Size(width.toDouble(), height.toDouble())
                        val resizedImg = Mat(newSize, CvType.CV_8UC4)
                        Imgproc.resize(img, resizedImg, newSize)
                        onNextStep(resizedImg)
                        Imgproc.medianBlur(resizedImg, resizedImg, 5)
                        onNextStep(resizedImg)
                        val cannedImg = Mat(newSize, CvType.CV_8UC1)
                        Imgproc.Canny(resizedImg, cannedImg, 70.0, 200.0, 3, true)
                        resizedImg.release()
                        onNextStep(cannedImg)
                        Imgproc.threshold(cannedImg, cannedImg, 70.0, 255.0, Imgproc.THRESH_OTSU)
                        onNextStep(cannedImg)
                        val dilatedImg = Mat(newSize, CvType.CV_8UC1)
                        val morph = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
                        Imgproc.dilate(cannedImg, dilatedImg, morph, Point(-1.0, -1.0), 2, 1, Scalar(1.0))
                        cannedImg.release()
                        morph.release()
                        onNextStep(dilatedImg)
                        timingLogger.addSplit("Segmentation")
                        val contours = ArrayList<MatOfPoint>()
                        val hierarchy = Mat()
                        Imgproc.findContours(dilatedImg, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
                        hierarchy.release()
                        Log.d(TAG, "contours found: " + contours.size)
                        contours.sortWith { o1, o2 -> java.lang.Double.compare(Imgproc.contourArea(o2), Imgproc.contourArea(o1)) }
                        Imgproc.drawContours(dilatedImg, contours, 0, Scalar(255.0, 255.0, 250.0))
                        onNextStep(dilatedImg)
                        dilatedImg.release()
                        timingLogger.addSplit("Find contours")
                        var rectContour: MatOfPoint? = null
                        foundPoints = null
                        for (contour in contours) {
                            val mat = MatOfPoint2f(*contour.toArray())
                            val peri = Imgproc.arcLength(mat, true)
                            val approx = MatOfPoint2f()
                            Imgproc.approxPolyDP(mat, approx, 0.02 * peri, true)
                            val points = approx.toArray()
                            Log.d("SCANNER", "approx size " + points.size)
                            if (points.size == 4) {
                                val spoints = CVProcessor.sortPoints(points)
                                if (CVProcessor.isInside(spoints, newSize) && CVProcessor.isLargeEnough(spoints, newSize, 0.40)) {
                                    rectContour = contour
                                    foundPoints = spoints
                                    break
                                }
                            }
                        }
                        timingLogger.addSplit("Find points")
                        if (rectContour != null) {
                            val scaledPoints = arrayOfNulls<Point>(foundPoints!!.size)
                            var i = 0
                            while (i < foundPoints.size) {
                                scaledPoints[i] = Point(foundPoints[i].x * ratio, foundPoints[i].y * ratio)
                                i++
                            }
                            Log.d("SCANNER", "drawing lines")
                            Imgproc.line(img, scaledPoints[0], scaledPoints[1], Scalar(250.0, 20.0, 20.0))
                            Imgproc.line(img, scaledPoints[0], scaledPoints[3], Scalar(250.0, 20.0, 20.0))
                            Imgproc.line(img, scaledPoints[1], scaledPoints[2], Scalar(250.0, 20.0, 20.0))
                            Imgproc.line(img, scaledPoints[3], scaledPoints[2], Scalar(250.0, 20.0, 20.0))
                            timingLogger.addSplit("Upscaling points, drawing lines")
                        }
                        onNextStep(img)
                        timingLogger.dumpToLog()
                        img.release()
                    }
                    CVCommand.PASSPORT_SCAN_HOUGHLINES -> {
                        val timingLogger = TimingLogger(TAG, "Detecting Passport - HoughLinesP")
                        val img = data.input.clone()
                        data.input.release()
                        //find contours
                        val ratio = img.size().height / FIXED_HEIGHT
                        val widthI = (img.size().width / ratio)
                        val heightI = (img.size().height / ratio)
                        val newSize = Size(widthI.toDouble(), heightI.toDouble())
                        val resizedImg = Mat(newSize, CvType.CV_8UC4)
                        Imgproc.resize(img, resizedImg, newSize)
                        onNextStep(resizedImg)
                        Imgproc.medianBlur(resizedImg, resizedImg, 13)
                        onNextStep(resizedImg)
                        val cannedImg = Mat(newSize, CvType.CV_8UC1)
                        Imgproc.Canny(resizedImg, cannedImg, 70.0, 200.0, 3, true)
                        //resizedImg.release();
                        onNextStep(cannedImg)

                        val morphR = Imgproc.getStructuringElement(Imgproc.CV_SHAPE_RECT, Size(5.0, 5.0))
                        Imgproc.morphologyEx(cannedImg, cannedImg, Imgproc.MORPH_CLOSE, morphR, Point(-1.0, -1.0), 1)
                        timingLogger.addSplit("Segmentation")
                        val lines = MatOfFloat4()
                        Imgproc.HoughLinesP(cannedImg, lines, 1.0, Math.PI / 180, 30, 30.0, 150.0)
                        timingLogger.addSplit("Hough lines")
                        Log.d("SCANNER", "got lines: " + lines.rows())
                        if (lines.rows() >= 3) {
                            val hLines = ArrayList<Line>()
                            val vLines = ArrayList<Line>()
                            var i = 0
                            while (i < lines.rows()) {
                                val vec = lines[i, 0]
                                val l = Line(vec[0], vec[1], vec[2], vec[3])
                                if (l.isNearHorizontal) hLines.add(l) else if (l.isNearVertical) vLines.add(l)
                                i++
                            }
                            hLines.sortWith { o1, o2 -> ceil(o1.start.y - o2.start.y).toInt() }
                            vLines.sortWith { o1, o2 -> ceil(o1.start.x - o2.start.x).toInt() }
                            timingLogger.addSplit("Separating horizontal and vertical lines")
                            if (hLines.size >= 2 && vLines.size >= 2) {
                                val nhLines = Line.joinSegments(hLines)
                                val nvLines = Line.joinSegments(vLines)
                                nhLines.sortWith { o1, o2 -> ceil(o2.length() - o1.length()).toInt() }
                                nvLines.sortWith { o1, o2 -> ceil(o2.length() - o1.length()).toInt() }
                                timingLogger.addSplit("Joining line segments")
                                if (nvLines.size > 1 && nhLines.size > 0 || nvLines.size > 0 && nhLines.size > 1) {
                                    var left: Line? = null
                                    var right: Line? = null
                                    var bottom: Line? = null
                                    var top: Line? = null
                                    for (l in nvLines) {
                                        if (l.length() / height < 0.60 || left != null && right != null) break
                                        if (left == null && l.isInleft(width.toDouble())) {
                                            left = l
                                            continue
                                        }
                                        if (right == null && !l.isInleft(width.toDouble())) right = l
                                    }
                                    for (l in nhLines) {
                                        if (l.length() / width < 0.60 || top != null && bottom != null) break
                                        if (bottom == null && l.isInBottom(height.toDouble())) {
                                            bottom = l
                                            continue
                                        }
                                        if (top == null && !l.isInBottom(height.toDouble())) top = l
                                    }
                                    timingLogger.addSplit("Finding edges")
                                    foundPoints = null
                                    if (left != null && right != null && (bottom != null || top != null)) {
                                        val vLeft = if (bottom != null) bottom.intersect(left) else top!!.intersect(left)
                                        val vRight = if (bottom != null) bottom.intersect(right) else top!!.intersect(right)
                                        if (vLeft != null && vRight != null) {
                                            val pwidth = Line(vLeft, vRight).length()
                                            val pRatio = 3.465f / 4.921f
                                            val pHeight = pRatio * pwidth
                                            var dxFactor = pHeight / Line(vLeft, left.end).length()
                                            val tLeftX = (1 - dxFactor) * vLeft.x + dxFactor * left.end.x
                                            val tLeftY = (1 - dxFactor) * vLeft.y + dxFactor * left.end.y
                                            val tLeft = Point(tLeftX, tLeftY)
                                            dxFactor = pHeight / Line(vRight, right.end).length()
                                            val tRightX = (1 - dxFactor) * vRight.x + dxFactor * right.end.x
                                            val tRightY = (1 - dxFactor) * vRight.y + dxFactor * right.end.y
                                            val tRight = Point(tRightX, tRightY)
                                            foundPoints = arrayOf(vLeft, vRight, tLeft, tRight)
                                        }
                                    } else if (top != null && bottom != null && (left != null || right != null)) {
                                        val vTop = if (left != null) left.intersect(top) else right!!.intersect(top)
                                        val vBottom = if (left != null) left.intersect(bottom) else right!!.intersect(bottom)
                                        if (vTop != null && vBottom != null) {
                                            val pHeight = Line(vTop, vBottom).length()
                                            val pRatio = 4.921f / 3.465f
                                            val pWidth = pRatio * pHeight
                                            var dxFactor = pWidth / Line(vTop, top.end).length()
                                            val tTopX = (1 - dxFactor) * vTop.x + dxFactor * top.end.x
                                            val tTopY = (1 - dxFactor) * vTop.y + dxFactor * top.end.y
                                            val tTop = Point(tTopX, tTopY)
                                            dxFactor = pWidth / Line(vBottom, bottom.end).length()
                                            val tBottomX = (1 - dxFactor) * vBottom.x + dxFactor * bottom.end.x
                                            val tBottomY = (1 - dxFactor) * vBottom.y + dxFactor * bottom.end.y
                                            val tBottom = Point(tBottomX, tBottomY)
                                            foundPoints = arrayOf(tTop, tBottom, vTop, vBottom)
                                        }
                                    }
                                    timingLogger.addSplit("Calculating vertices")
                                    timingLogger.dumpToLog()
                                    if (foundPoints != null) {
                                        drawRect(resizedImg, foundPoints)
                                        onNextStep(resizedImg)
                                    }
                                }
                            }
                        }
                        resizedImg.release()
                    }
                    CVCommand.PASSPORT_SCAN_MRZ -> {
                        //downscale
                        val img = data.input.clone()
                        data.input.release()
                        val ratio = img.size().height / FIXED_HEIGHT
                        val width = (img.size().width / ratio) as Int
                        val height = (img.size().height / ratio) as Int
                        val newSize = Size(width, height)
                        val resizedImg = Mat(newSize, CvType.CV_8UC4)
                        Imgproc.resize(img, resizedImg, newSize)
                        onNextStep(resizedImg)
                        val gray = Mat()
                        Imgproc.cvtColor(resizedImg, gray, Imgproc.COLOR_BGR2GRAY)
                        Imgproc.medianBlur(gray, gray, 3)
                        onNextStep(gray)
                        morph = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(13, 5))
                        dilatedImg = Mat()
                        Imgproc.morphologyEx(gray, dilatedImg, Imgproc.MORPH_BLACKHAT, morph)
                        onNextStep(dilatedImg)
                        gray.release()
                        val gradX = Mat()
                        Imgproc.Sobel(dilatedImg, gradX, CvType.CV_32F, 1, 0)
                        dilatedImg.release()
                        Core.convertScaleAbs(gradX, gradX, 1.0, 0.0)
                        val minMax = Core.minMaxLoc(gradX)
                        Core.convertScaleAbs(
                            gradX, gradX, 255 / (minMax.maxVal - minMax.minVal),
                            -(minMax.minVal * 255 / (minMax.maxVal - minMax.minVal))
                        )
                        Imgproc.morphologyEx(gradX, gradX, Imgproc.MORPH_CLOSE, morph)
                        val thresh = Mat()
                        Imgproc.threshold(gradX, thresh, 0.0, 255.0, Imgproc.THRESH_OTSU)
                        onNextStep(thresh)
                        gradX.release()
                        morph.release()
                        morph = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(21, 21))
                        Imgproc.morphologyEx(thresh, thresh, Imgproc.MORPH_CLOSE, morph)
                        Imgproc.erode(thresh, thresh, Mat(), Point(-1, -1), 4)
                        onNextStep(thresh)
                        morph.release()
                        val col = resizedImg.size().width as Int
                        val p = (resizedImg.size().width * 0.05) as Int
                        val row = resizedImg.size().height as Int
                        var i = 0
                        while (i < row) {
                            var j = 0
                            while (j < p) {
                                thresh.put(i, j, 0.0)
                                thresh.put(i, col - j, 0.0)
                                j++
                            }
                            i++
                        }
                        contours = ArrayList<MatOfPoint>()
                        hierarchy = Mat()
                        Imgproc.findContours(thresh, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
                        hierarchy.release()
                        Log.d(TAG, "contours found: " + contours.size)
                        Collections.sort(
                            contours,
                            Comparator<MatOfPoint?> { o1, o2 -> java.lang.Double.compare(Imgproc.contourArea(o2), Imgproc.contourArea(o1)) })
                        rectContour = null
                        foundPoints = null
                        for (c in contours) {
                            val bRect = Imgproc.boundingRect(c)
                            val aspectRatio = bRect.width / bRect.height.toFloat()
                            val coverageRatio = bRect.width / col.toFloat()
                            Log.d(TAG, "AR: $aspectRatio, CR: $coverageRatio")
                            if (aspectRatio > 5 && coverageRatio > 0.70) {
                                Imgproc.drawContours(resizedImg, Arrays.asList(c), 0, Scalar(255, 0, 0), 5)
                                onNextStep(resizedImg)
                                val c2f = MatOfPoint2f(*c.toArray())
                                val peri = Imgproc.arcLength(c2f, true)
                                val approx = MatOfPoint2f()
                                Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true)
                                val points = approx.toArray()
                                Log.d("SCANNER", "approx size: " + points.size)

                                // select biggest 4 angles polygon
                                if (points.size == 4) {
                                    foundPoints = CVProcessor.sortPoints(points)
                                    break
                                } else if (points.size == 2) {
                                    if (rectContour == null) {
                                        rectContour = c
                                        foundPoints = points
                                    } else {
                                        //try to merge
                                        val box1 = Imgproc.minAreaRect(MatOfPoint2f(*c.toArray()))
                                        val box2 = Imgproc.minAreaRect(MatOfPoint2f(*rectContour.toArray()))
                                        val ar = (box1.size.width / box2.size.width).toFloat()
                                        if (box1.size.width > 0 && box2.size.width > 0 && 0.5 < ar && ar < 2.0) {
                                            if (abs(box1.angle - box2.angle) <= 0.1 ||
                                                abs(Math.PI - (box1.angle - box2.angle)) <= 0.1
                                            ) {
                                                val minAngle = Math.min(box1.angle, box2.angle)
                                                val relX = box1.center.x - box2.center.x
                                                val rely = box1.center.y - box2.center.y
                                                val distance = Math.abs(rely * Math.cos(minAngle) - relX * Math.sin(minAngle))
                                                if (distance < 1.5 * (box1.size.height + box2.size.height)) {
                                                    val allPoints: Array<Point> = Arrays.copyOf(foundPoints, 4)
                                                    System.arraycopy(points, 0, allPoints, 2, 2)
                                                    Log.d("SCANNER", "after merge approx size: " + allPoints.size)
                                                    if (allPoints.size == 4) {
                                                        foundPoints = CVProcessor.sortPoints(allPoints)
                                                        break
                                                    }
                                                }
                                            }
                                        }
                                        rectContour = null
                                        foundPoints = null
                                    }
                                }
                            }
                        }
                        if (foundPoints != null && foundPoints.size == 4) {
                            val lowerLeft: Point = foundPoints.get(3)
                            val lowerRight: Point = foundPoints.get(2)
                            val topLeft: Point = foundPoints.get(0)
                            val topRight: Point = foundPoints.get(1)
                            var w = sqrt((lowerRight.x - lowerLeft.x).pow(2.0) + (lowerRight.y - lowerLeft.y).pow(2.0))
                            var h = sqrt((topLeft.x - lowerLeft.x).pow(2.0) + (topLeft.y - lowerLeft.y).pow(2.0))
                            var px = ((lowerLeft.x + w) * 0.03).toInt()
                            var py = ((lowerLeft.y + h) * 0.03).toInt()
                            lowerLeft.x = lowerLeft.x - px
                            lowerLeft.y = lowerLeft.y + py
                            px = ((lowerRight.x + w) * 0.03).toInt()
                            py = ((lowerRight.y + h) * 0.03).toInt()
                            lowerRight.x = lowerRight.x + px
                            lowerRight.y = lowerRight.y + py
                            val pRatio = 3.465f / 4.921f
                            w = Math.sqrt(Math.pow(lowerRight.x - lowerLeft.x, 2.0) + Math.pow(lowerRight.y - lowerLeft.y, 2.0))
                            h = pRatio * w
                            h -= h * 0.04
                            Log.d(
                                "SCANNER", """
     topLeft:(${topLeft.x}, ${topLeft.y})
     topRight:(${topRight.x},${topRight.y})
     lowLeft:(${lowerLeft.x},${lowerLeft.y})
     lowRight:(${lowerRight.x},${lowerRight.y})
     """.trimIndent()
                            )
                            topLeft.y = lowerLeft.y - h
                            topRight.y = lowerLeft.y - h

                            foundPoints = CVProcessor.getUpscaledPoints(foundPoints, ratio)

                            drawRect(img, foundPoints)
                            onNextStep(img)
                        }
                        img.release()
                    }
                    CVCommand.PASSPORT_SCAN_MRZ2 -> {
                        //downscale
                        img = data.input.clone()
                        data.input.release()
                        ratio = img.size().height / FIXED_HEIGHT
                        width = (img.size().width / ratio) as Int
                        height = (img.size().height / ratio) as Int
                        newSize = Size(width, height)
                        resizedImg = Mat(newSize, CvType.CV_8UC4)
                        Imgproc.resize(img, resizedImg, newSize)
                        onNextStep(resizedImg)
                        gray = Mat()
                        Imgproc.cvtColor(resizedImg, gray, Imgproc.COLOR_BGR2GRAY)
                        Imgproc.medianBlur(gray, gray, 3)
                        onNextStep(gray)
                        morph = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(13, 5))
                        dilatedImg = Mat()
                        Imgproc.morphologyEx(gray, dilatedImg, Imgproc.MORPH_BLACKHAT, morph)
                        onNextStep(dilatedImg)
                        gray.release()
                        gradX = Mat()
                        Imgproc.Sobel(dilatedImg, gradX, CvType.CV_32F, 1, 0)
                        dilatedImg.release()
                        Core.convertScaleAbs(gradX, gradX, 1.0, 0.0)
                        minMax = Core.minMaxLoc(gradX)
                        Core.convertScaleAbs(
                            gradX, gradX, 255 / (minMax.maxVal - minMax.minVal),
                            -(minMax.minVal * 255 / (minMax.maxVal - minMax.minVal))
                        )
                        Imgproc.morphologyEx(gradX, gradX, Imgproc.MORPH_CLOSE, morph)
                        thresh = Mat()
                        Imgproc.threshold(gradX, thresh, 0.0, 255.0, Imgproc.THRESH_OTSU)
                        onNextStep(thresh)
                        gradX.release()
                        morph.release()
                        morph = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(21, 21))
                        Imgproc.morphologyEx(thresh, thresh, Imgproc.MORPH_CLOSE, morph)
                        Imgproc.erode(thresh, thresh, Mat(), Point(-1, -1), 4)
                        onNextStep(thresh)
                        morph.release()
                        col = resizedImg.size().width as Int
                        p = (resizedImg.size().width * 0.05) as Int
                        row = resizedImg.size().height as Int
                        var i = 0
                        while (i < row) {
                            var j = 0
                            while (j < p) {
                                thresh.put(i, j, 0.0)
                                thresh.put(i, col - j, 0.0)
                                j++
                            }
                            i++
                        }
                        contours = ArrayList<MatOfPoint>()
                        hierarchy = Mat()
                        Imgproc.findContours(thresh, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
                        hierarchy.release()
                        Log.d(TAG, "contours found: " + contours.size)
                        Collections.sort(
                            contours,
                            Comparator<MatOfPoint?> { o1, o2 -> java.lang.Double.compare(Imgproc.contourArea(o2), Imgproc.contourArea(o1)) })
                        rectContour = null
                        foundPoints = null
                        for (c in contours) {
                            val bRect = Imgproc.boundingRect(c)
                            val aspectRatio = bRect.width / bRect.height.toFloat()
                            val coverageRatio = bRect.width / col as Float
                            Log.d(TAG, "AR: $aspectRatio, CR: $coverageRatio")
                            if (aspectRatio > 5 && coverageRatio > 0.70) {
                                //Imgproc.drawContours(resizedImg, Arrays.asList(c), 0, new Scalar(255, 0, 0), 5);
                                //onNextStep(resizedImg);
                                val c2f = MatOfPoint2f(*c.toArray())
                                val peri = Imgproc.arcLength(c2f, true)
                                val approx = MatOfPoint2f()
                                Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true)
                                val points = approx.toArray()
                                Log.d("SCANNER", "approx size: " + points.size)

                                // select biggest 4 angles polygon
                                if (points.size == 4) {
                                    foundPoints = CVProcessor.sortPoints(points)
                                    break
                                } else if (points.size == 2) {
                                    if (rectContour == null) {
                                        rectContour = c
                                        foundPoints = points
                                    } else {
                                        //try to merge
                                        val box1 = Imgproc.minAreaRect(MatOfPoint2f(*c.toArray()))
                                        val box2 = Imgproc.minAreaRect(MatOfPoint2f(*rectContour.toArray()))
                                        val ar = (box1.size.width / box2.size.width).toFloat()
                                        if (box1.size.width > 0 && box2.size.width > 0 && 0.5 < ar && ar < 2.0) {
                                            if (Math.abs(box1.angle - box2.angle) <= 0.1 ||
                                                Math.abs(Math.PI - (box1.angle - box2.angle)) <= 0.1
                                            ) {
                                                val minAngle = Math.min(box1.angle, box2.angle)
                                                val relX = box1.center.x - box2.center.x
                                                val rely = box1.center.y - box2.center.y
                                                val distance = Math.abs(rely * Math.cos(minAngle) - relX * Math.sin(minAngle))
                                                if (distance < 1.5 * (box1.size.height + box2.size.height)) {
                                                    val allPoints: Array<Point> = Arrays.copyOf(foundPoints, 4)
                                                    System.arraycopy(points, 0, allPoints, 2, 2)
                                                    Log.d("SCANNER", "after merge approx size: " + allPoints.size)
                                                    if (allPoints.size == 4) {
                                                        foundPoints = CVProcessor.sortPoints(allPoints)
                                                        break
                                                    }
                                                }
                                            }
                                        }
                                        rectContour = null
                                        foundPoints = null
                                    }
                                }
                            }
                        }
                        if (foundPoints != null && foundPoints.size == 4) {
                            val lowerLeft: Point = foundPoints.get(3)
                            val lowerRight: Point = foundPoints.get(2)
                            val topLeft: Point = foundPoints.get(0)
                            val topRight: Point = foundPoints.get(1)
                            var w = Math.sqrt(Math.pow(lowerRight.x - lowerLeft.x, 2.0) + Math.pow(lowerRight.y - lowerLeft.y, 2.0))
                            var h = Math.sqrt(Math.pow(topLeft.x - lowerLeft.x, 2.0) + Math.pow(topLeft.y - lowerLeft.y, 2.0))
                            var px = ((lowerLeft.x + w) * 0.03).toInt()
                            var py = ((lowerLeft.y + h) * 0.03).toInt()
                            lowerLeft.x = lowerLeft.x - px
                            lowerLeft.y = lowerLeft.y + py
                            px = ((lowerRight.x + w) * 0.03).toInt()
                            py = ((lowerRight.y + h) * 0.03).toInt()
                            lowerRight.x = lowerRight.x + px
                            lowerRight.y = lowerRight.y + py
                            val pRatio = 3.465f / 4.921f
                            w = Math.sqrt(Math.pow(lowerRight.x - lowerLeft.x, 2.0) + Math.pow(lowerRight.y - lowerLeft.y, 2.0))
                            h = pRatio * w
                            h = h - h * 0.04
                            Log.d(
                                "SCANNER", """
     topLeft:(${topLeft.x}, ${topLeft.y})
     topRight:(${topRight.x},${topRight.y})
     lowLeft:(${lowerLeft.x},${lowerLeft.y})
     lowRight:(${lowerRight.x},${lowerRight.y})
     """.trimIndent()
                            )
                            topLeft.y = lowerLeft.y - h
                            topRight.y = lowerLeft.y - h
                            topLeft.x = 0.0
                            topRight.x = resizedImg.width().toDouble()
                            Imgproc.line(resizedImg, topLeft, topRight, Scalar(0, 0, 0), 2)
                            onNextStep(resizedImg)
                            Imgproc.medianBlur(resizedImg, resizedImg, 5)
                            //onNextStep(resizedImg);
                            cannedImg = Mat(newSize, CvType.CV_8UC1)
                            Imgproc.Canny(resizedImg, cannedImg, 70.0, 200.0, 3, true)
                            resizedImg.release()
                            onNextStep(cannedImg)
                            Imgproc.threshold(cannedImg, cannedImg, 70.0, 255.0, Imgproc.THRESH_OTSU)
                            onNextStep(cannedImg)
                            dilatedImg = Mat(newSize, CvType.CV_8UC1)
                            morph = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3, 3))
                            Imgproc.dilate(cannedImg, dilatedImg, morph, Point(-1, -1), 2, 1, Scalar(1))
                            cannedImg.release()
                            morph.release()
                            onNextStep(dilatedImg)
                            contours = ArrayList<MatOfPoint>()
                            hierarchy = Mat()
                            Imgproc.findContours(dilatedImg, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
                            hierarchy.release()
                            Log.d(TAG, "contours found: " + contours.size)
                            Collections.sort(
                                contours,
                                Comparator<MatOfPoint?> { o1, o2 -> java.lang.Double.compare(Imgproc.contourArea(o2), Imgproc.contourArea(o1)) })
                            Imgproc.drawContours(dilatedImg, contours, 0, Scalar(255, 255, 250))
                            onNextStep(dilatedImg)
                            dilatedImg.release()
                            rectContour = null
                            foundPoints = null
                            for (contour in contours) {
                                val mat = MatOfPoint2f(*contour.toArray())
                                val peri = Imgproc.arcLength(mat, true)
                                val approx = MatOfPoint2f()
                                Imgproc.approxPolyDP(mat, approx, 0.02 * peri, true)
                                val points = approx.toArray()
                                Log.d("SCANNER", "approx size " + points.size)
                                if (points.size == 4) {
                                    val spoints = CVProcessor.sortPoints(points)
                                    if (CVProcessor.isInside(spoints, newSize) && CVProcessor.isLargeEnough(spoints, newSize, 0.40)) {
                                        rectContour = contour
                                        foundPoints = spoints
                                        break
                                    }
                                }
                            }
                            if (rectContour != null) {
                                val scaledPoints = arrayOfNulls<Point>(foundPoints.size)
                                var i = 0
                                while (i < foundPoints.size) {
                                    scaledPoints[i] = Point(foundPoints.get(i).x * ratio, foundPoints.get(i).y * ratio)
                                    i++
                                }
                                drawRect(img, scaledPoints)
                                onNextStep(img)
                            }
                            img.release()
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_SCAN_TYPE = "scan_type"
    }
}
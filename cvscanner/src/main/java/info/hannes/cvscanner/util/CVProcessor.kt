package info.hannes.cvscanner.util

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfFloat4
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.geometry.Geometry
import org.opencv.imgproc.Imgproc
import timber.log.Timber.Forest.d
import java.util.Collections
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object CVProcessor {
    const val PASSPORT_ASPECT_RATIO: Float = 3.465f / 4.921f
    const val FIXED_HEIGHT: Int = 800

    @JvmStatic
    fun getScaleRatio(srcSize: Size): Double {
        return srcSize.height / FIXED_HEIGHT
    }

    @JvmStatic
    fun findContours(src: Mat): MutableList<MatOfPoint?> {
        val img = src.clone()

        //find contours
        val ratio = getScaleRatio(img.size())
        val width = (img.size().width / ratio).toInt()
        val height = (img.size().height / ratio).toInt()
        val newSize = Size(width.toDouble(), height.toDouble())
        val resizedImg = Mat(newSize, CvType.CV_8UC4)
        Imgproc.resize(img, resizedImg, newSize)
        img.release()

        Imgproc.medianBlur(resizedImg, resizedImg, 7)

        val cannedImg = Mat(newSize, CvType.CV_8UC1)
        Imgproc.Canny(resizedImg, cannedImg, 70.0, 200.0, 3, true)
        resizedImg.release()

        Imgproc.threshold(cannedImg, cannedImg, 70.0, 255.0, Imgproc.THRESH_OTSU)

        val dilatedImg = Mat(newSize, CvType.CV_8UC1)
        val morph = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.dilate(cannedImg, dilatedImg, morph, Point(-1.0, -1.0), 2, 1, Scalar(1.0))
        cannedImg.release()
        morph.release()

        val contours = ArrayList<MatOfPoint?>()
        val hierarchy = Mat()
        Imgproc.findContours(dilatedImg, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        hierarchy.release()
        dilatedImg.release()

        d("contours found: ${contours.size}")

        Collections.sort(contours, Comparator { o1, o2 -> Geometry.contourArea(o2).compareTo(Geometry.contourArea(o1)) })

        return contours
    }

    @JvmStatic
    fun findContoursForMRZ(src: Mat): MutableList<MatOfPoint?> {
        val img = src.clone()
        src.release()
        val ratio = getScaleRatio(img.size())
        val width = (img.size().width / ratio).toInt()
        val height = (img.size().height / ratio).toInt()
        val newSize = Size(width.toDouble(), height.toDouble())
        val resizedImg = Mat(newSize, CvType.CV_8UC4)
        Imgproc.resize(img, resizedImg, newSize)

        val gray = Mat()
        Imgproc.cvtColor(resizedImg, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.medianBlur(gray, gray, 3)

        //Imgproc.blur(gray, gray, new Size(3, 3));
        var morph = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(13.0, 5.0))
        val dilatedImg = Mat()
        Imgproc.morphologyEx(gray, dilatedImg, Imgproc.MORPH_BLACKHAT, morph)
        gray.release()

        val gradX = Mat()
        Imgproc.Sobel(dilatedImg, gradX, CvType.CV_32F, 1, 0)
        dilatedImg.release()
        Core.convertScaleAbs(gradX, gradX, 1.0, 0.0)
        val minMax = Core.minMaxLoc(gradX)
        Core.convertScaleAbs(
            gradX, gradX, (255 / (minMax.maxVal - minMax.minVal)),
            -((minMax.minVal * 255) / (minMax.maxVal - minMax.minVal))
        )
        Imgproc.morphologyEx(gradX, gradX, Imgproc.MORPH_CLOSE, morph)

        val thresh = Mat()
        Imgproc.threshold(gradX, thresh, 0.0, 255.0, Imgproc.THRESH_OTSU)
        gradX.release()
        morph.release()

        morph = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(21.0, 21.0))
        Imgproc.morphologyEx(thresh, thresh, Imgproc.MORPH_CLOSE, morph)
        Imgproc.erode(thresh, thresh, Mat(), Point(-1.0, -1.0), 4)
        morph.release()

        val col = resizedImg.size().width.toInt()
        val p = (resizedImg.size().width * 0.05).toInt()
        val row = resizedImg.size().height.toInt()
        for (i in 0..<row) {
            for (j in 0..<p) {
                thresh.put(i, j, 0.0)
                thresh.put(i, col - j, 0.0)
            }
        }

        val contours: MutableList<MatOfPoint?> = ArrayList()
        val hierarchy = Mat()
        Imgproc.findContours(thresh, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        hierarchy.release()

        d("contours found: ${contours.size}")

        Collections.sort(contours, Comparator { o1, o2 -> Geometry.contourArea(o2).compareTo(Geometry.contourArea(o1)) })

        return contours
    }

    @JvmStatic
    fun getQuadForPassport(img: Mat, frameWidth: Double, frameHeight: Double): Quadrilateral? {
        var frameWidth = frameWidth
        var frameHeight = frameHeight
        val requiredCoverageRatio = 0.60
        val ratio = getScaleRatio(img.size())
        val width = img.size().width / ratio
        val height = img.size().height / ratio

        if (frameHeight == 0.0 || frameWidth == 0.0) {
            frameWidth = width
            frameHeight = height
        } else {
            frameWidth /= ratio
            frameHeight /= ratio
        }

        val newSize = Size(width, height)
        val resizedImg = Mat(newSize, CvType.CV_8UC4)
        Imgproc.resize(img, resizedImg, newSize)

        Imgproc.medianBlur(resizedImg, resizedImg, 13)

        val cannedImg = Mat(newSize, CvType.CV_8UC1)
        Imgproc.Canny(resizedImg, cannedImg, 70.0, 200.0, 3, true)
        resizedImg.release()

        val morphR = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))

        Imgproc.morphologyEx(cannedImg, cannedImg, Imgproc.MORPH_CLOSE, morphR, Point(-1.0, -1.0), 1)

        val lines = MatOfFloat4()
        Imgproc.HoughLinesP(cannedImg, lines, 1.0, Math.PI / 180, 30, 30.0, 150.0)

        if (lines.rows() >= 3) {
            val hLines = ArrayList<Line?>()
            val vLines = ArrayList<Line?>()

            for (i in 0..<lines.rows()) {
                val vec = lines.get(i, 0)
                val l = Line(vec[0], vec[1], vec[2], vec[3])
                if (l.isNearHorizontal) hLines.add(l)
                else if (l.isNearVertical) vLines.add(l)
            }

            if (hLines.size >= 2 && vLines.size >= 2) {
                Collections.sort(hLines, Comparator { o1, o2 -> ceil(o1!!.start.y - o2!!.start.y).toInt() })

                Collections.sort(vLines, Comparator { o1, o2 -> ceil(o1!!.start.x - o2!!.start.x).toInt() })
            }

            val nhLines = Line.joinSegments(hLines)

            val nvLines = Line.joinSegments(vLines)

            if ((nvLines.size > 1 && nhLines.isNotEmpty()) || (nvLines.isNotEmpty() && nhLines.size > 1)) {
                Collections.sort(nhLines, Comparator { o1, o2 -> ceil(o2!!.length() - o1!!.length()).toInt() })

                Collections.sort(nvLines, Comparator { o1, o2 -> ceil(o2!!.length() - o1!!.length()).toInt() })

                var left: Line? = null
                var right: Line? = null
                var bottom: Line? = null
                var top: Line? = null

                for (l in nvLines) {
                    if (l.length() / frameHeight < requiredCoverageRatio || (left != null && right != null)) break

                    if (left == null && l.isInleft(width)) {
                        left = l
                        continue
                    }

                    if (right == null && !l.isInleft(width)) right = l
                }

                for (l in nhLines) {
                    if (l.length() / frameWidth < requiredCoverageRatio || (top != null && bottom != null)) break

                    if (bottom == null && l.isInBottom(height)) {
                        bottom = l
                        continue
                    }

                    if (top == null && !l.isInBottom(height)) top = l
                }

                var foundPoints: Array<Point>? = null

                if ((left != null && right != null) && (bottom != null || top != null)) {
                    val vLeft = if (bottom != null) bottom.intersect(left) else top!!.intersect(left)
                    val vRight = if (bottom != null) bottom.intersect(right) else top!!.intersect(right)
                    d("got the edges")
                    if (vLeft != null && vRight != null) {
                        val pWidth = Line(vLeft, vRight).length()
                        val pHeight = pWidth / PASSPORT_ASPECT_RATIO

                        val tLeft = getPointOnLine(vLeft, left.end, pHeight)
                        val tRight = getPointOnLine(vRight, right.end, pHeight)

                        foundPoints = arrayOf(vLeft, vRight, tLeft, tRight)
                    }
                } else if ((top != null && bottom != null) && (left != null || right != null)) {
                    val vTop = if (left != null) left.intersect(top) else right!!.intersect(top)
                    val vBottom = if (left != null) left.intersect(bottom) else right!!.intersect(bottom)
                    d("got the edges")
                    if (vTop != null && vBottom != null) {
                        val pHeight = Line(vTop, vBottom).length()
                        val pWidth = pHeight * PASSPORT_ASPECT_RATIO

                        val tTop = getPointOnLine(vTop, top.end, pWidth)
                        val tBottom = getPointOnLine(vBottom, bottom.end, pWidth)

                        foundPoints = arrayOf(tTop, tBottom, vTop, vBottom)
                    }
                }

                if (foundPoints != null) {
                    val sPoints = sortPoints(foundPoints)

                    if (isInside(sPoints, newSize)
                        && isLargeEnough(sPoints, Size(frameWidth, frameHeight), requiredCoverageRatio)
                    ) {
                        return Quadrilateral(sPoints)
                    } else d("Not inside")
                }
            }
        }
        return null
    }

    fun getPointOnLine(origin: Point?, another: Point?, distance: Double): Point {
        val dFactor = distance / Line(origin, another).length()
        val x = ((1 - dFactor) * origin!!.x) + (dFactor * another!!.x)
        val y = ((1 - dFactor) * origin.y) + (dFactor * another.y)
        return Point(x, y)
    }

    @JvmStatic
    fun getQuadrilateral(contours: MutableList<MatOfPoint>, srcSize: Size): Quadrilateral? {
        val ratio = getScaleRatio(srcSize)
        val height: Double = srcSize.height / ratio.toInt()
        val width: Double = (srcSize.width / ratio.toInt())
        val size = Size(width, height)

        for (c in contours) {
            val c2f = MatOfPoint2f(*c.toArray())
            val peri = Geometry.arcLength(c2f, true)
            val approx = MatOfPoint2f()
            Geometry.approxPolyDP(c2f, approx, 0.02 * peri, true)

            val points = approx.toArray()
            d("approx size: ${points.size}")

            // select biggest 4 angles polygon
            if (points.size == 4) {
                val foundPoints = sortPoints(points)

                if (isInside(foundPoints, size) && isLargeEnough(foundPoints, size, 0.25)) {
                    return Quadrilateral(foundPoints)
                } else {
                    //showToast(context, "Try getting closer to the ID");
                    d("Not inside defined area")
                }
            }
        }

        //showToast(context, "Make sure the ID is on a contrasting background");
        return null
    }

    @JvmStatic
    fun getQuadForPassport(contours: MutableList<MatOfPoint>, srcSize: Size, frameSize: Int): Quadrilateral? {
        val requiredAspectRatio = 5
        val requiredCoverageRatio = 0.80f

        var rectContour: MatOfPoint? = null
        var foundPoints: Array<Point>? = arrayOf(Point(), Point(), Point(), Point())

        val ratio = getScaleRatio(srcSize)
        val width: Double = srcSize.width / ratio.toInt()
        val frameWidth = frameSize / ratio.toInt()

        for (c in contours) {
            val bRect = Geometry.boundingRect(c)
            val aspectRatio = bRect.width / bRect.height.toFloat()
            val coverageRatio = if (frameSize != 0) bRect.width / frameWidth.toFloat() else bRect.width / width.toFloat()

            d("AR: $aspectRatio, CR: $coverageRatio, frameWidth: $frameWidth")

            if (aspectRatio > requiredAspectRatio && coverageRatio > requiredCoverageRatio) {
                val c2f = MatOfPoint2f(*c.toArray())
                val peri = Geometry.arcLength(c2f, true)
                val approx = MatOfPoint2f()
                Geometry.approxPolyDP(c2f, approx, 0.02 * peri, true)

                val points = approx.toArray()
                d("approx size: ${points.size}")

                // select biggest 4 angles polygon
                if (points.size == 4) {
                    foundPoints = sortPoints(points)
                    break
                } else if (points.size == 2) {
                    if (rectContour == null) {
                        rectContour = c
                        foundPoints = points
                    } else {
                        //try to merge
                        val box1 = Geometry.minAreaRect(MatOfPoint2f(*c.toArray()))
                        val box2 = Geometry.minAreaRect(MatOfPoint2f(*rectContour.toArray()))

                        val ar = (box1.size.width / box2.size.width).toFloat()
                        if (box1.size.width > 0 && box2.size.width > 0 && 0.5 < ar && ar < 2.0) {
                            if (abs(box1.angle - box2.angle) <= 0.1 ||
                                abs(Math.PI - (box1.angle - box2.angle)) <= 0.1
                            ) {
                                val minAngle = min(box1.angle, box2.angle)
                                val relX = box1.center.x - box2.center.x
                                val rely = box1.center.y - box2.center.y
                                val distance = abs((rely * cos(minAngle)) - (relX * sin(minAngle)))
                                if (distance < (1.5 * (box1.size.height + box2.size.height))) {
                                    val previousPoints = foundPoints ?: continue
                                    val allPoints = arrayOf(
                                        previousPoints[0],
                                        previousPoints[1],
                                        points[0],
                                        points[1]
                                    )
                                    d("after merge approx size: ${allPoints.size}")
                                    if (allPoints.size == 4) {
                                        foundPoints = sortPoints(allPoints)
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
            val lowerLeft = foundPoints[3]
            val lowerRight = foundPoints[2]
            val topLeft = foundPoints[0]
            var w = sqrt((lowerRight.x - lowerLeft.x).pow(2.0) + (lowerRight.y - lowerLeft.y).pow(2.0))
            var h = sqrt((topLeft.x - lowerLeft.x).pow(2.0) + (topLeft.y - lowerLeft.y).pow(2.0))
            var px = ((lowerLeft.x + w) * 0.03).toInt()
            var py = ((lowerLeft.y + h) * 0.03).toInt()
            lowerLeft.x -= px
            lowerLeft.y += py

            px = ((lowerRight.x + w) * 0.03).toInt()
            py = ((lowerRight.y + h) * 0.03).toInt()
            lowerRight.x += px
            lowerRight.y += py

            val pRatio = 3.465f / 4.921f
            w = sqrt((lowerRight.x - lowerLeft.x).pow(2.0) + (lowerRight.y - lowerLeft.y).pow(2.0))

            h = pRatio * w
            h -= (h * 0.04)

            foundPoints[1] = Point(lowerRight.x, lowerRight.y - h)
            foundPoints[0] = Point(lowerLeft.x, lowerLeft.y - h)

            return Quadrilateral(foundPoints)
        }

        return null
    }

    @JvmStatic
    fun sortPoints(src: Array<Point>): Array<Point> {
        val srcPoints = ArrayList(mutableListOf(*src))

        val result = arrayOf(Point(), Point(), Point(), Point())

        val sumComparator: Comparator<Point> = object : Comparator<Point> {
            override fun compare(lhs: Point?, rhs: Point?): Int {
                if (lhs == null || rhs == null) return 0
                return (lhs.x + lhs.y).compareTo(rhs.x + rhs.y)
            }
        }

        val diffComparator: Comparator<Point> = Comparator { lhs, rhs -> (lhs.y - lhs.x).compareTo(rhs.y - rhs.x) }

        // top-left corner = minimal sum
        result[0] = Collections.min<Point?>(srcPoints, sumComparator)!!

        // bottom-right corner = maximal sum
        result[2] = Collections.max<Point?>(srcPoints, sumComparator)!!

        // top-right corner = minimal difference
        result[1] = Collections.min<Point?>(srcPoints, diffComparator)!!

        // bottom-left corner = maximal difference
        result[3] = Collections.max<Point?>(srcPoints, diffComparator)!!

        return result
    }

    @JvmStatic
    fun isInside(points: Array<Point>, size: Size): Boolean {
        val width = size.width.toInt()
        val height = size.height.toInt()

        val isInside =
            points[0].x >= 0 && points[0].y >= 0 && points[1].x <= width && points[1].y >= 0 && points[2].x <= width && points[2].y <= height && points[3].x >= 0 && points[3].y <= height

        d("w: $width, h: $height\nPoints: ${points[0]}, ${points[1]}, ${points[2]}, ${points[3]}, result: $isInside")
        return isInside
    }

    @JvmStatic
    fun isLargeEnough(points: Array<Point>, size: Size, ratio: Double): Boolean {
        val contentWidth = max(Line(points[0], points[1]).length(), Line(points[3], points[2]).length())
        val contentHeight = max(Line(points[0], points[3]).length(), Line(points[1], points[2]).length())

        val widthRatio = contentWidth / size.width
        val heightRatio = contentHeight / size.height

        d("ratio: wr-$widthRatio, hr-$heightRatio, w: ${size.width}, h: ${size.height}, cw: $contentWidth, ch: $contentHeight")

        return widthRatio >= ratio && heightRatio >= ratio
    }

    @JvmStatic
    fun getUpscaledPoints(points: Array<Point?>, scaleFactor: Double): Array<Point?> {
        val rescaledPoints = arrayOfNulls<Point>(4)

        for (i in 0..3) {
            val x: Double = points[i]!!.x * scaleFactor.toInt()
            val y: Double = points[i]!!.y * scaleFactor.toInt()
            rescaledPoints[i] = Point(x, y)
        }

        return rescaledPoints
    }

    /**
     * @param src - actual image
     * @param pts - points scaled up with respect to actual image
     * @return transformed image
     */
    @JvmStatic
    fun fourPointTransform(src: Mat, pts: Array<Point>): Mat {
        val tl = pts[0]
        val tr = pts[1]
        val br = pts[2]
        val bl = pts[3]

        val widthA = sqrt((br.x - bl.x).pow(2.0) + (br.y - bl.y).pow(2.0))
        val widthB = sqrt((tr.x - tl.x).pow(2.0) + (tr.y - tl.y).pow(2.0))

        val dw = max(widthA, widthB)
        val maxWidth = dw.toInt()


        val heightA = sqrt((tr.x - br.x).pow(2.0) + (tr.y - br.y).pow(2.0))
        val heightB = sqrt((tl.x - bl.x).pow(2.0) + (tl.y - bl.y).pow(2.0))

        val dh = max(heightA, heightB)
        val maxHeight = dh.toInt()

        val doc = Mat(maxHeight, maxWidth, CvType.CV_8UC4)

        val srcMat = Mat(4, 1, CvType.CV_32FC2)
        val dstMat = Mat(4, 1, CvType.CV_32FC2)

        srcMat.put(0, 0, tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y)
        dstMat.put(0, 0, 0.0, 0.0, dw, 0.0, dw, dh, 0.0, dh)

        val m = Geometry.getPerspectiveTransform(srcMat, dstMat)

        Imgproc.warpPerspective(src, doc, m, doc.size())

        return doc
    }

    @JvmStatic
    fun adjustBrightnessAndContrast(src: Mat, clipPercentage: Double): Mat {
        var clipPercentage = clipPercentage
        val histSize = 256
        val alpha: Double
        val beta: Double
        var minGray: Double
        var maxGray: Double

        val gray: Mat?
        if (src.type() == CvType.CV_8UC1) {
            gray = src.clone()
        } else {
            gray = Mat()
            Imgproc.cvtColor(src, gray, if (src.type() == CvType.CV_8UC3) Imgproc.COLOR_RGB2GRAY else Imgproc.COLOR_RGBA2GRAY)
        }

        if (clipPercentage == 0.0) {
            val minMaxGray = Core.minMaxLoc(gray)
            minGray = minMaxGray.minVal
            maxGray = minMaxGray.maxVal
        } else {
            val hist = Mat()
            val size = MatOfInt(histSize)
            val channels = MatOfInt(0)
            val ranges = MatOfFloat(0f, 256f)
            Imgproc.calcHist(mutableListOf<Mat?>(gray), channels, Mat(), hist, size, ranges, false)
            gray.release()

            val accumulator = DoubleArray(histSize)

            accumulator[0] = hist.get(0, 0)[0]
            for (i in 1..<histSize) {
                accumulator[i] = accumulator[i - 1] + hist.get(i, 0)[0]
            }

            hist.release()

            val max = accumulator[accumulator.size - 1]
            clipPercentage = (clipPercentage * (max / 100.0))
            clipPercentage /= 2.0

            minGray = 0.0
            while (minGray < histSize && accumulator[minGray.toInt()] < clipPercentage) {
                minGray++
            }

            maxGray = (histSize - 1).toDouble()
            while (maxGray >= 0 && accumulator[maxGray.toInt()] >= (max - clipPercentage)) {
                maxGray--
            }
        }

        val inputRange = maxGray - minGray
        alpha = (histSize - 1) / inputRange
        beta = -minGray * alpha

        val result = Mat()
        src.convertTo(result, -1, alpha, beta)

        if (result.type() == CvType.CV_8UC4) {
            Core.mixChannels(mutableListOf<Mat?>(src), mutableListOf<Mat?>(result), MatOfInt(3, 3))
        }

        return result
    }

    @JvmStatic
    fun sharpenImage(src: Mat): Mat {
        val sharped = Mat()
        Imgproc.GaussianBlur(src, sharped, Size(0.0, 0.0), 3.0)
        Core.addWeighted(src, 1.5, sharped, -0.5, 0.0, sharped)

        return sharped
    }

    class Quadrilateral(@JvmField var points: Array<Point>?)

}
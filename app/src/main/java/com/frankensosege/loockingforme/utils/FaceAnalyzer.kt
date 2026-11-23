package com.frankensosege.loockingforme.utils

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

// ─── 모델 설정 ────────────────────────────────────────────────────────────────

/** 모델 입력 정규화 방식 — 모델마다 학습 때 쓴 방식이 다르므로 설정으로 분리 */
enum class InputNormalization {
    /** (pixel - 127.5) / 128.0  — FaceNet(VGGFace2 계열) 고정 정규화 */
    FIXED_128,

    /** 이미지별 표준화 (pixel - mean) / std  — deepface 'Facenet' 방식 */
    PER_IMAGE,

    /** pixel / 255  →  [0, 1] */
    ZERO_ONE,
}

/**
 * 얼굴 정렬 기하 — 두 눈을 입력 이미지의 어느 위치·간격에 놓을지
 * @param eyeDistRatio 두 눈 사이 거리 / 입력 크기
 * @param eyeYRatio    두 눈 중심의 세로 위치 / 입력 크기 (위에서부터)
 */
data class AlignGeometry(
    val eyeDistRatio: Float,
    val eyeYRatio: Float,
)

/**
 * 얼굴 임베딩 모델 설정 — 모델 교체 시 이 값만 바꾸면 된다.
 * (새 모델 파일은 assets에 넣고 ModelSpec을 만들어 FaceAnalyzer 생성 시 전달)
 */
data class ModelSpec(
    val fileName: String,
    val inputSize: Int,
    val embeddingDim: Int,
    val normalization: InputNormalization,
    val geometry: AlignGeometry,
    /** NNAPI(기기 가속기) 사용 여부 — false면 CPU fp32. 기기에 따라 NNAPI가 정밀도를 낮출 수 있음 */
    val useNnapi: Boolean = true,
) {
    companion object {
        /** 현재 사용 중인 모델 (facenet.tflite, 160×160 → 512차원) */
        val FACENET_512 = ModelSpec(
            fileName      = "facenet.tflite",
            inputSize     = 160,
            embeddingDim  = 512,
            // 실기기 평가(사진 72장): 정렬 수정 후 ZERO_ONE / 눈 간격 0.33이 분리도 1위
            // (FIXED_128과는 차이가 작아 사실상 동률, PER_IMAGE는 열세)
            normalization = InputNormalization.ZERO_ONE,
            geometry      = AlignGeometry(eyeDistRatio = 0.33f, eyeYRatio = 0.40f),
            useNnapi = false,
        )
    }
}

/**
 * TFLite FaceNet 기반 얼굴 감지 및 임베딩 추출
 *
 * ── 전처리 파이프라인 ─────────────────────────────────────────────────────────
 *   1. EXIF 회전 보정        : 기기 방향 무관 정립 이미지
 *   2. 4방향 회전 시도       : 0°/90°/180°/270° 순차 감지 → 누운 사진 대응
 *   3. 눈 기준 정렬 + 크롭   : 두 눈을 고정 위치로 보내는 변환 행렬 1개로
 *                              회전·크기·크롭을 한 번에 처리 (입력 크기 출력)
 *                              ★ 이전의 "이미지 전체 회전 후 원본 좌표로 크롭" 방식은
 *                                회전 시 좌표가 어긋나 엉뚱한 영역이 잘리는 버그가 있었음
 *   4. 정규화                : ModelSpec.normalization
 *   5. NHWC ByteBuffer 변환  : TFLite 입력 형식
 *
 * ── 품질 필터 ─────────────────────────────────────────────────────────────────
 *   검색(스캔) 시 얼굴 박스가 MIN_FACE_PX 미만인 얼굴은 임베딩이 불안정하므로 제외
 *
 * ── 추론 가속 ─────────────────────────────────────────────────────────────────
 *   NNAPI (Android 9+) → CPU 4스레드 폴백
 */
class FaceAnalyzer(
    private val context: Context,
    private val spec: ModelSpec = ModelSpec.FACENET_512,
) {

    // ─── ML Kit 감지기 (2개 — 고정확/광각) ──────────────────────────────────

    /** 표준 감지기 — 높은 정확도 */
    private val accurateDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.05f)   // 작은 얼굴도 감지
            .build()
    )

    /** 보조 감지기 — 작은 얼굴/측면 얼굴 (minFaceSize 최소화) */
    private val fallbackDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.03f)
            .build()
    )

    // ─── TFLite 인터프리터 ────────────────────────────────────────────────────

    private var interpreter: Interpreter? = null

    @Synchronized
    private fun getOrCreateInterpreter(): Interpreter? {
        if (interpreter != null) return interpreter
        return try {
            val model   = loadModelFromAssets(context, spec.fileName) ?: return null
            val options = Interpreter.Options().apply {
                setUseNNAPI(spec.useNnapi)
                setNumThreads(4)
            }
            Interpreter(model, options).also { interpreter = it }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "TFLite 초기화 실패: ${e.message}")
            null
        }
    }

    private fun loadModelFromAssets(context: Context, fileName: String): MappedByteBuffer? {
        return try {
            val afd: AssetFileDescriptor = context.assets.openFd(fileName)
            FileInputStream(afd.fileDescriptor).channel.map(
                FileChannel.MapMode.READ_ONLY,
                afd.startOffset,
                afd.declaredLength,
            )
        } catch (e: Exception) {
            android.util.Log.e(TAG, "모델 로드 실패: ${e.message}")
            null
        }
    }

    // ─── 공개 API ─────────────────────────────────────────────────────────────

    /**
     * 이미지 URI → 모든 얼굴 임베딩 목록
     * 4방향 회전 시도로 누운 사진도 처리
     * 얼굴 박스가 MIN_FACE_PX 미만인 작은 얼굴은 제외 (불안정한 임베딩 방지)
     */
    suspend fun extractFaceEmbeddings(context: Context, imageUri: Uri): List<FloatArray> =
        withContext(Dispatchers.Default) {
            val (bitmap, rotation) = loadBitmapWithExifRaw(context, imageUri)
                ?: return@withContext emptyList()
            val (faces, finalBitmap) = detectWithRotations(bitmap, rotation)

            val usable = faces.filter {
                it.boundingBox.width() >= MIN_FACE_PX && it.boundingBox.height() >= MIN_FACE_PX
            }
            if (usable.size < faces.size) {
                android.util.Log.d(TAG, "작은 얼굴 ${faces.size - usable.size}개 제외 (최소 ${MIN_FACE_PX}px)")
            }
            usable.mapNotNull { face -> runInference(finalBitmap, face) }
        }

    /**
     * 얼굴 감지 — Face 객체 리스트 반환 (UI 오버레이 + 선택 모두 사용)
     * bitmap은 이미 EXIF 보정된 상태
     * Face 객체를 직접 보관하여 터치 선택 시 재감지 없이 크롭 가능
     */
    suspend fun detectFaceObjects(bitmap: Bitmap): List<Face> =
        withContext(Dispatchers.Default) {
            detectFaces(bitmap, accurateDetector)
        }

    /** detectFaceObjects() 결과에서 UI 오버레이용 paddedBox Rect 목록 추출 */
    fun facesToBounds(faces: List<Face>, bitmapW: Int, bitmapH: Int): List<Rect> =
        faces.map { paddedBox(it.boundingBox, bitmapW, bitmapH) }

    fun listImagesInDirectory(context: Context, dirUri: Uri): List<Uri> {
        val dir = DocumentFile.fromTreeUri(context, dirUri) ?: return emptyList()
        return dir.listFiles()
            .filter { it.isFile && it.type?.startsWith("image/") == true }
            .map { it.uri }
    }

    /**
     * 선택된 Face 객체로 썸네일 크롭 및 저장
     *
     * ★ 썸네일 크롭 방식:
     *   - 눈 기준 정렬을 하지 않음 (사용자가 선택한 얼굴 영역을 그대로 보여주는 것이 목적)
     *   - 정렬은 임베딩 추출(runInference)에서만 적용
     *
     * @param slotIndex 슬롯 인덱스 (0~3) — face_list_{listId}_slot_{slotIndex}.jpg
     */
    suspend fun cropAndSaveFaceThumbnail(
        context: Context,
        bitmap: Bitmap,
        face: Face,
        listId: Long,
        slotIndex: Int = 0,
    ): String? = withContext(Dispatchers.IO) {
        try {
            // 원본 BoundingBox 기준 크롭 (패딩 포함)
            val box  = paddedBox(face.boundingBox, bitmap.width, bitmap.height)
            val crop = safeCrop(bitmap, box)

            // 96×96 리사이즈
            val thumb = Bitmap.createScaledBitmap(crop, THUMBNAIL_SIZE, THUMBNAIL_SIZE, true)

            val dir  = java.io.File(context.cacheDir, "face_thumbnails").also { it.mkdirs() }
            val file = java.io.File(dir, "face_list_${listId}_slot_${slotIndex}.jpg")
            file.outputStream().use { thumb.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            file.absolutePath
        } catch (e: Exception) { null }
    }

    // ─── 핵심: 4방향 회전 시도 감지 ─────────────────────────────────────────

    /**
     * 4방향 회전으로 얼굴 감지 — 누운 사진 / 각도 큰 사진 대응
     *
     * 전략:
     * 1. EXIF로 얻은 원본 각도 우선 시도
     * 2. 실패 시 나머지 3방향(90°/180°/270°)을 순차 시도
     * 3. 표준 감지기 실패 시 보조 감지기(minFaceSize 최소) 재시도
     * 4. 감지된 얼굴이 있으면 해당 회전이 적용된 bitmap과 함께 반환
     *
     * @param bitmap   EXIF 보정이 적용된 원본 비트맵
     * @param exifRotation EXIF에서 읽은 회전각(0/90/180/270)
     */
    private suspend fun detectWithRotations(
        bitmap: Bitmap,
        exifRotation: Float,
    ): Pair<List<Face>, Bitmap> {
        // EXIF 기준 원본 우선, 나머지 각도 순서 결정
        val rotations = buildList {
            add(0f)   // EXIF 이미 적용됨 → 추가 회전 없음
            listOf(90f, 180f, 270f).forEach { add(it) }
        }.distinct()

        for (extraRot in rotations) {
            val rotBitmap = if (extraRot == 0f) bitmap
            else rotateBitmap(bitmap, extraRot)

            // 표준 감지기 시도
            var faces = detectFaces(rotBitmap, accurateDetector)

            // 표준 감지기 실패 → 보조 감지기(minFaceSize 최소) 재시도
            if (faces.isEmpty()) {
                faces = detectFaces(rotBitmap, fallbackDetector)
            }

            if (faces.isNotEmpty()) {
                android.util.Log.d(TAG, "얼굴 감지 성공: 추가 회전 ${extraRot}°, 감지 수 ${faces.size}")
                return faces to rotBitmap
            }
        }
        android.util.Log.d(TAG, "4방향 시도 후 얼굴 미감지")
        return emptyList<Face>() to bitmap
    }

    // ─── TFLite 추론 ─────────────────────────────────────────────────────────

    /**
     * 전처리 + TFLite 추론
     *
     * STEP 1. 눈 기준 정렬 + 크롭 + 리사이즈 (입력 크기)
     * STEP 2. 정규화 + TFLite 추론 → 임베딩
     * STEP 3. L2 정규화
     */
    private fun runInference(src: Bitmap, face: Face): FloatArray? {
        return try {
            val input = preprocessFace(src, face, spec.geometry)
            inferFromBitmap(input, spec.normalization)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "추론 실패: ${e.message}")
            null
        }
    }

    /**
     * 얼굴 전처리 — 입력 크기(정사각형) 비트맵 반환
     * 눈 랜드마크가 없으면 박스 기준 크롭으로 대체
     */
    private fun preprocessFace(src: Bitmap, face: Face, geometry: AlignGeometry): Bitmap {
        alignToTemplate(src, face, geometry)?.let { return it }

        // 폴백: 눈 랜드마크 없음 → 박스 + 12% 패딩 크롭
        val box = paddedBox(face.boundingBox, src.width, src.height, FALLBACK_PAD_RATIO)
        return Bitmap.createScaledBitmap(safeCrop(src, box), spec.inputSize, spec.inputSize, true)
    }

    /**
     * 두 눈을 입력 이미지의 고정 위치로 보내는 유사 변환(회전+크기+이동) 1개로
     * 정렬·크롭·리사이즈를 한 번에 처리한다.
     *
     * - 좌우 눈 구분은 ML Kit의 LEFT/RIGHT에 의존하지 않고 x 좌표가 작은 쪽을 왼쪽으로 사용
     * - 얼굴 주변만 먼저 잘라 쓰고, 많이 줄여야 할 때는 절반씩 축소해 앨리어싱 방지
     */
    private fun alignToTemplate(src: Bitmap, face: Face, geometry: AlignGeometry): Bitmap? {
        val size = spec.inputSize
        val a = face.getLandmark(FaceLandmark.LEFT_EYE)?.position  ?: return null
        val b = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null
        val (p1, p2) = if (a.x <= b.x) a to b else b to a

        val eyeDist = hypot(p2.x - p1.x, p2.y - p1.y)
        if (eyeDist < MIN_EYE_DIST_PX) return null

        // 출력 전체를 덮는 데 필요한 반경(눈 간격 단위)만큼 원본에서 잘라낸다
        val farY   = max(geometry.eyeYRatio, 1f - geometry.eyeYRatio)
        val half   = hypot(0.5f, farY) / geometry.eyeDistRatio * eyeDist * 1.05f
        val cx     = (p1.x + p2.x) / 2f
        val cy     = (p1.y + p2.y) / 2f
        val left   = floor(cx - half).toInt().coerceAtLeast(0)
        val top    = floor(cy - half).toInt().coerceAtLeast(0)
        val right  = ceil(cx + half).toInt().coerceAtMost(src.width)
        val bottom = ceil(cy + half).toInt().coerceAtMost(src.height)
        if (right - left < 2 || bottom - top < 2) return null

        var region = Bitmap.createBitmap(src, left, top, right - left, bottom - top)
        var x1 = p1.x - left
        var y1 = p1.y - top
        var x2 = p2.x - left
        var y2 = p2.y - top

        // 목표 눈 간격 대비 현재 눈 간격이 2배 넘게 크면 절반씩 축소
        val dstEye = geometry.eyeDistRatio * size
        while (dstEye / hypot(x2 - x1, y2 - y1) < 0.5f && region.width >= 4 && region.height >= 4) {
            val newW = region.width / 2
            val newH = region.height / 2
            val sx   = newW.toFloat() / region.width
            val sy   = newH.toFloat() / region.height
            region = Bitmap.createScaledBitmap(region, newW, newH, true)
            x1 *= sx; y1 *= sy; x2 *= sx; y2 *= sy
        }

        val centerX = size / 2f
        val eyeY    = geometry.eyeYRatio * size
        val srcPts  = floatArrayOf(x1, y1, x2, y2)
        val dstPts  = floatArrayOf(centerX - dstEye / 2f, eyeY, centerX + dstEye / 2f, eyeY)

        val matrix = Matrix()
        if (!matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 2)) return null

        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(region, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /**
     * 입력 크기로 이미 전처리된 비트맵 → 임베딩 (L2 정규화 포함)
     */
    private fun inferFromBitmap(bitmap: Bitmap, normalization: InputNormalization): FloatArray? {
        return try {
            val tflite = getOrCreateInterpreter()
            if (tflite == null) {
                // 모델 파일 없음 → null 반환 (폴백 임베딩은 임베딩 공간이 달라 비교 불가)
                android.util.Log.e(TAG,
                    "${spec.fileName} 없음. app/src/main/assets/${spec.fileName}를 배치하세요.")
                return null
            }
            val size  = spec.inputSize
            val input = if (bitmap.width == size && bitmap.height == size) bitmap
            else Bitmap.createScaledBitmap(bitmap, size, size, true)

            val buf    = bitmapToNormalizedBuffer(input, normalization)
            val output = Array(1) { FloatArray(spec.embeddingDim) }
            tflite.run(buf, output)
            l2Normalize(output[0])
        } catch (e: Exception) {
            android.util.Log.e(TAG, "추론 실패: ${e.message}")
            null
        }
    }

    /**
     * Bitmap → Float32 NHWC ByteBuffer (레이아웃 [1, size, size, 3], RGB)
     *
     * 정규화는 모델이 학습된 방식과 같아야 한다 (InputNormalization 참고).
     */
    private fun bitmapToNormalizedBuffer(bitmap: Bitmap, normalization: InputNormalization): ByteBuffer {
        val size   = spec.inputSize
        val count  = size * size
        val pixels = IntArray(count)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)

        val values = FloatArray(count * CHANNELS)
        for (i in 0 until count) {
            val px = pixels[i]
            values[i * 3]     = ((px shr 16) and 0xFF).toFloat()   // R
            values[i * 3 + 1] = ((px shr 8)  and 0xFF).toFloat()   // G
            values[i * 3 + 2] = (px          and 0xFF).toFloat()   // B
        }

        when (normalization) {
            InputNormalization.FIXED_128 -> {
                for (k in values.indices) values[k] = (values[k] - 127.5f) / 128.0f
            }
            InputNormalization.ZERO_ONE -> {
                for (k in values.indices) values[k] = values[k] / 255.0f
            }
            InputNormalization.PER_IMAGE -> {
                var sum = 0.0
                for (v in values) sum += v
                val mean = sum / values.size
                var sq = 0.0
                for (v in values) { val d = v - mean; sq += d * d }
                val std = sqrt(sq / values.size)
                // FaceNet prewhiten과 동일: std가 너무 작을 때 0 나눗셈 방지
                val adj = max(std, 1.0 / sqrt(values.size.toDouble()))
                for (k in values.indices) values[k] = ((values[k] - mean) / adj).toFloat()
            }
        }

        val buf = ByteBuffer
            .allocateDirect(count * CHANNELS * FLOAT_BYTES)
            .apply { order(ByteOrder.nativeOrder()) }
        buf.asFloatBuffer().put(values)
        buf.rewind()
        return buf
    }

    // ─── 유틸 ─────────────────────────────────────────────────────────────────

    /** BoundingBox + 패딩 (UI 오버레이·썸네일 기본값 30%) */
    private fun paddedBox(
        box: Rect,
        imgW: Int,
        imgH: Int,
        padRatio: Float = BBOX_PAD_RATIO,
    ): Rect {
        val padX = (box.width()  * padRatio).toInt()
        val padY = (box.height() * padRatio).toInt()
        return Rect(
            (box.left   - padX).coerceAtLeast(0),
            (box.top    - padY).coerceAtLeast(0),
            (box.right  + padX).coerceAtMost(imgW),
            (box.bottom + padY).coerceAtMost(imgH),
        )
    }

    /** 경계 클램핑 안전 크롭 */
    private fun safeCrop(src: Bitmap, box: Rect): Bitmap {
        val l = box.left.coerceIn(0, src.width  - 1)
        val t = box.top.coerceIn(0,  src.height - 1)
        val r = box.right.coerceIn(l + 1, src.width)
        val b = box.bottom.coerceIn(t + 1, src.height)
        return Bitmap.createBitmap(src, l, t, r - l, b - t)
    }

    /** 비트맵 회전 */
    private fun rotateBitmap(src: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    /** L2 정규화 */
    private fun l2Normalize(v: FloatArray): FloatArray {
        val norm = sqrt(v.sumOf { (it * it).toDouble() }).toFloat().coerceAtLeast(1e-10f)
        return FloatArray(v.size) { v[it] / norm }
    }

    /**
     * EXIF 보정 적용 후 비트맵 + 원본 회전각 반환
     * 회전각은 4방향 감지 시 시작 각도 결정에 사용
     */
    private fun loadBitmapWithExifRaw(context: Context, uri: Uri): Pair<Bitmap, Float>? {
        return try {
            val raw = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return null

            val degrees = context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90  -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f

            val corrected = if (degrees == 0f) raw
            else rotateBitmap(raw, degrees)

            corrected to degrees
        } catch (e: Exception) { null }
    }

    private suspend fun detectFaces(
        bitmap: Bitmap,
        detector: com.google.mlkit.vision.face.FaceDetector,
    ): List<Face> =
        suspendCancellableCoroutine { cont ->
            detector.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { faces -> cont.resume(faces) }
                .addOnFailureListener { e   -> cont.resumeWithException(e) }
        }

    // ─── Augmentation 임베딩 ──────────────────────────────────────────────────

    /**
     * 소스 사진 등록 시 다양한 변형(Augmentation)으로 여러 임베딩 생성
     *
     * 생성하는 변형:
     *   1. 원본
     *   2. 좌우 반전 (Mirror) — 측면 촬영 대응
     *   3. 밝기 +30           — 밝은 환경 대응
     *   4. 밝기 -30           — 어두운 환경 대응
     *
     * @param bitmap EXIF 보정이 완료된 원본 비트맵
     * @param face   ML Kit Face 객체
     * @return 증강된 임베딩 목록 (최소 1개, 최대 4개)
     */
    suspend fun extractAugmentedEmbeddings(
        bitmap: Bitmap,
        face: Face,
    ): List<FloatArray> = withContext(Dispatchers.Default) {
        val results = mutableListOf<FloatArray>()

        // 1. 원본 — 정렬된 입력 비트맵 1회 생성 후 변형들에 재사용
        val aligned = try {
            preprocessFace(bitmap, face, spec.geometry)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "전처리 실패: ${e.message}")
            return@withContext results
        }
        inferFromBitmap(aligned, spec.normalization)?.let { results.add(it) }
        if (results.isEmpty()) return@withContext results   // 원본 실패 시 중단

        // 2. 좌우 반전 — 측면 포즈 대응
        val flipped = Bitmap.createBitmap(
            aligned, 0, 0, aligned.width, aligned.height,
            Matrix().apply { postScale(-1f, 1f, aligned.width / 2f, aligned.height / 2f) },
            true,
        )
        inferFromBitmap(flipped, spec.normalization)?.let { results.add(it) }

        // 3. 밝기 +30 — 밝은 환경 대응
        adjustBrightness(aligned, 30)?.let { bright ->
            inferFromBitmap(bright, spec.normalization)?.let { results.add(it) }
        }

        // 4. 밝기 -30 — 어두운 환경 대응
        adjustBrightness(aligned, -30)?.let { dark ->
            inferFromBitmap(dark, spec.normalization)?.let { results.add(it) }
        }

        results
    }

    /**
     * 비트맵 밝기 조정
     * @param delta -255 ~ +255 범위
     */
    private fun adjustBrightness(src: Bitmap, delta: Int): Bitmap? {
        return try {
            val config = src.config ?: Bitmap.Config.ARGB_8888
            val dst    = Bitmap.createBitmap(src.width, src.height, config)
            val canvas = android.graphics.Canvas(dst)
            val cm = android.graphics.ColorMatrix().apply {
                set(floatArrayOf(
                    1f, 0f, 0f, 0f, delta.toFloat(),
                    0f, 1f, 0f, 0f, delta.toFloat(),
                    0f, 0f, 1f, 0f, delta.toFloat(),
                    0f, 0f, 0f, 1f, 0f,
                ))
            }
            val paint = android.graphics.Paint().apply {
                colorFilter = android.graphics.ColorMatrixColorFilter(cm)
            }
            canvas.drawBitmap(src, 0f, 0f, paint)
            dst
        } catch (e: Exception) { null }
    }

    // ─── [임시 디버그] 전처리·정규화 비교 평가 ─────────────────────────────────
    //
    // 평가 폴더 구조 (앱 전용 외부 저장소, 권한 불필요):
    //   /sdcard/Android/data/<패키지>/files/eval/<사람이름>/사진.jpg ...
    // 사람별 하위 폴더 2개 이상, 폴더당 사진 5장 이상 권장.
    // 각 사진에서 가장 큰 얼굴 1개를 사용한다.
    // 모델 확정 후 이 구역 전체를 삭제할 것.

    private class DebugConfig(
        val name: String,
        val normalization: InputNormalization,
        val preprocess: (Bitmap, Face) -> Bitmap,
    )

    private fun buildDebugConfigs(): List<DebugConfig> {
        val configs = mutableListOf<DebugConfig>()
        for (norm in InputNormalization.values()) {
            // 이전 방식(정렬 버그 포함) — 개선 효과 비교용
            configs += DebugConfig("이전방식 패딩30%  / $norm", norm) { b, f -> legacyPreprocess(b, f) }
            // 새 방식 — 눈 간격 비율 3종
            for (ratio in listOf(0.27f, 0.33f, 0.40f)) {
                val g = AlignGeometry(ratio, 0.40f)
                configs += DebugConfig("새방식 눈간격 $ratio / $norm", norm) { b, f ->
                    preprocessFace(b, f, g)
                }
            }
        }
        return configs
    }

    /** 이전 전처리(비교용): 박스+30% 패딩, 이미지 전체 회전 후 원본 좌표로 크롭 */
    private fun legacyPreprocess(src: Bitmap, face: Face): Bitmap {
        val box     = paddedBox(face.boundingBox, src.width, src.height)
        val aligned = legacyAlignFaceByEyes(face, src, box) ?: safeCrop(src, box)
        return Bitmap.createScaledBitmap(aligned, spec.inputSize, spec.inputSize, true)
    }

    private fun legacyAlignFaceByEyes(face: Face, src: Bitmap, cropBox: Rect): Bitmap? {
        val leftEye  = face.getLandmark(FaceLandmark.LEFT_EYE)?.position  ?: return null
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null
        val angle = Math.toDegrees(
            atan2(
                (rightEye.y - leftEye.y).toDouble(),
                (rightEye.x - leftEye.x).toDouble(),
            )
        ).toFloat()
        if (abs(angle) < 2f) return safeCrop(src, cropBox)
        val eyeCenterX = (leftEye.x + rightEye.x) / 2f
        val eyeCenterY = (leftEye.y + rightEye.y) / 2f
        val matrix     = Matrix().apply { postRotate(-angle, eyeCenterX, eyeCenterY) }
        val rotated    = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        return safeCrop(rotated, cropBox)
    }

    /**
     * 평가 실행 → 결과 리포트(여러 줄) 반환. 첫 줄이 1위 요약.
     * 결과는 logcat(태그 FaceEval)과 평가 폴더의 eval_result.txt에도 기록한다.
     */
    suspend fun debugEvaluate(evalRoot: File): String = withContext(Dispatchers.Default) {
        val people = evalRoot.listFiles { f -> f.isDirectory }?.sortedBy { it.name }.orEmpty()
        if (people.size < 2) {
            return@withContext "평가 폴더에 사람별 하위 폴더가 2개 이상 필요합니다: ${evalRoot.absolutePath}"
        }

        val configs = buildDebugConfigs()
        // data[설정][사람] = 임베딩 목록
        val data = Array(configs.size) { Array(people.size) { mutableListOf<FloatArray>() } }
        val imageCounts = IntArray(people.size)

        people.forEachIndexed { pi, dir ->
            val files = dir.listFiles { f ->
                f.isFile && f.extension.lowercase(Locale.US) in DEBUG_IMAGE_EXT
            }?.sortedBy { it.name }.orEmpty()

            for (file in files) {
                val (bmp, rot) = loadBitmapWithExifRaw(context, Uri.fromFile(file)) ?: continue
                val (faces, faceBitmap) = detectWithRotations(bmp, rot)
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                if (face == null) {
                    android.util.Log.w(DEBUG_TAG, "얼굴 미감지: ${dir.name}/${file.name}")
                    continue
                }
                imageCounts[pi]++
                configs.forEachIndexed { ci, cfg ->
                    try {
                        val input = cfg.preprocess(faceBitmap, face)
                        inferFromBitmap(input, cfg.normalization)?.let { data[ci][pi].add(it) }
                    } catch (e: Exception) {
                        android.util.Log.w(DEBUG_TAG, "${cfg.name} 실패: ${e.message}")
                    }
                }
            }
        }

        val header = buildString {
            append("평가 사진 수: ")
            people.forEachIndexed { i, d -> append("${d.name}=${imageCounts[i]}  ") }
        }

        // 설정별 통계
        class Row(val name: String, val sameMean: Double, val diffMean: Double,
                  val dPrime: Double, val bestAcc: Double, val bestThr: Double)

        val rows = mutableListOf<Row>()
        configs.forEachIndexed { ci, cfg ->
            val same = mutableListOf<Double>()
            val diff = mutableListOf<Double>()
            for (p in people.indices) {
                val a = data[ci][p]
                for (i in a.indices) for (j in i + 1 until a.size) same += dot(a[i], a[j])
                for (q in p + 1 until people.size) {
                    for (x in a) for (y in data[ci][q]) diff += dot(x, y)
                }
            }
            if (same.isEmpty() || diff.isEmpty()) return@forEachIndexed

            val mS = same.average()
            val mD = diff.average()
            val vS = same.sumOf { (it - mS) * (it - mS) } / same.size
            val vD = diff.sumOf { (it - mD) * (it - mD) } / diff.size
            val pooled = sqrt((vS + vD) / 2.0).coerceAtLeast(1e-9)

            // 균형 정확도가 최대가 되는 임계값 탐색
            var bestAcc = 0.0
            var bestThr = 0.0
            var thr = -0.2
            while (thr <= 1.0) {
                val tpr = same.count { it >= thr }.toDouble() / same.size
                val tnr = diff.count { it < thr }.toDouble() / diff.size
                val acc = (tpr + tnr) / 2.0
                if (acc > bestAcc) { bestAcc = acc; bestThr = thr }
                thr += 0.01
            }
            rows += Row(cfg.name, mS, mD, (mS - mD) / pooled, bestAcc, bestThr)
        }

        if (rows.isEmpty()) {
            return@withContext "평가 가능한 데이터가 부족합니다 (사람당 사진 2장 이상 필요). $header"
        }

        rows.sortByDescending { it.dPrime }
        val lines = rows.map { r ->
            "${r.name} | 같은사람 ${f3(r.sameMean)} | 다른사람 ${f3(r.diffMean)} | " +
                    "분리도 ${f3(r.dPrime)} | 최적임계값 ${f3(r.bestThr)} (균형정확도 ${f3(r.bestAcc)})"
        }
        val best = rows.first()
        val summary = "최고: ${best.name} / 분리도 ${f3(best.dPrime)} / " +
                "최적임계값 ${f3(best.bestThr)} / 균형정확도 ${f3(best.bestAcc)}"

        val report = buildString {
            appendLine(summary)
            appendLine("NNAPI 사용: ${spec.useNnapi}")
            appendLine(header)
            appendLine("(분리도가 클수록 같은 사람과 다른 사람이 잘 구분됨)")
            lines.forEach { appendLine(it) }
        }
        report.lines().forEach { android.util.Log.i(DEBUG_TAG, it) }
        try { File(evalRoot, "eval_result.txt").writeText(report) } catch (_: Exception) { }
        report
    }

    private fun dot(a: FloatArray, b: FloatArray): Double {
        var s = 0.0
        for (i in a.indices) s += a[i] * b[i]
        return s
    }

    private fun f3(x: Double): String = String.format(Locale.US, "%.3f", x)

    fun close() {
        interpreter?.close()
        interpreter = null
    }

    companion object {
        private const val TAG           = "FaceAnalyzer"
        private const val DEBUG_TAG     = "FaceEval"
        private val DEBUG_IMAGE_EXT     = setOf("jpg", "jpeg", "png", "webp")
        const val MODEL_FILE            = "facenet.tflite"
        const val INPUT_SIZE            = 160
        const val EMBEDDING_DIM         = 512
        const val CHANNELS              = 3
        const val FLOAT_BYTES           = 4
        const val BBOX_PAD_RATIO        = 0.30f
        const val THUMBNAIL_SIZE        = 96

        /** 눈 랜드마크가 없을 때 박스 크롭 폴백 패딩 */
        private const val FALLBACK_PAD_RATIO = 0.12f

        /** 검색 시 이보다 작은 얼굴(박스 가로·세로 px)은 제외 */
        const val MIN_FACE_PX               = 60

        /** 두 눈 간격이 이보다 작으면 정렬 불가로 보고 폴백 */
        private const val MIN_EYE_DIST_PX   = 8f
    }
}
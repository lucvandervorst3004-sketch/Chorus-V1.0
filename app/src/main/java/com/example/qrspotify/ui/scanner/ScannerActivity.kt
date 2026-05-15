package com.example.qrspotify.ui.scanner

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.qrspotify.R
import com.example.qrspotify.classic.ClassicModeConfig
import com.example.qrspotify.data.AppStateStore
import com.example.qrspotify.databinding.ActivityScannerBinding
import com.example.qrspotify.qr.QrParser
import com.example.qrspotify.spotify.SpotifyManager
import com.example.qrspotify.ui.nowplaying.NowPlayingActivity
import com.example.qrspotify.ui.settings.SettingsActivity
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScannerBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var barcodeScanner: BarcodeScanner

    private var cameraProvider: ProcessCameraProvider? = null
    private var isScanHandled = false
    private var isCameraStarted = false
    private var scanLineAnimator: ObjectAnimator? = null

    private var classicRole: String = ClassicModeConfig.ROLE_PARTY
    private var classicVariant: String = ClassicModeConfig.VARIANT_STANDARD

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets()

        classicRole = ClassicModeConfig.sanitizeRole(
            intent.getStringExtra(ClassicModeConfig.EXTRA_CLASSIC_ROLE)
        )
        classicVariant = ClassicModeConfig.sanitizeVariant(
            intent.getStringExtra(ClassicModeConfig.EXTRA_CLASSIC_VARIANT)
        )

        applyModeTheme()

        cameraExecutor = Executors.newSingleThreadExecutor()

        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        barcodeScanner = BarcodeScanning.getClient(options)

        binding.buttonCloseScanner.setOnClickListener {
            finish()
        }

        binding.buttonOpenSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        renderScanningState()
        startCameraIfPermissionGranted()
    }

    private fun applyInsets() {
        val initialLeft = binding.root.paddingLeft
        val initialTop = binding.root.paddingTop
        val initialRight = binding.root.paddingRight
        val initialBottom = binding.root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                initialLeft,
                initialTop + systemBars.top,
                initialRight,
                initialBottom + systemBars.bottom
            )
            insets
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScanLineAnimation()
        cameraProvider?.unbindAll()
        barcodeScanner.close()
        cameraExecutor.shutdown()
    }

    private fun applyModeTheme() {
        val isHighPressure = ClassicModeConfig.isHighPressure(classicVariant)
        val isSolo = ClassicModeConfig.isSolo(classicRole)

        if (isHighPressure) {
            val white = Color.parseColor("#FFF3F3")
            val softWhite = Color.parseColor("#E7D7D9")
            val red = Color.parseColor("#FF5A66")

            binding.root.setBackgroundResource(R.drawable.bg_high_pressure_screen)
            binding.scannerHeaderCard.setBackgroundResource(R.drawable.bg_scanner_header_card_high_pressure)
            binding.scannerInfoCard.setBackgroundResource(R.drawable.bg_scanner_status_card_pressure)
            binding.scanTargetContainer.setBackgroundResource(R.drawable.bg_scanner_camera_frame_pressure)
            binding.viewScanLine.setBackgroundResource(R.drawable.bg_scanner_scanline_high_pressure)
            binding.textScannerLiveBadge.setBackgroundResource(R.drawable.bg_scanner_live_badge_pressure)
            binding.scannerStateIconShell.setBackgroundResource(R.drawable.bg_scanner_state_icon_pressure)
            binding.textScannerEyebrow.setBackgroundResource(R.drawable.bg_scanner_badge_high_pressure)

            binding.buttonCloseScanner.setBackgroundResource(R.drawable.bg_button_mode_outline_high_pressure)
            binding.buttonOpenSettings.setBackgroundResource(R.drawable.bg_button_mode_outline_high_pressure)

            binding.imageScannerModeIcon.setImageResource(R.drawable.ic_classic_flame)
            binding.imageScannerModeIcon.setColorFilter(red)
            binding.imageScannerStateIcon.setColorFilter(red)

            binding.textScannerEyebrow.text = "High Pressure"
            binding.textScannerTitle.text = "Scan onder druk"
            binding.textScannerEyebrow.setTextColor(red)
            binding.textScannerTitle.setTextColor(red)
            binding.textScanInfo.setTextColor(softWhite)
            binding.textScannerStateTitle.setTextColor(white)
            binding.textScannerStateBody.setTextColor(softWhite)
            binding.textScannerLiveBadge.setTextColor(red)
            binding.textScannerVariantChip.setBackgroundResource(R.drawable.bg_scanner_chip_pressure)
            binding.textScannerVariantChip.setTextColor(red)

            binding.buttonCloseScanner.setTextColor(white)
            binding.buttonOpenSettings.setTextColor(white)
        } else {
            binding.root.setBackgroundResource(R.drawable.bg_stage_screen)
            binding.scannerHeaderCard.setBackgroundResource(R.drawable.bg_scanner_header_card)
            binding.scannerInfoCard.setBackgroundResource(R.drawable.bg_scanner_status_card)
            binding.scanTargetContainer.setBackgroundResource(R.drawable.bg_scanner_camera_frame)
            binding.viewScanLine.setBackgroundResource(R.drawable.bg_scan_line)
            binding.textScannerLiveBadge.setBackgroundResource(R.drawable.bg_scanner_live_badge)
            binding.scannerStateIconShell.setBackgroundResource(R.drawable.bg_scanner_state_icon)
            binding.textScannerEyebrow.setBackgroundResource(R.drawable.bg_scanner_badge)

            binding.buttonCloseScanner.setBackgroundResource(R.drawable.bg_button_mode_outline)
            binding.buttonOpenSettings.setBackgroundResource(R.drawable.bg_button_mode_outline)

            binding.imageScannerModeIcon.setImageResource(R.drawable.ic_classic_lp)
            binding.imageScannerModeIcon.clearColorFilter()
            binding.imageScannerStateIcon.setColorFilter(
                ContextCompat.getColor(this, R.color.gold_bright)
            )

            binding.textScannerEyebrow.text = "Classic scanner"
            binding.textScannerTitle.text = "Scan je kaart"
            binding.textScannerEyebrow.setTextColor(
                ContextCompat.getColor(this, R.color.accent_amber)
            )
            binding.textScannerTitle.setTextColor(
                ContextCompat.getColor(this, R.color.text_primary)
            )
            binding.textScanInfo.setTextColor(
                ContextCompat.getColor(this, R.color.text_secondary)
            )
            binding.textScannerStateTitle.setTextColor(
                ContextCompat.getColor(this, R.color.text_primary)
            )
            binding.textScannerStateBody.setTextColor(
                ContextCompat.getColor(this, R.color.text_secondary)
            )

            binding.buttonCloseScanner.setTextColor(
                ContextCompat.getColor(this, R.color.text_primary)
            )
            binding.buttonOpenSettings.setTextColor(
                ContextCompat.getColor(this, R.color.text_primary)
            )
            binding.textScannerLiveBadge.setTextColor(
                ContextCompat.getColor(this, R.color.gold_bright)
            )
            binding.textScannerVariantChip.setBackgroundResource(R.drawable.bg_scanner_chip_casual)
            binding.textScannerVariantChip.setTextColor(
                ContextCompat.getColor(this, R.color.gold_bright)
            )
        }

        binding.textScannerRoleChip.text = ClassicModeConfig.roleLabel(classicRole)
        binding.textScannerRoleChip.setBackgroundResource(
            if (isSolo) R.drawable.bg_scanner_chip_solo else R.drawable.bg_scanner_chip_party
        )
        binding.textScannerRoleChip.setTextColor(
            ContextCompat.getColor(this, if (isSolo) R.color.gold_bright else R.color.neon_cyan)
        )
        binding.textScannerVariantChip.text = ClassicModeConfig.variantLabel(classicVariant)

        val cornerColor = ContextCompat.getColor(
            this,
            if (isHighPressure) R.color.neon_red else R.color.gold_bright
        )
        binding.imageScannerCornerTopStart.setColorFilter(cornerColor)
        binding.imageScannerCornerTopEnd.setColorFilter(cornerColor)
        binding.imageScannerCornerBottomStart.setColorFilter(cornerColor)
        binding.imageScannerCornerBottomEnd.setColorFilter(cornerColor)
    }

    private fun startCameraIfPermissionGranted() {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            renderPermissionMissingState()
            binding.previewView.alpha = 0.15f
            return
        }

        startCamera()
    }

    private fun startCamera() {
        if (isCameraStarted) {
            renderScanningState()
            startScanLineAnimation()
            return
        }

        renderScanningState()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also { previewUseCase ->
                    previewUseCase.surfaceProvider = binding.previewView.surfaceProvider
                }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                processImageProxy(imageProxy)
            }

            cameraProvider?.unbindAll()
            cameraProvider?.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )

            isCameraStarted = true
            startScanLineAnimation()
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        if (isScanHandled) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                val rawValue = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                if (!rawValue.isNullOrBlank() && !isScanHandled) {
                    isScanHandled = true
                    runOnUiThread {
                        handleScanResult(rawValue)
                    }
                }
            }
            .addOnFailureListener { throwable ->
                AppStateStore.setError(
                    "Scannen mislukt: ${throwable.message ?: throwable.javaClass.simpleName}"
                )
                runOnUiThread {
                    renderRetryState()
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun handleScanResult(rawValue: String) {
        renderFoundState()

        val parseResult = QrParser.resolve(rawValue)

        if (!parseResult.isValid || parseResult.spotifyUri.isNullOrBlank()) {
            isScanHandled = false
            AppStateStore.setScanStatus("QR ongeldig")
            AppStateStore.setError(parseResult.message)
            renderInvalidState()
            return
        }

        AppStateStore.setScanResult(rawValue, parseResult.spotifyUri)
        renderStartingPlaybackState()

        if (ClassicModeConfig.isHighPressure(classicVariant)) {
            SpotifyManager.connect(this) { success, message ->
                runOnUiThread {
                    if (success) {
                        stopScanLineAnimation()
                        startActivity(
                            Intent(this, NowPlayingActivity::class.java)
                                .putExtra(ClassicModeConfig.EXTRA_CLASSIC_ROLE, classicRole)
                                .putExtra(ClassicModeConfig.EXTRA_CLASSIC_VARIANT, classicVariant)
                                .putExtra(NowPlayingActivity.EXTRA_TRACK_URI, parseResult.spotifyUri)
                                .putExtra(NowPlayingActivity.EXTRA_AUTO_START_HIGH_PRESSURE, true)
                        )
                        finish()
                    } else {
                        isScanHandled = false
                        AppStateStore.setError(message)
                        renderStartFailedState()
                    }
                }
            }
        } else {
            SpotifyManager.connect(this) { success, message ->
                runOnUiThread {
                    if (success) {
                        stopScanLineAnimation()
                        startActivity(
                            Intent(this, NowPlayingActivity::class.java)
                                .putExtra(ClassicModeConfig.EXTRA_CLASSIC_ROLE, classicRole)
                                .putExtra(ClassicModeConfig.EXTRA_CLASSIC_VARIANT, classicVariant)
                                .putExtra(NowPlayingActivity.EXTRA_TRACK_URI, parseResult.spotifyUri)
                                .putExtra(NowPlayingActivity.EXTRA_AUTO_START_STANDARD, true)
                        )
                        finish()
                    } else {
                        isScanHandled = false
                        AppStateStore.setError(message)
                        renderStartFailedState()
                    }
                }
            }
        }
    }

    private fun renderScanningState() {
        binding.textScanInfo.text = "Houd de QR-code rustig binnen het kader."

        val title = buildString {
            append(ClassicModeConfig.roleLabel(classicRole))
            append(" • ")
            append(ClassicModeConfig.variantLabel(classicVariant))
        }

        binding.textScannerStateTitle.text = title
        binding.textScannerStateBody.text =
            when {
                ClassicModeConfig.isHighPressure(classicVariant) && ClassicModeConfig.isSolo(classicRole) ->
                    "Na het scannen start direct een strenge 10-secondenronde. Je kunt dezelfde kaart maximaal 2 keer starten en je kunt je antwoord invullen in de app."
                ClassicModeConfig.isHighPressure(classicVariant) && ClassicModeConfig.isParty(classicRole) ->
                    "Na het scannen hoor je alleen de eerste 10 seconden. Elke kaart kun je maximaal 2 keer starten. Geen antwoordscherm in de app."
                ClassicModeConfig.isSolo(classicRole) ->
                    "Na het scannen speel je solo verder en kun je je antwoord in de app invullen."
                else ->
                    "Zodra de kaart is herkend start het nummer automatisch. Titel en artiest blijven verborgen zoals in party play."
            }
    }

    private fun renderPermissionMissingState() {
        binding.textScanInfo.text = "Camera nodig om te scannen."
        binding.textScannerStateTitle.text = "Camera nog niet beschikbaar"
        binding.textScannerStateBody.text =
            "Geef cameratoegang in Android-instellingen om kaarten te kunnen scannen."
        stopScanLineAnimation()
    }

    private fun renderFoundState() {
        binding.textScanInfo.text = "Kaart gevonden."
        binding.textScannerStateTitle.text = "QR-code herkend"
        binding.textScannerStateBody.text =
            "We controleren de kaart en maken het nummer klaar om af te spelen."
    }

    private fun renderInvalidState() {
        binding.textScanInfo.text = "Deze kaart werkt niet."
        binding.textScannerStateTitle.text = "Kaart niet geldig"
        binding.textScannerStateBody.text =
            "Probeer een andere kaart of scan dezelfde kaart opnieuw."
    }

    private fun renderStartingPlaybackState() {
        binding.textScanInfo.text = "Nummer start..."
        binding.textScannerStateTitle.text = "Klaar om te luisteren"
        binding.textScannerStateBody.text =
            if (ClassicModeConfig.isHighPressure(classicVariant)) {
                "De app opent nu High Pressure. De eerste 10 seconden starten meteen in het volgende scherm."
            } else {
                "De app opent nu Classic Mode en start het nummer automatisch."
            }
    }

    private fun renderStartFailedState() {
        binding.textScanInfo.text = "Starten lukte niet."
        binding.textScannerStateTitle.text = "Opnieuw proberen"
        binding.textScannerStateBody.text =
            "Scan nog een keer. De verbinding met Spotify of de kaart kon niet goed worden gestart."
    }

    private fun renderRetryState() {
        binding.textScanInfo.text = "Probeer opnieuw te scannen."
        binding.textScannerStateTitle.text = "Scannen onderbroken"
        binding.textScannerStateBody.text =
            "Houd de kaart iets stiller en zorg dat de QR-code goed zichtbaar blijft."
    }

    private fun startScanLineAnimation() {
        binding.viewScanLine.visibility = View.VISIBLE

        binding.scanTargetContainer.post {
            if (scanLineAnimator?.isRunning == true) return@post

            val containerHeight = binding.scanTargetContainer.height.toFloat()
            val lineHeight = binding.viewScanLine.height.toFloat()
            val travel = ((containerHeight - lineHeight) / 2f).coerceAtLeast(0f)

            scanLineAnimator = ObjectAnimator.ofFloat(
                binding.viewScanLine,
                View.TRANSLATION_Y,
                -travel,
                travel
            ).apply {
                duration = 1700L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = LinearInterpolator()
                start()
            }
        }
    }

    private fun stopScanLineAnimation() {
        scanLineAnimator?.cancel()
        scanLineAnimator = null
        binding.viewScanLine.translationY = 0f
        binding.viewScanLine.visibility = View.INVISIBLE
    }
}

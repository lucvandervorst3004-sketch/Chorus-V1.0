package com.example.qrspotify.ui.classic

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.qrspotify.R
import com.example.qrspotify.classic.ClassicModeConfig
import com.example.qrspotify.databinding.ActivityClassicModeChoiceBinding
import com.example.qrspotify.ui.scanner.ScannerActivity

class ClassicModeChoiceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityClassicModeChoiceBinding
    private var selectedRole = ClassicModeConfig.ROLE_PARTY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityClassicModeChoiceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets()

        binding.buttonBackClassicMode.setOnClickListener {
            finish()
        }

        binding.buttonSoloClassic.setOnClickListener {
            selectedRole = ClassicModeConfig.ROLE_SOLO
            renderRoleSelection()
        }

        binding.buttonPartyClassic.setOnClickListener {
            selectedRole = ClassicModeConfig.ROLE_PARTY
            renderRoleSelection()
        }

        binding.buttonCasualClassic.setOnClickListener {
            openScanner(
                role = selectedRole,
                variant = ClassicModeConfig.VARIANT_STANDARD
            )
        }

        binding.buttonHighPressureClassic.setOnClickListener {
            openScanner(
                role = selectedRole,
                variant = ClassicModeConfig.VARIANT_HIGH_PRESSURE
            )
        }

        renderRoleSelection()
    }

    private fun applyInsets() {
        val initialLeft = binding.contentClassicModeChoice.paddingLeft
        val initialTop = binding.contentClassicModeChoice.paddingTop
        val initialRight = binding.contentClassicModeChoice.paddingRight
        val initialBottom = binding.contentClassicModeChoice.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootClassicModeChoice) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.contentClassicModeChoice.setPadding(
                initialLeft,
                initialTop + systemBars.top,
                initialRight,
                initialBottom + systemBars.bottom
            )
            insets
        }
    }

    private fun openScanner(role: String, variant: String) {
        startActivity(
            Intent(this, ScannerActivity::class.java)
                .putExtra(ClassicModeConfig.EXTRA_CLASSIC_ROLE, role)
                .putExtra(ClassicModeConfig.EXTRA_CLASSIC_VARIANT, variant)
        )
    }

    private fun renderRoleSelection() {
        val soloSelected = selectedRole == ClassicModeConfig.ROLE_SOLO
        binding.buttonSoloClassic.isSelected = soloSelected
        binding.buttonPartyClassic.isSelected = !soloSelected

        binding.textSoloSelectionChip.background = ContextCompat.getDrawable(
            this,
            if (soloSelected) R.drawable.bg_classic_selected_chip else R.drawable.bg_classic_choice_chip
        )
        binding.textPartySelectionChip.background = ContextCompat.getDrawable(
            this,
            if (soloSelected) R.drawable.bg_classic_choice_chip else R.drawable.bg_classic_selected_chip
        )

        binding.textSoloSelectionChip.text = if (soloSelected) "Gekozen" else "Kies"
        binding.textPartySelectionChip.text = if (soloSelected) "Kies" else "Gekozen"

        binding.textSoloSelectionChip.setTextColor(
            ContextCompat.getColor(this, if (soloSelected) R.color.text_dark else R.color.home_text_muted)
        )
        binding.textPartySelectionChip.setTextColor(
            ContextCompat.getColor(this, if (soloSelected) R.color.home_text_muted else R.color.text_dark)
        )

        binding.textClassicSelectedRole.text =
            if (soloSelected) "Solo gekozen" else "Party gekozen"
    }
}

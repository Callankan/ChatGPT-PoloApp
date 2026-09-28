package com.poloapp

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.poloapp.ui.PoloRoot

class MainActivity : FragmentActivity() {
    private val model: PoloViewModel by viewModels()
    private var financeUnlocked by mutableStateOf(false)
    private val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PoloRoot(model, financeUnlocked, { done -> authenticate(done) }, { financeUnlocked = false }) }
    }
    private fun authenticate(done: () -> Unit) {
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            model.reportError("Configura la huella o un bloqueo de pantalla en los ajustes de Android para proteger la financiación.")
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { financeUnlocked = true; done() }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) model.reportError(errString.toString()) }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Tu financiación, privada").setSubtitle("Desbloquea con la huella o el bloqueo de pantalla").setAllowedAuthenticators(authenticators).build())
    }
    override fun onStop() { super.onStop(); financeUnlocked = false }
}

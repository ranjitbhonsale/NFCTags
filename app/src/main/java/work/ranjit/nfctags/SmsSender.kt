package work.ranjit.nfctags

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat

object SmsSender {
    private const val TAG = "SmsSender"

    fun hasSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun sendSms(context: Context, rawPhoneNumbers: String?, message: String?): String {
        if (!hasSmsPermission(context)) {
            Log.e(TAG, "SEND_SMS permission not granted")
            return "Failed: SEND_SMS permission not granted"
        }

        if (rawPhoneNumbers.isNullOrBlank()) {
            return "Failed: No phone number specified"
        }

        val text = message?.trim() ?: ""
        if (text.isEmpty()) {
            return "Failed: SMS message is empty"
        }

        val numbers = rawPhoneNumbers.split(",", ";", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (numbers.isEmpty()) {
            return "Failed: No valid phone numbers found"
        }

        val smsManager = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get SmsManager", e)
            return "Failed to get SmsManager: ${e.message}"
        }

        if (smsManager == null) {
            return "Failed: SmsManager not available on this device"
        }

        var successCount = 0
        val failureDetails = mutableListOf<String>()

        for (number in numbers) {
            try {
                val parts = smsManager.divideMessage(text)
                if (parts.size > 1) {
                    smsManager.sendMultipartTextMessage(number, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(number, null, text, null, null)
                }
                successCount++
                Log.d(TAG, "SMS dispatched to $number")
            } catch (e: Exception) {
                Log.e(TAG, "Failed sending SMS to $number", e)
                failureDetails.add("$number (${e.localizedMessage ?: "Error"})")
            }
        }

        return if (failureDetails.isEmpty()) {
            if (numbers.size == 1) {
                "SMS sent to ${numbers[0]}"
            } else {
                "SMS sent to all $successCount numbers"
            }
        } else {
            "Sent: $successCount, Failed: ${failureDetails.joinToString("; ")}"
        }
    }
}

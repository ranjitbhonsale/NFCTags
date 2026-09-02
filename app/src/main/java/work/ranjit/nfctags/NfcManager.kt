package work.ranjit.nfctags

import android.app.Activity
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.nio.charset.Charset

enum class NdefPayloadType {
    TEXT, URI, UNKNOWN
}

data class NfcTagData(
    val tagId: String = "",
    val technologies: List<String> = emptyList(),
    val payload: String = "",
    val payloadType: NdefPayloadType = NdefPayloadType.UNKNOWN,
    val isWritable: Boolean = false,
    val type: String = "Unknown"
)

class NfcManager(private val activity: Activity) : NfcAdapter.ReaderCallback {

    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    
    private val _tagData = MutableStateFlow(NfcTagData())
    val tagData: StateFlow<NfcTagData> = _tagData.asStateFlow()

    private val _statusMessage = MutableStateFlow("Tap a tag to the back of your phone")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private var currentTag: Tag? = null

    private fun triggerHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = activity.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = activity.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(50)
                }
            }
        } catch (e: Exception) {
            // Ignore if vibration fails or requires permission we don't have
        }
    }

    fun enableReaderMode() {
        if (nfcAdapter != null) {
            val options = Bundle()
            options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
            nfcAdapter.enableReaderMode(
                activity,
                this,
                NfcAdapter.FLAG_READER_NFC_A or
                        NfcAdapter.FLAG_READER_NFC_B or
                        NfcAdapter.FLAG_READER_NFC_F or
                        NfcAdapter.FLAG_READER_NFC_V or
                        NfcAdapter.FLAG_READER_NFC_BARCODE,
                options
            )
            _statusMessage.value = "Ready to scan. Tap a tag to your phone."
        } else {
            _statusMessage.value = "NFC is not available on this device."
        }
    }

    fun disableReaderMode() {
        nfcAdapter?.disableReaderMode(activity)
    }

    override fun onTagDiscovered(tag: Tag?) {
        if (tag == null) return
        currentTag = tag
        triggerHapticFeedback()
        
        val idHex = bytesToHex(tag.id)
        val techList = tag.techList.map { it.substringAfterLast('.') }
        
        var payloadText = "Empty tag"
        var pType = NdefPayloadType.UNKNOWN
        var writable = false
        var tagType = "Unknown"

        val ndef = Ndef.get(tag)
        if (ndef != null) {
            tagType = ndef.type
            writable = ndef.isWritable
            try {
                ndef.connect()
                val ndefMessage = ndef.ndefMessage
                if (ndefMessage != null) {
                    val (text, type) = parseMessage(ndefMessage)
                    payloadText = text
                    pType = type
                }
            } catch (e: Exception) {
                Log.e("NfcManager", "Error reading NDEF", e)
                payloadText = "Error reading tag data"
            } finally {
                try { ndef.close() } catch (e: Exception) {}
            }
        } else {
            val mifareClassic = MifareClassic.get(tag)
            if (mifareClassic != null) {
                tagType = "Mifare Classic"
                payloadText = "Mifare Classic detected."
            }
        }

        _tagData.value = NfcTagData(
            tagId = idHex,
            technologies = techList,
            payload = payloadText,
            payloadType = pType,
            isWritable = writable,
            type = tagType
        )
        
        _statusMessage.value = "Tag Detected Successfully!"
    }
    
    fun writeNdefMessage(text: String, isUri: Boolean = false, passwordHex: String = "") {
        val tag = currentTag
        if (tag == null) {
            _statusMessage.value = "No tag detected. Please scan a tag first."
            return
        }

        _statusMessage.value = "Writing to tag..."

        try {
            val ndef = Ndef.get(tag)
            val message = if (text.isEmpty()) {
                NdefMessage(arrayOf(NdefRecord(NdefRecord.TNF_EMPTY, null, null, null)))
            } else if (isUri) {
                NdefRecord.createUri(text)?.let { NdefMessage(arrayOf(it)) } ?: createTextNdefMessage(text)
            } else {
                createTextNdefMessage(text)
            }

            if (ndef != null) {
                ndef.connect()
                
                if (!ndef.isWritable) {
                    _statusMessage.value = "Tag is read-only."
                    return
                }
                
                if (ndef.maxSize < message.toByteArray().size) {
                    _statusMessage.value = "Message too large for this tag."
                    return
                }
                
                ndef.writeNdefMessage(message)
                _statusMessage.value = "Successfully wrote to tag."
                triggerHapticFeedback()
                
                // update payload view
                _tagData.value = _tagData.value.copy(
                    payload = if (text.isEmpty()) "Empty tag" else text,
                    payloadType = if (text.isEmpty()) NdefPayloadType.UNKNOWN else if (isUri) NdefPayloadType.URI else NdefPayloadType.TEXT
                )
                
            } else {
                val formatable = NdefFormatable.get(tag)
                if (formatable != null) {
                    formatable.connect()
                    formatable.format(message)
                    _statusMessage.value = "Successfully formatted and wrote to tag."
                    triggerHapticFeedback()
                } else {
                    // Try Mifare Classic write if applicable
                    val mifare = MifareClassic.get(tag)
                    if (mifare != null && !isUri && text.isNotEmpty()) {
                       writeMifareClassic(mifare, text, passwordHex)
                    } else {
                        _statusMessage.value = "Tag does not support NDEF."
                    }
                }
            }
        } catch (e: Exception) {
            _statusMessage.value = "Failed to write: ${e.message}"
            Log.e("NfcManager", "Write error", e)
        } finally {
            try { Ndef.get(tag)?.close() } catch (e: Exception) {}
            try { NdefFormatable.get(tag)?.close() } catch (e: Exception) {}
            try { MifareClassic.get(tag)?.close() } catch (e: Exception) {}
        }
    }
    
    private fun writeMifareClassic(mifare: MifareClassic, text: String, keyHex: String) {
        try {
            mifare.connect()
            val key = if (keyHex.length == 12) hexStringToByteArray(keyHex) else MifareClassic.KEY_DEFAULT
            
            // Try authenticating to sector 1
            val auth = mifare.authenticateSectorWithKeyA(1, key)
            if (auth) {
                val blockIndex = mifare.sectorToBlock(1)
                val data = text.toByteArray(Charset.forName("US-ASCII")).copyOf(16) // Max 16 bytes per block
                mifare.writeBlock(blockIndex, data)
                _statusMessage.value = "Successfully wrote to Mifare Classic."
                triggerHapticFeedback()
            } else {
                _statusMessage.value = "Mifare Classic authentication failed."
            }
        } catch (e: Exception) {
            _statusMessage.value = "Mifare write error: ${e.message}"
        }
    }

    private fun createTextNdefMessage(text: String): NdefMessage {
        val langBytes = "en".toByteArray(Charset.forName("US-ASCII"))
        val textBytes = text.toByteArray(Charset.forName("UTF-8"))
        val payload = ByteArray(1 + langBytes.size + textBytes.size)
        payload[0] = langBytes.size.toByte()
        System.arraycopy(langBytes, 0, payload, 1, langBytes.size)
        System.arraycopy(textBytes, 0, payload, 1 + langBytes.size, textBytes.size)

        val record = NdefRecord(
            NdefRecord.TNF_WELL_KNOWN,
            NdefRecord.RTD_TEXT,
            ByteArray(0),
            payload
        )
        return NdefMessage(arrayOf(record))
    }

    private fun parseMessage(message: NdefMessage): Pair<String, NdefPayloadType> {
        val records = message.records
        if (records.isEmpty()) return Pair("Empty", NdefPayloadType.UNKNOWN)
        
        val record = records[0]
        
        // Check for URI
        if (record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_URI)) {
            val payload = record.payload
            val prefix = URI_PREFIX_MAP[payload[0]] ?: ""
            val fullUri = prefix + String(payload, 1, payload.size - 1, Charset.forName("UTF-8"))
            return Pair(fullUri, NdefPayloadType.URI)
        }
        
        // Check for Smart Poster containing URI
        if (record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_SMART_POSTER)) {
            return Pair("Smart Poster URI", NdefPayloadType.URI) // Simplification
        }
        
        // Check for Text
        if (record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT)) {
            val payload = record.payload
            val languageCodeLength = (payload[0].toInt() and 0x3F)
            val text = String(
                payload,
                languageCodeLength + 1,
                payload.size - languageCodeLength - 1,
                Charset.forName("UTF-8")
            )
            return Pair(text, NdefPayloadType.TEXT)
        }
        
        return Pair("Unsupported format", NdefPayloadType.UNKNOWN)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = "0123456789ABCDEF"[v ushr 4]
            hexChars[j * 2 + 1] = "0123456789ABCDEF"[v and 0x0F]
        }
        return String(hexChars)
    }
    
    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4)
                    + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
    
    companion object {
        private val URI_PREFIX_MAP = mapOf(
            0x00.toByte() to "",
            0x01.toByte() to "http://www.",
            0x02.toByte() to "https://www.",
            0x03.toByte() to "http://",
            0x04.toByte() to "https://",
            0x05.toByte() to "tel:",
            0x06.toByte() to "mailto:",
            0x07.toByte() to "ftp://anonymous:anonymous@",
            0x08.toByte() to "ftp://ftp.",
            0x09.toByte() to "ftps://",
            0x0A.toByte() to "sftp://",
            0x0B.toByte() to "smb://",
            0x0C.toByte() to "nfs://",
            0x0D.toByte() to "ftp://",
            0x0E.toByte() to "dav://",
            0x0F.toByte() to "news:",
            0x10.toByte() to "telnet://",
            0x11.toByte() to "imap:",
            0x12.toByte() to "rtsp://",
            0x13.toByte() to "urn:",
            0x14.toByte() to "pop:",
            0x15.toByte() to "sip:",
            0x16.toByte() to "sips:",
            0x17.toByte() to "tftp:",
            0x18.toByte() to "btspp://",
            0x19.toByte() to "btl2cap://",
            0x1A.toByte() to "btgoep://",
            0x1B.toByte() to "tcpobex://",
            0x1C.toByte() to "irdaobex://",
            0x1D.toByte() to "file://",
            0x1E.toByte() to "urn:epc:id:",
            0x1F.toByte() to "urn:epc:tag:",
            0x20.toByte() to "urn:epc:pat:",
            0x21.toByte() to "urn:epc:raw:",
            0x22.toByte() to "urn:epc:",
            0x23.toByte() to "urn:nfc:"
        )
    }
}

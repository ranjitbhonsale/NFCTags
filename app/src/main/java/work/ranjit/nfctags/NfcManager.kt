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
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.nio.charset.Charset

data class NfcTagData(
    val tagId: String = "",
    val technologies: List<String> = emptyList(),
    val payload: String = "",
    val isWritable: Boolean = false,
    val type: String = "Unknown"
)

class NfcManager(private val activity: Activity) : NfcAdapter.ReaderCallback {

    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    
    private val _tagData = MutableStateFlow(NfcTagData())
    val tagData: StateFlow<NfcTagData> = _tagData.asStateFlow()

    private val _statusMessage = MutableStateFlow("Waiting for NFC tag...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private var currentTag: Tag? = null

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
            _statusMessage.value = "NFC Reader Mode Enabled. Bring a tag close."
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
        
        val idHex = bytesToHex(tag.id)
        val techList = tag.techList.map { it.substringAfterLast('.') }
        
        var payloadText = "No NDEF content"
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
                    payloadText = readTextFromMessage(ndefMessage)
                }
            } catch (e: Exception) {
                Log.e("NfcManager", "Error reading NDEF", e)
            } finally {
                try { ndef.close() } catch (e: Exception) {}
            }
        } else {
            val mifareClassic = MifareClassic.get(tag)
            if (mifareClassic != null) {
                tagType = "Mifare Classic"
                payloadText = "Mifare Classic detected. Use password (key) to read/write sectors."
            }
        }

        _tagData.value = NfcTagData(
            tagId = idHex,
            technologies = techList,
            payload = payloadText,
            isWritable = writable,
            type = tagType
        )
        
        _statusMessage.value = "Tag Detected!"
    }
    
    fun writeNdefMessage(text: String, passwordHex: String = "") {
        val tag = currentTag
        if (tag == null) {
            _statusMessage.value = "No tag available to write."
            return
        }

        _statusMessage.value = "Writing to tag..."

        try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                
                // Password functionality for NDEF tags (like NTAG21x) requires raw commands.
                // For simplicity, we are implementing plain NDEF write here.
                // Advanced password handling is complex and hardware specific.
                
                if (!ndef.isWritable) {
                    _statusMessage.value = "Tag is read-only."
                    return
                }
                
                val message = createTextNdefMessage(text)
                if (ndef.maxSize < message.toByteArray().size) {
                    _statusMessage.value = "Message too large for this tag."
                    return
                }
                
                ndef.writeNdefMessage(message)
                _statusMessage.value = "Successfully wrote to tag."
                
                // update payload view
                _tagData.value = _tagData.value.copy(payload = text)
                
            } else {
                val formatable = NdefFormatable.get(tag)
                if (formatable != null) {
                    formatable.connect()
                    val message = createTextNdefMessage(text)
                    formatable.format(message)
                    _statusMessage.value = "Successfully formatted and wrote to tag."
                } else {
                    // Try Mifare Classic write if applicable
                    val mifare = MifareClassic.get(tag)
                    if (mifare != null) {
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
                _statusMessage.value = "Successfully wrote to Mifare Classic Sector 1."
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

    private fun readTextFromMessage(message: NdefMessage): String {
        val records = message.records
        for (record in records) {
            if (record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT)) {
                val payload = record.payload
                val languageCodeLength = (payload[0].toInt() and 0x3F)
                return String(
                    payload,
                    languageCodeLength + 1,
                    payload.size - languageCodeLength - 1,
                    Charset.forName("UTF-8")
                )
            }
        }
        return "Unsupported NDEF content"
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
}

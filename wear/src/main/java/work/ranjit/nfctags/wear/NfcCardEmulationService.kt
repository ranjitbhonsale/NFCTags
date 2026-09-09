package work.ranjit.nfctags.wear

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import java.nio.charset.StandardCharsets
import java.util.Arrays

class NfcCardEmulationService : HostApduService() {

    companion object {
        private const val TAG = "NfcHceService"

        // Status Words (ISO 7816-4)
        private val STATUS_SUCCESS = byteArrayOf(0x90.toByte(), 0x00.toByte())
        private val STATUS_FAILED = byteArrayOf(0x6F.toByte(), 0x00.toByte())
        private val CLA_NOT_SUPPORTED = byteArrayOf(0x6E.toByte(), 0x00.toByte())
        private val INS_NOT_SUPPORTED = byteArrayOf(0x6D.toByte(), 0x00.toByte())
        private val FILE_NOT_FOUND = byteArrayOf(0x6A.toByte(), 0x82.toByte())

        // APDU Commands (ISO 7816-4)
        // SELECT Application AID: D2 76 00 00 85 01 01
        private val SELECT_APPLICATION = byteArrayOf(
            0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte(), 0x07.toByte(),
            0xD2.toByte(), 0x76.toByte(), 0x00.toByte(), 0x00.toByte(), 0x85.toByte(), 0x01.toByte(), 0x01.toByte(), 0x00.toByte()
        )

        // SELECT CC (Capability Container) File: E1 03
        private val SELECT_CC_FILE = byteArrayOf(
            0x00.toByte(), 0xA4.toByte(), 0x00.toByte(), 0x0C.toByte(), 0x02.toByte(),
            0xE1.toByte(), 0x03.toByte()
        )

        // SELECT NDEF Data File: E1 04
        private val SELECT_NDEF_FILE = byteArrayOf(
            0x00.toByte(), 0xA4.toByte(), 0x00.toByte(), 0x0C.toByte(), 0x02.toByte(),
            0xE1.toByte(), 0x04.toByte()
        )

        // Standard Capability Container for Type 4 NDEF Tag
        // Defines NDEF file size of 1024 bytes (0x0400), Read access granted without security (0x00), Write access read-only (0xFF)
        private val CC_FILE = byteArrayOf(
            0x00, 0x0F, // CCLEN: 15 bytes
            0x20,       // Mapping Version: 2.0
            0x00, 0x7F, // MLe: Max R-APDU size 127 bytes
            0x00, 0x7F, // MLc: Max C-APDU size 127 bytes
            0x04, 0x06, // NDEF File Control TLV (Tag 0x04, Length 0x06)
            0xE1.toByte(), 0x04.toByte(), // File Identifier: E104
            0x04, 0x00, // Max NDEF size: 1024 bytes
            0x00,       // Read access: Free
            0xFF.toByte() // Write access: No write
        )
    }

    private var selectedFile: SelectedFile = SelectedFile.NONE

    private enum class SelectedFile {
        NONE, CC, NDEF
    }

    override fun onCreate() {
        super.onCreate()
        WearTagRepository.init(this)
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null || commandApdu.size < 4) {
            return STATUS_FAILED
        }

        // 1. SELECT Application
        if (Arrays.equals(SELECT_APPLICATION, commandApdu) ||
            (commandApdu.size >= 12 && commandApdu[0] == 0x00.toByte() && commandApdu[1] == 0xA4.toByte() && commandApdu[2] == 0x04.toByte())
        ) {
            Log.d(TAG, "HCE: NDEF Application Selected")
            selectedFile = SelectedFile.NONE
            return STATUS_SUCCESS
        }

        // 2. SELECT CC File (E1 03)
        if (Arrays.equals(SELECT_CC_FILE, commandApdu)) {
            Log.d(TAG, "HCE: CC File Selected")
            selectedFile = SelectedFile.CC
            return STATUS_SUCCESS
        }

        // 3. SELECT NDEF File (E1 04)
        if (Arrays.equals(SELECT_NDEF_FILE, commandApdu)) {
            Log.d(TAG, "HCE: NDEF File Selected")
            selectedFile = SelectedFile.NDEF
            return STATUS_SUCCESS
        }

        // 4. READ BINARY command (INS = 0xB0)
        if (commandApdu[1] == 0xB0.toByte()) {
            val offset = ((commandApdu[2].toInt() and 0xFF) shl 8) or (commandApdu[3].toInt() and 0xFF)
            val length = if (commandApdu.size > 4) (commandApdu[4].toInt() and 0xFF) else 0

            return when (selectedFile) {
                SelectedFile.CC -> {
                    readBinary(CC_FILE, offset, length)
                }
                SelectedFile.NDEF -> {
                    val ndefData = buildCurrentNdefData()
                    readBinary(ndefData, offset, length)
                }
                SelectedFile.NONE -> FILE_NOT_FOUND
            }
        }

        return INS_NOT_SUPPORTED
    }

    private fun readBinary(data: ByteArray, offset: Int, length: Int): ByteArray {
        if (offset >= data.size) {
            return STATUS_FAILED
        }
        val readLen = if (length == 0 || offset + length > data.size) {
            data.size - offset
        } else {
            length
        }

        val result = ByteArray(readLen + 2)
        System.arraycopy(data, offset, result, 0, readLen)
        result[readLen] = 0x90.toByte()
        result[readLen + 1] = 0x00.toByte()
        return result
    }

    private fun buildCurrentNdefData(): ByteArray {
        val activeTag = WearTagRepository.activeTag.value
        val textToEmulate = if (activeTag != null) {
            if (activeTag.payload.isNotEmpty() && activeTag.payload != "Empty tag") {
                activeTag.payload
            } else {
                "TAG_ID:${activeTag.tagId}"
            }
        } else {
            "NFC_WEAR_DEFAULT"
        }

        // Create Text NDEF Record
        val langBytes = "en".toByteArray(StandardCharsets.US_ASCII)
        val textBytes = textToEmulate.toByteArray(StandardCharsets.UTF_8)
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
        val message = NdefMessage(arrayOf(record))
        val messageBytes = message.toByteArray()

        // Type 4 NDEF file requires 2-byte big-endian NLEN prefix
        val fileBytes = ByteArray(messageBytes.size + 2)
        fileBytes[0] = ((messageBytes.size ushr 8) and 0xFF).toByte()
        fileBytes[1] = (messageBytes.size and 0xFF).toByte()
        System.arraycopy(messageBytes, 0, fileBytes, 2, messageBytes.size)
        return fileBytes
    }

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "HCE Deactivated, reason: $reason")
        selectedFile = SelectedFile.NONE
    }
}

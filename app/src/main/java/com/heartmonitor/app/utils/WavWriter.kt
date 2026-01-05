package com.heartmonitor.app.utils

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder



fun convertPcmToWav(
    pcmFile: File,
    wavFile: File,
    sampleRate: Int,
    channels: Int
) {
    val pcmBytes = pcmFile.readBytes()
    val writer = WavWriter(
        file = wavFile,
        sampleRate = sampleRate,
        channels = channels,
        bitsPerSample = 16
    )
    writer.writePcmBytes(pcmBytes)
    writer.close()
    android.util.Log.e(
        "WAV",
        "size=${wavFile.length()}"
    )
}
class WavWriter(
    private val file: File,
    private val sampleRate: Int = 8000,
    private val channels: Int = 1,
    private val bitsPerSample: Int = 16
) {
    private val raf = RandomAccessFile(file, "rw")
    private var dataBytesWritten = 0



    init {
        raf.setLength(0)
        writeWavHeader(0) // placeholder sizes
    }

    fun writePcmBytes(pcm: ByteArray) {
        raf.seek(44L + dataBytesWritten)
        raf.write(pcm)
        dataBytesWritten += pcm.size
    }



    fun close() {
        // Rewrite header with correct sizes
        raf.seek(0)
        writeWavHeader(dataBytesWritten)
        raf.close()
    }

    private fun writeWavHeader(dataSize: Int) {
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = channels * (bitsPerSample / 8)
        val chunkSize = 36 + dataSize

        fun writeString(s: String) = raf.writeBytes(s)
        fun writeLEInt(v: Int) {
            val bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v)
            raf.write(bb.array())
        }
        fun writeLEShort(v: Short) {
            val bb = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v)
            raf.write(bb.array())
        }

        writeString("RIFF")
        writeLEInt(chunkSize)
        writeString("WAVE")

        writeString("fmt ")
        writeLEInt(16) // PCM fmt chunk size
        writeLEShort(1) // audio format = PCM
        writeLEShort(channels.toShort())
        writeLEInt(sampleRate)
        writeLEInt(byteRate)
        writeLEShort(blockAlign.toShort())
        writeLEShort(bitsPerSample.toShort())

        writeString("data")
        writeLEInt(dataSize)
    }
}

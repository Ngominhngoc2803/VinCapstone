package com.heartmonitor.app.utils

import kotlin.math.abs
import kotlin.math.sqrt

object SignalProcessor {
    
    /**
     * Calculate BPM from heart signal data using peak detection
     * @param signalData Raw signal data from the stethoscope
     * @param sampleRate Samples per second (Hz)
     * @return Calculated BPM
     */
    fun calculateBpm(signalData: List<Float>, sampleRate: Int = 1000): Int {
        if (signalData.size < sampleRate) return 0
        
        // Simple peak detection
        val peaks = detectPeaks(signalData)
        
        if (peaks.size < 2) return 0
        
        // Calculate average interval between peaks
        val intervals = peaks.zipWithNext { a, b -> b - a }
        val averageInterval = intervals.average()
        
        // Convert to BPM
        val bpm = (60.0 * sampleRate / averageInterval).toInt()
        
        return bpm.coerceIn(30, 220) // Reasonable BPM range
    }
    
    /**
     * Detect peaks in the signal using simple threshold-based detection
     */
    private fun detectPeaks(signalData: List<Float>): List<Int> {
        val peaks = mutableListOf<Int>()
        val threshold = signalData.map { abs(it) }.average() * 1.5
        var lastPeakIndex = -100
        
        for (i in 2 until signalData.size - 2) {
            val current = signalData[i]
            
            // Check if it's a local maximum above threshold
            if (current > threshold &&
                current > signalData[i - 1] &&
                current > signalData[i - 2] &&
                current > signalData[i + 1] &&
                current > signalData[i + 2] &&
                i - lastPeakIndex > 100 // Minimum distance between peaks
            ) {
                peaks.add(i)
                lastPeakIndex = i
            }
        }
        
        return peaks
    }
    
    /**
     * Apply a simple moving average filter to smooth the signal
     */
    fun smoothSignal(signalData: List<Float>, windowSize: Int = 5): List<Float> {
        if (signalData.size < windowSize) return signalData
        
        return signalData.windowed(windowSize, 1, true) { window ->
            window.average().toFloat()
        }
    }
    
    /**
     * Normalize signal data to range [-1, 1]
     */
    fun normalizeSignal(signalData: List<Float>): List<Float> {
        if (signalData.isEmpty()) return emptyList()
        
        val max = signalData.maxOrNull() ?: 1f
        val min = signalData.minOrNull() ?: -1f
        val range = max - min
        
        if (range == 0f) return signalData.map { 0f }
        
        return signalData.map { (it - min) / range * 2 - 1 }
    }
    
    /**
     * Calculate signal statistics
     */
    fun calculateStatistics(signalData: List<Float>): SignalStatistics {
        if (signalData.isEmpty()) {
            return SignalStatistics(0f, 0f, 0f, 0f, 0f)
        }
        
        val mean = signalData.average().toFloat()
        val variance = signalData.map { (it - mean) * (it - mean) }.average().toFloat()
        val stdDev = sqrt(variance.toDouble()).toFloat()
        val min = signalData.minOrNull() ?: 0f
        val max = signalData.maxOrNull() ?: 0f
        
        return SignalStatistics(mean, stdDev, variance, min, max)
    }
    
    /**
     * Extract heart rate variability features
     */
    fun calculateHrv(signalData: List<Float>, sampleRate: Int = 1000): HrvMetrics {
        val peaks = detectPeaks(signalData)
        
        if (peaks.size < 3) {
            return HrvMetrics(0.0, 0.0, 0.0)
        }
        
        // Calculate RR intervals in milliseconds
        val rrIntervals = peaks.zipWithNext { a, b -> 
            (b - a).toDouble() / sampleRate * 1000 
        }
        
        // SDNN - Standard deviation of NN intervals
        val meanRr = rrIntervals.average()
        val sdnn = sqrt(rrIntervals.map { (it - meanRr) * (it - meanRr) }.average())
        
        // RMSSD - Root mean square of successive differences
        val successiveDiffs = rrIntervals.zipWithNext { a, b -> (b - a) * (b - a) }
        val rmssd = sqrt(successiveDiffs.average())
        
        // pNN50 - Percentage of successive RR intervals that differ by more than 50ms
        val nn50Count = rrIntervals.zipWithNext { a, b -> abs(b - a) > 50 }.count { it }
        val pnn50 = (nn50Count.toDouble() / (rrIntervals.size - 1)) * 100
        
        return HrvMetrics(sdnn, rmssd, pnn50)
    }
}

data class SignalStatistics(
    val mean: Float,
    val stdDev: Float,
    val variance: Float,
    val min: Float,
    val max: Float
)

data class HrvMetrics(
    val sdnn: Double,
    val rmssd: Double,
    val pnn50: Double
)

# Heart Health Monitor App

A comprehensive Android application for personal heart health monitoring that works with ESP32-based digital stethoscopes via Bluetooth Low Energy (BLE). The app features real-time heart sound recording, AI-powered health analysis using ChatGPT, and detailed user health tracking.

## 📱 Screenshots Overview

The app consists of three main screens:
1. **Storage & Statistics** - Main recording interface with BLE connection and BPM chart
2. **Signal Analysis** - Detailed waveform view with AI chatbox for health inquiries
3. **User Profile** - Weekly health summary, AI recommendations, and doctor information

---

## 🛠️ Project Setup Instructions

### Prerequisites

- **Android Studio**: Ladybug (2024.2.1) or newer
- **JDK**: 17 or higher
- **Android SDK**: API 36 (Android 16.0) - compileSdk
- **Minimum SDK**: API 26 (Android 8.0)
- **Kotlin**: 2.0.21
- **Gradle**: 8.9

### Step 1: Create New Project in Android Studio

1. Open Android Studio
2. Click "New Project"
3. Select "Empty Activity" (Compose)
4. Configure:
   - Name: `HeartHealthMonitor`
   - Package name: `com.heartmonitor.app`
   - Language: Kotlin
   - Minimum SDK: API 26
5. Click "Finish"

### Step 2: Replace Project Files

After creating the project, replace/add the following files from this repository:

```
HeartHealthMonitor/
├── build.gradle.kts                    # Root build file
├── settings.gradle.kts                 # Settings with dependencies
├── gradle/
│   └── libs.versions.toml              # Version catalog
├── app/
│   ├── build.gradle.kts                # App build file
│   ├── proguard-rules.pro              # ProGuard rules
│   └── src/main/
│       ├── AndroidManifest.xml         # Permissions & app config
│       ├── res/
│       │   ├── values/
│       │   │   ├── strings.xml
│       │   │   ├── colors.xml
│       │   │   └── themes.xml
│       │   └── xml/
│       │       ├── backup_rules.xml
│       │       └── data_extraction_rules.xml
│       └── java/com/heartmonitor/app/
│           ├── HeartMonitorApplication.kt
│           ├── MainActivity.kt
│           ├── bluetooth/
│           │   └── BleManager.kt
│           ├── data/
│           │   ├── local/
│           │   │   ├── Entities.kt
│           │   │   ├── Daos.kt
│           │   │   └── HeartMonitorDatabase.kt
│           │   ├── remote/
│           │   │   └── OpenAIService.kt
│           │   └── repository/
│           │       └── Repositories.kt
│           ├── di/
│           │   └── AppModule.kt
│           ├── domain/model/
│           │   ├── HeartRecording.kt
│           │   ├── UserProfile.kt
│           │   └── ChatMessage.kt
│           ├── presentation/
│           │   ├── components/
│           │   │   ├── CommonComponents.kt
│           │   │   └── BpmChart.kt
│           │   ├── navigation/
│           │   │   └── Navigation.kt
│           │   ├── screens/
│           │   │   ├── home/
│           │   │   │   └── HomeScreen.kt
│           │   │   ├── analysis/
│           │   │   │   └── AnalysisScreen.kt
│           │   │   └── profile/
│           │   │       └── ProfileScreen.kt
│           │   ├── theme/
│           │   │   ├── Theme.kt
│           │   │   └── Typography.kt
│           │   └── viewmodel/
│           │       ├── HomeViewModel.kt
│           │       ├── AnalysisViewModel.kt
│           │       └── ProfileViewModel.kt
│           └── utils/
│               ├── SignalProcessor.kt
│               └── DateTimeUtils.kt
```

### Step 3: Configure OpenAI API Key

1. Open `app/build.gradle.kts`
2. Find the line:
   ```kotlin
   buildConfigField("String", "OPENAI_API_KEY", "\"YOUR_OPENAI_API_KEY\"")
   ```
3. Replace `YOUR_OPENAI_API_KEY` with your actual OpenAI API key

**Important**: For production, use a more secure method like:
- `local.properties` file (excluded from git)
- Environment variables
- Encrypted storage

### Step 4: Sync and Build

1. Click "Sync Project with Gradle Files"
2. Wait for dependencies to download
3. Build the project: `Build > Make Project`

---

## 📂 Architecture Overview

The app follows **Clean Architecture** with **MVVM** pattern:

```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                        │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ HomeScreen  │  │AnalysisScreen│  │ProfileScreen│         │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘         │
│         │                │                │                  │
│  ┌──────▼──────┐  ┌──────▼──────┐  ┌──────▼──────┐         │
│  │HomeViewModel│  │AnalysisVM   │  │ProfileVM    │         │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘         │
└─────────┼────────────────┼────────────────┼─────────────────┘
          │                │                │
┌─────────▼────────────────▼────────────────▼─────────────────┐
│                      Domain Layer                            │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              Models (HeartRecording, UserProfile)    │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────┬───────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────┐
│                       Data Layer                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │  Room DB     │  │  OpenAI API  │  │  BLE Manager │       │
│  │  (Local)     │  │  (Remote)    │  │  (Bluetooth) │       │
│  └──────────────┘  └──────────────┘  └──────────────┘       │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔌 ESP32 BLE Integration

### BLE Service Configuration

The app expects your ESP32 to advertise the following UUIDs:

```kotlin
// Heart Rate Service (Standard BLE)
HEART_MONITOR_SERVICE_UUID = "0000180D-0000-1000-8000-00805f9b34fb"

// Heart Rate Measurement Characteristic
HEART_RATE_MEASUREMENT_UUID = "00002A37-0000-1000-8000-00805f9b34fb"

// Custom Heart Signal Data Characteristic (for raw waveform)
HEART_SIGNAL_DATA_UUID = "00002A38-0000-1000-8000-00805f9b34fb"
```

### ESP32 Data Format

The app expects heart signal data as 16-bit signed integers sent in little-endian format:

```cpp
// ESP32 Arduino Example
void sendHeartData(int16_t* samples, int count) {
    uint8_t buffer[count * 2];
    for (int i = 0; i < count; i++) {
        buffer[i * 2] = samples[i] & 0xFF;         // Low byte
        buffer[i * 2 + 1] = (samples[i] >> 8) & 0xFF; // High byte
    }
    pCharacteristic->setValue(buffer, count * 2);
    pCharacteristic->notify();
}
```

### Customizing UUIDs

To use custom UUIDs, modify `BleManager.kt`:

```kotlin
companion object {
    val HEART_MONITOR_SERVICE_UUID: UUID = UUID.fromString("YOUR-SERVICE-UUID")
    val HEART_SIGNAL_DATA_UUID: UUID = UUID.fromString("YOUR-CHARACTERISTIC-UUID")
}
```

---

## 🤖 AI Integration (ChatGPT)

The app uses OpenAI's GPT-4o-mini for:
1. **Signal Analysis** - Automated detection of heart conditions
2. **Chat Interface** - Interactive Q&A about heart health

### API Configuration

Located in `OpenAIService.kt`:

```kotlin
data class ChatCompletionRequest(
    val model: String = "gpt-4o-mini",  // Change model here
    val messages: List<ChatRequestMessage>,
    val max_tokens: Int = 1000,
    val temperature: Float = 0.7f
)
```

### Integrating Custom ML Models

To add your own ML model for classification, modify `HomeViewModel.kt`:

```kotlin
fun stopRecording() {
    // ... existing code ...
    
    // Replace this simple classification with your ML model
    val healthStatus = when {
        avgBpm > 120 || avgBpm < 50 -> HealthStatus.ISSUES_DETECTED
        maxBpm > 150 -> HealthStatus.ISSUES_DETECTED
        // Add your ML model prediction here:
        // yourMLModel.predict(signalData) -> HealthStatus
        else -> HealthStatus.GOOD_HEALTH
    }
}
```

---

## 📊 Key Features Implementation

### 1. Real-time Signal Recording

```kotlin
// HomeViewModel.kt
private fun observeBleSignal() {
    viewModelScope.launch {
        bleManager.heartSignalData.collect { newData ->
            if (_uiState.value.isRecording) {
                signalBuffer.addAll(newData)
                val currentBpm = SignalProcessor.calculateBpm(signalBuffer)
                // Update UI state...
            }
        }
    }
}
```

### 2. BPM Calculation (Peak Detection)

```kotlin
// SignalProcessor.kt
fun calculateBpm(signalData: List<Float>, sampleRate: Int = 1000): Int {
    val peaks = detectPeaks(signalData)
    if (peaks.size < 2) return 0
    
    val intervals = peaks.zipWithNext { a, b -> b - a }
    val averageInterval = intervals.average()
    
    return (60.0 * sampleRate / averageInterval).toInt().coerceIn(30, 220)
}
```

### 3. Room Database Persistence

```kotlin
// All recordings are automatically persisted
@Entity(tableName = "heart_recordings")
data class HeartRecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val timestamp: LocalDateTime,
    val signalData: List<Float>,
    val healthStatus: String,
    // ...
)
```

---

## 🔧 Customization Guide

### Changing Theme Colors

Edit `Theme.kt`:

```kotlin
val OrangePrimary = Color(0xFFFF6B35)  // Main accent color
val GreenGood = Color(0xFF4CAF50)       // Good health indicator
val RedWarning = Color(0xFFF44336)      // Warning indicator
```

### Adding New Screens

1. Create screen composable in `presentation/screens/`
2. Add route to `Navigation.kt`
3. Create ViewModel if needed
4. Add navigation item if visible in bottom nav

### Modifying Signal Processing

Edit `SignalProcessor.kt` to add:
- Custom peak detection algorithms
- Filter implementations
- HRV (Heart Rate Variability) metrics
- Spectral analysis

---

## 📱 Testing Without ESP32

The app includes simulated data generation for testing:

```kotlin
// HomeViewModel.kt - startSimulatedRecording()
private fun generateSimulatedHeartSignal(): List<Float> {
    // Generates realistic-looking heart waveform data
    // Used when BLE is not connected
}
```

To test:
1. Run the app on an emulator or device
2. Click "Start Recording" without connecting BLE
3. The app will generate simulated heart signals

---

## 🚀 Building for Release

1. Create signing key:
   ```bash
   keytool -genkey -v -keystore release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias release
   ```

2. Configure signing in `app/build.gradle.kts`:
   ```kotlin
   signingConfigs {
       create("release") {
           storeFile = file("release-key.jks")
           storePassword = "your-password"
           keyAlias = "release"
           keyPassword = "your-password"
       }
   }
   ```

3. Build release APK:
   ```bash
   ./gradlew assembleRelease
   ```

---

## 📋 Dependencies Used

| Library | Purpose |
|---------|---------|
| Jetpack Compose | Modern UI toolkit |
| Hilt | Dependency injection |
| Room | Local database |
| Retrofit | Network requests (OpenAI API) |
| Navigation Compose | Screen navigation |
| Accompanist Permissions | Runtime permissions |
| Kotlin Coroutines | Async operations |
| DataStore | Preferences storage |

---

## ⚠️ Important Notes

1. **Medical Disclaimer**: This app is for educational purposes only. Do not use for medical diagnosis.

2. **API Costs**: OpenAI API calls incur costs. Monitor your usage.

3. **Bluetooth Permissions**: Android 12+ requires `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` permissions.

4. **Battery Usage**: Continuous BLE scanning can drain battery. Implement scan timeouts in production.

---

## 📞 Support

For issues or questions:
- Check the code comments for implementation details
- Review the architecture diagram above
- Ensure all dependencies are properly synced

---

## 📄 License

This project is provided for educational purposes. Please respect privacy and medical regulations when deploying.

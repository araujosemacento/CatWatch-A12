# Especificação de Engenharia de Software: CatWatch A12

Este documento serve como especificação técnica detalhada e instrução de contexto (prompt de sistema / PRD) para o desenvolvimento de um aplicativo Android nativo em Kotlin voltado para monitoramento contínuo e detecção seletiva de gatos utilizando visão computacional embarcada.

---

## 1. Visão Geral do Projeto

* **Nome do Projeto:** `CatWatch A12`
* **Objetivo:** Monitorar continuamente o ambiente por meio da câmera traseira do dispositivo, identificar a entrada de gatos no enquadramento e persistir unicamente os registros com capturas estáticas (*snapshots*) de alta qualidade e metadados de auditoria, descartando qualquer gravação contínua em vídeo para preservar a memória física e a integridade térmica do aparelho.
* **Público-alvo/Dispositivo Alvo:** Samsung Galaxy A12 (`SM-A127M` / Exynos 850 octa-core Cortex-A55 @ 2.0 GHz, 3GB/4GB RAM, Android 11 a 13).
* **Restrição Crítica:** Operação 24/7 sem vazamento de memória (*zero-memory leak*), sem sobrecarga de CPU/GPU (limite de 15% a 25% de uso contínuo) e proteção contra estrangulamento térmico (*thermal throttling*).

---

## 2. Requisitos de Hardware e Premissas de Otimização

1. **Eficiência Térmica e Subamostragem:** O Exynos 850 não dispõe de NPU dedicada. O analisador de visão computacional deve limitar a taxa de inferência a **1 a 2 frames por segundo (FPS)**. O pipeline de câmera pode capturar a 30 FPS, mas os frames excedentes devem ser descartados imediatamente no buffer nativo.
2. **Buffer Zero-Copy:** Utilização obrigatória da API `CameraX` com entrega de `ImageProxy` diretamente para o `InputImage` do ML Kit, liberando o frame via `imageProxy.close()` no bloco `finally`/`addOnCompleteListener`.
3. **Resolução Assíncrona Dual:**
   * **Fluxo de Análise (`ImageAnalysis`):** 640x480 (VGA) ou resolução próxima a 480p para minimizar o processamento por matriz de pixel.
   * **Fluxo de Captura (`ImageCapture`):** Resolução padrão de foto (ex: 1080p ou nativa da lente) acionada apenas quando o evento for confirmado, garantindo imagem nítida para auditoria.
4. **Política de Cooldown (Anti-duplicação):** Uma vez que um gato seja detectado e registrado, o sistema entra em um estado de descanso (*debounce/cooldown*) configurável (padrão: 10 a 15 segundos) antes de permitir novo registro para o mesmo animal contínuo no campo de visão.

---

## 3. Arquitetura do Sistema

```plaintext
                      +-----------------------------+
                      |       CameraX Stream        |
                      +--------------+--------------+
                                     |
             +-----------------------+-----------------------+
             |                                               |
             v                                               v
+--------------------------+                   +---------------------------+
|      ImageAnalysis       |                   |       ImageCapture        |
|  (640x480 @ 1-2 FPS)     |                   |    (Alta Resolução)       |
+------------+-------------+                   +-------------+-------------+
             |                                               |
             v                                               | (Disparo sob
+--------------------------+                                 |  demanda)
|    CatDetectorAnalyzer   |                                 |
|  - Throttling (500-1000ms)                                 |
|  - Google ML Kit         |                                 |
|    Image Labeling        |                                 |
+------------+-------------+                                 |
             |                                               |
     [Gato Detectado?]                                       |
             |                                               |
             +--- SIM ---> [Verifica Cooldown Ativo?]        |
                                  |                          |
                                 NÃO                         |
                                  |                          |
                                  +---------> Dispara Gatilho+
                                              |
                                              v
                                   +---------------------+
                                   |   SnapshotManager   |
                                   | - Salva JPG em disco|
                                   | - Cria log no Room  |
                                   +---------------------+
```

---

## 4. Stack Tecnológica e Dependências

* **Linguagem:** Kotlin 1.9+ (com Coroutines)
* **JVM / JDK de Compilação:** Java 17 LTS (exigido pelo Android Gradle Plugin 8.x; garante compatibilidade perfeita com desugaring e compiladores de símbolos).
* **SDK Mínimo (`minSdk`):** 26 (Android 8.0)
* **SDK Alvo (`targetSdk`):** 33 ou 34 (Android 13/14)
* **Bibliotecas Principais:**
  * Jetpack CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`)
  * Google ML Kit Image Labeling (`com.google.mlkit:image-labeling:17.0.9`) - Modelo on-device com 400+ rótulos visuais (inclui "Cat", "Kitten"), ultraleve para CPU Exynos 850 sem NPU.
  * Jetpack Room 2.6.1 + KSP (Kotlin Symbol Processing para persistência leve de auditoria)
  * Lifecycle & Foreground Service

### Arquivo: `app/build.gradle.kts` (Exemplo)

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.catwatch.detector"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.catwatch.detector"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // AndroidX & UI Básica
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // CameraX
    val cameraVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraVersion")
    implementation("androidx.camera:camera-camera2:$cameraVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraVersion")
    implementation("androidx.camera:camera-view:$cameraVersion")

    // Google ML Kit (Classificação de Imagens com suporte nativo a felinos)
    implementation("com.google.mlkit:image-labeling:17.0.9")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Room (Banco local para feed de auditoria via KSP)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
}
```

---

## 5. Implementação Técnica dos Componentes Principais

### 5.1. Analisador com Subamostragem e Filtro Semântico (`CatDetectorAnalyzer.kt`)

```kotlin
package com.catwatch.detector.camera

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions

class CatDetectorAnalyzer(
    private val onCatDetected: (Float) -> Unit
) : ImageAnalysis.Analyzer {

    // Configuração do detector on-device com threshold de confiança
    private val options = ImageLabelerOptions.Builder()
        .setConfidenceThreshold(0.60f)
        .build()

    private val labeler = ImageLabeling.getClient(options)
    
    // Controle de throttling: Processar no máximo 2 quadros por segundo
    private var lastAnalyzedTimestamp = 0L
    private val throttleIntervalMs = 500L

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()

        // Descarte rápido caso o intervalo de subamostragem não tenha decorrido
        if (currentTimestamp - lastAnalyzedTimestamp < throttleIntervalMs) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        lastAnalyzedTimestamp = currentTimestamp
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        labeler.process(inputImage)
            .addOnSuccessListener { labels ->
                for (label in labels) {
                    val labelText = label.text
                    // O modelo Image Labeling identifica entidades específicas de felinos
                    if (labelText.equals("Cat", ignoreCase = true) || 
                        labelText.equals("Kitten", ignoreCase = true) ||
                        labelText.equals("Felidae", ignoreCase = true)) {
                        onCatDetected(label.confidence)
                        return@addOnSuccessListener
                    }
                }
            }
            .addOnFailureListener {
                // Silenciar ou registrar logs mínimos para não onerar o I/O
            }
            .addOnCompleteListener {
                // Fechar o ImageProxy é imperativo para evitar o congelamento do buffer CameraX
                imageProxy.close()
            }
    }
}
```

---

### 5.2. Gerenciador de Disparo e Cooldown (`CaptureCoordinator.kt`)

```kotlin
package com.catwatch.detector.core

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService

class CaptureCoordinator(
    private val context: Context,
    private val imageCapture: ImageCapture,
    private val ioExecutor: ExecutorService,
    private val onEventLogged: (File, Float) -> Unit
) {
    private var lastCaptureTimestamp = 0L
    private val cooldownDurationMs = 15_000L // 15 segundos entre eventos consecutivos

    fun onCatCandidate(confidence: Float = 1.0f) {
        val now = System.currentTimeMillis()
        if (now - lastCaptureTimestamp < cooldownDurationMs) {
            // Animal ainda em cena ou evento duplicado recente
            return
        }

        lastCaptureTimestamp = now
        executeSnapshot(confidence)
    }

    private fun executeSnapshot(confidence: Float) {
        val storageDir = File(context.filesDir, "cat_events").apply {
            if (!exists()) mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val photoFile = File(storageDir, "CAT_${timestamp}.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ioExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.i("CaptureCoordinator", "Snapshot gravado com sucesso: ${photoFile.absolutePath}")
                    onEventLogged(photoFile, confidence)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CaptureCoordinator", "Falha na gravação do snapshot", exception)
                }
            }
        )
    }
}
```

---

### 5.3. Modelo de Dados de Auditoria (`CatEventEntity.kt`)

```kotlin
package com.catwatch.detector.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cat_events")
data class CatEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val filePath: String,
    val confidence: Float
)
```

---

## 6. Configurações de Permissões e Manifesto

### Arquivo: `AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Permissões Obrigatórias -->
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CAMERA" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <uses-feature android:name="android.hardware.camera" android:required="true" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="CatWatch"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.MaterialComponents.DayNight.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="portrait">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>
</manifest>
```

---

## 7. Instruções Operacionais no Aparelho (Galaxy A12)

Para garantir que o SM-A127M funcione de forma ininterrupta sem intervenção do sistema One UI:

1. **Configuração de Bateria (One UI):**
   * Acessar: *Configurações > Assistência do Aparelho > Bateria > Mais configurações de bateria*.
   * Habilitar: **Proteger a bateria** (limita a carga máxima a 85% para evitar degradação química em carregamento contínuo).
2. **Exceção de Otimização de Bateria:**
   * Acessar: *Configurações > Aplicativos > CatWatch > Bateria*.
   * Selecionar: **Irrestrito** (evita suspensão pelo agendador de processos da Samsung).
3. **Gerenciamento de Tela:**
   * Reduzir o brilho da tela ao nível mínimo enquanto o app estiver em execução.
   * Não utilizar resolução maior que 720p para visualização de pré-visualização (*Preview View*).

---

## 8. Prompt Mestre para o Agente de IA

Copie e cole o bloco abaixo no seu agente de código para iniciar a geração do projeto:

```text
Você é um desenvolvedor Android sênior especialista em Kotlin, CameraX e Google ML Kit.
Sua tarefa é implementar a aplicação completa descrita na especificação 'CatWatch A12'.

Requisitos de Código:
1. Implemente o código completo em Kotlin com arquitetura limpa e desacoplada, utilizando Room via KSP e compatibilidade com Java 17.
2. Utilize CameraX configurando dois casos de uso vinculados ao ProcessCameraProvider:
   - ImageAnalysis configurado com STRATEGY_KEEP_ONLY_LATEST e resolução VGA (640x480).
   - ImageCapture configurado para resolução padrão de foto.
3. Desenvolva o CatDetectorAnalyzer integrando o ImageLabeling do ML Kit (com.google.mlkit:image-labeling) para classificação on-device de felinos ("Cat", "Kitten", "Felidae") com threshold >= 0.60f.
4. Implemente controle de throttling (inferência a cada 500ms) e cooldown de 15 a 45 segundos para não duplicar capturas do mesmo evento.
5. Garanta o descarte correto de buffers chamando imageProxy.close() em todos os fluxos de saída (sucesso, falha ou descarte por throttle).
6. Crie uma interface minimalista com:
   - PreviewView ocupando a metade superior.
   - RecyclerView com o histórico recente de fotos capturadas na metade inferior.
```

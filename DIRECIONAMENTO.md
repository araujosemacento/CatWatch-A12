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
   * **Fluxo de Análise (`ImageAnalysis`):** 640x480 (VGA) ou resolução próxima a 480p para minimizar o processamento por matriz de pixel. O modelo a ser utilizado é estritamente o `Image Labeling` genérico para preservação de bateria e temperatura. **Nunca** incorpore detecção espacial com Bounding Boxes (`Object Detection`), uma vez que os recursos do SoC do A12 são insuficientes para tal em regimes 24/7.
   * **Fluxo de Captura (`ImageCapture`):** Resolução forçada para 720p (compressão de 80% JPEG) para poupar uso de disco a longo prazo.
4. **Política de Cooldown (Anti-duplicação):** O sistema entra em um estado de descanso de **2 minutos (120 segundos)** após uma sessão. O *threshold* do ML Kit para reconhecer o felino deve operar em **80% a 85%** de precisão.

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

    // Configuração do detector on-device com threshold estrito
    private val options = ImageLabelerOptions.Builder()
        .setConfidenceThreshold(0.80f)
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

### 5.2. Gerenciador de Disparo, Confirmação de Hidratação e Cooldown (`CaptureCoordinator.kt`)

O disparo não depende de delays cegos. Adota-se a estratégia **Dual-Snapshot de Confirmação**:

1. **$T_0$ (Aproximação Imediata):** Ao detectar felino ($\ge 0.60$), dispara imediatamente o snapshot de chegada (`CAT_APPROACH_...jpg`). Isso assegura que visitas rápidas nunca sejam perdidas.
2. **Janela Ativa de 5s (Verificação de Permanência):** Inicia-se uma janela de monitoramento de 5 segundos. Se aos 5 segundos o analisador confirmar que o gato permanece na área da bacia, dispara-se o segundo snapshot (`CAT_DRINKING_...jpg`) e marca-se o evento como hidratação confirmada.
3. **Armazenamento Público (`Pictures/CatWatch`):** Para facilitar a localização nos aplicativos nativos "Galeria" e "Meus Arquivos" do Galaxy A12, os arquivos são salvos no diretório público `Pictures/CatWatch` com notificação ao `MediaScannerConnection`.
4. **Cooldown Pós-Evento:** Após o ciclo de aproximação/confirmação, aplica-se um cooldown de 20 a 30 segundos para evitar saturação de I/O enquanto o animal bebe confortavelmente.

```kotlin
package com.catwatch.detector.core

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService

class CaptureCoordinator(
    private val context: Context,
    private val imageCapture: ImageCapture,
    private val ioExecutor: ExecutorService,
    private val coroutineScope: CoroutineScope,
    private val onEventLogged: (File, Float, String, Boolean) -> Unit
) {
    private var lastEventTimestamp = 0L
    private val cooldownDurationMs = 120_000L // 2 Minutos
    private val confirmationDelayMs = 5_000L
    private var activeMonitoringJob: Job? = null
    private var lastSeenConfidence = 0f
    private var isCatPresent = false

    fun onCatCandidate(confidence: Float = 1.0f) {
        val now = System.currentTimeMillis()
        lastSeenConfidence = confidence
        isCatPresent = true

        if (now - lastEventTimestamp < cooldownDurationMs) {
            return
        }

        if (activeMonitoringJob == null || activeMonitoringJob?.isActive == false) {
            lastEventTimestamp = now
            // Disparo imediato T0 (Aproximação)
            executeSnapshot("APPROACH", confidence, isConfirmed = false)

            // Janela de confirmação de 5 segundos
            activeMonitoringJob = coroutineScope.launch(Dispatchers.Default) {
                isCatPresent = false
                delay(confirmationDelayMs)
                if (isCatPresent) {
                    // Gato continuou presente: confirmação de hidratação
                    executeSnapshot("DRINKING", lastSeenConfidence, isConfirmed = true)
                }
            }
        }
    }

    private fun executeSnapshot(eventType: String, confidence: Float, isConfirmed: Boolean) {
        val storageDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "CatWatch"
        ).apply { if (!exists()) mkdirs() }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val photoFile = File(storageDir, "CAT_${eventType}_${timestamp}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ioExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    // Notifica o MediaStore da Samsung para indexar na Galeria e Meus Arquivos
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(photoFile.absolutePath),
                        arrayOf("image/jpeg"),
                        null
                    )
                    onEventLogged(photoFile, confidence, eventType, isConfirmed)
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

### 5.3. Modelo de Dados de Auditoria (`CatEventEntity.kt` e DAO)

```kotlin
package com.catwatch.detector.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cat_events")
data class CatEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val filePath: String,
    val confidence: Float,
    val eventType: String = "APPROACH", // "APPROACH" ou "DRINKING"
    val isConfirmedDrinking: Boolean = false
)

@Dao
interface CatEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: CatEventEntity): Long

    @Query("SELECT * FROM cat_events ORDER BY timestamp DESC")
    fun getAllEventsPaged(): Flow<List<CatEventEntity>>

    @Query("SELECT * FROM cat_events WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getEventsByDateRange(startTime: Long, endTime: Long): Flow<List<CatEventEntity>>

    @Delete
    suspend fun deleteEvent(event: CatEventEntity)

    @Query("DELETE FROM cat_events WHERE id IN (:ids)")
    suspend fun deleteEventsByIds(ids: List<Long>)
}
```

---

### 5.4. Interface, Visualização e Gerenciamento de Feed

Para assegurar excelente usabilidade no acompanhamento do animal, o módulo de UI conta com os seguintes componentes e fluxos operacionais:

1. **Controle Operacional de Monitoramento em 3 Estados (`Segmented Button`):**
   * Localizado no cabeçalho superior (`normalToolbar`), substitui o switch binário por um seletor compacto de 3 estados (`MaterialButtonToggleGroup` com seleção única). **Nota de Arquitetura One UI:** O uso de `SwitchCompat`/`MaterialSwitch` é proibido devido a falha interna de NPE (`makeLayout(null)` $\rightarrow$ `StaticLayout.<init>`) decorrente da injeção forçada de `showText = true` pelos temas da Samsung.
     * **Desligado (`Standby`):** Desvincula a câmera (`cameraProvider.unbindAll()`), desligando o sensor óptico. Alívio térmico imediato para uso focado no histórico de fotos ou manutenção do disco. Exibe uma camada de sobreposição (`cameraOffOverlay`) com ícone `ic_videocam_off` e texto técnico *"Câmera em repouso"*.
     * **Enquadrar (`Preview Sem IA`):** Câmera ativa no `PreviewView`, mas o `CatDetectorAnalyzer` descarta todos os quadros no topo do loop (`imageProxy.close()`). Permite alinhar o tripé e limpar a tigela sem disparar capturas indesejadas.
     * **Monitorar (`Operação 24/7`):** Pipeline completo ativo (`PreviewView` + `CatDetectorAnalyzer` com threshold de 80% + Dual-Snapshot T0/T5 via `CaptureCoordinator`).
   * **Persistência e Inicialização Segura:** O estado do seletor é persistido em `SharedPreferences` (`camera_mode`). Durante a inicialização (`onCreate` ou retorno de permissões), a rotina de inicialização de câmera deve obrigatoriamente validar o estado salvo: se estiver em `Desligado`, o sensor físico de câmera não deve ser vinculado ao ciclo de vida (`bindToLifecycle`).
   * **Highlight Dinâmico de Cores:** Estilizado através de `ColorStateList` dinâmico (`selector_toggle_text.xml`), garantindo que o botão atualmente ativo receba a cor de destaque temática (`#81C784`) e os inativos permaneçam em cinza neutro (`#888888`), eliminando cores estáticas no XML.
2. **Painel Dinâmico Deslizante de Eventos (45% a 80%) com Zero-Jank no Exynos 850:**
   * **Modo Painel (Repouso em ~45%):** Quando no topo da lista (`position == 0`), o feed ocupa a metade inferior e o preview tem amplo destaque. A navegação sticky fica engatada e o FAB permanece oculto.
   * **Modo Galeria Expandida (Expansão até 80%):** Ao rolar para baixo para inspecionar fotos antigas, o painel do feed desliza suavemente sobre o preview, travando em 80% da tela. Uma faixa superior de 20% do preview é preservada para garantir a percepção de que a câmera continua ativa. O modo sticky desatraca para não interromper a leitura do histórico e o FAB com chevron é exibido.
   * **Invariante Crítica de Performance (Zero-Jank):** A `PreviewView` do CameraX **nunca é redimensionada dinamicamente** durante o scroll (o que causaria recomposição contínua de buffers na GPU do Exynos 850). O painel de feed sobrepõe o preview através de translação vertical (`translationY`).
   * **Detecção Híbrida de Rolagem:** O painel monitora eventos de rolagem via `RecyclerView.OnScrollListener` para listas longas e integra um `GestureDetector` via `addOnItemTouchListener` para responder a gestos verticais mesmo quando o feed possui poucas entradas (sem rolagem nativa suficiente).
3. **Navegação Sticky e Botão Flutuante (FAB com Badge):** *(IMPLEMENTADO)*
   * O feed acompanha automaticamente as novas fotos que chegam se o usuário estiver na posição zero (`position == 0`).
   * Ao rolar para o passado, o FAB (`fabScrollToTop`) é revelado no canto inferior direito. Se novos registros entrarem enquanto a lista está rolada, o badge indicador (`fabNewItemsBadge` com ponto vermelho) alerta o usuário. Ao tocar no FAB, a lista executa `smoothScrollToPosition(0)`, recolhe o painel para 45% e reativa o comportamento sticky.
4. **Visualizador de Carrossel de Sessão em Tela Cheia (`CatEventDetailDialogFragment`):** *(IMPLEMENTADO)*
   * Implementado com `ViewPager2` horizontal e transição suave, com swipe estritamente restrito aos eventos que compõem a **Chained Session** ativa (agrupamento via `ChainedSessionManager` para capturas ocorridas em intervalo $\le 5\text{min}$).
   * **Indicador Dinâmico de Pontos (*Dots Indicator*):** Acoplado através de `TabLayout` e `TabLayoutMediator` com drawable vetorial customizado (`bg_tab_indicator.xml`). O indicador de páginas exibe pontos ovais destacados para a foto ativa e torna-se automaticamente visível apenas quando a sessão possui múltiplas imagens (`sessionEvents.size > 1`), ficando oculto para capturas isoladas.
   * Inclui contador de posição ("Foto X de Y"), metadados dinâmicos e confirmação de exclusão atômica (Room + disco).
5. **Múltiplos Formatos de Agrupamento (*Grid Mode* & *List Mode*):** *(Lote 2)*
   * Lógica analítica no `CatEventViewModel` capaz de agrupar fotos contínuas espaçadas por $< 5\text{min}$ numa única **Chained Session** (Sessão Encadeada de Hidratação).
   * No modo grade: Sessões viram "Álbuns" indicando o tempo de início e fim. Divisores de data isolam os dias.
   * No modo lista: Visualização por "Accordions" (dias colapsáveis), e itens de sessão divididos com uma barra indicativa de continuidade de evento.
6. **Filtro Temporal Dinâmico e Exclusões em Lote:** *(IMPLEMENTADO)*
   * Chips de filtros rápidos (*"Hoje"*, *"Últimas 24h"*, *"Ontem"*, *"Todos"*) e *MaterialDatePicker* para intervalo customizado com ícones vetoriais nativos.
   * Modo de seleção em lote com contêiner unificado (`toolbarContainer`), eliminando sobreposição com a barra de chips e garantindo proteção contra escalas de fontes ampliadas.

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

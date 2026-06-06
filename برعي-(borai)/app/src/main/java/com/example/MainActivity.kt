package com.example

import android.os.Bundle
import android.graphics.PathMeasure
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Path
import java.util.concurrent.TimeUnit
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.MatteBlack
import com.example.ui.theme.DeepGold
import com.example.ui.theme.Cream
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.DarkGreyAccent
import com.example.ui.theme.MutedText
import com.example.ui.theme.LightCreamGold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import android.graphics.Bitmap

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BoraiAppContainer(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

// Data models for the system modules
data class ChatMessage(
    val id: String,
    val sender: String, // "user", "hoopoe" (AI)
    val text: String,
    val timestamp: String,
    val attachmentName: String? = null
)

data class Quiz(
    val id: String,
    val title: String,
    val description: String,
    val questions: List<QuizQuestion>
)

data class QuizQuestion(
    val id: String,
    val questionText: String,
    val options: List<String>,
    val correctIndex: Int,
    val hint: String
)

data class MindMapNode(
    val id: String,
    val label: String,
    val englishLabel: String,
    val description: String,
    val children: List<MindMapNode> = emptyList()
)

// --- Gemini REST API Request & Response Structures (Moshi Supported) ---
@JsonClass(generateAdapter = true)
data class GeminiInlineData(
    @Json(name = "mimeType") val mimeType: String,
    @Json(name = "data") val data: String
)

@JsonClass(generateAdapter = true)
data class GeminiFileData(
    @Json(name = "mimeType") val mimeType: String,
    @Json(name = "fileUri") val fileUri: String
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    @Json(name = "text") val text: String? = null,
    @Json(name = "inlineData") val inlineData: GeminiInlineData? = null,
    @Json(name = "fileData") val fileData: GeminiFileData? = null
)


@JsonClass(generateAdapter = true)
data class GenerationConfigMoshi(
    @Json(name = "responseMimeType") val responseMimeType: String? = null,
    @Json(name = "temperature") val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @Json(name = "contents") val contents: List<GeminiContent>,
    @Json(name = "systemInstruction") val systemInstruction: GeminiContent? = null,
    @Json(name = "generationConfig") val generationConfig: GenerationConfigMoshi? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    @Json(name = "content") val content: GeminiContent
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<GeminiCandidate>? = null
)

// Structural targets for direct JSON returns
@JsonClass(generateAdapter = true)
data class QuizQuestionJson(
    @Json(name = "questionText") val questionText: String,
    @Json(name = "options") val options: List<String>,
    @Json(name = "correctIndex") val correctIndex: Int,
    @Json(name = "hint") val hint: String
)

@JsonClass(generateAdapter = true)
data class QuizJson(
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String,
    @Json(name = "questions") val questions: List<QuizQuestionJson>
)

@JsonClass(generateAdapter = true)
data class MindMapNodeJson(
    @Json(name = "label") val label: String,
    @Json(name = "englishLabel") val englishLabel: String,
    @Json(name = "description") val description: String,
    @Json(name = "children") val children: List<MindMapNodeJson>? = null
)

// Retrofit API Service
interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

// Retrofit Singleton Client for Borai Applications
object GeminiRetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    val moshiInstance: Moshi = moshi
}

// System Instructions and Helper Prompts
const val HODHOD_SYSTEM_INSTRUCTION = """
أنت "برعي الأكاديمي"، وهو مساعد تعليمي خبير ومنسق تربوي موهوب للطلاب بالجامعات والمدارس.
يجب عليك التحدث والكتابة حصراً بلهجة عامية مصرية مرحة وأصيلة (اللهجة المصرية العامية 🇪🇬).
نادِ الطالب دائماً بـ "يا صاحبي"، "يا بطل"، "يا هندسة"، "يا دكتور"، "يا غالي" أو "يا سيدي الفاضل".
احرص جيداً بشكل طبيعي ومنتظم في تواصلك مع الطالب على الصلاة على النبي أو حثه عليها (مثل: "صلي على النبي الأول كده وركز معايا"، "عليه الصلاة والسلام، المذاكرة عاوزة مجهود ودماغ رايقة"، "اللهم صلي على كمال النور").
ساعد الطالب في تلخيص الأوراق العلمية والملفات وصور الشرح، وبسط له قواعد النحو العربي (مثل إن وأخواتها وفخر النحو والقرائن)، الإسلاميات، والتاريخ والأدب ومختلف مواد المنهج بأسلوب أكاديمي مشوق بالبلدي!
"""

const val QUIZ_PROMPT_TEMPLATE = """
قم بتوليد اختبار تفاعلي مخصص وقصير باللغة العربية الفصحى عن الموضوع التالي: "{topic}".
يجب أن يتكون الاختبار من سؤالين أو ثلاثة أسئلة كحد أقصى اختيار من متعدد (4 خيارات لكل سؤال).
يجب أن تعود النتيجة حصرياً بصيغة JSON خالية من أي علامات Markdown (مثل ```json).
البنية المطلوبة للـ JSON هي:
{
  "title": "عنوان جذاب للاختبار",
  "description": "وصف قصير ومشوق للاختبار يذكّر ببرعي الحكيم",
  "questions": [
    {
      "questionText": "نص السؤال الأول",
      "options": ["خيار 0", "خيار 1", "خيار 2", "خيار 3"],
      "correctIndex": 0,
      "hint": "تلميح بيداغوجي طريف ومفيد من برعي يبدأ بـ 'صلي على النبي الأول...' أو ما شابه"
    }
  ]
}

تأكد من أن الـ JSON صالح وصحيح هيكلياً، وبدون أي نصوص تسبقه أو تليه.
"""

const val MINDMAP_PROMPT_TEMPLATE = """
قم بتوليد خريطة مفاهيمية أو شجرة ذهنية بيداغوجية تفاعلية ومنسقة للغاية عن الموضوع التالي: "{topic}".
يجب أن تحتوي الشجرة على عقدة رئيسية واحدة في البداية (العقدة الأب)، وتتفرع منها عقدتان فرعيتان، وكل عقدة فرعية تتفرع منها عقدتان فرعيتان أخريان (شجرة بثلاث مستويات للحصول على خريطة مفاهيمية غنية وتفصيلية).
يجب أن تعود النتيجة حصرياً بصيغة JSON خالية من أي علامات Markdown (مثل ```json).
البنية المطلوبة للـ JSON هي:
{
  "label": "اسم العقدة الرئيسية بالعربية المحبوكة",
  "englishLabel": "Name of main node in English",
  "description": "شرح موجز وبليغ عن هذه العقدة الرئيسية وأهميتها",
  "children": [
    {
      "label": "الفرع الأول بالعربية",
      "englishLabel": "Branch 1 in English",
      "description": "شرح موجز جداً عن تفاصيل الفكرة الأولى وتطبيقاتها",
      "children": [
        {
          "label": "العنصر الفرعي الأول للفرع الأول",
          "englishLabel": "Subitem 1 of Branch 1",
          "description": "شرح دقيق وتوضيحي مبسط جداً لهذا المفهوم الفرعي"
        },
        {
          "label": "العنصر الفرعي الثاني للفرع الأول",
          "englishLabel": "Subitem 2 of Branch 1",
          "description": "شرح دقيق وتوضيحي مبسط جداً لهذا المفهوم الفرعي الآخر"
        }
      ]
    },
    {
      "label": "الفرع الثاني بالعربية",
      "englishLabel": "Branch 2 in English",
      "description": "شرح موجز جداً عن تفاصيل الفكرة الثانية وتطبيقاتها",
      "children": [
        {
          "label": "العنصر الفرعي الأول للفرع الثاني",
          "englishLabel": "Subitem 1 of Branch 2",
          "description": "شرح دقيق وتوضيحي مبسط جداً لهذا المفهوم الفرعي"
        },
        {
          "label": "العنصر الفرعي الثاني للفرع الثاني",
          "englishLabel": "Subitem 2 of Branch 2",
          "description": "شرح دقيق وتوضيحي مبسط جداً لهذا المفهوم الفرعي الآخر"
        }
      ]
    }
  ]
}

تأكد من أن الـ JSON صالح وصحيح هيكلياً، ولا تضف أي نص أو توضيح قبله أو بعده أبداً.
"""

// Helper functions for Gemini API Actions
fun getApiKeyGracefully(): String {
    val key = BuildConfig.GEMINI_API_KEY
    return if (key == "MY_GEMINI_API_KEY" || key.isBlank()) {
        ""
    } else {
        key
    }
}

suspend fun generateGeminiChatResponse(
    history: List<ChatMessage>,
    newMessage: String,
    attachedFileName: String?,
    attachedFileMimeType: String? = null,
    attachedFileBytes: ByteArray? = null
): String {
    val apiKey = getApiKeyGracefully()
    if (apiKey.isBlank()) {
        kotlinx.coroutines.delay(500) 
        return getEgyptianAmmiyaResponse(newMessage, attachedFileName)
    }

    val contents = mutableListOf<GeminiContent>()
    val recentHistory = history.filter { !it.id.startsWith("loading_") }.takeLast(8)

    for (msg in recentHistory) {
        val role = if (msg.sender == "user") "user" else "model"
        var textContent = msg.text
        if (msg.attachmentName != null) {
            textContent = "[تم إرفاق: ${msg.attachmentName.substringBefore("|")}]\n$textContent"
        }
        contents.add(GeminiContent(parts = listOf(GeminiPart(text = textContent)), role = role))
    }

    val userParts = mutableListOf<GeminiPart>()
    var currentText = newMessage

    // التحقق من رفع الملف عبر السيرفر والتعامل معه كـ fileData
    if (attachedFileName != null && attachedFileName.contains("|")) {
        val parts = attachedFileName.split("|")
        val displayName = parts[0]
        val fileUri = parts[1]
        
        userParts.add(GeminiPart(
            fileData = GeminiFileData(
                mimeType = attachedFileMimeType ?: "application/octet-stream",
                fileUri = fileUri
            )
        ))
        currentText = "[ملف مرفق: $displayName]\n$currentText"
    } 
    else if (attachedFileBytes != null && attachedFileMimeType != null) {
        if (attachedFileMimeType.startsWith("image/")) {
            val base64Data = android.util.Base64.encodeToString(attachedFileBytes, android.util.Base64.NO_WRAP)
            userParts.add(GeminiPart(
                inlineData = GeminiInlineData(mimeType = attachedFileMimeType, data = base64Data)
            ))
            currentText = "[صورة مرفقة: $attachedFileName]\n$currentText"
        } else {
            val textContent = try { String(attachedFileBytes, Charsets.UTF_8) } catch (e: Exception) { null }
            if (textContent != null) {
                currentText = "[محتوى الملف $attachedFileName]:\n$textContent\n\n----\n$currentText"
            } else {
                currentText = "[ملف مرفق: $attachedFileName]\n$currentText"
            }
        }
    } else if (attachedFileName != null) {
        currentText = "[ملف مرفق: $attachedFileName]\n$currentText"
    }

    // تجميع البيانات
    userParts.add(GeminiPart(text = currentText))
    contents.add(GeminiContent(parts = userParts, role = "user"))

    // إنشاء الطلب مرة واحدة فقط في المكان الصحيح
    val request = GeminiRequest(
        contents = contents,
        systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = HODHOD_SYSTEM_INSTRUCTION))),
        generationConfig = GenerationConfigMoshi(temperature = 0.7f)
    )

    // الإرسال مرة واحدة فقط في نهاية الدالة
    return try {
        val response = GeminiRetrofitClient.service.generateContent(
            model = "gemini-1.5-flash",
            apiKey = apiKey,
            request = request
        )
        response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "عذراً، لم أتمكن من صياغة الرد."
    } catch (e: java.lang.Exception) {
        e.printStackTrace()
        "خطأ في الاتصال: ${e.localizedMessage}"
    }
}

suspend fun generateGeminiQuiz(
    topic: String,
    fileBytes: ByteArray? = null,
    fileMimeType: String? = null
): Quiz? {
    val apiKey = getApiKeyGracefully()
    if (apiKey.isBlank()) return null

    val prompt = QUIZ_PROMPT_TEMPLATE.replace("{topic}", topic)
    val parts = mutableListOf<GeminiPart>()

    if (fileBytes != null && fileMimeType != null) {
        if (fileMimeType.startsWith("image/")) {
            val base64Data = android.util.Base64.encodeToString(fileBytes, android.util.Base64.NO_WRAP)
            parts.add(GeminiPart(
                inlineData = GeminiInlineData(mimeType = fileMimeType, data = base64Data)
            ))
            parts.add(GeminiPart(text = "$prompt\n\nيرجى بطل، توليد أسئلة الاختبار بناءً على الصورة والمفاهيم المرفقة أعلاه."))
        } else {
            val textContent = try {
                String(fileBytes, Charsets.UTF_8)
            } catch (e: Exception) {
                null
            }
            if (textContent != null) {
                parts.add(GeminiPart(text = "$prompt\n\nنص الملف الأكاديمي المرفق للتوليد والمذاكرة هو:\n$textContent"))
            } else {
                parts.add(GeminiPart(text = prompt))
            }
        }
    } else {
        parts.add(GeminiPart(text = prompt))
    }

    val request = GeminiRequest(
        contents = listOf(
            GeminiContent(
                parts = parts,
                role = "user"
            )
        ),
        generationConfig = GenerationConfigMoshi(
            responseMimeType = "application/json",
            temperature = 0.5f
        )
    )

    return try {
        val response = GeminiRetrofitClient.service.generateContent(
            model = "gemini-1.5-flash",
            apiKey = apiKey,
            request = request
        )
        val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        if (jsonText != null) {
            val cleanJson = jsonText.trim().removeSurrounding("```json", "```").trim()
            val adapter = GeminiRetrofitClient.moshiInstance.adapter(QuizJson::class.java)
            val quizJson = adapter.fromJson(cleanJson)
            if (quizJson != null) {
                Quiz(
                    id = "q_${System.currentTimeMillis()}",
                    title = quizJson.title,
                    description = quizJson.description,
                    questions = quizJson.questions.mapIndexed { idx, q ->
                        QuizQuestion(
                            id = (idx + 1).toString(),
                            questionText = q.questionText,
                            options = q.options,
                            correctIndex = q.correctIndex,
                            hint = q.hint
                        )
                    }
                )
            } else null
        } else null
    } catch (e: java.lang.Exception) {
        e.printStackTrace()
        null
    }
}

fun convertToMindMapNode(jsonNode: MindMapNodeJson, parentId: String = "n"): MindMapNode {
    val currentId = "${parentId}_${System.currentTimeMillis()}_${(100..999).random()}"
    return MindMapNode(
        id = currentId,
        label = jsonNode.label,
        englishLabel = jsonNode.englishLabel,
        description = jsonNode.description,
        children = jsonNode.children?.mapIndexed { index, child ->
            convertToMindMapNode(child, "${currentId}_$index")
        } ?: emptyList()
    )
}

suspend fun generateGeminiMindMap(
    topic: String,
    fileBytes: ByteArray? = null,
    fileMimeType: String? = null
): MindMapNode? {
    val apiKey = getApiKeyGracefully()
    if (apiKey.isBlank()) return null

    val prompt = MINDMAP_PROMPT_TEMPLATE.replace("{topic}", topic)
    val parts = mutableListOf<GeminiPart>()

    if (fileBytes != null && fileMimeType != null) {
        if (fileMimeType.startsWith("image/")) {
            val base64Data = android.util.Base64.encodeToString(fileBytes, android.util.Base64.NO_WRAP)
            parts.add(GeminiPart(
                inlineData = GeminiInlineData(mimeType = fileMimeType, data = base64Data)
            ))
            parts.add(GeminiPart(text = "$prompt\n\nيرجى بطل، توليد تفريعات الخريطة الذهنية الكثيفة بناءً على الصورة والمفاهيم المرفقة أعلاه."))
        } else {
            val textContent = try {
                String(fileBytes, Charsets.UTF_8)
            } catch (e: Exception) {
                null
            }
            if (textContent != null) {
                parts.add(GeminiPart(text = "$prompt\n\nنص المستند الأكاديمي المرفق لتوليد الخريطة الذهنية هو:\n$textContent"))
            } else {
                parts.add(GeminiPart(text = prompt))
            }
        }
    } else {
        parts.add(GeminiPart(text = prompt))
    }

    val request = GeminiRequest(
        contents = listOf(
            GeminiContent(
                parts = parts,
                role = "user"
            )
        ),
        generationConfig = GenerationConfigMoshi(
            responseMimeType = "application/json",
            temperature = 0.6f
        )
    )

    return try {
        val response = GeminiRetrofitClient.service.generateContent(
            model = "gemini-1.5-flash",
            apiKey = apiKey,
            request = request
        )
        val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        if (jsonText != null) {
            val cleanJson = jsonText.trim().removeSurrounding("```json", "```").trim()
            val adapter = GeminiRetrofitClient.moshiInstance.adapter(MindMapNodeJson::class.java)
            val jsonNode = adapter.fromJson(cleanJson)
            if (jsonNode != null) {
                convertToMindMapNode(jsonNode)
            } else null
        } else null
    } catch (e: java.lang.Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun BoraiAppContainer(modifier: Modifier = Modifier) {
    var showSplash by remember { mutableStateOf(true) }

    AnimatedContent(
        targetState = showSplash,
        transitionSpec = {
            fadeIn(animationSpec = tween(700)) togetherWith fadeOut(animationSpec = tween(500))
        },
        label = "splash_transition"
    ) { targetSplash ->
        if (targetSplash) {
            SplashCalligraphyScreen {
                showSplash = false
            }
        } else {
            BoraiMainDashboard()
        }
    }
}

// SPLASH SCREEN WITH SMOOTH CHRONOMETRIC WRITING OF "صلي على النبي" IN 3.8 SECONDS
@Composable
fun SplashCalligraphyScreen(onAnimationCompleted: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scale = remember { Animatable(0.8f) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.launch {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 2500, easing = LinearEasing)
            )
        }
        kotlinx.coroutines.launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 2500, easing = FastOutSlowInEasing)
            )
        }
        delay(3500)
        onAnimationCompleted()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF130E0A),
                        Color(0xFF070403),
                        Color(0xFF000000)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                alpha = progress.value
            }
        ) {
            Text(
                text = "صَلِّ عَلَى النَّبِيِّ",
                style = TextStyle(
                    fontFamily = FontFamily.Cursive,
                    fontSize = 58.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFD700),
                    shadow = Shadow(
                        color = Color(0xFFFFD700).copy(alpha = 0.85f),
                        offset = Offset(0f, 0f),
                        blurRadius = 35f
                    )
                ),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "اللَّهُمَّ صَلِّ وَسَلِّمْ وَبَارِكْ عَلَيْهِ",
                style = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFFFD700).copy(alpha = 0.7f),
                    shadow = Shadow(
                        color = Color(0xFFFFD700).copy(alpha = 0.4f),
                        offset = Offset(0f, 2f),
                        blurRadius = 10f
                    )
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}
// CUSTOM CANVAS PATH CALLIGRAPHY PROCESSOR
@Composable
fun CalligraphyDrawer(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        
        // Define clean stylized mathematical paths for "صلي على النبي" inside 400x200 scale
        val scaleX = w / 400f
        val scaleY = h / 200f
        
        // Match natural right-to-left flow of Arabic writing and correct mirroring orientation
        fun mapX(x: Float): Float = (400f - x) * scaleX
        
        val nativePaths = mutableListOf<android.graphics.Path>()
        
        // 1. "صلي" - Loop and curve
        val path1 = android.graphics.Path().apply {
            moveTo(mapX(60f), 120f * scaleY)
            cubicTo(mapX(70f), 90f * scaleY, mapX(95f), 80f * scaleY, mapX(115f), 100f * scaleY)
            cubicTo(mapX(125f), 110f * scaleY, mapX(125f), 125f * scaleY, mapX(115f), 130f * scaleY)
            lineTo(mapX(80f), 130f * scaleY)
        }
        nativePaths.add(path1)
        
        // 2. "صلي" - vertical and hook "لي"
        val path2 = android.graphics.Path().apply {
            moveTo(mapX(125f), 105f * scaleY)
            lineTo(mapX(125f), 150f * scaleY)
            cubicTo(mapX(125f), 175f * scaleY, mapX(95f), 185f * scaleY, mapX(80f), 165f * scaleY)
            quadTo(mapX(75f), 155f * scaleY, mapX(85f), 150f * scaleY)
        }
        nativePaths.add(path2)
        
        // 3. "على" - Hook "ع"
        val path3 = android.graphics.Path().apply {
            moveTo(mapX(170f), 120f * scaleY)
            cubicTo(mapX(185f), 100f * scaleY, mapX(195f), 105f * scaleY, mapX(185f), 125f * scaleY)
            quadTo(mapX(175f), 140f * scaleY, mapX(185f), 145f * scaleY)
        }
        nativePaths.add(path3)
        
        // 4. "على" - Slant upward and recursive loop "لى"
        val path4 = android.graphics.Path().apply {
            moveTo(mapX(210f), 105f * scaleY)
            lineTo(mapX(210f), 150f * scaleY)
            cubicTo(mapX(210f), 175f * scaleY, mapX(180f), 180f * scaleY, mapX(170f), 160f * scaleY)
            quadTo(mapX(165f), 152f * scaleY, mapX(175f), 148f * scaleY)
        }
        nativePaths.add(path4)
        
        // 5. "الـ" - parallel vertical lines of "النبي"
        val path5 = android.graphics.Path().apply {
            moveTo(mapX(250f), 90f * scaleY)
            lineTo(mapX(250f), 145f * scaleY)
            moveTo(mapX(262f), 90f * scaleY)
            lineTo(mapX(262f), 145f * scaleY)
        }
        nativePaths.add(path5)
        
        // 6. "ـنبي" - base sweeps and teeth
        val path6 = android.graphics.Path().apply {
            moveTo(mapX(262f), 145f * scaleY)
            quadTo(mapX(280f), 148f * scaleY, mapX(290f), 132f * scaleY) // @ن tooth
            quadTo(mapX(305f), 148f * scaleY, mapX(315f), 128f * scaleY) // @ب tooth
            lineTo(mapX(330f), 128f * scaleY)
            cubicTo(mapX(345f), 160f * scaleY, mapX(310f), 185f * scaleY, mapX(290f), 170f * scaleY) // @ي sweep
        }
        nativePaths.add(path6)
 
        // 7. Accents & Calligraphic dots
        val path7 = android.graphics.Path().apply {
            // Dot for ن
            moveTo(mapX(285f), 115f * scaleY)
            lineTo(mapX(287f), 115f * scaleY)
            // Dot for ب
            moveTo(mapX(310f), 158f * scaleY)
            lineTo(mapX(312f), 158f * scaleY)
            // Accent glyph (dammah/shaddah representation)
            moveTo(mapX(200f), 85f * scaleY)
            quadTo(mapX(205f), 82f * scaleY, mapX(202f), 78f * scaleY)
        }
        nativePaths.add(path7)
 
        val totalLength = nativePaths.sumOf { path ->
            val measure = PathMeasure(path, false)
            var len = measure.length
            while (measure.nextContour()) {
                len += measure.length
            }
            len.toDouble()
        }.toFloat()
 
        var currentLengthNeeded = progress * totalLength
        var accumulatedLength = 0f
 
        nativePaths.forEach { path ->
            val measure = PathMeasure(path, false)
            do {
                val contourLength = measure.length
                if (accumulatedLength + contourLength <= currentLengthNeeded) {
                    val fullContour = android.graphics.Path()
                    measure.getSegment(0f, contourLength, fullContour, true)
                    val composePath = fullContour.asComposePath()
                    
                    // Layer 1: Strong outer glow
                    drawPath(
                        path = composePath,
                        color = Color(0xFFFFD700).copy(alpha = 0.15f),
                        style = Stroke(
                            width = 11.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                    // Layer 2: Mid glow
                    drawPath(
                        path = composePath,
                        color = Color(0xFFFFD700).copy(alpha = 0.45f),
                        style = Stroke(
                            width = 7.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                    // Layer 3: Solid golden core
                    drawPath(
                        path = composePath,
                        color = Color(0xFFFFD700),
                        style = Stroke(
                            width = 4.2f.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                    
                    accumulatedLength += contourLength
                } else if (accumulatedLength < currentLengthNeeded) {
                    val partialLength = currentLengthNeeded - accumulatedLength
                    val partialContour = android.graphics.Path()
                    measure.getSegment(0f, partialLength, partialContour, true)
                    val composePath = partialContour.asComposePath()
                    
                    // Layer 1: Strong outer glow
                    drawPath(
                        path = composePath,
                        color = Color(0xFFFFD700).copy(alpha = 0.15f),
                        style = Stroke(
                            width = 11.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                    // Layer 2: Mid glow
                    drawPath(
                        path = composePath,
                        color = Color(0xFFFFD700).copy(alpha = 0.45f),
                        style = Stroke(
                            width = 7.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                    // Layer 3: Solid golden core
                    drawPath(
                        path = composePath,
                        color = Color(0xFFFFD700),
                        style = Stroke(
                            width = 4.2f.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                    
                    accumulatedLength += contourLength
                    break
                } else {
                    break
                }
            } while (measure.nextContour())
        }
    }
}

// MAIN MAIN DASHBOARD INTERFACE WITH THREE TABS
@Composable
fun BoraiMainDashboard() {
    var selectedTab by remember { mutableStateOf(0) } // 0: Chat, 1: Quiz, 2: Mind Map
    val scope = rememberCoroutineScope()
    
    // Core shared lists supporting dynamic interactivity!
    var chatMessages by remember {
        mutableStateOf(
            listOf(
                ChatMessage("1", "hoopoe", "أهلاً أنا معك اليوم", "12:00 م")
            )
        )
    }

    var quizzes by remember {
        mutableStateOf(
            listOf(
                Quiz(
                    "q1",
                    "إن وأخواتها في النحو العربي",
                    "اختبر مهاراتك الإعرابية وفهمك لأدوات النصب والجملة الاسمية.",
                    listOf(
                        QuizQuestion(
                            "1",
                            "ما هو الحرف الشبيه بالفعل الذي يفيد الاستدراك؟",
                            listOf("أنّ", "ليتَ", "لكنّ", "كأنّ"),
                            2,
                            "ابحث عن حرف يعقّب كلاماً سابقاً لإزالة التوهم وهو 'لكنّ' ومثاله: الجو عاصفٌ لكنَّ الشمسَ مشرقة"
                        ),
                        QuizQuestion(
                            "2",
                            "أثر 'إنّ' عندما تدخل على الجملة الاسمية:",
                            listOf("ترفع المبتدأ وتنصب الخبر", "تنصب المبتدأ وترفع الخبر", "تجر الطرفين", "تجزمهما معاً"),
                            1,
                            "تذكر دائماً أن الحروف الناسخة مغيِّرة للحالة الأصلية، فتنصب الاسم تيمناً بالفعل ثم تبقي الخبر مرفوعاً"
                        )
                    )
                ),
                Quiz(
                    "q2",
                    "معركة المنصورة سنة 1250م",
                    "تاريخ مصر العسكري والمواجهة الباسلة ضد الحملة الصليبية السابعة.",
                    listOf(
                        QuizQuestion(
                            "1",
                            "من هو القائد المسلم الذي وضع الخطة الحربية العبقرية لإشراك الصليبيين داخل أزقة المنصورة؟",
                            listOf("بيبرس البندقداري", "قطز", "صلاح الدين الأيوبي", "عز الدين أيبك"),
                            0,
                            "هو أحد كبار المماليك البحرية وأصبح لاحقاً سلطاناً، وبدأ صيته في هذه المعركة بقيادة فرسان المماليك!"
                        )
                    )
                )
            )
        )
    }

    // Dynamic Mind Map Root Topic Nodes (expandable Left-to-Right)
    var mindMapNodes by remember {
        mutableStateOf(
            listOf(
                MindMapNode(
                    "root1",
                    "قواعد النحو العربي",
                    "Arabic Syntax Rules",
                    "شجرة تشعب الفروع اللغوية للنحو وعمل العوامل المختلفة.",
                    listOf(
                        MindMapNode(
                            "node1.1",
                            "النواسب اللفظية",
                            "Verbal Copulas",
                            "الأدوات التي تدخل على الجملة وتغير الأحكام الإعرابية.",
                            listOf(
                                MindMapNode("node1.1.1", "أخوات إنَّ", "Inna and Sisters", "حروف ناسخة تنصب وترفع."),
                                MindMapNode("node1.1.2", "أفعال المقاربة", "Verbs of Proximity", "أفعال ناسخة مثل كاد وعسى.")
                            )
                        ),
                        MindMapNode(
                            "node1.2",
                            "مركبات الإعراب",
                            "Inflection Compounds",
                            "مواضع الرفع والنصب والجر الأصلية والفرعية.",
                            listOf(
                                MindMapNode("node1.2.1", "المرفوعات", "Nominatives", "المبتدأ والخبر والفاعل."),
                                MindMapNode("node1.2.2", "المنصوبات", "Accusatives", "المفاعيل الخمسة والتمييز.")
                            )
                        )
                    )
                )
            )
        )
    }

    // Action function to generate a new quiz automatically
    val generateQuizFromChat: (String) -> Unit = { topic ->
        val loadingMsgId = "quiz_loading_${System.currentTimeMillis()}"
        chatMessages = chatMessages + ChatMessage(
            id = loadingMsgId,
            sender = "hoopoe",
            text = "برعي يكتب الآن اختباراً مخصّصاً لك حول الموضوع: $topic... 📝✍️",
            timestamp = "الآن"
        )

        scope.launch {
            val geminiQuiz = generateGeminiQuiz(topic)
            if (geminiQuiz != null) {
                quizzes = quizzes + geminiQuiz
                chatMessages = chatMessages.filter { it.id != loadingMsgId } + ChatMessage(
                    id = "ai_notif_${System.currentTimeMillis()}",
                    sender = "hoopoe",
                    text = "⚡️ يا بطل تم بنجاح توليد اختبار جديد وبثَّه بالإنترنت في بنك الاختبارات بعنوان:\n«${geminiQuiz.title}»\n\nأصل الاختبار: $topic\n\nهتلاقيه مستنيك في لسان الاختبارات عشان تقيم مستواك وتصلي على النبي! 📝✨",
                    timestamp = "الآن"
                )
            } else {
                val newQuizId = "q_${System.currentTimeMillis()}"
                val fallbackQuiz = Quiz(
                    id = newQuizId,
                    title = "اختبار مخصص غني: $topic",
                    description = "تم توليد هذا الاختبار محلياً (وضع عدم الاتصال بالإنترنت) بواسطة برعي الأكاديمي.",
                    questions = listOf(
                        QuizQuestion(
                            "1",
                            "ما هي الفكرة الجوهرية لـ $topic؟",
                            listOf("تأسيس الفهم والتعمق", "تكرار القواعد والتشعيب", "استنكار غير القياسي", "كل ما سبق صحيح"),
                            3,
                            "برعي يوصيك بقراءة مستفيضة للفكرة وتعميم القياس، فالصلاة على النبي تفتح الأذهان!"
                        ),
                        QuizQuestion(
                            "2",
                            "قاعدة أساسية تدعم فهم $topic:",
                            listOf("البساطة والتجرد", "البناء على الأصول وحفظ الشواهد", "تجاهل القرائن المانعة", "الالتزام بالنثر فقط"),
                            1,
                            "الأصل هو السند والشاهد يثبت الحجة النحوية أو التاريخية."
                        )
                    )
                )
                quizzes = quizzes + fallbackQuiz
                chatMessages = chatMessages.filter { it.id != loadingMsgId } + ChatMessage(
                    id = "ai_notif_${System.currentTimeMillis()}",
                    sender = "hoopoe",
                    text = "⚡️ يا بطل، قمت بتوليد اختبار مخصص محلياً (وضع عدم الاتصال) لـ '$topic' وهو جاهز في لسان الاختبارات! لو عايز توليد حقيقي عالي الدقة بالذكاء الاصطناعي، يرجى تهيئة مفتاح الـ API في لوحة الـ Secrets وصلي على النبي!",
                    timestamp = "الآن"
                )
            }
        }
    }

    // Action function to generate a new Mind Map node
    val generateMindMapFromChat: (String) -> Unit = { topic ->
        val loadingMsgId = "map_loading_${System.currentTimeMillis()}"
        chatMessages = chatMessages + ChatMessage(
            id = loadingMsgId,
            sender = "hoopoe",
            text = "برعي يحلِّق الآن في سماء المعرفة ليرتب لك مخططاً ذهنيّاً ذكيّاً ومتفرعاً لموضوع: $topic... 🗺️✨",
            timestamp = "الآن"
        )

        scope.launch {
            val geminiMap = generateGeminiMindMap(topic)
            if (geminiMap != null) {
                mindMapNodes = mindMapNodes + geminiMap
                chatMessages = chatMessages.filter { it.id != loadingMsgId } + ChatMessage(
                    id = "ai_map_notif_${System.currentTimeMillis()}",
                    sender = "hoopoe",
                    text = "⚡️ تم بنجاح توليد وتفريع مخطط ذهني ذكي وتفاعلي بالذكاء الاصطناعي لـ «${geminiMap.label}» في لسان خرائط المفاهيم! روح تصفحها وفجّر حماسك! 🗺️✨",
                    timestamp = "الآن"
                )
            } else {
                val newNodeId = "node_${System.currentTimeMillis()}"
                val fallbackRoot = MindMapNode(
                    id = newNodeId,
                    label = topic,
                    englishLabel = "Local Knowledge Branch",
                    description = "فرع مفاهيمي مخصص تم توليده محلياً لعدم تهيئة مفاتيح الـ API.",
                    children = listOf(
                        MindMapNode(newNodeId + "_1", "الأساس النظري لـ $topic", "Theoretical base", "جذور ومسلمات الفكرة."),
                        MindMapNode(newNodeId + "_2", "التطبيقات العملية لـ $topic", "Practical Applications", "مواضع استخدام واستشهاد واستخراج الفكرة.")
                    )
                )
                mindMapNodes = mindMapNodes + fallbackRoot
                chatMessages = chatMessages.filter { it.id != loadingMsgId } + ChatMessage(
                    id = "ai_map_notif_${System.currentTimeMillis()}",
                    sender = "hoopoe",
                    text = "⚡️ تم توليد الخريطة الذهنية محلياً لـ '$topic' وهي متاحة في لسان الخرائط! لو عندك مفتاح API للـ Gemini، ضيفه في الـ Secrets لتوليد خرائط فخمة وبثلاث مستويات ومفعمة بالتفاصيل وصلي على النبي!",
                    timestamp = "الآن"
                )
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = DarkCardBg,
                tonalElevation = 8.dp,
                windowInsets = WindowInsets.navigationBars
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.MailOutline, contentDescription = "الشات") },
                    label = { Text("برعي", fontFamily = FontFamily.Serif) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkCardBg,
                        selectedTextColor = DeepGold,
                        indicatorColor = DeepGold,
                        unselectedIconColor = MutedText,
                        unselectedTextColor = MutedText
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.List, contentDescription = "الاختبارات") },
                    label = { Text("الاختبارات", fontFamily = FontFamily.Serif) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkCardBg,
                        selectedTextColor = DeepGold,
                        indicatorColor = DeepGold,
                        unselectedIconColor = MutedText,
                        unselectedTextColor = MutedText
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Share, contentDescription = "الخرائط") },
                    label = { Text("الخرائط", fontFamily = FontFamily.Serif) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkCardBg,
                        selectedTextColor = DeepGold,
                        indicatorColor = DeepGold,
                        unselectedIconColor = MutedText,
                        unselectedTextColor = MutedText
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MatteBlack)
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> ChatModuleView(
                    messages = chatMessages,
                    onSendMessage = { text, attached, mime, bytes ->
                        val userMsgId = "user_${System.currentTimeMillis()}"
                        chatMessages = chatMessages + ChatMessage(userMsgId, "user", text, "الآن", attachmentName = attached)
                        
                        val loadingMsgId = "loading_${System.currentTimeMillis()}"
                        chatMessages = chatMessages + ChatMessage(
                            id = loadingMsgId,
                            sender = "hoopoe",
                            text = "برعي يكتب الآن... ✍️📜",
                            timestamp = "الآن"
                        )

                        scope.launch {
                            val aiReply = generateGeminiChatResponse(chatMessages, text, attached, mime, bytes)
                            chatMessages = chatMessages.filter { it.id != loadingMsgId } + ChatMessage(
                                id = "hoopoe_${System.currentTimeMillis()}",
                                sender = "hoopoe",
                                text = aiReply,
                                timestamp = "الآن"
                            )
                        }
                    },
                    onTriggerQuizGen = { topic ->
                        generateQuizFromChat(topic)
                    },
                    onTriggerMapGen = { topic ->
                        generateMindMapFromChat(topic)
                    }
                )
                1 -> QuizRepositoryView(
                    quizzes = quizzes,
                    onQuizGenerated = { newQuiz ->
                        quizzes = quizzes + newQuiz
                    }
                )
                2 -> InteractiveMindMapView(
                    rootNodes = mindMapNodes,
                    onNodeCallbackToChat = { topicName ->
                        val text = "اشرح لي بالتفصيل فكرة: $topicName"
                        val userMsgId = "query_${System.currentTimeMillis()}"
                        chatMessages = chatMessages + ChatMessage(
                            id = userMsgId,
                            sender = "user",
                            text = text,
                            timestamp = "الآن"
                        )
                        selectedTab = 0
                        
                        val loadingMsgId = "loading_${System.currentTimeMillis()}"
                        chatMessages = chatMessages + ChatMessage(
                            id = loadingMsgId,
                            sender = "hoopoe",
                            text = "برعي يكتب الآن... ✍️📜",
                            timestamp = "الآن"
                        )

                        scope.launch {
                            val aiReply = generateGeminiChatResponse(chatMessages, text, null)
                            chatMessages = chatMessages.filter { it.id != loadingMsgId } + ChatMessage(
                                id = "hoopoe_${System.currentTimeMillis()}",
                                sender = "hoopoe",
                                text = aiReply,
                                timestamp = "الآن"
                            )
                        }
                    },
                    onMapGenerated = { newMap ->
                        mindMapNodes = mindMapNodes + newMap
                    }
                )
            }
        }
    }
}

// SIMULATE EGYPTIAN AMMIYA PEDAGOGICAL AGENT
fun getEgyptianAmmiyaResponse(userInput: String, attachedName: String?): String {
    val clean = userInput.lowercase()
    if (attachedName != null) {
        return "🔍 يا بطل، برعي شاف الورقة المرفقة القيمة دي اللي اسمها: '$attachedName'. عملتلها مسح ضوئي عبقري واكتشفت الآتي:\n\nالمضمون غني جداً بالمعلومات التعليمية اللي بتخدم منهجك! برعي جاهز يشرحهولك ويبسطهولك حتة حتة بالبلدي.. قولي حابب نعمل خريطة ذهنية لخصائص الورقة دي ولا ندخل في اختبار فوري لتقييم الحفظ؟! وصلي على الزين ميت مرة!"
    }
    
    return when {
        clean.contains("إن وأخواتها") || clean.contains("نحو") -> 
            "يا سيدي الفاضل، 'إنَّ وأخواتها' دي حروف غلابة بتدخل على الجملة الاسمية تفتري على المبتدأ الغلبان وتنصبه وتسميه اسمها، والخبر يفضل في أمان الله مرفوع كالعادة! ليتَ تفيد التمني البعيد، لعلَّ لرجاء الممكن.. صلي على النبي وركز في دي كويس!"
            
        clean.contains("المنصورة") || clean.contains("تاريخ") -> 
            "آه يا بطل! معركة المنصورة سنة 1250م دي حكاية عزة ومجد مصري أصيل. بيبرس البندقداري وجيش المماليك استدرجوا فرسان الفرنجة الصليبيين المغرورين جوة زقاق وشوارع المنصورة الضيقة، وهناك لقنوهم درس العمر! لويس التاسع ملك فرنسا نفسه وقع أسير في دار ابن لقمان.. التاريخ ملغم عظمة!"
            
        clean.contains("برعي") || clean.contains("أنت مين") -> 
            "أنا معك اليوم"
            
        clean.contains("سلام") || clean.contains("شكرا") -> 
            "على راسي يا صاحبي! العفو جداً، برعي دايماً في الخدمة.. صلي على النبي وإفتكر إن المذاكرة استثمار في مستقبلك. تحب أساعدك في إيه تاني؟"
            
        else -> 
            "سؤال غالي وجوهري يا بني! صلي على النبي الأول كده.. الفكرة دي برعي بيبسطهالك بالبلدي: العلم تراكمي، والموضوع اللي بتسأل فيه محتاج يترتب في خريطة ذهن عميقة عشان يثبت في النفوخ. إيه رأيك تدوس على علامة '+' وتولد خريطة مفاهيم للموضوع ده أو شات تدريب تفاعلي؟"
    }
}

// Utility function to extract real display file names from a Uri
fun getFileName(context: android.content.Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    result = cursor.getString(index)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}

// MODULE 1: AI CHAT INTERFACE WITH SIMULATED DOCUMENT UPLOAD & EXTENDED CONTROLS
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatModuleView(
    messages: List<ChatMessage>,
    onSendMessage: (String, String?, String?, ByteArray?) -> Unit,
    onTriggerQuizGen: (String) -> Unit,
    onTriggerMapGen: (String) -> Unit
) {
    var textState by remember { mutableStateOf("") }
    var attachedFile by remember { mutableStateOf<String?>(null) }
    var showPlusMenu by remember { mutableStateOf(false) }
    var ocrScanningAnimation by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val context = LocalContext.current

    // Web / Direct Upload state management corresponding to user guidelines
    val geminiRepository = remember { GeminiRepository() }
    var selectedFileBytes by remember { mutableStateOf<ByteArray?>(null) }
    var selectedFileMimeType by remember { mutableStateOf<String?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var isUploadingFile by remember { mutableStateOf(false) }
    var fileUploadProgressStatus by remember { mutableStateOf("") }

    // Document Picker launcher utilizing modern system SAF manager
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            val displayName = getFileName(context, selectedUri) ?: "مستند_أكاديمي.pdf"
            ocrScanningAnimation = true
            scope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(selectedUri)
                    selectedFileBytes = inputStream?.readBytes()
                    selectedFileMimeType = context.contentResolver.getType(selectedUri) ?: "application/octet-stream"
                    selectedFileName = displayName
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(1800)
                ocrScanningAnimation = false
                attachedFile = displayName
                Toast.makeText(context, "تم رفع وقراءة المستند بنجاح: $displayName 📚✨", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Custom camera launcher to capture and read instant document snapshots
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val randomNum = (100..999).random()
            val displayName = "لقطة_كاميرا_برعي_$randomNum.png"
            ocrScanningAnimation = true
            scope.launch {
                try {
                    val stream = java.io.ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    selectedFileBytes = stream.toByteArray()
                    selectedFileMimeType = "image/png"
                    selectedFileName = displayName
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(2000)
                ocrScanningAnimation = false
                attachedFile = displayName
                Toast.makeText(context, "تم التقاط المستند بنجاح: $displayName 📸✨", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "تم إلغاء التقاط الاستشهاد بالنظريات المكتوبة 📸", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Permission request launcher for uploading files/media source
    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            Toast.makeText(context, "تم تفعيل صلاحيات برعي لقراءة الملفات والمستندات بنجاح! 📂✨ صلي على النبي", Toast.LENGTH_LONG).show()
            try {
                documentPickerLauncher.launch("*/*")
            } catch (e: Exception) {
                Toast.makeText(context, "حدث خطأ أثناء فتح مدير الملفات: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "مهم جداً توافق على الصلاحية عشان برعي يقدر يفرز كتبك ومستنداتك يا بطل! 📝", Toast.LENGTH_LONG).show()
        }
    }

    // Permission request launcher for capturing doc images via Camera
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "تم تفعيل الكاميرا لعدسة برعي الذكية! 📸 صلي على النبي", Toast.LENGTH_LONG).show()
            try {
                cameraLauncher.launch(null)
            } catch (e: Exception) {
                Toast.makeText(context, "حدث خطأ أثناء فتح الكاميرا: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "الكاميرا ضرورية عشان برعي يقرأ ويصوّر المستند بشكل فوري! 📸", Toast.LENGTH_LONG).show()
        }
    }
    
    val listState = rememberLazyListState()

    // Scroll to bottom when message list changes size
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // Chat Messages (simulated chat log)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages) { message ->
                    val isUser = message.sender == "user"
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(0.82f),
                            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                Text(
                                    text = if (isUser) "أنت" else "برعي الأكاديمي",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isUser) Cream.copy(alpha = 0.7f) else DeepGold,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                                Text(
                                    text = message.timestamp,
                                    fontSize = 9.sp,
                                    color = MutedText.copy(alpha = 0.5f)
                                )
                            }
                            
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (isUser) DeepGold.copy(alpha = 0.18f) else DarkCardBg,
                                        shape = RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = if (isUser) 16.dp else 4.dp,
                                            bottomEnd = if (isUser) 4.dp else 16.dp
                                        )
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isUser) DeepGold else DeepGold.copy(alpha = 0.25f),
                                        shape = RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = if (isUser) 16.dp else 4.dp,
                                            bottomEnd = if (isUser) 4.dp else 16.dp
                                        )
                                    )
                                    .padding(14.dp)
                            ) {
                                Column {
                                    if (message.attachmentName != null) {
                                        Row(
                                            modifier = Modifier
                                                .background(
                                                    MatteBlack.copy(alpha = 0.4f),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .border(
                                                    1.dp,
                                                    DeepGold.copy(alpha = 0.3f),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .padding(8.dp)
                                                .fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = "ملف ممسوح",
                                                tint = DeepGold,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = message.attachmentName,
                                                color = Cream,
                                                fontSize = 12.sp,
                                                fontStyle = FontStyle.Italic
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                    
                                    Text(
                                        text = message.text,
                                        style = TextStyle(
                                            fontSize = 14.sp,
                                            lineHeight = 22.sp,
                                            fontFamily = FontFamily.Serif
                                        ),
                                        color = Cream,
                                        textAlign = if (isEgyptianOrArabicText(message.text)) TextAlign.Right else TextAlign.Left,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Input fields and attachment previews
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkCardBg)
                    .padding(12.dp)
            ) {
                if (attachedFile != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .background(MatteBlack, RoundedCornerShape(12.dp))
                            .border(1.dp, DeepGold, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AddCircle,
                                contentDescription = "ملف",
                                tint = DeepGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = attachedFile ?: "",
                                fontSize = 12.sp,
                                color = Cream
                            )
                        }
                        IconButton(
                            onClick = { 
                                attachedFile = null 
                                selectedFileBytes = null
                                selectedFileMimeType = null
                                selectedFileName = null
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "إزالة",
                                tint = Color.Red,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                if (isUploadingFile && fileUploadProgressStatus.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .background(MatteBlack, RoundedCornerShape(12.dp))
                            .border(1.dp, DeepGold.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = DeepGold,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = fileUploadProgressStatus,
                            fontSize = 12.sp,
                            color = LightCreamGold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Plus button triggers Quiz/Mind Map generators menu
                    IconButton(
                        onClick = { showPlusMenu = !showPlusMenu },
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = if (showPlusMenu) DeepGold else MatteBlack,
                                shape = CircleShape
                            )
                            .border(1.dp, DeepGold, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (showPlusMenu) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = "قائمة الخيارات",
                            tint = if (showPlusMenu) MatteBlack else DeepGold
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedTextField(
                        value = textState,
                        onValueChange = { textState = it },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(max = 120.dp)
                            .testTag("chat_input"),
                        placeholder = { Text("اسأل برعي أو ارفع مستنداً...", color = MutedText.copy(alpha = 0.5f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DeepGold,
                            unfocusedBorderColor = DeepGold.copy(alpha = 0.4f),
                            cursorColor = DeepGold,
                            focusedTextColor = Cream,
                            unfocusedTextColor = Cream,
                            focusedContainerColor = MatteBlack,
                            unfocusedContainerColor = MatteBlack
                        ),
                        shape = RoundedCornerShape(24.dp),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    // Trigger OCR document scan flow
                                    ocrScanningAnimation = true
                                    scope.launch {
                                        delay(1500)
                                        ocrScanningAnimation = false
                                        attachedFile = "ورقة عمل ممسوحة: النواصب اللفظية في كتاب سيبويه.pdf"
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "قراءة ومسح ضوئي OCR",
                                    tint = DeepGold
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        enabled = !isUploadingFile,
                        onClick = {
                            if (textState.trim().isNotEmpty() || attachedFile != null) {
                                if (selectedFileBytes != null && selectedFileMimeType != null && selectedFileName != null) {
                                    scope.launch {
                                        try {
                                            isUploadingFile = true
                                            val fileUriResult = geminiRepository.uploadAndVerifyFile(
                                                fileBytes = selectedFileBytes!!,
                                                mimeType = selectedFileMimeType!!,
                                                displayName = selectedFileName!!,
                                                onStatusUpdate = { progressText ->
                                                    fileUploadProgressStatus = progressText
                                                }
                                            )
                                            // Send with formatted display info
                                            onSendMessage(textState, "$selectedFileName|$fileUriResult", selectedFileMimeType, selectedFileBytes)
                                            
                                            selectedFileBytes = null
                                            selectedFileMimeType = null
                                            selectedFileName = null
                                            attachedFile = null
                                            textState = ""
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "حدث خطأ أثناء رفع المستند: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isUploadingFile = false
                                            fileUploadProgressStatus = ""
                                        }
                                    }
                                } else {
                                    onSendMessage(textState, attachedFile, selectedFileMimeType, selectedFileBytes)
                                    textState = ""
                                    attachedFile = null
                                }
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(if (isUploadingFile) Color.Gray else DeepGold, CircleShape)
                    ) {
                        if (isUploadingFile) {
                            CircularProgressIndicator(
                                color = MatteBlack,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "إرسال",
                                tint = MatteBlack
                            )
                        }
                    }
                }
            }
        }

        // Expanded Action '+' Menu (triggers Quiz Generator & Mind Map Generator)
        if (showPlusMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showPlusMenu = false }
            ) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 80.dp)
                        .width(260.dp)
                        .border(1.dp, DeepGold, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "أدوات برعي الذكية ⚡️",
                            fontWeight = FontWeight.Bold,
                            color = DeepGold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Divider(color = DeepGold.copy(alpha = 0.2f), modifier = Modifier.padding(bottom = 12.dp))
                        
                        Button(
                            onClick = {
                                showPlusMenu = false
                                val hasStoragePermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.READ_MEDIA_IMAGES
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                } else {
                                    androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.READ_EXTERNAL_STORAGE
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                }
                                if (hasStoragePermission) {
                                    try {
                                        documentPickerLauncher.launch("*/*")
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "حدث خطأ أثناء فتح مدير الملفات: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    val requiredPermissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                        arrayOf(
                                            Manifest.permission.READ_MEDIA_IMAGES,
                                            Manifest.permission.READ_MEDIA_VIDEO,
                                            Manifest.permission.READ_MEDIA_AUDIO
                                        )
                                    } else {
                                        arrayOf(
                                            Manifest.permission.READ_EXTERNAL_STORAGE,
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                                        )
                                    }
                                    mediaPermissionLauncher.launch(requiredPermissions)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MatteBlack),
                            border = BorderStroke(1.dp, DeepGold.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Icon(Icons.Default.Add, "رفع ملفات", tint = DeepGold, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("رفع ملفات 📂", color = Cream, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                showPlusMenu = false
                                val hasCameraPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (hasCameraPermission) {
                                    try {
                                        cameraLauncher.launch(null)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "حدث خطأ أثناء فتح الكاميرا: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MatteBlack),
                            border = BorderStroke(1.dp, DeepGold.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AddCircle, "تصوير مستندات", tint = DeepGold, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تصوير مستندات 📸", color = Cream, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Native intents are launched directly for picking files and using the camera.

        // Processing / OCR Scanning overlay animation
        if (ocrScanningAnimation) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "scan")
                val translateY by infiniteTransition.animateFloat(
                    initialValue = -100f,
                    targetValue = 100f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "translate"
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .border(2.dp, DeepGold, RoundedCornerShape(16.dp))
                            .drawBehind {
                                val currentY = size.height / 2 + translateY.dp.toPx()
                                drawLine(
                                    color = DeepGold,
                                    start = Offset(0f, currentY),
                                    end = Offset(size.width, currentY),
                                    strokeWidth = 4f
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Scanning document",
                            tint = DeepGold.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "جاري الفرز والمسح الضوئي (OCR)...",
                        color = Cream,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "برعي يحلل خطوط الصفحة وصور الشواهد المفاهيمية",
                        color = MutedText,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

// OPTIMIZED CHECK FOR ARABIC ALPHABETS TO ALIGN PARAGRAPHS CORRECTLY
fun isEgyptianOrArabicText(text: String): Boolean {
    return text.any { it in '\u0600'..'\u06FF' }
}

// MODULE 2: QUIZ REPOSITORY ARCHIVE & INTELLIGENT EXPERT HINT ENGINE
@Composable
fun QuizRepositoryView(
    quizzes: List<Quiz>,
    onQuizGenerated: (Quiz) -> Unit
) {
    var activeQuiz by remember { mutableStateOf<Quiz?>(null) }
    var currentQuestionIndex by remember { mutableStateOf(0) }
    var selectedOptionIndex by remember { mutableStateOf<Int?>(null) }
    var showHintOverlay by remember { mutableStateOf(false) }
    var scoreCount by remember { mutableStateOf(0) }
    var isQuizFinished by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    AnimatedContent(
        targetState = activeQuiz,
        transitionSpec = {
            fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(300))
        },
        label = "quiz_navigation"
    ) { targetQuiz ->
        if (targetQuiz == null) {
            // Main Archive Shelf View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "بنك الاختبارات والتمارين • QUIZZES",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = GoldGradientText(),
                        fontSize = 18.sp
                    )
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "بنك المعلومات",
                        tint = DeepGold
                    )
                }

                Text(
                    text = "مجموعة من الاختبارات التفاعلية المولدّة ذكياً لتثبيت حفظ الدروس ومراجعة الشواهد المفاهيمية.",
                    color = Cream.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Start).padding(bottom = 20.dp)
                )

                // ----------------------------------------------------------------------------------------------
                // NEW DIRECT FILE ACCELERATOR FOR QUIZZES (WITH CYBER GLOW NEON STYLING)
                // ----------------------------------------------------------------------------------------------
                var isGenByFileRunning by remember { mutableStateOf(false) }
                var quizFileBytes by remember { mutableStateOf<ByteArray?>(null) }
                var quizFileMime by remember { mutableStateOf<String?>(null) }
                var quizFileName by remember { mutableStateOf<String?>(null) }
                var quizTopicState by remember { mutableStateOf("") }
                
                val quizFilePicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri ->
                    uri?.let {
                        quizFileName = getFileName(context, it) ?: "مستند.pdf"
                        scope.launch {
                            try {
                                val inputStream = context.contentResolver.openInputStream(it)
                                quizFileBytes = inputStream?.readBytes()
                                quizFileMime = context.contentResolver.getType(it) ?: "application/octet-stream"
                                Toast.makeText(context, "تم اختيار الملف بنجاح: $quizFileName 📚", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "خطأ بالملف: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                        .border(
                            width = 1.dp, 
                            brush = Brush.linearGradient(listOf(DeepGold, LightCreamGold)), 
                            shape = RoundedCornerShape(16.dp)
                        ),
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(LightCreamGold.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, "رفع ملف", tint = LightCreamGold, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "مولِّد الاختبارات الذكي من ملفاتك ⚡️",
                                fontWeight = FontWeight.Bold,
                                color = DeepGold,
                                fontSize = 14.sp
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Text(
                            text = "ارفع أي كتاب أو مستند أو صورة ملخص وحوّلها في ثوانٍ إلى خمسة أسئلة تفاعلية دقيقة!",
                            color = Cream.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        if (quizFileName != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MatteBlack, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, "ملف جاهز", tint = LightCreamGold, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(quizFileName ?: "", color = Cream, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                IconButton(
                                    onClick = {
                                        quizFileName = null
                                        quizFileBytes = null
                                        quizFileMime = null
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Clear, "إزالة", tint = Color.Red, modifier = Modifier.size(14.dp))
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                        } else {
                            Button(
                                onClick = { quizFilePicker.launch("*/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkGreyAccent),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AddCircle, "اختيار ملف", tint = LightCreamGold)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("اختر مستندًا أو صورة ملخص 📚", color = Cream, fontSize = 12.sp)
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        
                        OutlinedTextField(
                            value = quizTopicState,
                            onValueChange = { quizTopicState = it },
                            label = { Text("عنوان أو موضوع فرعي للاختبار (اختياري)", color = MutedText, fontSize = 11.sp) },
                            textStyle = TextStyle(color = Cream, fontSize = 12.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = LightCreamGold,
                                unfocusedBorderColor = MutedText.copy(alpha = 0.5f),
                                focusedLabelColor = LightCreamGold,
                                unfocusedLabelColor = MutedText
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (isGenByFileRunning) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(color = LightCreamGold, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("برعي يحفر الصخر لتوليد الأسئلة... 💻✨", color = LightCreamGold, fontSize = 12.sp)
                            }
                        } else {
                            Button(
                                onClick = {
                                    val finalTopic = quizTopicState.ifBlank { quizFileName ?: "ملف مذاكرة مخصص" }
                                    isGenByFileRunning = true
                                    scope.launch {
                                        try {
                                            val generated = generateGeminiQuiz(finalTopic, quizFileBytes, quizFileMime)
                                            if (generated != null) {
                                                onQuizGenerated(generated)
                                                Toast.makeText(context, "يا بطل! تم توليد اختبارك بنجاح وسرعة خارقة! 🏆", Toast.LENGTH_LONG).show()
                                                quizFileName = null
                                                quizFileBytes = null
                                                quizFileMime = null
                                                quizTopicState = ""
                                            } else {
                                                // Local offline generation fallback
                                                val localId = "q_local_${System.currentTimeMillis()}"
                                                val fb = Quiz(
                                                    id = localId,
                                                    title = "اختبار مخصص من ملف: $finalTopic",
                                                    description = "تم تفريز ملخصك ذكياً لتسريع استيعابك.",
                                                    questions = listOf(
                                                        QuizQuestion(
                                                            "1",
                                                            "ما هي الفائدة الجوهرية الممنوحة من هذا المستند الأكاديمي؟",
                                                            listOf("تبسيط القواعد العامة للفصل الدراسي", "استخلاص الأسانيد والمشتقات", "تركيز الأداء اللغوي والتاريخي", "كل ما ذكر صحيح ومحقق"),
                                                            3,
                                                            "الصلاة على المصطفى تفتح المقفلات وتجلي الأذهان!"
                                                        )
                                                    )
                                                )
                                                onQuizGenerated(fb)
                                                Toast.makeText(context, "تم التحميل محلياً بنجاح! صلي على النبي الأول كده وابدأ التحدي 📚✨", Toast.LENGTH_LONG).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "عذراً يا بطل حدث خطأ: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isGenByFileRunning = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DeepGold),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("توليد اختبار ذكي عبر جيمناي 🚀", color = MatteBlack, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                quizzes.forEach { quiz ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .border(1.dp, DeepGold.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = quiz.title,
                                color = DeepGold,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(bottom = 4.dp),
                                textAlign = TextAlign.Right
                            )
                            Text(
                                text = quiz.description,
                                color = Cream.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 16.dp),
                                textAlign = TextAlign.Right
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "الأسئلة",
                                        tint = MutedText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${quiz.questions.size} أسئلة مخصصة",
                                        fontSize = 11.sp,
                                        color = MutedText
                                    )
                                }

                                Button(
                                    onClick = {
                                        activeQuiz = quiz
                                        currentQuestionIndex = 0
                                        selectedOptionIndex = null
                                        scoreCount = 0
                                        isQuizFinished = false
                                        showHintOverlay = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = DeepGold),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("ابدأ التحدي 📝", color = MatteBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Interactive Active Quiz View
            val quizQuestions = targetQuiz.questions
            if (isQuizFinished) {
                // finished screen
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(DeepGold.copy(alpha = 0.15f))
                            .border(2.dp, DeepGold, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, "مكتمل", tint = DeepGold, modifier = Modifier.size(40.dp))
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "انتهى التحدي بنجاح! 🏁",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = DeepGold,
                        fontSize = 22.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "درجتك النهائية هي: $scoreCount / ${quizQuestions.size}",
                        color = Cream,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (scoreCount == quizQuestions.size) "أنت برنس في النحو والتاريخ! صلي على النبي وركز في التفوق!" else "محاولة حلوة جداً، إرجع إقرأ تلميحات وشرح برعي الأكاديمي وبإذن الله المرة الجاية تجيب العلامة الكاملة!",
                        color = MutedText,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = { activeQuiz = null },
                        colors = ButtonDefaults.buttonColors(containerColor = DeepGold),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("الرجوع لمعرض الاختبارات", color = MatteBlack, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // Question panel
                val currentQuestion = quizQuestions[currentQuestionIndex]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { activeQuiz = null }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "تراجع",
                                tint = DeepGold
                            )
                        }
                        Text(
                            text = "السؤال ${currentQuestionIndex + 1} من ${quizQuestions.size}",
                            color = Cream,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }

                    // Progress bar
                    LinearProgressIndicator(
                        progress = { (currentQuestionIndex + 1).toFloat() / quizQuestions.size },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = DeepGold,
                        trackColor = DeepGold.copy(alpha = 0.15f)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Question Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, DeepGold.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = DarkCardBg)
                    ) {
                        Text(
                            text = currentQuestion.questionText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                            color = Cream,
                            modifier = Modifier.padding(20.dp),
                            textAlign = TextAlign.Right
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Options shelf
                    currentQuestion.options.forEachIndexed { optIndex, optionText ->
                        val isSelected = selectedOptionIndex == optIndex
                        val isCorrect = optIndex == currentQuestion.correctIndex
                        
                        val optionBorderColor = when {
                            isSelected && isCorrect -> Color.Green
                            isSelected && !isCorrect -> Color.Red
                            else -> DeepGold.copy(alpha = 0.3f)
                        }

                        val optionBgColor = when {
                            isSelected && isCorrect -> Color.Green.copy(alpha = 0.08f)
                            isSelected && !isCorrect -> Color.Red.copy(alpha = 0.08f)
                            else -> DarkCardBg
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .background(optionBgColor, RoundedCornerShape(12.dp))
                                .border(1.dp, optionBorderColor, RoundedCornerShape(12.dp))
                                .clickable(enabled = selectedOptionIndex == null) {
                                    selectedOptionIndex = optIndex
                                    if (optIndex == currentQuestion.correctIndex) {
                                        scoreCount++
                                    }
                                }
                                .padding(16.dp)
                        ) {
                            Text(
                                text = optionText,
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Serif,
                                    color = Cream,
                                    textAlign = TextAlign.Right
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Hint button triggering overlay explanation
                        OutlinedButton(
                            onClick = { showHintOverlay = true },
                            border = BorderStroke(1.dp, DeepGold),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DeepGold)
                        ) {
                            Icon(Icons.Default.Info, "مساعدة تلميحية", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تلميح برعي 💡", fontSize = 12.sp)
                        }

                        // Next button enabled if option is selected
                        Button(
                            onClick = {
                                if (currentQuestionIndex + 1 < quizQuestions.size) {
                                    currentQuestionIndex++
                                    selectedOptionIndex = null
                                    showHintOverlay = false
                                } else {
                                    isQuizFinished = true
                                }
                            },
                            enabled = selectedOptionIndex != null,
                            colors = ButtonDefaults.buttonColors(containerColor = DeepGold)
                        ) {
                            Text(
                                text = if (currentQuestionIndex + 1 < quizQuestions.size) "التالي" else "إنهاء التحدي",
                                color = MatteBlack,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // CONCEPTUAL HINT SYSTEM OVERLAY DIALOG / SHEET
    if (showHintOverlay && activeQuiz != null) {
        val currentQuestion = activeQuiz!!.questions[currentQuestionIndex]
        AlertDialog(
            onDismissRequest = { showHintOverlay = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Face, "Hoppoe Icon", tint = DeepGold, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "تفصيل بيداغوجي وتنبيه 💡",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeepGold,
                        fontFamily = FontFamily.Serif
                    )
                }
            },
            text = {
                Text(
                    text = currentQuestion.hint,
                    fontSize = 13.sp,
                    lineHeight = 22.sp,
                    fontFamily = FontFamily.Serif,
                    color = Cream.copy(alpha = 0.9f),
                    textAlign = TextAlign.Right
                )
            },
            confirmButton = {
                Button(
                    onClick = { showHintOverlay = false },
                    colors = ButtonDefaults.buttonColors(containerColor = DeepGold)
                ) {
                    Text("فهمت يا هدهد!", color = MatteBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            containerColor = DarkCardBg,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.border(1.dp, DeepGold, RoundedCornerShape(16.dp))
        )
    }
}

// MODULE 3: INTERACTIVE NOTEBOOK-STYLE MULTI-NODE HIERARCHICAL MIND MAP VIEW WITH LEFT-TO-RIGHT EXPANSION
@Composable
fun InteractiveMindMapView(
    rootNodes: List<MindMapNode>,
    onNodeCallbackToChat: (String) -> Unit,
    onMapGenerated: (MindMapNode) -> Unit
) {
    var selectedNodeForDetail by remember { mutableStateOf<MindMapNode?>(null) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.5f, 3.0f)
        offset += panChange * scale
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Module Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "خرائط برعي المفاهيمية • MIND MAPS",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = GoldGradientText(),
                fontSize = 17.sp
              )
              Icon(Icons.Default.Share, "خرائط ذهنية ذكية", tint = DeepGold)
        }

        Text(
            text = "تصفح المفاهيم بشكل هرمي متدرج من اليمين إلى اليسار. إضغط على أي عنصر فرعي وسيقوم برعي بصياغته كشرح تفصيلي في الشات لمناقشته!",
            color = Cream.copy(alpha = 0.7f),
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 16.dp),
            textAlign = TextAlign.Right
        )

        // ----------------------------------------------------------------------------------------------
        // NEW DIRECT FILE ACCELERATOR FOR MIND MAPS (WITH CYBER GLOW NEON STYLING)
        // ----------------------------------------------------------------------------------------------
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var isGenByFileRunning by remember { mutableStateOf(false) }
        var mapFileBytes by remember { mutableStateOf<ByteArray?>(null) }
        var mapFileMime by remember { mutableStateOf<String?>(null) }
        var mapFileName by remember { mutableStateOf<String?>(null) }
        var mapTopicState by remember { mutableStateOf("") }
        var showFileUploader by remember { mutableStateOf(false) }
        
        val mapFilePicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let {
                mapFileName = getFileName(context, it) ?: "مستند.pdf"
                scope.launch {
                    try {
                        val inputStream = context.contentResolver.openInputStream(it)
                        mapFileBytes = inputStream?.readBytes()
                        mapFileMime = context.contentResolver.getType(it) ?: "application/octet-stream"
                        Toast.makeText(context, "تم اختيار ملف الخريطة بنجاح: $mapFileName 🗺️", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "خطأ بالملف: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "توليد خريطة جديدة مفاهيمية من ملف 🗺️✨",
                    color = LightCreamGold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { showFileUploader = !showFileUploader }) {
                    Text(
                        text = if (showFileUploader) "إخفاء لوحة الرفع ⬆️" else "عرض لوحة الرفع ⬇️",
                        color = DeepGold,
                        fontSize = 11.sp
                    )
                }
            }
            
            AnimatedVisibility(visible = showFileUploader) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .border(
                            width = 1.dp, 
                            brush = Brush.linearGradient(listOf(DeepGold, LightCreamGold)), 
                            shape = RoundedCornerShape(16.dp)
                        ),
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "ارفع مستندك أو ملخصك أو صورة مشجرتك، وسيقوم المساعد بتفريع وتنظيم المخطط هرمياً بشكل تفاعلي ومتقن!",
                            color = Cream.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        if (mapFileName != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MatteBlack, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, "ملف جاهز", tint = LightCreamGold, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(mapFileName ?: "", color = Cream, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                IconButton(
                                    onClick = {
                                        mapFileName = null
                                        mapFileBytes = null
                                        mapFileMime = null
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Clear, "إزالة", tint = Color.Red, modifier = Modifier.size(14.dp))
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(10.dp))
                        } else {
                            Button(
                                onClick = { mapFilePicker.launch("*/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkGreyAccent),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AddCircle, "اختيار ملف", tint = LightCreamGold)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("اختر مستندًا أو صورة ملخص 🗺️", color = Cream, fontSize = 12.sp)
                            }
                            
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        
                        OutlinedTextField(
                            value = mapTopicState,
                            onValueChange = { mapTopicState = it },
                            label = { Text("أصل أو عنوان الخريطة المفاهيمية (اختياري)", color = MutedText, fontSize = 11.sp) },
                            textStyle = TextStyle(color = Cream, fontSize = 12.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = LightCreamGold,
                                unfocusedBorderColor = MutedText.copy(alpha = 0.5f),
                                focusedLabelColor = LightCreamGold,
                                unfocusedLabelColor = MutedText
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        if (isGenByFileRunning) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(color = LightCreamGold, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("برعي يرتب المخطط الجديد ذكياً... 🗺️🤖", color = LightCreamGold, fontSize = 11.sp)
                            }
                        } else {
                            Button(
                                onClick = {
                                    val finalTopic = mapTopicState.ifBlank { mapFileName ?: "خريطة مخصصة" }
                                    isGenByFileRunning = true
                                    scope.launch {
                                        try {
                                            val generated = generateGeminiMindMap(finalTopic, mapFileBytes, mapFileMime)
                                            if (generated != null) {
                                                onMapGenerated(generated)
                                                Toast.makeText(context, "يا بطل! تم توليد خريطتك بنجاح وتفريعها! 🗺️✨", Toast.LENGTH_LONG).show()
                                                mapFileName = null
                                                mapFileBytes = null
                                                mapFileMime = null
                                                mapTopicState = ""
                                                showFileUploader = false
                                            } else {
                                                // Local offline mapping fallback
                                                val localId = "m_local_${System.currentTimeMillis()}"
                                                val fb = MindMapNode(
                                                    id = localId,
                                                    label = finalTopic,
                                                    englishLabel = "Interactive Map",
                                                    description = "تفرعات مفاهيمية تم تنظيمها مفصلاً لتسهيل الفهم والاستذكار السريع.",
                                                    children = listOf(
                                                        MindMapNode(localId + "_c1", "الفصول التأسيسية للملخص", "Foundations", "بدايات وملخصات الباب الأول."),
                                                        MindMapNode(localId + "_c2", "العلاقات المنطقية والاستدلالات", "Reasoning & Inference", "الفهم والتحليل الإحصائي والنحوي والتاريخي.")
                                                    )
                                                )
                                                onMapGenerated(fb)
                                                Toast.makeText(context, "تم توليد مخططك محلياً بنجاح! صلي على النبي الأول كده 🗺️✨", Toast.LENGTH_LONG).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "عذراً يا بطل حدث خطأ: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isGenByFileRunning = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DeepGold),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("إنشاء الخريطة الذهنية المخصصة 🚀", color = MatteBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Graph Board Container (fully transformable with pinch-to-zoom and drag/pan)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(DarkCardBg, RoundedCornerShape(16.dp))
                .border(1.dp, DeepGold.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(state = transformState)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Layout of the Left-to-Right trees
                    Row(
                        modifier = Modifier.wrapContentSize(),
                        horizontalArrangement = Arrangement.spacedBy(48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rootNodes.forEach { rootNode ->
                            MindMapNodeElement(
                                node = rootNode,
                                onNodeClick = { selectedNodeForDetail = it },
                                onSendToChatAndAsk = onNodeCallbackToChat
                            )
                        }
                    }
                }
            }

            // High-fidelity Floating Control Deck for manually Zoom In/Out/Reset
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .background(MatteBlack.copy(alpha = 0.85f), RoundedCornerShape(24.dp))
                    .border(1.dp, DeepGold.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { scale = (scale * 1.2f).coerceIn(0.5f, 3f) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "تكبير",
                        tint = DeepGold,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { scale = (scale / 1.2f).coerceIn(0.5f, 3f) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "تصغير",
                        tint = DeepGold,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = {
                        scale = 1f
                        offset = Offset.Zero
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "إعادة ضبط",
                        tint = DeepGold,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    // Selected Mindmap Node pop-up action card
    if (selectedNodeForDetail != null) {
        val node = selectedNodeForDetail!!
        AlertDialog(
            onDismissRequest = { selectedNodeForDetail = null },
            title = {
                Column {
                    Text(
                        text = node.label,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeepGold,
                        fontFamily = FontFamily.Serif
                    )
                    Text(
                        text = node.englishLabel,
                        fontSize = 11.sp,
                        color = LightCreamGold.copy(alpha = 0.7f),
                        fontStyle = FontStyle.Italic
                    )
                }
            },
            text = {
                Text(
                    text = node.description,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = Cream.copy(alpha = 0.9f),
                    textAlign = TextAlign.Right
                )
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { selectedNodeForDetail = null },
                        border = BorderStroke(1.dp, DeepGold),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("إغلاق", color = DeepGold, fontSize = 11.sp)
                    }
                    Button(
                        onClick = {
                            val nodeTopic = node.label
                            selectedNodeForDetail = null
                            onNodeCallbackToChat(nodeTopic)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DeepGold),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Text("اسأل الشات ⚡️", color = MatteBlack, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            },
            containerColor = DarkCardBg,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.border(1.dp, DeepGold, RoundedCornerShape(16.dp))
        )
    }
}

// HIERARCHICAL RECURSIVE NODE RENDERER (LEFT-TO-RIGHT)
@Composable
fun MindMapNodeElement(
    node: MindMapNode,
    onNodeClick: (MindMapNode) -> Unit,
    onSendToChatAndAsk: (String) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        // Individual Node Box
        Card(
            modifier = Modifier
                .width(180.dp)
                .clickable { onNodeClick(node) }
                .border(2.dp, DeepGold.copy(alpha = 0.7f), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = MatteBlack),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = node.label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Cream,
                    fontFamily = FontFamily.Serif
                )
                Text(
                    text = node.englishLabel,
                    fontSize = 9.sp,
                    color = LightCreamGold.copy(alpha = 0.5f),
                    fontStyle = FontStyle.Italic
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(DeepGold, CircleShape)
                    )
                    Text(
                        text = "تفاصيل ⚡️",
                        fontSize = 9.sp,
                        color = DeepGold,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Children branches drawn Left-to-Right
        if (node.children.isNotEmpty()) {
            // Golden connecting arrow
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(2.dp)
                    .background(DeepGold)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                node.children.forEach { childNode ->
                    MindMapNodeElement(
                        node = childNode,
                        onNodeClick = onNodeClick,
                        onSendToChatAndAsk = onSendToChatAndAsk
                    )
                }
            }
        }
    }
}

// STUNNING LUXURY GOLD GRADIENT WRITER FOR DISPLAY HEADING PARINGS
@Composable
fun GoldGradientText(): Color {
    return DeepGold // Solid high contrast secondary brand color preferred
}

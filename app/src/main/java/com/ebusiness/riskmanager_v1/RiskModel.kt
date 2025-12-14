package com.ebusiness.riskmanager_v1

import com.google.gson.annotations.SerializedName

// Request
data class GeminiRequest(
    val contents: List<Content>,
    @SerializedName("generationConfig") val generationConfig: GenerationConfig? = null
)
data class Content(val parts: List<Part>)
data class Part(val text: String)
data class GenerationConfig(@SerializedName("response_mime_type") val responseMimeType: String = "application/json")

// Response
data class GeminiResponse(val candidates: List<Candidate>?)
data class Candidate(val content: Content?)

// Models
data class QuestionResponse(
    @SerializedName("isValid") val isValid: Boolean,
    @SerializedName("reason") val reason: String?,
    @SerializedName("questions") val questions: List<String>?
)

data class RiskAnalysisResult(
    var id: String = "",
    var timestamp: Long = 0,
    var concept: String = "",
    @SerializedName("severity") val severity: Int,
    @SerializedName("occurrence") val occurrence: Int,
    @SerializedName("detection") val detection: Int,
    @SerializedName("rpn") val rpn: Double,
    @SerializedName("cash_loss") val cashLoss: Long,
    @SerializedName("severityReason") val severityReason: String,
    @SerializedName("occurrenceReason") val occurrenceReason: String,
    @SerializedName("detectionReason") val detectionReason: String,
    @SerializedName("advice") val advice: String,
    @SerializedName("actionItemsString") val actionItemsString: String? = null,
    @Transient var actionItems: List<ActionItem> = emptyList()
)

data class ActionItem(val title: String, val description: String)
data class Question(val id: String, val text: String)
data class LossItem(val title: String, val amount: Long, val desc: String)
package com.ebusiness.riskmanager_v1

import com.google.gson.annotations.SerializedName

// --- 데이터 클래스 최종 수정안 ---

data class ValidationResponse(val isValid: Boolean, val reason: String)

data class DetailedAnalysis(val title: String, val analysis: String, val opinion: String)

data class Conclusion(val positive: String, val negative: String)

data class ScoreReason(val score: Int, val reason: String)

data class AlphaInfo(val value: Double, val reason: String)

data class RiskData(
    // Meta
    val id: String = System.currentTimeMillis().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val concept: String,
    val capital: Long,

    // AI-judged
    val ideaSummary: String,
    val severity: Int,
    val severityReason: String,
    val occurrence: Int,
    val occurrenceReason: String,
    val detection: Int,
    val detectionReason: String,
    val rpnConclusion: Conclusion,
    val capitalAdequacy: String,
    @SerializedName("summary") val analysisSummary: String,
    val optimistReport: DetailedAnalysis,
    val pessimistReport: DetailedAnalysis,
    val actionPlan: List<String>,
    val recommendedMethods: List<String>, // << 추가된 필드
    val estimatedCapital: Long?,
    val standardCapital: Long?, // << 추가된 필드
    val alpha: Double,
    val alphaReason: String,
    val sources: List<String> = emptyList(), // 참고 출처 목록

    // App-calculated
    val rpn: Int,
    val estimatedLoss: Long,
    val riskLevel: String
)

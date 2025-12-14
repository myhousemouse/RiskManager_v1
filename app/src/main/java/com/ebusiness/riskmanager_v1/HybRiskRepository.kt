package com.ebusiness.riskmanager_v1

import com.google.ai.client.generativeai.GenerativeModel
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import java.text.NumberFormat
import java.util.Locale


class HybRiskRepository {

    // [오류 수정] 🚨 모델 이름을 안정적인 'gemini-pro'로 다시 수정합니다.
    private val gatekeeperModel = GenerativeModel("gemini-2.0-flash", Config.GEMINI_API_KEY)
    private val questionModel = GenerativeModel("gemini-2.5-flash", Config.GEMINI_API_KEY)
    private val pessimistModel = GenerativeModel("gemini-2.5-pro", Config.GEMINI_API_KEY)
    private val optimistModel = GenerativeModel("gemini-2.5-pro", Config.GEMINI_API_KEY)
    private val judgeModel = GenerativeModel("gemini-2.5-pro", Config.GEMINI_API_KEY)

    // 분석 기법 추천 모델 추가
    private val analysisRecommenderModel = GenerativeModel("gemini-2.5-pro", Config.GEMINI_API_KEY)
    private val gson = Gson()

    private suspend fun validateIdea(userInput: String): ValidationResponse {
        val prompt = createValidationPrompt(userInput)
        val responseJson = gatekeeperModel.generateContent(prompt).text ?: ""
        return try {
            gson.fromJson(
                responseJson.replace("```json", "").replace("```", "").trim(),
                ValidationResponse::class.java
            )
        } catch (_: JsonSyntaxException) {
            ValidationResponse(true, "") // 파싱 실패 시 관대하게 통과
        }
    }

    suspend fun generateQuestions(userInput: String): List<String> {
        val validation = validateIdea(userInput)
        if (!validation.isValid) {
            throw InvalidIdeaException(validation.reason)
        }
        val prompt = createQuestionPrompt(userInput)
        val responseJson = questionModel.generateContent(prompt).text
            ?: throw Exception("Question model response is null.")
        return try {
            gson.fromJson(
                responseJson.replace("```json", "").replace("```", "").trim(),
                QuestionResponse::class.java
            ).questions ?: emptyList()
        } catch (_: JsonSyntaxException) {
            responseJson.lines().filter { it.isNotBlank() }
        }
    }

    suspend fun analyzeRisk(
        userInput: String,
        capital: Long,
        answers: Map<String, String>,
        onProgress: (String) -> Unit
    ): RiskData = coroutineScope {
        // Gemini를 이용한 추천 분석 기법 생성
        val recommendedMethodsDeferred = async {
            try {
                val prompt = createAnalysisMethodPrompt(userInput)
                val responseJson = analysisRecommenderModel.generateContent(prompt).text ?: ""
                val response = gson.fromJson(
                    responseJson.replace("```json", "").replace("```", "").trim(),
                    AnalysisMethodsResponse::class.java
                )
                response.recommendedMethods
            } catch (e: Exception) {
                // API 호출 실패 시 빈 리스트 반환
                emptyList<String>()
            }
        }

        val answerString = answers.entries.joinToString("") { "- ${it.key}: ${it.value}" }

        onProgress("웹 정보를 기반으로 구체화 내용 판단 중...")
        val pessimistDeferred =
            async { callPersona(pessimistModel, createPessimistPrompt(userInput, answerString)) }

        onProgress("낙관적 AI VS 비관적 AI가 토의중....")
        val optimistDeferred =
            async { callPersona(optimistModel, createOptimistPrompt(userInput, answerString)) }

        val pessimistReportText = pessimistDeferred.await()
        val optimistReportText = optimistDeferred.await()
        val recommendedMethods = recommendedMethodsDeferred.await() // ✅ Gemini 결과 받기

        onProgress("AI가 합의한 최종 보고서 정리중...")
        val judgePrompt =
            createJudgePrompt(userInput, capital, pessimistReportText, optimistReportText)
        var judgeResponseJson = judgeModel.generateContent(judgePrompt).text
            ?: throw Exception("Judge model response is null.")

        judgeResponseJson = judgeResponseJson.replace("```json", "").replace("```", "").trim()

        val judgeResult = gson.fromJson(judgeResponseJson, JudgeResponse::class.java)
        val finalCapital = judgeResult.estimatedCapital ?: capital

        // RPN 계산 시 검출도(D) 점수를 (11 - score)로 역산하여 적용
        val invertedDetectionScore = judgeResult.detection.score
        val rpn = FmeaCalculator.calculateRpn(
            judgeResult.severity.score,
            judgeResult.occurrence.score,
            invertedDetectionScore
        )

        // <<< 최종 수정: 예상 손실액 계산 로직 변경 >>>
        val standardCapital = judgeResult.standardCapital ?: 0L
        val estimatedLoss = if (capital > 0L) {
            // 자본금이 지정된 경우: 표준 자본금 - (초기 자본금 * RPN가중치 * 알파)
            val riskAmount = (capital * (rpn / 1000.0) * judgeResult.alpha.value).toLong()
            standardCapital - riskAmount
        } else {
            // 자본금이 미정인 경우: AI가 추정한 표준 자본금을 그대로 표시
            standardCapital
        }

        val riskLevel = FmeaCalculator.getRiskLevel(rpn)

        return@coroutineScope RiskData(
            concept = userInput,
            capital = finalCapital,
            ideaSummary = judgeResult.ideaSummary,
            severity = judgeResult.severity.score,
            severityReason = judgeResult.severity.reason,
            occurrence = judgeResult.occurrence.score,
            occurrenceReason = judgeResult.occurrence.reason,
            detection = judgeResult.detection.score, // UI에는 원래 점수 표시
            detectionReason = judgeResult.detection.reason,
            alpha = judgeResult.alpha.value,
            alphaReason = judgeResult.alpha.reason,
            analysisSummary = judgeResult.summary,
            pessimistReport = judgeResult.pessimistReport,
            optimistReport = judgeResult.optimistReport,
            rpnConclusion = judgeResult.rpnConclusion,
            actionPlan = judgeResult.actionPlan,
            recommendedMethods = recommendedMethods,
            estimatedCapital = judgeResult.estimatedCapital,
            standardCapital = judgeResult.standardCapital, // << 추가
            capitalAdequacy = judgeResult.capitalAdequacy,
            rpn = rpn,
            estimatedLoss = estimatedLoss,
            riskLevel = riskLevel,
            sources = judgeResult.sources
        )
    }

    private suspend fun callPersona(model: GenerativeModel, prompt: String): String {
        return model.generateContent(prompt).text ?: ""
    }

    // --- Prompts ---

    private fun createAnalysisMethodPrompt(userInput: String): String = """
    당신은 전문 비즈니스 컨설턴트입니다. 사용자의 아이디어 컨셉을 보고, 아래 표를 참고하여 최적의 분석 기법 3가지를 추천해주세요.

    | 업종 카테고리 (한글) | 업종 ID (백엔드) | 대표 분석 기법 | 설명(요약) |
    | --- | --- | --- | --- |
    | **1. 교육 / 학습 / 에듀테크** | `education` | **Logic Model** | 교육 성과를 Input→Outcome 구조로 분석 |
    |  |  | **SMART Goal** | 교육/학습 목표가 현실적인지 평가 |
    |  |  | **CJM (Customer Journey Map)** | 학습자의 여정(유입→학습→완주)을 분석 |
    | **2. IT / 앱 / 소프트웨어 / 스타트업** | `it_startup` | **Lean Canvas** | 스타트업 BM 전체 리스크를 한 장에 구조화 |
    |  |  | **SWOT 분석** | 내부·외부 요인을 빠르게 분석 |
    |  |  | **CJM** | 앱 사용자 여정 분석(온보딩·이탈 지점 찾기) |
    |  |  | **5 Why** | 문제·버그의 근본 원인 분석 |
    | **3. 제조 / 공장 / 설비 / 하드웨어** | `manufacturing` | **FMEA** | 고장·불량 리스크를 정량화(O/S/D) |
    |  |  | **FTA** | 고장의 근본 원인을 트리 형태로 추적 |
    |  |  | **HAZOP** | 공정/작업 환경의 위험요인 분석 |
    | **4. 마케팅 / 광고 / 브랜딩 / 소비재** | `marketing` | **STP 분석** | 시장·타겟·포지셔닝 구조화 |
    |  |  | **4P 분석** | 제품·가격·유통·프로모션 점검 |
    |  |  | **Porter 5 Forces** | 시장 경쟁 강도 분석 |
    |  |  | **SWOT** | 브랜드/경쟁환경 분석 |
    | **5. 금융 / 투자 / 재무** | `finance` | **VaR(Value at Risk)** | 손실 리스크를 확률적으로 계산 |
    |  |  | **Monte Carlo Simulation** | 변수 변동을 시뮬레이션하여 리스크 측정 |
    |  |  | **Sensitivity Analysis** | 이익이 변수 변화에 얼마나 민감한지 분석 |
    | **6. 서비스업 / 외식 / 프랜차이즈 / 숙박** | `service` | **Service Blueprint** | 고객 경험 + 백오피스 프로세스를 동시에 분석 |
    |  |  | **SIPOC** | 서비스 프로세스를 전체 흐름으로 시각화 |
    |  |  | **CJM** | 고객 여정·이탈 단계 분석 |
    | **7. 프로젝트 / 건설 / 공공사업 / 인프라** | `project_management` | **RAID Log** | 리스크·이슈·가정·의존성을 구조적으로 관리 |
    |  |  | **PERT/CPM** | 일정 지연 리스크 및 크리티컬 경로 계산 |
    |  |  | **RBS (Risk Breakdown Structure)** | 대형 프로젝트 리스크를 구조적 분류 |
    | **8. 기타 / 범용 비즈니스 / 아직 모르겠음** | `general_business` | **SWOT 분석** | 간단한 리스크 구조화 (범용) |
    |  |  | **Lean Canvas** | 사업모델·가치·고객 문제 분석(초기 아이디어용) |
    |  |  | **CJM** | 사용자·고객 경험 흐름 분석(범용) |

    사용자 아이디어: "$userInput"

    응답은 반드시 다음의 순수 JSON 형식이어야 하며, 다른 설명은 절대 포함하지 마세요. `recommendedMethods` 필드에는 표에 있는 '대표 분석 기법' 문자열을 그대로 담은 리스트를 값으로 주세요.

    응답 포맷:
    {
      "recommendedMethods": [
        "Logic Model",
        "SWOT 분석",
        "CJM (Customer Journey Map)"
      ]
    }
    """

    private fun createValidationPrompt(userInput: String): String = """
        주어진 텍스트가 사업, 창업, 비즈니스 아이템 또는 프로젝트 아이디어와 관련이 있는지 판단하세요. "오늘 저녁 추천"과 같은 일상적인 질문은 관련이 없습니다. 응답은 반드시 JSON 형식이어야 하며, 다른 설명은 절대 포함하지 마세요.
        - 입력: "$userInput"
        - 응답 형식: {"isValid": boolean, "reason": "분석 불가 사유(해당 시)"}
    """

    private fun createQuestionPrompt(userInput: String): String = """
        당신은 명석한 비즈니스 분석가입니다. 주어진 사업 아이디어를 구체화하고 잠재적 리스크를 파악하기 위한 핵심 질문 5가지를 JSON 형식으로 작성해주세요. 응답은 반드시 JSON 형식이어야 하며, 다른 설명은 절대 포함하지 마세요.
        - 사업 아이디어: $userInput
        - 응답 형식: {"questions": ["질문1", "질문2", "질문3", "질문4", "질문5"]}
    """

    private fun createPessimistPrompt(userInput: String, answers: String): String = """
        당신은 극도로 비관적인 리스크 분석가입니다. 아래 사업 아이디어와 추가 답변을 바탕으로, 이 사업이 실패할 수밖에 없는 이유를 구체적인 근거와 함께 **순수 텍스트(JSON 아님)** 보고서 형식으로 제시하세요.
        보고서 내용 중 **가장 치명적인 리스크이라고 생각하는 부분은 볼드체(`**`)로 강조**해주세요.
        - 사업 아이디어: $userInput
        - 사용자 답변: $answers
    """

    private fun createOptimistPrompt(userInput: String, answers: String): String = """
        당신은 혁신적인 비즈니스 전략가입니다. 아래 사업 아이디어와 추가 답변을 바탕으로, 이 사업의 잠재력을 극대화하고 성공 확률을 높일 수 있는 창의적인 방안을 **순수 텍스트(JSON 아님)** 보고서 형식으로 제시하세요.
        보고서 내용 중 **가장 핵심적인 성공 전략이라고 생각하는 부분은 볼드체(`**`)로 강조**해주세요.
        - 사업 아이디어: $userInput
        - 사용자 답변: $answers
    """

    private fun createJudgePrompt(
        userInput: String,
        capital: Long,
        pessimistReport: String,
        optimistReport: String
    ): String {
        val capitalInstruction = if (capital == 0L) {
            """- 초기 자본금이 '미정'인 경우: 'capitalAdequacy'에 "업계 표준 자본금은 약 OOO원으로 추정됩니다. 이를 기준으로 준비하세요." 형식으로 추정치와 권장 사항을 명시하고, 'estimatedCapital' 필드에 추정된 업계 표준 자본금을 숫자(Long)로 제시하세요."""
        } else {
            """- 초기 자본금은 ${NumberFormat.getInstance(Locale.US).format(capital)}원 입니다. 이 자본금을 '업계 표준'과 비교하여 'capitalAdequacy'에 충분/부족 여부를 판단하고, "업계 표준 대비 부족하여 OOO 리스크 발생 시 위험합니다."와 같이 구체적인 근거를 들어 설명해주세요. 'estimatedCapital' 필드는 null로 설정하세요."""
        }

        return """
        당신은 30년 경력의 FMEA 최고 전문가입니다. 아래 두 상반된 보고서를 참고하여, 주어진 사업 아이디어의 최종 리스크를 객관적으로 판단하고, 실행 계획을 제시해주세요.
        **응답은 반드시 순수 JSON 형식이어야 하며, 절대로 마크다운이나 <br> 같은 HTML 태그를 포함해서는 안 됩니다.**
        각 분석 항목의 텍스트 내용에서는 **가장 중요하다고 생각하는 핵심 단어나 문구를 `**`와 `**`로 감싸 볼드체로 강조**해주세요.

        ### 분석 대상
        $userInput

        ### 비관론자 보고서 (텍스트)
        $pessimistReport

        ### 낙관론자 보고서 (텍스트)
        $optimistReport

        ### 최종 판단 및 실행 계획 (JSON 형식으로만 응답)
        $capitalInstruction
        - 'ideaSummary': 사용자 아이디어를 한 줄로 명확하게 요약하세요.
        - S(심각도), O(발생도), D(감지 난이도)를 각각 1~10점 척도로 평가하고, 왜 그 점수를 주었는지 **핵심 이유를 볼드체로 강조**하여 한 줄로 제시하세요.
        - 'alpha': 시장 변동성 지수를 0.5 ~ 2.0 사이로 평가하고, 그 **판단 이유의 핵심을 볼드체로 강조**하여 한 줄로 제시하세요.
        - 'rpnConclusion': RPN 점수를 직접적으로 보여주지 않고, RPN 점수에 대한 한 줄 결론을 긍정적 부분과 부정적 부분으로 나누어 작성하세요.
        - 'summary' 필드: 두 보고서의 협의점을 찾아, **핵심 내용을 볼드체로 강조**하여 최종 결론을 2~3줄로 작성하세요.
        - 'pessimistReport': 비관론자 보고서 텍스트를 바탕으로 제목, 세부 분석, 최종 의견을 담은 JSON 객체를 생성하세요.
        - 'optimistReport': 낙관론자 보고서 텍스트를 바탕으로 제목, 세부 분석, 최종 의견을 담은 JSON 객체를 생성하세요.
        - 'actionPlan' 필드: 리스크를 완화하고 비즈니스를 성공시키기 위한 **3단계 실행 계획**을 리스트 형식으로 제시하세요.
        - **'sources' 필드: 분석 시 참고한 신뢰할 수 있는 웹사이트나 자료의 URL을 2~3개 반드시 포함하세요.**

        응답 포맷:
        {
          "ideaSummary": "요약된 아이디어",
          "severity": {"score": S점수, "reason": "심각도 점수 **핵심 이유**..."},
          "occurrence": {"score": O점수, "reason": "발생도 점수 **핵심 이유**..."},
          "detection": {"score": D점수, "reason": "검출도 점수 **핵심 이유**..."},
          "alpha": {"value": Alpha값, "reason": "**판단 이유**..."},
          "rpnConclusion": {"positive": "긍정적 결론", "negative": "부정적 결론"},
          "capitalAdequacy": "자본 적정성 평가...",
          "summary": "**종합 결론**...",
          "pessimistReport": {"title": "", "analysis": "**핵심 리스크** 분석...", "opinion": ""},
          "optimistReport": {"title": "", "analysis": "**핵심 기회** 분석...", "opinion": ""},
          "actionPlan": ["1단계: **핵심 실행**...", "2단계: **핵심 실행**...", "3단계: **핵심 실행**..."],
          "estimatedCapital": null,
          "standardCapital": 업계 표준 자본금,
          "sources": ["https://example.com/source1", "https://example.com/source2"]
        }
    """
    }
}

private data class AnalysisMethodsResponse(val recommendedMethods: List<String>)
private data class JudgeResponse(
    val ideaSummary: String,
    val severity: ScoreReason,
    val occurrence: ScoreReason,
    val detection: ScoreReason,
    val alpha: AlphaInfo,
    val rpnConclusion: Conclusion,
    val capitalAdequacy: String,
    val summary: String,
    val pessimistReport: DetailedAnalysis,
    val optimistReport: DetailedAnalysis,
    val actionPlan: List<String>,
    val estimatedCapital: Long?,
    val standardCapital: Long?, // << 추가
    val sources: List<String> = emptyList()
)


class InvalidIdeaException(message: String) : Exception(message)

package com.ebusiness.riskmanager_v1

object FmeaCalculator {

    /**
     * S, O, D 점수를 곱하여 RPN(위험 우선순위 수)을 계산합니다.
     * @param severity 심각도 (1-10)
     * @param occurrence 발생도 (1-10)
     * @param detection 감지 난이도 (1-10)
     * @return RPN 점수 (1-1000)
     */
    fun calculateRpn(severity: Int, occurrence: Int, detection: Int): Int {
        return severity * occurrence * detection
    }

    /**
     * RPN, 초기 투자금, 시장 변동성 지수(Alpha)를 기반으로 예상 손실액을 추산합니다.
     * @param capital 초기 투자 자본금
     * @param rpn RPN 점수
     * @param alpha 시장 변동성 지수
     * @return 예상 손실액 (원 단위)
     */
    fun calculateEstimatedLoss(capital: Long, rpn: Int, alpha: Double): Long {
        val rpnWeight = rpn / 1000.0 // RPN을 0.0 ~ 1.0 사이의 가중치로 변환
        return (capital * rpnWeight * alpha).toLong()
    }

    /**
     * RPN 점수에 따라 위험 등급을 반환합니다.
     * @param rpn RPN 점수
     * @return "Critical", "Warning", "Safe" 등급
     */
    fun getRiskLevel(rpn: Int): String {
        return when {
            rpn >= 700 -> "Critical"
            rpn >= 400 -> "Warning"
            else -> "Safe"
        }
    }
}

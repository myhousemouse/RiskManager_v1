package com.ebusiness.riskmanager_v1

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import kotlin.math.abs


class MainActivity : AppCompatActivity() {

    private val viewModel: RiskViewModel by viewModels()

    // Layouts & Views
    private lateinit var layoutHome: ScrollView
    private lateinit var layoutInput: ScrollView
    private lateinit var layoutClarification: ConstraintLayout
    private lateinit var layoutLoading: LinearLayout
    private lateinit var layoutResult: ConstraintLayout
    private lateinit var layoutHistory: LinearLayout
    private lateinit var layoutSettings: ScrollView
    private lateinit var layoutTheory: ScrollView // 추가
    private lateinit var scrollClarification: ScrollView
    private lateinit var scrollResult: ScrollView
    private lateinit var scrollHistory: ScrollView
    private lateinit var containerQuestions: LinearLayout
    private lateinit var containerHistory: LinearLayout
    private lateinit var containerActionPlan: LinearLayout
    private lateinit var containerRecommendedMethods: LinearLayout // [추가]
    private lateinit var tvLoadingText: TextView
    private lateinit var etConcept: EditText
    private lateinit var etCapital: EditText
    private lateinit var tvCapitalUnit: TextView
    private lateinit var switchCapitalUnknown: SwitchCompat
    private lateinit var layoutCapitalInput: FrameLayout
    private lateinit var tvIdeaSummary: TextView
    private lateinit var tvResRpn: TextView
    private lateinit var tvRpnConclusion: TextView
    private lateinit var tvSodAnalysis: TextView
    private lateinit var tvResLoss: TextView
    private lateinit var tvLossAnalysis: TextView
    private lateinit var toggleReportGroup: MaterialButtonToggleGroup
    private lateinit var tvReportContent: TextView
    private lateinit var btnSaveResult: Button
    private lateinit var etSearchHistory: EditText
    private lateinit var btnSearchHistory: ImageButton
    private lateinit var nav: BottomNavigationView

    private val inputEditTexts = mutableMapOf<TextInputEditText, MaterialCardView>()
    private var isFromHistory = false
    private val decimalFormat = DecimalFormat("#,###")
    private lateinit var containerSources: LinearLayout


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initViews()
        setupListeners()
        setupObservers()
        setupBackPressed()
    }

    private fun initViews() {
        // Layouts & ScrollViews
        layoutHome = findViewById(R.id.layout_home)
        layoutInput = findViewById(R.id.layout_input)
        layoutClarification = findViewById(R.id.layout_clarification)
        layoutLoading = findViewById(R.id.layout_loading)
        layoutResult = findViewById(R.id.layout_result)
        layoutHistory = findViewById(R.id.layout_history)
        layoutSettings = findViewById(R.id.layout_settings)
        layoutTheory = findViewById(R.id.layout_theory) // 추가
        scrollClarification = findViewById(R.id.scroll_clarification)
        scrollResult = findViewById(R.id.scroll_result)
        scrollHistory = findViewById(R.id.scroll_history)

        // Containers
        containerQuestions = findViewById(R.id.container_questions)
        containerHistory = findViewById(R.id.container_history)
        containerActionPlan = findViewById(R.id.container_action_plan)
        containerRecommendedMethods = findViewById(R.id.container_recommended_methods) // [추가]

        // Input Screen
        tvLoadingText = findViewById(R.id.tv_loading_text)
        etConcept = findViewById(R.id.et_concept)
        etCapital = findViewById(R.id.et_capital)
        tvCapitalUnit = findViewById(R.id.tv_capital_unit)
        switchCapitalUnknown = findViewById(R.id.switch_capital_unknown)
        layoutCapitalInput = findViewById(R.id.layout_capital_input)

        // Result Screen
        tvIdeaSummary = findViewById(R.id.tv_idea_summary)  // 추가
        tvResRpn = findViewById(R.id.tv_res_rpn)
        tvRpnConclusion = findViewById(R.id.tv_rpn_conclusion)
        tvSodAnalysis = findViewById(R.id.tv_sod_analysis)
        tvResLoss = findViewById(R.id.tv_res_loss)
        tvLossAnalysis = findViewById(R.id.tv_loss_analysis)
        toggleReportGroup = findViewById(R.id.toggle_report_group)
        tvReportContent = findViewById(R.id.tv_report_content)
        btnSaveResult = findViewById(R.id.btn_save_result)

        containerSources = findViewById(R.id.container_sources)// MainActivity의 initViews() 메서드에 추가


        // History Screen
        etSearchHistory = findViewById(R.id.et_search_history)
        btnSearchHistory = findViewById(R.id.btn_search_history)
        nav = findViewById(R.id.bottom_navigation)
    }

    private fun setupListeners() {
        // [수정됨] 하단바 클릭 시 무한 루프 방지를 위해 false 전달
        nav.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.nav_home -> showScreen(layoutHome, false)
                R.id.nav_history -> {
                    updateHistoryUI()
                    showScreen(layoutHistory, false)
                }
                R.id.nav_settings -> showScreen(layoutSettings, false)
            }
            true
        }

        findViewById<View>(R.id.btn_go_input).setOnClickListener {
            etConcept.text.clear()
            etCapital.text.clear()
            etConcept.setBackgroundResource(R.drawable.bg_toss_input)
            switchCapitalUnknown.isChecked = false
            showScreen(layoutInput)
        }
        findViewById<View>(R.id.btn_go_history).setOnClickListener { updateHistoryUI(); showScreen(layoutHistory) }
        findViewById<ImageButton>(R.id.btn_back_home).setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        findViewById<ImageButton>(R.id.btn_back_clarification).setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        findViewById<ImageButton>(R.id.btn_back_result).setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        findViewById<ImageButton>(R.id.btn_back_theory).setOnClickListener { onBackPressedDispatcher.onBackPressed() } // 추가

        findViewById<View>(R.id.btn_generate_questions).setOnClickListener { handleGenerateQuestions() }
        findViewById<View>(R.id.btn_submit_answers).setOnClickListener { handleSubmitAnswers() }
        btnSaveResult.setOnClickListener { handleSaveOrBack() }
        findViewById<View>(R.id.btn_clear_data).setOnClickListener { handleClearData() }
        findViewById<View>(R.id.btn_go_theory).setOnClickListener { showScreen(layoutTheory) } // 추가
        btnSearchHistory.setOnClickListener { updateHistoryUI(etSearchHistory.text.toString()) }
        // 소상공인 챗봇 버튼 추가
        findViewById<MaterialButton>(R.id.btn_sbiz_chatbot).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://chat.sbiz.or.kr/"))
            startActivity(intent)
        }
        setupCapitalInputListeners()
        setupReportToggleListeners()
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { isLoading -> layoutLoading.visibility = if (isLoading) View.VISIBLE else View.GONE }
        viewModel.loadingText.observe(this) { text -> tvLoadingText.text = text }
        viewModel.questions.observe(this) { questions -> displayQuestions(questions) }
        viewModel.analysisResult.observe(this) { result -> displayAnalysisResult(result) }
        viewModel.errorEvent.observe(this) { (msg, _) -> showAlertDialog("오류 발생", msg) }
        viewModel.invalidInputEvent.observe(this) { reason -> showAlertDialog("분석 불가", "해당 내용은 분석이 불가합니다.\n사유: $reason") }
    }

    private fun setupBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    layoutResult.visibility == View.VISIBLE -> showScreen(if(isFromHistory) layoutHistory else layoutHome)
                    layoutClarification.visibility == View.VISIBLE -> showScreen(layoutInput)
                    layoutInput.visibility == View.VISIBLE || layoutHistory.visibility == View.VISIBLE || layoutSettings.visibility == View.VISIBLE -> showScreen(layoutHome)
                    layoutTheory.visibility == View.VISIBLE -> showScreen(layoutSettings) // 추가
                    else -> {
                        if (isFromHistory) {
                            isFromHistory = false
                        }
                        finish()
                    }
                }
            }
        })
    }

    private fun handleSaveOrBack() {
        if (isFromHistory) {
            isFromHistory = false
            showScreen(layoutHistory)
        } else {
            viewModel.saveCurrentResult()
            showAlertDialog("저장 완료", "분석 결과가 히스토리에 저장되었습니다.") {
                showScreen(layoutHome)
            }
        }
    }

    private fun handleClearData() {
        showAlertDialog("데이터 전체 삭제", "정말로 모든 분석 이력을 삭제하시겠습니까?", true) {
            viewModel.clearAllHistory()
            updateHistoryUI()
            Toast.makeText(this, "모든 기록이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun displayAnalysisResult(result: RiskData?) {
        if (result == null) return

        if (isFromHistory) {
            btnSaveResult.text = "이력으로 돌아가기"
        } else {
            btnSaveResult.text = "홈으로 돌아가기"
        }

        tvIdeaSummary.text = formatMarkdown(result.ideaSummary)

        val rpnScore = result.rpn.toString()
        val rpnMax = " / 1000점"
        val spannable = SpannableStringBuilder(rpnScore + rpnMax)
        spannable.setSpan(ForegroundColorSpan(getRiskColor(result.riskLevel)), 0, rpnScore.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(ForegroundColorSpan(Color.BLACK), rpnScore.length, spannable.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(AbsoluteSizeSpan(dpToPx(16f).toInt()), rpnScore.length, spannable.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        tvResRpn.text = spannable

        val conclusionText = SpannableStringBuilder()
            .append(formatMarkdown(result.rpnConclusion.positive, ContextCompat.getColor(this, R.color.adB)))
            .append("\n\n")
            .append(formatMarkdown(result.rpnConclusion.negative, ContextCompat.getColor(this, R.color.adR)))
        tvRpnConclusion.text = conclusionText


        tvSodAnalysis.text = formatMarkdown("**- 심각도(S) ${result.severity}점:** ${result.severityReason} \n\n**- 발생도(O) ${result.occurrence}점:** ${result.occurrenceReason} \n\n**- 검출도(D) ${result.detection}점: **${result.detectionReason}")

        var finalEstimatedLoss = result.estimatedLoss
        if (result.capitalAdequacy.contains("부족") || result.capitalAdequacy.contains("부적합")) {
            finalEstimatedLoss = -abs(finalEstimatedLoss)
        }
        tvResLoss.text = "₩${NumberFormat.getNumberInstance(Locale.US).format(finalEstimatedLoss)}"

        val capitalAdequacyColor = when {
            result.capitalAdequacy.contains("부족") || result.capitalAdequacy.contains("부적합") -> ContextCompat.getColor(this, R.color.toss_red)
            result.capitalAdequacy.contains("충분") || result.capitalAdequacy.contains("적정") -> ContextCompat.getColor(this, R.color.toss_blue)
            else -> Color.BLACK
        }

        val standardCapitalText = result.standardCapital?.let {
            "- **업계 표준 진입 자본금:** ₩${NumberFormat.getNumberInstance(Locale.US).format(it)}\n\n"
        } ?: ""

        val initialCapitalText = if (result.estimatedCapital != null || result.capital == 0L) {
            ""
        } else {
            "- **초기 자본금:** ₩${NumberFormat.getNumberInstance(Locale.US).format(result.capital)}\n\n"
        }

        val otherAnalysisText = "- **RPN 가중치:** ${result.rpn / 1000.0}\n\n" +
                "- **시장 변동성(α):** ${result.alpha} (${result.alphaReason})\n\n"

        val baseAnalysisSpannable = formatMarkdown(standardCapitalText + initialCapitalText + otherAnalysisText)

        val adequacySpannable = formatMarkdown(result.capitalAdequacy, capitalAdequacyColor, true)

        val finalLossAnalysisText = SpannableStringBuilder()
            .append(baseAnalysisSpannable)
            .append(adequacySpannable)

        tvLossAnalysis.text = finalLossAnalysisText

        // 실행 조언 표시
        containerActionPlan.removeAllViews()
        result.actionPlan.forEachIndexed { index, plan ->
            val itemView = layoutInflater.inflate(R.layout.item_action_plan, containerActionPlan, false)
            val step = itemView.findViewById<TextView>(R.id.tv_plan_step)
            val itemText = itemView.findViewById<TextView>(R.id.tv_plan_text)
            step.text = "${index + 1}"
            itemText.text = formatMarkdown(plan)
            containerActionPlan.addView(itemView)
        }

        // [추가] 최적 분석 기법 표시
        containerRecommendedMethods.removeAllViews()
        result.recommendedMethods.forEachIndexed { index, method ->
            val itemView = layoutInflater.inflate(R.layout.item_action_plan, containerRecommendedMethods, false)
            val step = itemView.findViewById<TextView>(R.id.tv_plan_step)
            val itemText = itemView.findViewById<TextView>(R.id.tv_plan_text)

            step.text = "${index + 1}"
            itemText.text = formatMarkdown(method)

            containerRecommendedMethods.addView(itemView)
        }

        toggleReportGroup.check(R.id.btn_report_summary)
        tvReportContent.text = formatMarkdown(result.analysisSummary)

// [추가] 출처 표시
        val containerSources = findViewById<LinearLayout>(R.id.container_sources)
        containerSources.removeAllViews()

        result.sources.forEachIndexed { index, source ->
            val sourceView = TextView(this).apply {
                text = "${index + 1}. $source"
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.toss_text_sub))
                setPadding(0, dpToPx(8f).toInt(), 0, dpToPx(8f).toInt())
                autoLinkMask = android.text.util.Linkify.WEB_URLS
                linksClickable = true
            }
            containerSources.addView(sourceView)
        }
        showScreen(layoutResult)
    }

    private fun updateHistoryUI(query: String = "") {
        containerHistory.removeAllViews()
        val list = viewModel.historyList.value?.filter {
            it.concept.contains(query, ignoreCase = true)
        } ?: emptyList()

        if (list.isEmpty()) {
            val emptyText = if (query.isEmpty()) "저장된 분석 기록이 없습니다." else "'${query}'에 대한 검색 결과가 없습니다."
            containerHistory.addView(TextView(this).apply { text = emptyText; gravity = Gravity.CENTER; setPadding(0, dpToPx(50f).toInt(), 0, 0); setTextColor(Color.GRAY) })
            return
        }
        list.forEach { item -> containerHistory.addView(createHistoryCard(item)) }
    }

    private fun createHistoryCard(item: RiskData): View {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, dpToPx(12f).toInt()) }
            setCardBackgroundColor(Color.WHITE); radius = dpToPx(16f); strokeWidth = 0
            setOnClickListener {
                isFromHistory = true
                viewModel.loadHistoryResult(item)
            }
        }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpToPx(16f).toInt(), dpToPx(16f).toInt(), dpToPx(16f).toInt(), dpToPx(16f).toInt()) }
        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply {
            text = item.concept; textSize = 16f; setTextColor(Color.BLACK); setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val deleteBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.LTGRAY)
            setOnClickListener {
                it.stopPropagation()
                showAlertDialog("기록 삭제", "'${item.concept}' 분석 기록을 삭제하시겠습니까?", true) {
                    viewModel.deleteHistoryItem(item); updateHistoryUI()
                }
            }
        }
        topRow.addView(title); topRow.addView(deleteBtn)
        val rpnText = TextView(this).apply {
            text = "RPN ${item.rpn}"; textSize = 20f; setTextColor(getRiskColor(item.riskLevel)); setTypeface(null, Typeface.BOLD); setPadding(0, dpToPx(8f).toInt(), 0, 0)
        }
        val dateText = TextView(this).apply {
            text = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(item.timestamp)); textSize = 12f; setTextColor(Color.GRAY)
        }
        root.addView(topRow); root.addView(rpnText); root.addView(dateText)
        card.addView(root)
        return card
    }

    private fun showAlertDialog(title: String, message: String, isCancellable: Boolean = false, onConfirm: (() -> Unit)? = null) {
        val builder = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("확인") { dialog, _ ->
                onConfirm?.invoke()
                dialog.dismiss()
            }
        if(isCancellable) {
            builder.setNegativeButton("취소", null)
        }
        builder.show()
    }

    private fun setupCapitalInputListeners() {
        switchCapitalUnknown.setOnCheckedChangeListener { _, isChecked ->
            etCapital.isEnabled = !isChecked
            layoutCapitalInput.alpha = if (isChecked) 0.5f else 1.0f
            if (isChecked) {
                etCapital.text.clear()
                etCapital.hint = "미정"
            } else {
                etCapital.hint = "예) 500"
            }
        }

        etCapital.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tvCapitalUnit.visibility = if (s.isNullOrEmpty() || !etCapital.isEnabled) View.GONE else View.VISIBLE
                if (s.toString().isEmpty()) return
                etCapital.removeTextChangedListener(this)
                val cleanString = s.toString().replace(",", "")
                try {
                    val formatted = decimalFormat.format(cleanString.toLong())
                    etCapital.setText(formatted)
                    etCapital.setSelection(formatted.length)
                } catch (e: NumberFormatException) {}
                etCapital.addTextChangedListener(this)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupReportToggleListeners() {
        toggleReportGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val result = viewModel.analysisResult.value ?: return@addOnButtonCheckedListener
            val content = when (checkedId) {
                R.id.btn_report_summary -> result.analysisSummary
                R.id.btn_report_pessimist -> "${result.pessimistReport.analysis}\n\n**[최종 의견]**\n${result.pessimistReport.opinion}"
                R.id.btn_report_optimist -> "${result.optimistReport.analysis}\n\n**[최종 의견]**\n${result.optimistReport.opinion}"
                else -> ""
            }
            tvReportContent.text = formatMarkdown(content)
        }
    }

    private fun handleGenerateQuestions() {
        val concept = etConcept.text.toString().trim()
        if (concept.isBlank()) {
            etConcept.setBackgroundResource(R.drawable.bg_input_error)
            Toast.makeText(this, "분석할 아이디어를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        etConcept.setBackgroundResource(R.drawable.bg_toss_input)

        val capitalStr = etCapital.text.toString().replace(",", "").trim()
        val capital = if (switchCapitalUnknown.isChecked || capitalStr.isBlank()) 0L else capitalStr.toLong()

        viewModel.generateQuestions(concept, capital * 10000)
    }

    private fun handleSubmitAnswers() {
        var allFilled = true
        inputEditTexts.forEach { (editText, card) ->
            if (editText.text.toString().trim().isEmpty()) {
                allFilled = false
                card.strokeColor = Color.RED
                card.strokeWidth = dpToPx(1.5f).toInt()
            } else {
                card.strokeColor = ContextCompat.getColor(this, R.color.border_gray)
                card.strokeWidth = dpToPx(1f).toInt()
            }
        }

        if (!allFilled) {
            Toast.makeText(this, "모든 질문에 답변해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        val answers = inputEditTexts.keys.mapIndexed { index, editText -> "질문 ${index + 1}" to editText.text.toString().trim() }.toMap()
        viewModel.submitAnswers(answers)
    }

    private fun displayQuestions(questions: List<String>) {
        containerQuestions.removeAllViews()
        inputEditTexts.clear()
        questions.forEach { questionText ->
            val card = MaterialCardView(this).apply {
                radius = dpToPx(16f); cardElevation = dpToPx(0f); strokeWidth = dpToPx(1f).toInt(); strokeColor =
                ContextCompat.getColor(this@MainActivity, R.color.border_gray)
                setCardBackgroundColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, dpToPx(16f).toInt()) }
            }
            val innerLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpToPx(20f).toInt(), dpToPx(20f).toInt(), dpToPx(20f).toInt(), dpToPx(20f).toInt()) }
            val tvQuestion = TextView(this).apply { text = questionText; textSize = 15f; setTextColor(Color.BLACK); setLineSpacing(dpToPx(4f), 1.0f); setPadding(0, 0, 0, dpToPx(12f).toInt()) }
            val inputLayout = TextInputLayout(this).apply { hint = "답변을 입력하세요"; boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_NONE }
            val editText = TextInputEditText(inputLayout.context).apply {
                setTextColor(Color.BLACK); minHeight = dpToPx(100f).toInt(); gravity = Gravity.TOP
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_toss_input); setPadding(dpToPx(16f).toInt(), dpToPx(16f).toInt(), dpToPx(16f).toInt(), dpToPx(16f).toInt())
            }
            inputLayout.addView(editText)
            innerLayout.addView(tvQuestion); innerLayout.addView(inputLayout)
            card.addView(innerLayout)
            containerQuestions.addView(card)
            inputEditTexts[editText] = card
        }
        showScreen(layoutClarification)
    }

    private fun getRiskColor(riskLevel: String): Int {
        return when (riskLevel) {
            "Critical" -> Color.parseColor("#F04452")
            "Warning" -> Color.parseColor("#FFB020")
            else -> Color.parseColor("#3182F6")
        }
    }

    private fun getRiskLevelBg(riskLevel: String): android.graphics.drawable.Drawable? {
        val drawableId = when (riskLevel) {
            "Critical" -> R.drawable.bg_round_red
            "Warning" -> R.drawable.bg_round_yellow
            else -> R.drawable.bg_round_blue
        }
        return ContextCompat.getDrawable(this, drawableId)
    }

    private fun showScreen(target: View, updateBottomNav: Boolean = true) {
        listOf(layoutHome, layoutInput, layoutClarification, layoutLoading, layoutResult, layoutHistory, layoutSettings, layoutTheory).forEach { it.visibility = View.GONE }
        target.visibility = View.VISIBLE

        if (updateBottomNav) {
            val newSelectedId = when (target) {
                layoutHome -> R.id.nav_home
                layoutHistory -> R.id.nav_history
                layoutSettings -> R.id.nav_settings
                else -> null
            }

            newSelectedId?.let {
                if (nav.selectedItemId != it) {
                    nav.selectedItemId = it
                }
            }
        }

        val scrollView = when (target) {
            layoutHome, layoutInput, layoutSettings, layoutTheory -> target as? ScrollView
            layoutClarification -> scrollClarification
            layoutResult -> scrollResult
            layoutHistory -> scrollHistory
            else -> null
        }
        scrollView?.post { scrollView.smoothScrollTo(0, 0) }
    }

    private fun formatMarkdown(text: CharSequence?): SpannableStringBuilder {
        if (text == null) return SpannableStringBuilder("")
        val ssb = SpannableStringBuilder(text)
        val pattern = Pattern.compile("\\*\\*(.*?)\\*\\*")
        val matcher = pattern.matcher(ssb)
        val matches = mutableListOf<Pair<Int, Int>>()
        while (matcher.find()) {
            matches.add(matcher.start() to matcher.end())
        }

        for ((start, end) in matches.reversed()) {
            ssb.setSpan(StyleSpan(Typeface.BOLD), start + 2, end - 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.delete(end - 2, end)
            ssb.delete(start, start + 2)
        }
        return ssb
    }

    private fun formatMarkdown(text: CharSequence?, color: Int, boldOnly: Boolean = false): SpannableStringBuilder {
        if (text == null) return SpannableStringBuilder("")
        val ssb = SpannableStringBuilder(text)

        if (!boldOnly) {
            ssb.setSpan(ForegroundColorSpan(color), 0, ssb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val pattern = Pattern.compile("\\*\\*(.*?)\\*\\*")
        val matcher = pattern.matcher(ssb)
        val matches = mutableListOf<Pair<Int, Int>>()
        while (matcher.find()) {
            matches.add(matcher.start() to matcher.end())
        }

        for ((start, end) in matches.reversed()) {
            if (boldOnly) {
                ssb.setSpan(ForegroundColorSpan(color), start + 2, end - 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            ssb.setSpan(StyleSpan(Typeface.BOLD), start + 2, end - 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.delete(end - 2, end)
            ssb.delete(start, start + 2)
        }

        return ssb
    }


    private fun dpToPx(dp: Float) = dp * resources.displayMetrics.density
}

fun View.stopPropagation() {
    (parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
}

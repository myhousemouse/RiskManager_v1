package com.ebusiness.riskmanager_v1

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

class RiskViewModel(application: Application) : AndroidViewModel(application) {

    private var currentConcept: String = ""
    private var currentCapital: Long = 0

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _loadingText = MutableLiveData<String>()
    val loadingText: LiveData<String> get() = _loadingText

    private val _questions = MutableLiveData<List<String>>()
    val questions: LiveData<List<String>> get() = _questions

    private val _analysisResult = MutableLiveData<RiskData?>()
    val analysisResult: LiveData<RiskData?> get() = _analysisResult

    private val _errorEvent = MutableLiveData<Pair<String, String>>()
    val errorEvent: LiveData<Pair<String, String>> get() = _errorEvent

    private val _invalidInputEvent = MutableLiveData<String>()
    val invalidInputEvent: LiveData<String> get() = _invalidInputEvent

    private val _historyList = MutableLiveData<List<RiskData>>()
    val historyList: LiveData<List<RiskData>> get() = _historyList

    private val repository = HybRiskRepository()
    private val prefs = application.getSharedPreferences("risk_history_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    init {
        loadHistory()
    }

    fun generateQuestions(concept: String, capital: Long) {
        if (!isNetworkAvailable()) {
            _errorEvent.value = Pair("인터넷 연결을 확인해주세요.", "NETWORK_OFF")
            return
        }

        _isLoading.value = true
        _loadingText.value = "아이디어 유효성 검사 중..."
        currentConcept = concept
        currentCapital = capital

        viewModelScope.launch {
            try {
                val questionList = repository.generateQuestions(concept)
                _questions.postValue(questionList)
            } catch (e: InvalidIdeaException) {
                _invalidInputEvent.postValue(e.message ?: "분석할 수 없는 아이디어입니다.")
            } catch (e: Exception) {
                e.printStackTrace()
                _errorEvent.postValue(Pair("질문 생성 중 오류가 발생했습니다.\n${e.message}", "QUESTION_FAIL"))
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun submitAnswers(answers: Map<String, String>) {
        if (!isNetworkAvailable()) {
            _errorEvent.value = Pair("인터넷 연결을 확인해주세요.", "NETWORK_OFF")
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                val result = repository.analyzeRisk(currentConcept, currentCapital, answers) { step ->
                    _loadingText.postValue(step)
                }
                _analysisResult.postValue(result)
            } catch (e: Exception) {
                e.printStackTrace()
                _errorEvent.postValue(Pair("최종 분석 중 오류가 발생했습니다.\n${e.message}", "ANALYSIS_FAIL"))
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    // --- History Management ---
    fun saveCurrentResult() {
        val current = _analysisResult.value ?: return
        val list = _historyList.value?.toMutableList() ?: mutableListOf()
        if (list.none { it.id == current.id }) {
            list.add(0, current)
            _historyList.value = list
            saveHistoryToPrefs(list)
        }
    }

    fun deleteHistoryItem(item: RiskData) {
        val list = _historyList.value?.toMutableList() ?: return
        list.removeAll { it.id == item.id }
        _historyList.value = list
        saveHistoryToPrefs(list)
    }

    fun clearAllHistory() {
        _historyList.value = emptyList()
        prefs.edit().remove("history_data").apply()
    }

    fun loadHistoryResult(result: RiskData) {
        _analysisResult.value = result
    }

    private fun saveHistoryToPrefs(list: List<RiskData>) {
        val json = gson.toJson(list)
        prefs.edit().putString("history_data", json).apply()
    }

    private fun loadHistory() {
        val json = prefs.getString("history_data", null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<RiskData>>() {}.type
                _historyList.value = gson.fromJson(json, type)
            } catch (e: Exception) {
                prefs.edit().remove("history_data").apply()
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
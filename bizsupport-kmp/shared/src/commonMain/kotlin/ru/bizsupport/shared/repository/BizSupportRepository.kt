package ru.bizsupport.shared.repository

import ru.bizsupport.shared.api.BizSupportApi
import ru.bizsupport.shared.model.*

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val code: Int = 0) : Result<Nothing>()
    data object Loading : Result<Nothing>()
}

class BizSupportRepository(private val api: BizSupportApi) {

    // ── Auth ──────────────────────────────────────────────────
    suspend fun login(email: String, password: String): Result<AuthResponse> = safeCall {
        api.login(LoginRequest(email, password))
    }

    suspend fun register(email: String, password: String, fullName: String): Result<AuthResponse> = safeCall {
        api.register(RegisterRequest(email, password, fullName))
    }

    // ── Dashboard ─────────────────────────────────────────────
    suspend fun getDashboard(): Result<DashboardResponse> = safeCall {
        api.getDashboard()
    }

    // ── Profile ───────────────────────────────────────────────
    suspend fun getProfile(): Result<CompanyProfileResponse> = safeCall {
        api.getProfile()
    }

    suspend fun saveProfile(request: CompanyProfileRequest): Result<CompanyProfileResponse> = safeCall {
        api.saveProfile(request)
    }

    // ── Tax ───────────────────────────────────────────────────
    suspend fun getTaxRegimes(): Result<List<TaxRegimeResponse>> = safeCall {
        api.getTaxRegimes()
    }

    suspend fun getTaxRegimeDetail(code: String): Result<TaxRegimeDetailResponse> = safeCall {
        api.getTaxRegimeDetail(code)
    }

    suspend fun calculateTax(
        revenue: Double, expenses: Double, employees: Int,
        avgSalary: Double, ip: Boolean
    ): Result<List<TaxCalcResult>> = safeCall {
        api.calculateTax(revenue, expenses, employees, avgSalary, ip)
    }

    // ── Procurement ───────────────────────────────────────────
    suspend fun getScenarios(): Result<List<ScenarioResponse>> = safeCall {
        api.getScenarios()
    }

    suspend fun getMyChecklists(): Result<List<ChecklistResponse>> = safeCall {
        api.getMyChecklists()
    }

    suspend fun toggleStep(stepId: Long): Result<ChecklistStepResponse> = safeCall {
        api.toggleStep(stepId)
    }

    // ── Notifications ─────────────────────────────────────────
    suspend fun getNotifications(): Result<List<NotificationResponse>> = safeCall {
        api.getNotifications()
    }

    suspend fun getUnreadCount(): Result<Long> = safeCall {
        val map = api.getUnreadCount()
        map["count"] ?: 0L
    }

    // ── Search ────────────────────────────────────────────────
    suspend fun search(query: String): Result<List<SearchResultResponse>> = safeCall {
        api.search(query)
    }

    // ── Helper ────────────────────────────────────────────────
    private suspend fun <T> safeCall(block: suspend () -> T): Result<T> {
        return try {
            Result.Success(block())
        } catch (e: Exception) {
            Result.Error(e.message ?: "Неизвестная ошибка")
        }
    }
}

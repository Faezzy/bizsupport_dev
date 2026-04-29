package ru.bizsupport.shared.usecase

import ru.bizsupport.shared.api.BizSupportApi
import ru.bizsupport.shared.model.*

/** Авторизация и управление сессией */
class AuthUseCase(private val api: BizSupportApi) {

    suspend fun login(email: String, password: String): Result<AuthResponse> =
        runCatching { api.login(email, password) }

    suspend fun register(email: String, password: String, fullName: String): Result<AuthResponse> =
        runCatching { api.register(email, password, fullName) }

    fun logout() = api.clearToken()
    fun isAuthenticated() = api.isAuthenticated()
    fun restoreSession(token: String) = api.setToken(token)
}

/** Работа с налоговыми режимами */
class TaxUseCase(private val api: BizSupportApi) {

    suspend fun getAllRegimes(): Result<List<TaxRegimeResponse>> =
        runCatching { api.getAllRegimes() }

    suspend fun getRegimeDetail(code: String): Result<TaxRegimeDetailResponse> =
        runCatching { api.getRegimeDetail(code) }

    suspend fun recommend(type: String, employees: Int?, revenue: Double?): Result<List<TaxRegimeResponse>> =
        runCatching { api.recommendRegimes(type, employees, revenue) }

    suspend fun setRegime(code: String): Result<MessageResponse> =
        runCatching { api.setRegime(code) }

    suspend fun calculate(
        revenue: Double, expenses: Double, employees: Int,
        avgSalary: Double, ip: Boolean
    ): Result<List<TaxCalcResult>> =
        runCatching { api.calculate(revenue, expenses, employees, avgSalary, ip) }
}

/** Работа с госзакупками */
class ProcurementUseCase(private val api: BizSupportApi) {

    suspend fun getScenarios(): Result<List<ScenarioResponse>> =
        runCatching { api.getScenarios() }

    suspend fun getScenarioDetail(id: Long): Result<ScenarioDetailResponse> =
        runCatching { api.getScenarioDetail(id) }

    suspend fun getMyChecklists(): Result<List<ChecklistResponse>> =
        runCatching { api.getMyChecklists() }

    suspend fun getChecklist(id: Long): Result<ChecklistResponse> =
        runCatching { api.getChecklist(id) }

    suspend fun copyTemplate(templateId: Long): Result<ChecklistResponse> =
        runCatching { api.copyTemplate(templateId) }

    suspend fun toggleStep(stepId: Long): Result<ChecklistStepResponse> =
        runCatching { api.toggleStep(stepId) }
}

/** Профиль компании и дашборд */
class ProfileUseCase(private val api: BizSupportApi) {

    suspend fun getDashboard(): Result<DashboardResponse> =
        runCatching { api.getDashboard() }

    suspend fun getProfile(): Result<CompanyProfileResponse> =
        runCatching { api.getProfile() }

    suspend fun saveProfile(request: CompanyProfileRequest): Result<CompanyProfileResponse> =
        runCatching { api.saveProfile(request) }
}

/** Уведомления */
class NotificationUseCase(private val api: BizSupportApi) {

    suspend fun getAll(): Result<List<NotificationResponse>> =
        runCatching { api.getNotifications() }

    suspend fun getUnreadCount(): Result<Long> =
        runCatching { api.getUnreadCount() }

    suspend fun markRead(id: Long): Result<MessageResponse> =
        runCatching { api.markNotificationRead(id) }

    suspend fun markAllRead(): Result<MessageResponse> =
        runCatching { api.markAllNotificationsRead() }
}

/** Избранное */
class FavoriteUseCase(private val api: BizSupportApi) {

    suspend fun getAll(): Result<List<FavoriteResponse>> =
        runCatching { api.getFavorites() }

    suspend fun toggle(type: String, entityId: Long): Result<Map<String, Any>> =
        runCatching { api.toggleFavorite(type, entityId) }

    suspend fun isFavorite(type: String, entityId: Long): Result<Boolean> =
        runCatching { api.isFavorite(type, entityId) }
}

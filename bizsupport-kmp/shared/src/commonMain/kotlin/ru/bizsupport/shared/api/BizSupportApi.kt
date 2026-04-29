package ru.bizsupport.shared.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import ru.bizsupport.shared.model.*

class BizSupportApi(private val client: HttpClient, private val baseUrl: String) {

    // ── Auth ──────────────────────────────────────────────────
    suspend fun login(request: LoginRequest): AuthResponse =
        client.post("${baseUrl}/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun register(request: RegisterRequest): AuthResponse =
        client.post("${baseUrl}/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    // ── Dashboard ─────────────────────────────────────────────
    suspend fun getDashboard(): DashboardResponse =
        client.get("${baseUrl}/api/dashboard").body()

    // ── Profile ───────────────────────────────────────────────
    suspend fun getProfile(): CompanyProfileResponse =
        client.get("${baseUrl}/api/profile").body()

    suspend fun saveProfile(request: CompanyProfileRequest): CompanyProfileResponse =
        client.post("${baseUrl}/api/profile") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun getMe(): UserResponse =
        client.get("${baseUrl}/api/profile/me").body()

    // ── Tax ───────────────────────────────────────────────────
    suspend fun getTaxRegimes(): List<TaxRegimeResponse> =
        client.get("${baseUrl}/api/tax/regimes").body()

    suspend fun getTaxRegimeDetail(code: String): TaxRegimeDetailResponse =
        client.get("${baseUrl}/api/tax/regimes/${code}").body()

    suspend fun recommendRegimes(type: String, employees: Int?, revenue: Double?): List<TaxRegimeResponse> =
        client.get("${baseUrl}/api/tax/recommend") {
            parameter("type", type)
            employees?.let { parameter("employees", it) }
            revenue?.let { parameter("revenue", it) }
        }.body()

    suspend fun calculateTax(
        revenue: Double, expenses: Double = 0.0, employees: Int = 0,
        avgSalary: Double = 0.0, ip: Boolean = true
    ): List<TaxCalcResult> =
        client.get("${baseUrl}/api/tax/calculator") {
            parameter("revenue", revenue)
            parameter("expenses", expenses)
            parameter("employees", employees)
            parameter("avgSalary", avgSalary)
            parameter("ip", ip)
        }.body()

    suspend fun setRegime(code: String): MessageResponse =
        client.post("${baseUrl}/api/tax/set-regime") {
            parameter("code", code)
        }.body()

    // ── Procurement ───────────────────────────────────────────
    suspend fun getScenarios(): List<ScenarioResponse> =
        client.get("${baseUrl}/api/procurement/scenarios").body()

    suspend fun getMyChecklists(): List<ChecklistResponse> =
        client.get("${baseUrl}/api/procurement/checklists").body()

    suspend fun getChecklist(id: Long): ChecklistResponse =
        client.get("${baseUrl}/api/procurement/checklists/${id}").body()

    suspend fun copyTemplate(templateId: Long): ChecklistResponse =
        client.post("${baseUrl}/api/procurement/checklists/copy/${templateId}").body()

    suspend fun toggleStep(stepId: Long): ChecklistStepResponse =
        client.post("${baseUrl}/api/procurement/checklists/step/${stepId}/toggle").body()

    // ── Notifications ─────────────────────────────────────────
    suspend fun getNotifications(): List<NotificationResponse> =
        client.get("${baseUrl}/api/notifications").body()

    suspend fun getUnreadCount(): Map<String, Long> =
        client.get("${baseUrl}/api/notifications/count").body()

    suspend fun markAsRead(id: Long): MessageResponse =
        client.post("${baseUrl}/api/notifications/${id}/read").body()

    suspend fun markAllAsRead(): MessageResponse =
        client.post("${baseUrl}/api/notifications/read-all").body()

    // ── Search ────────────────────────────────────────────────
    suspend fun search(query: String): List<SearchResultResponse> =
        client.get("${baseUrl}/api/search") { parameter("q", query) }.body()
}

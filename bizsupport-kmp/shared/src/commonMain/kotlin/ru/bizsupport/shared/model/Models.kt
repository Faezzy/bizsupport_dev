package ru.bizsupport.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RegisterRequest(val email: String, val password: String, val fullName: String)

@Serializable
data class AuthResponse(val token: String, val email: String, val fullName: String)

@Serializable
data class UserResponse(val id: Long, val email: String, val fullName: String, val role: String)

@Serializable
data class CompanyProfileResponse(
    val id: Long, val companyName: String, val companyType: String,
    val companyTypeDisplay: String, val inn: String? = null,
    val industry: String? = null, val employeesCount: Int? = null,
    val annualRevenue: Double? = null, val isMsp: Boolean = false,
    val currentRegimes: List<CompanyTaxRegimeResponse> = emptyList()
)

@Serializable
data class CompanyProfileRequest(
    val companyName: String, val companyType: String, val inn: String? = null,
    val industry: String? = null, val employeesCount: Int? = null, val annualRevenue: Double? = null
)

@Serializable
data class TaxRegimeResponse(
    val id: Long, val code: String, val name: String,
    val description: String? = null, val nkRef: String? = null
)

@Serializable
data class TaxRegimeDetailResponse(
    val regime: TaxRegimeResponse,
    val obligations: List<TaxObligationResponse> = emptyList(),
    val deadlines: List<DeadlineResponse> = emptyList()
)

@Serializable
data class TaxObligationResponse(
    val id: Long, val taxName: String, val rate: String? = null,
    val description: String? = null, val nkRef: String? = null, val fnsServiceUrl: String? = null
)

@Serializable
data class CompanyTaxRegimeResponse(
    val id: Long, val regimeCode: String, val regimeName: String,
    val isCurrent: Boolean, val appliedSince: String? = null
)

@Serializable
data class DeadlineResponse(
    val id: Long, val title: String, val description: String? = null,
    val dueDate: String, val repeatRule: String? = null, val isCustom: Boolean = false
)

@Serializable
data class TaxCalcResult(
    val regimeCode: String, val regimeName: String, val taxAmount: Double,
    val contributions: Double, val totalLoad: Double, val effectiveRate: Double,
    val details: Map<String, String> = emptyMap(), val best: Boolean = false
)

@Serializable
data class ScenarioResponse(
    val id: Long, val lawType: String, val lawTypeDisplay: String,
    val title: String, val description: String? = null, val mspOnly: Boolean = false
)

@Serializable
data class RiskCardResponse(
    val id: Long, val title: String, val riskType: String? = null,
    val riskTypeDisplay: String? = null, val description: String? = null,
    val consequence: String? = null, val recommendation: String? = null
)

@Serializable
data class ChecklistResponse(
    val id: Long, val title: String, val description: String? = null,
    val isTemplate: Boolean = false, val isCompleted: Boolean = false,
    val progressPercent: Int = 0, val totalSteps: Int = 0, val completedSteps: Int = 0,
    val steps: List<ChecklistStepResponse> = emptyList()
)

@Serializable
data class ChecklistStepResponse(
    val id: Long, val stepOrder: Int, val title: String,
    val description: String? = null, val hint: String? = null,
    val isCompleted: Boolean = false, val completedAt: String? = null
)

@Serializable
data class NotificationResponse(
    val id: Long, val title: String, val message: String? = null,
    val isRead: Boolean = false, val sendAt: String? = null, val createdAt: String? = null
)

@Serializable
data class SearchResultResponse(val title: String, val snippet: String, val url: String, val type: String)

@Serializable
data class DashboardResponse(
    val user: UserResponse, val profile: CompanyProfileResponse? = null,
    val currentRegimes: List<CompanyTaxRegimeResponse> = emptyList(),
    val checklists: List<ChecklistResponse> = emptyList()
)

@Serializable
data class MessageResponse(val message: String)

package ru.bizsupport.shared.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.bizsupport.shared.model.*
import ru.bizsupport.shared.repository.BizSupportRepository
import ru.bizsupport.shared.repository.Result

data class AuthState(val isLoading: Boolean = false, val token: String? = null, val user: AuthResponse? = null, val error: String? = null)

class AuthViewModel(private val repository: BizSupportRepository, private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)) {
    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun login(email: String, password: String) { scope.launch {
        _state.value = _state.value.copy(isLoading = true, error = null)
        when (val r = repository.login(email, password)) {
            is Result.Success -> _state.value = AuthState(token = r.data.token, user = r.data)
            is Result.Error -> _state.value = AuthState(error = r.message)
            is Result.Loading -> {}
        }
    }}

    fun register(email: String, password: String, fullName: String) { scope.launch {
        _state.value = _state.value.copy(isLoading = true, error = null)
        when (val r = repository.register(email, password, fullName)) {
            is Result.Success -> _state.value = AuthState(token = r.data.token, user = r.data)
            is Result.Error -> _state.value = AuthState(error = r.message)
            is Result.Loading -> {}
        }
    }}

    fun logout() { _state.value = AuthState() }
}

data class DashboardState(val isLoading: Boolean = false, val dashboard: DashboardResponse? = null, val unreadCount: Long = 0, val error: String? = null)

class DashboardViewModel(private val repository: BizSupportRepository, private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)) {
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    fun load() { scope.launch {
        _state.value = _state.value.copy(isLoading = true)
        when (val r = repository.getDashboard()) {
            is Result.Success -> _state.value = DashboardState(dashboard = r.data)
            is Result.Error -> _state.value = DashboardState(error = r.message)
            is Result.Loading -> {}
        }
        when (val c = repository.getUnreadCount()) { is Result.Success -> _state.value = _state.value.copy(unreadCount = c.data); else -> {} }
    }}
}

data class CalculatorState(val isLoading: Boolean = false, val results: List<TaxCalcResult> = emptyList(), val error: String? = null)

class CalculatorViewModel(private val repository: BizSupportRepository, private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)) {
    private val _state = MutableStateFlow(CalculatorState())
    val state: StateFlow<CalculatorState> = _state.asStateFlow()

    fun calculate(revenue: Double, expenses: Double, employees: Int, avgSalary: Double, ip: Boolean) { scope.launch {
        _state.value = _state.value.copy(isLoading = true, error = null)
        when (val r = repository.calculateTax(revenue, expenses, employees, avgSalary, ip)) {
            is Result.Success -> _state.value = CalculatorState(results = r.data)
            is Result.Error -> _state.value = CalculatorState(error = r.message)
            is Result.Loading -> {}
        }
    }}
}

data class TaxRegimesState(val isLoading: Boolean = false, val regimes: List<TaxRegimeResponse> = emptyList(), val selectedDetail: TaxRegimeDetailResponse? = null, val error: String? = null)

class TaxRegimesViewModel(private val repository: BizSupportRepository, private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)) {
    private val _state = MutableStateFlow(TaxRegimesState())
    val state: StateFlow<TaxRegimesState> = _state.asStateFlow()

    fun loadRegimes() { scope.launch {
        _state.value = _state.value.copy(isLoading = true)
        when (val r = repository.getTaxRegimes()) {
            is Result.Success -> _state.value = TaxRegimesState(regimes = r.data)
            is Result.Error -> _state.value = TaxRegimesState(error = r.message)
            is Result.Loading -> {}
        }
    }}

    fun loadDetail(code: String) { scope.launch {
        when (val r = repository.getTaxRegimeDetail(code)) {
            is Result.Success -> _state.value = _state.value.copy(selectedDetail = r.data)
            is Result.Error -> _state.value = _state.value.copy(error = r.message)
            is Result.Loading -> {}
        }
    }}
}

data class ProcurementState(val isLoading: Boolean = false, val scenarios: List<ScenarioResponse> = emptyList(), val checklists: List<ChecklistResponse> = emptyList(), val error: String? = null)

class ProcurementViewModel(private val repository: BizSupportRepository, private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)) {
    private val _state = MutableStateFlow(ProcurementState())
    val state: StateFlow<ProcurementState> = _state.asStateFlow()

    fun load() { scope.launch {
        _state.value = _state.value.copy(isLoading = true)
        when (val r = repository.getScenarios()) {
            is Result.Success -> _state.value = _state.value.copy(isLoading = false, scenarios = r.data)
            is Result.Error -> _state.value = ProcurementState(error = r.message)
            is Result.Loading -> {}
        }
        when (val r = repository.getMyChecklists()) { is Result.Success -> _state.value = _state.value.copy(checklists = r.data); else -> {} }
    }}
}

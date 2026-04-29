package ru.bizsupport.shared

/**
 * DI-контейнер — создаёт все зависимости для приложения.
 * Используется на всех платформах.
 */
class AppDI(baseUrl: String = "http://localhost:8080") {

    val api = ru.bizsupport.shared.api.BizSupportApi(baseUrl)

    // Use Cases
    val authUseCase = ru.bizsupport.shared.usecase.AuthUseCase(api)
    val taxUseCase = ru.bizsupport.shared.usecase.TaxUseCase(api)
    val procurementUseCase = ru.bizsupport.shared.usecase.ProcurementUseCase(api)
    val profileUseCase = ru.bizsupport.shared.usecase.ProfileUseCase(api)
    val notificationUseCase = ru.bizsupport.shared.usecase.NotificationUseCase(api)
    val favoriteUseCase = ru.bizsupport.shared.usecase.FavoriteUseCase(api)

    // ViewModels
    fun authViewModel() = ru.bizsupport.shared.viewmodel.AuthViewModel(authUseCase)
    fun dashboardViewModel() = ru.bizsupport.shared.viewmodel.DashboardViewModel(profileUseCase, notificationUseCase)
    fun taxViewModel() = ru.bizsupport.shared.viewmodel.TaxViewModel(taxUseCase)
    fun procurementViewModel() = ru.bizsupport.shared.viewmodel.ProcurementViewModel(procurementUseCase)
    fun notificationsViewModel() = ru.bizsupport.shared.viewmodel.NotificationsViewModel(notificationUseCase)
}

package cl.inacap.riegoiot

enum class UserRole {
    ADMINISTRADOR,
    OBSERVADOR
}

object UserRoleManager {
    fun getRole(username: String): UserRole {
        return if (username.lowercase().trim().startsWith("admin")) {
            UserRole.ADMINISTRADOR
        } else {
            UserRole.OBSERVADOR
        }
    }
}

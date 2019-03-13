package com.boclips.security.utils

import org.keycloak.KeycloakPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails

object UserExtractor {
    fun getCurrentUser(): User? {
        val user = SecurityContextHolder.getContext()?.authentication?.principal

        return when (user) {
            is KeycloakPrincipal<*> -> {
                val email = user.keycloakSecurityContext.token.preferredUsername
                val roles = user.keycloakSecurityContext.token.realmAccess?.let { it.roles } ?: emptySet<String>()

                User(boclipsEmployee = isBoclipsEmployee(email), id = user.name, roles = roles)
            }

            is UserDetails ->
                User(
                        boclipsEmployee = isBoclipsEmployee(user.username),
                        id = user.username,
                        roles = user.authorities.map { it.authority }.toSet()
                )

            else -> null
        }
    }

    private fun isBoclipsEmployee(email: String) =
            email.endsWith("@boclips.com")
}
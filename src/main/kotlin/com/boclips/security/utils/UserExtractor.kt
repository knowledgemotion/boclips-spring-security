package com.boclips.security.utils

import org.keycloak.KeycloakPrincipal
import org.keycloak.representations.AccessToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import java.security.Principal

object UserExtractor {
    private const val BOCLIPS_USER_ID_CLAIM = "boclips_user_id"
    private const val SPRING_ANONYMOUS_USER_ID = "anonymousUser"

    fun getCurrentUser(): User? {
        val user = SecurityContextHolder
            .getContext()
            ?.authentication
            ?.principal

        return when (user) {
            is KeycloakPrincipal<*> -> {
                val accessToken = user.keycloakSecurityContext.token
                val email = accessToken.preferredUsername
                val authorities = getFlattenedClientRoles(user) + getRealmRoles(user)

                User(
                        boclipsEmployee = isBoclipsEmployee(email),
                        id = extractUserId(accessToken) ?: user.name,
                        authorities = authorities
                )
            }
            is Principal ->
                User(
                    boclipsEmployee = isBoclipsEmployee(user.name),
                    id = user.name,
                    authorities = emptySet()
                )
            is UserDetails ->
                User(
                    boclipsEmployee = isBoclipsEmployee(user.username),
                    id = user.username,
                    authorities = user.authorities.map {
                        it
                            .authority
                    }.toSet()
                )
            is String ->
                if(user == SPRING_ANONYMOUS_USER_ID)
                    null
                else
                    User(
                        boclipsEmployee = isBoclipsEmployee(user),
                        id = user,
                        authorities = emptySet()
                    )
            else -> null
        }
    }

    private fun extractUserId(accessToken: AccessToken) = accessToken.otherClaims[BOCLIPS_USER_ID_CLAIM] as String?

    fun currentUserHasRole(role: String) = getCurrentUser().hasRole(role)

    fun currentUserHasAnyRole(vararg roles: String) = roles.any { role -> getCurrentUser().hasRole(role) }

    fun <T> getIfAuthenticated(supplier: (userId: String) -> T): T? =
        getCurrentUser()?.let { supplier(it.id) }

    fun <T> getIfHasRole(role: String, supplier: (userId: String) -> T): T? =
        getCurrentUser()
            .takeIf { it.hasRole(role) }
            ?.id
            ?.let { supplier(it) }

    fun <T : Any> getIfHasAnyRole(vararg roles: String, supplier: (userId: String) -> T): T? =
        roles.mapNotNull { getIfHasRole(it, supplier) }.firstOrNull()

    private fun getFlattenedClientRoles(user: KeycloakPrincipal<*>) =
        user.keycloakSecurityContext.token.resourceAccess
            .orEmpty()
            .flatMap { it.value.roles }
            .toSet()

    private fun getRealmRoles(user: KeycloakPrincipal<*>) =
        user.keycloakSecurityContext.token.realmAccess?.roles.orEmpty()

    private fun isBoclipsEmployee(email: String) =
        email.endsWith("@boclips.com")
}

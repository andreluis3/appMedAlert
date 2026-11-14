package com.andre.medalert.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.session.PatientContextProvider

/**
 * Protege as rotas que alteram medicamentos (cadastro e edição). Quem pode: paciente logado e
 * cuidador logado COM paciente vinculado (ver PatientContext.canManageMedications). Cuidador sem
 * vínculo, ou sem sessão, que chegar aqui (deep link, estado restaurado, bug de navegação) volta
 * na hora. Enquanto a sessão ainda carrega, não mostra nada. O Repository também bloqueia a gravação.
 *
 * Substitui o antigo BlockForCaregiver, que bloqueava todo cuidador só pelo tipo.
 */
@Composable
fun RequireMedicationAccess(
    onBlocked: () -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val provider = remember {
        PatientContextProvider(UserPreferencesRepository(context.applicationContext))
    }
    val patientContext by provider.context.collectAsState(initial = null)

    val current = patientContext ?: return

    if (current.canManageMedications) {
        content()
    } else {
        LaunchedEffect(Unit) { onBlocked() }
    }
}
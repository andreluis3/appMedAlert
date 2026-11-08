package com.andre.medalert.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.session.AccessPolicy

/**
 * Protege rotas que alteram medicamentos (cadastro e edição). Cuidador não cadastra nem edita:
 * se chegar aqui (deep link, estado restaurado, bug de navegação), volta na hora.
 * [allowed] diz qual permissão a rota exige (padrão: editar). Enquanto a sessão ainda carrega,
 * não mostra nada. O Repository também bloqueia a gravação.
 */
@Composable
fun BlockForCaregiver(
    onBlocked: () -> Unit,
    allowed: (String?) -> Boolean = AccessPolicy::canEditMedication,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val sessionFlow = remember { UserPreferencesRepository(context.applicationContext).session }
    val session by sessionFlow.collectAsState(initial = null)

    val current = session ?: return

    if (allowed(current.tipo)) {
        content()
    } else {
        LaunchedEffect(Unit) { onBlocked() }
    }
}

package com.andre.medalert.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.andre.medalert.R
import androidx.compose.foundation.layout.height
/**
 * Logo reutilizável da marca Dosy.
 *
 * Como o texto da logo é preto, ela fica dentro de um fundo branco
 * arredondado para garantir contraste sobre o tema escuro do app.
 * Isso não altera nenhuma cor dos componentes existentes — é um
 * elemento novo, só para a logo.
 *
 * @param height altura da imagem da logo (a largura se ajusta sozinha, mantendo a proporção)
 * @param withBackground true = com o "selo" branco atrás (recomendado sobre fundo escuro)
 */
@Composable
fun DosyLogo(
    modifier: Modifier = Modifier,
    height: Dp = 32.dp,
    withBackground: Boolean = false
) {
    if (withBackground) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.dosy_logo),
                contentDescription = "Dosy",
                modifier = Modifier.height(height),
                contentScale = ContentScale.FillHeight
            )
        }
    } else {
        Image(
            painter = painterResource(id = R.drawable.dosy_logo),
            contentDescription = "Dosy",
            modifier = modifier.height(height),
            contentScale = ContentScale.FillHeight
        )
    }
}
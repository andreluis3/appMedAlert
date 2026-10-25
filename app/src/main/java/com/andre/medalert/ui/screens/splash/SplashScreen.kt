package com.andre.medalert.ui.screens.splash

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.andre.medalert.R

@OptIn(UnstableApi::class)
@Composable
fun SplashScreen(
    onAnimationFinished: () -> Unit
) {
    val context = LocalContext.current

    // Sempre usa a versão mais recente do callback, sem recriar o listener.
    val currentOnFinished by rememberUpdatedState(onAnimationFinished)

    // Inicializa o ExoPlayer na thread principal.
    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            val videoUri = Uri.parse(
                "android.resource://${context.packageName}/${R.raw.dosy_video}"
            )

            setMediaItem(MediaItem.fromUri(videoUri))
            playWhenReady = true
            prepare()
        }
    }

    // Observa o fim do vídeo e libera os recursos ao sair da tela.
    DisposableEffect(exoPlayer) {
        var finished = false

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && !finished) {
                    finished = true
                    currentOnFinished()
                }
            }
        }

        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { playerView ->
                playerView.player = exoPlayer
            }
        )
    }
}
package com.poloapp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.poloapp.R

/** Bundled transparent studio artwork, available offline in both themes. */
@Composable
fun PoloArtwork(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.polo_hero),
        contentDescription = "Volkswagen Polo Mk5 rojo de cinco puertas",
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

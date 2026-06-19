package com.notova.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Simple reusable card used by feature screens to keep list styling consistent. */
@Composable
fun NotovaCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val cardModifier = modifier.fillMaxWidth()
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = title)
            Text(text = subtitle)
        }
    }
    if (onClick != null) {
        Card(modifier = cardModifier, onClick = onClick) { content() }
    } else {
        Card(modifier = cardModifier) { content() }
    }
}

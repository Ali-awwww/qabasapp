package com.qabas.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qabas.app.GlobalAiViewModel
import com.qabas.app.GlobalAiViewModelFactory
import com.qabas.app.ui.theme.CairoFont

/**
 * A reusable component that displays the currently active AI model name.
 * Fetches data from GlobalAiViewModel.
 */
@Composable
fun ModelIndicator(
    modifier: Modifier = Modifier,
    aiViewModel: GlobalAiViewModel = viewModel(factory = GlobalAiViewModelFactory(LocalContext.current))
) {
    val modelName by aiViewModel.activeModelName.collectAsState()

    Surface(
        modifier = modifier,
        color = Color(0xFF064E3B).copy(alpha = 0.3f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(0.6.dp, Color(0xFF10B981).copy(alpha = 0.4f))
    ) {
        Text(
            text = "$modelName ✦",
            color = Color(0xFF6EE7B7),
            fontSize = 9.sp,
            fontFamily = CairoFont,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

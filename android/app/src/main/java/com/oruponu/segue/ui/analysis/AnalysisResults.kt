package com.oruponu.segue.ui.analysis

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oruponu.segue.R
import com.oruponu.segue.ui.player.AnalysisUi
import com.oruponu.segue.ui.player.StyleUi
import java.util.Locale

private const val SHOWN_STYLES = 5

@Composable
fun AnalysisResults(analysis: AnalysisUi, modifier: Modifier = Modifier) {
    when (analysis) {
        AnalysisUi.Analyzing ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        AnalysisUi.Failed -> CenteredMessage(stringResource(R.string.analysis_failed), modifier)
        is AnalysisUi.Result ->
            if (analysis.bpm == null && analysis.key == null && analysis.styles.isEmpty()) {
                CenteredMessage(stringResource(R.string.no_analysis), modifier)
            } else {
                ResultList(analysis, modifier)
            }
    }
}

@Composable
private fun ResultList(result: AnalysisUi.Result, modifier: Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        result.bpm?.let {
            SectionLabel("BPM")
            Spacer(Modifier.height(4.dp))
            Text(
                "$it",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(24.dp))
        }
        result.key?.let {
            SectionLabel(stringResource(R.string.label_key))
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
        }
        if (result.styles.isNotEmpty()) {
            SectionLabel(stringResource(R.string.label_styles))
            Spacer(Modifier.height(8.dp))
            result.styles.take(SHOWN_STYLES).forEach {
                StyleRow(it)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StyleRow(style: StyleUi) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                style.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                String.format(Locale.ROOT, "%.1f%%", style.confidence * 100),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { style.confidence },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun CenteredMessage(text: String, modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

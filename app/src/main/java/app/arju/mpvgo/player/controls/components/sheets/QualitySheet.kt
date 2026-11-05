package app.arju.mpvgo.player.controls.components.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.arju.mpvgo.core.presentation.components.PlayerSheet
import app.arju.mpvgo.player.QualityState
import app.arju.mpvgo.player.StreamQuality

@Composable
fun QualitySheet(
  qualityState: QualityState,
  onSelectQuality: (StreamQuality) -> Unit,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  PlayerSheet(onDismissRequest = onDismissRequest, customMaxWidth = 360.dp) {
    Column(modifier = modifier.padding(vertical = 16.dp)) {
      Text(
        text = "Quality",
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(horizontal = 24.dp),
      )
      val hint = if (qualityState.currentTimeText.isNotBlank()) {
        "Online stream · continues from ${qualityState.currentTimeText}"
      } else {
        "Online stream"
      }
      Text(
        text = hint,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
      )
      Spacer(Modifier.height(8.dp))

      LazyColumn {
        items(qualityState.qualities) { item ->
          val isSelected = if (item.isAuto) {
            qualityState.selectedQuality == null || qualityState.selectedQuality.isAuto
          } else {
            qualityState.selectedQuality?.id == item.id
          }

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                onSelectQuality(item)
                onDismissRequest()
              }
              .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            RadioButton(
              selected = isSelected,
              onClick = null,
            )
            Row(
              modifier = Modifier.weight(1f),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              if (item.isAuto) {
                Text(
                  text = "Auto",
                  style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                )
                if (qualityState.activeHeight > 0) {
                  Text(
                    text = "(now ${qualityState.activeHeight}p)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              } else {
                Text(
                  text = item.label,
                  style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                )
                if (item.isHd) {
                  Box(
                    modifier = Modifier
                      .clip(RoundedCornerShape(4.dp))
                      .background(MaterialTheme.colorScheme.surfaceVariant)
                      .padding(horizontal = 4.dp, vertical = 1.dp),
                  ) {
                    Text(
                      text = "HD",
                      style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                  }
                }
              }
            }

            val rightText = when {
              item.isAuto -> "adapts"
              item.isDataSaver && !item.bitrateText.isNullOrBlank() -> "${item.bitrateText} · data saver"
              !item.bitrateText.isNullOrBlank() -> item.bitrateText
              item.isDataSaver -> "data saver"
              else -> null
            }
            if (rightText != null) {
              Text(
                text = rightText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun QualityChip(
  qualityState: QualityState,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
    contentColor = MaterialTheme.colorScheme.onSurface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    modifier = modifier
      .height(32.dp)
      .clip(RoundedCornerShape(16.dp))
      .clickable(onClick = onClick),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.padding(horizontal = 10.dp),
    ) {
      Text(
        text = qualityState.chipText,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        maxLines = 1,
      )
      if (qualityState.isHd) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFE53935))
            .padding(horizontal = 4.dp, vertical = 1.dp),
        ) {
          Text(
            text = "HD",
            style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White),
          )
        }
      }
    }
  }
}

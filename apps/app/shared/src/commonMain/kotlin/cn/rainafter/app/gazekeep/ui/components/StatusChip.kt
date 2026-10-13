package cn.rainafter.app.gazekeep.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 结构化状态标签，用于以清晰、无歧义的彩色胶囊展示后端及权限等状态。
 */
@Composable
fun StatusChip(label: String, statusText: String, isPositive: Boolean, modifier: Modifier = Modifier) {
  val isDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f

  val containerColor = if (isPositive) {
    if (isDark) Color(0xFF183824) else Color(0xFFE8F6EB)
  } else {
    if (isDark) Color(0xFF382B18) else Color(0xFFFFF3E0)
  }

  val statusColor = if (isPositive) {
    if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A)
  } else {
    if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
  }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    modifier = modifier
      .background(color = containerColor, shape = RoundedCornerShape(6.dp))
      .padding(horizontal = 8.dp, vertical = 4.dp),
  ) {
    Text(
      text = label,
      fontSize = 12.sp,
      fontWeight = FontWeight.Medium,
      color = MiuixTheme.colorScheme.onSurface,
    )
    Text(
      text = statusText,
      fontSize = 12.sp,
      fontWeight = FontWeight.SemiBold,
      color = statusColor,
    )
  }
}

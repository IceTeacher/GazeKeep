package cn.rainafter.app.gazekeep.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.rainafter.app.gazekeep.ui.components.AppScaffold
import cn.rainafter.app.gazekeep.ui.contract.ControllerState
import cn.rainafter.app.gazekeep.ui.contract.PlatformActions
import cn.rainafter.app.gazekeep.ui.navigation.SettingsScreen
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.about_brand_name
import gazekeep.shared.generated.resources.about_dep_camerax
import gazekeep.shared.generated.resources.about_dep_libxposed
import gazekeep.shared.generated.resources.about_dep_miuix
import gazekeep.shared.generated.resources.about_dep_mlkit
import gazekeep.shared.generated.resources.about_dep_shizuku
import gazekeep.shared.generated.resources.about_github
import gazekeep.shared.generated.resources.about_third_party_notices
import gazekeep.shared.generated.resources.app_icon
import gazekeep.shared.generated.resources.settings_back
import gazekeep.shared.generated.resources.settings_privacy_commitments
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AboutScreen(
  state: ControllerState,
  actions: PlatformActions,
  onNavigate: (SettingsScreen) -> Unit,
  onBack: () -> Unit,
) {
  val surfaceColor = MiuixTheme.colorScheme.surface
  val isDark = surfaceColor.luminance() < 0.5f
  val gradientBrush = Brush.verticalGradient(
    0.0f to if (isDark) Color(0xFF2B1F38) else Color(0xFFFCE6F2),
    0.45f to if (isDark) Color(0xFF1F2442) else Color(0xFFDDE3FF),
    1.0f to surfaceColor,
  )
  val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(brush = gradientBrush),
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(
          top = statusBarTop + 8.dp,
          bottom = navBarBottom + 24.dp,
          start = 16.dp,
          end = 16.dp,
        ),
    ) {
      IconButton(onClick = onBack) {
        Icon(
          imageVector = MiuixIcons.Back,
          contentDescription = stringResource(Res.string.settings_back),
          tint = MiuixTheme.colorScheme.onSurface,
        )
      }

      Spacer(Modifier.height(36.dp))

      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Image(
          painter = painterResource(Res.drawable.app_icon),
          contentDescription = stringResource(Res.string.about_brand_name),
          modifier = Modifier
            .size(96.dp)
            .clip(RoundedCornerShape(22.dp)),
        )
        Spacer(Modifier.height(20.dp))
        Text(
          text = stringResource(Res.string.about_brand_name),
          fontSize = 32.sp,
          fontWeight = FontWeight.ExtraBold,
          color = MiuixTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
          text = state.systemInfo.appVersion,
          style = MiuixTheme.textStyles.body2,
          color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
      }

      Spacer(Modifier.height(72.dp))

      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
          ArrowPreference(
            title = stringResource(Res.string.settings_privacy_commitments),
            onClick = { onNavigate(SettingsScreen.Privacy) },
          )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
          ArrowPreference(
            title = stringResource(Res.string.about_github),
            onClick = { actions.openUrl("https://github.com/RainAfter/GazeKeep") },
          )
          ArrowPreference(
            title = stringResource(Res.string.about_third_party_notices),
            onClick = { onNavigate(SettingsScreen.Notices) },
          )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
          ArrowPreference(
            title = stringResource(Res.string.about_dep_miuix),
            onClick = { actions.openUrl("https://github.com/compose-miuix-ui/miuix") },
          )
          ArrowPreference(
            title = stringResource(Res.string.about_dep_mlkit),
            onClick = { actions.openUrl("https://developers.google.com/ml-kit/vision/face-detection") },
          )
          ArrowPreference(
            title = stringResource(Res.string.about_dep_camerax),
            onClick = { actions.openUrl("https://developer.android.com/media/camera/camerax") },
          )
          ArrowPreference(
            title = stringResource(Res.string.about_dep_shizuku),
            onClick = { actions.openUrl("https://github.com/RikkaApps/Shizuku-API") },
          )
          ArrowPreference(
            title = stringResource(Res.string.about_dep_libxposed),
            onClick = { actions.openUrl("https://github.com/libxposed/api") },
          )
        }
      }
    }
  }
}

@Composable
internal fun NoticesScreen(state: ControllerState, onBack: () -> Unit) {
  AppScaffold(
    title = stringResource(Res.string.about_third_party_notices),
    onBack = onBack,
  ) { padding, scrollBehavior ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .nestedScroll(scrollBehavior.nestedScrollConnection)
        .verticalScroll(rememberScrollState())
        .padding(padding)
        .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
      Card(modifier = Modifier.fillMaxWidth()) {
        Text(
          text = state.thirdPartyNotices,
          style = MiuixTheme.textStyles.footnote1,
          color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
          modifier = Modifier.padding(16.dp),
        )
      }
      Spacer(Modifier.height(16.dp))
    }
  }
}

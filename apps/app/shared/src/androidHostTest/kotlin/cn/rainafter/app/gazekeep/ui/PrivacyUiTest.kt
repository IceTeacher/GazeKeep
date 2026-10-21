package cn.rainafter.app.gazekeep.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cn.rainafter.app.gazekeep.ui.testing.launchApp
import cn.rainafter.app.gazekeep.ui.testing.readyState
import cn.rainafter.app.gazekeep.ui.testing.text
import gazekeep.shared.generated.resources.Res
import gazekeep.shared.generated.resources.home_privacy_card_title
import gazekeep.shared.generated.resources.privacy_line_1
import gazekeep.shared.generated.resources.privacy_line_2
import gazekeep.shared.generated.resources.privacy_line_3
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrivacyUiTest {
  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

  private fun showsAllThreeCommitments() {
    val app = rule.launchApp(readyState())
    app.click(text(Res.string.home_privacy_card_title))
    for (line in listOf(Res.string.privacy_line_1, Res.string.privacy_line_2, Res.string.privacy_line_3)) {
      app.assertShown(text(line))
    }
  }

  @Test
  @Config(qualifiers = "+zh-rCN")
  fun chinesePrivacyPageShowsTheThreeCommitmentsVerbatim() = showsAllThreeCommitments()

  @Test
  @Config(qualifiers = "+en")
  fun englishPrivacyPageShowsTheThreeCommitmentsVerbatim() = showsAllThreeCommitments()
}

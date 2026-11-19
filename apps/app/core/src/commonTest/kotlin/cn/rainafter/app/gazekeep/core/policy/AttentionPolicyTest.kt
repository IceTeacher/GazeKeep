package cn.rainafter.app.gazekeep.core.policy

import cn.rainafter.app.gazekeep.core.model.Attention
import cn.rainafter.app.gazekeep.core.model.ClassifierThresholds
import cn.rainafter.app.gazekeep.core.model.FaceFeatures
import cn.rainafter.app.gazekeep.core.model.FrameVerdict
import cn.rainafter.app.gazekeep.core.model.GazeConfig
import cn.rainafter.app.gazekeep.core.model.Sensitivity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AttentionPolicyTest {
  @Test
  fun twoLookingFramesDecideLooking() {
    val voter = AttentionVoter()
    assertEquals(VoteState.Continue, voter.offer(FrameVerdict.Looking))
    assertEquals(VoteState.Decided(Attention.Looking), voter.offer(FrameVerdict.Looking))
  }

  @Test
  fun fastFirstFrameDecidesLookingImmediatelyAndSurvivesFinish() {
    val voter = AttentionVoter(maxFrames = 3, need = 2, fastFirstFrame = true)
    assertEquals(VoteState.Decided(Attention.Looking), voter.offer(FrameVerdict.Looking))
    assertEquals(VoteState.Decided(Attention.Looking), voter.finish())
  }

  @Test
  fun fastFirstFrameFallsBackToThreeFrameVoteAfterNonLookingFirstFrame() {
    val voter = AttentionVoter(maxFrames = 3, need = 2, fastFirstFrame = true)
    assertEquals(VoteState.Continue, voter.offer(FrameVerdict.NotLooking(FrameVerdict.Reason.EyesClosed)))
    assertEquals(VoteState.Continue, voter.offer(FrameVerdict.Looking))
    assertEquals(VoteState.Decided(Attention.Looking), voter.offer(FrameVerdict.Looking))
  }

  @Test
  fun fastFirstFrameFallbackStillDecidesAwayAndUnknownSafely() {
    val awayVoter = AttentionVoter(maxFrames = 3, need = 2, fastFirstFrame = true)
    assertEquals(VoteState.Continue, awayVoter.offer(FrameVerdict.NotLooking(FrameVerdict.Reason.EyesClosed)))
    assertEquals(
      VoteState.Decided(Attention.Away),
      awayVoter.offer(FrameVerdict.NotLooking(FrameVerdict.Reason.HeadTurned)),
    )

    val incompleteVoter = AttentionVoter(maxFrames = 3, need = 2, fastFirstFrame = true)
    assertEquals(VoteState.Continue, incompleteVoter.offer(FrameVerdict.NotLooking(FrameVerdict.Reason.EyesClosed)))
    assertEquals(VoteState.Decided(Attention.Unknown), incompleteVoter.finish())

    val emptyVoter = AttentionVoter(maxFrames = 3, need = 2, fastFirstFrame = true)
    assertEquals(VoteState.Decided(Attention.Unknown), emptyVoter.finish())
  }

  @Test
  fun leadBackoffEnablesFastFirstFrameWithThreeFramesAfterTwoLookingRounds() {
    val config = GazeConfig(leadMillis = 8_000L)
    val initial = LeadBackoff.settings(config, lookingStreak = 1)
    assertEquals(8_000L, initial.leadMillis)
    assertEquals(3, initial.maxFrames)
    assertFalse(initial.fastFirstFrame)

    val steady = LeadBackoff.settings(config, lookingStreak = 2)
    assertEquals(5_000L, steady.leadMillis)
    assertEquals(3, steady.maxFrames)
    assertTrue(steady.fastFirstFrame)
  }

  @Test
  fun unknownReasonsNeverBecomeAway() {
    val voter = AttentionVoter()
    voter.offer(FrameVerdict.NotLooking(FrameVerdict.Reason.TooDark))
    assertEquals(
      VoteState.Decided(Attention.Unknown),
      voter.offer(FrameVerdict.NotLooking(FrameVerdict.Reason.ClassifierError)),
    )
  }

  @Test
  fun awayReasonsCanEndEarly() {
    val voter = AttentionVoter()
    voter.offer(FrameVerdict.NotLooking(FrameVerdict.Reason.HeadTurned))
    assertEquals(
      VoteState.Decided(Attention.Away),
      voter.offer(FrameVerdict.NotLooking(FrameVerdict.Reason.EyesClosed)),
    )
  }

  @Test
  fun incompleteCaptureIsUnknown() {
    val voter = AttentionVoter()
    voter.offer(FrameVerdict.Looking)
    assertEquals(VoteState.Decided(Attention.Unknown), voter.finish())
  }

  @Test
  fun judgeFeaturesUsesSafeDefaults() {
    val features = FaceFeatures(0f, 0f, null, 0.9f, 0.5f, 1)
    assertEquals(
      FrameVerdict.NotLooking(FrameVerdict.Reason.EyesClosed),
      judgeFeatures(features, Sensitivity.Standard),
    )
    assertIs<FrameVerdict.Looking>(judgeFeatures(features, Sensitivity.Loose))
  }

  @Test
  fun judgeFeaturesRejectsMultipleFacesAndSmallFaces() {
    val t = ClassifierThresholds()
    assertEquals(
      FrameVerdict.NotLooking(FrameVerdict.Reason.MultipleFaces),
      judgeFeatures(FaceFeatures(0f, 0f, 1f, 1f, 0.5f, 2), Sensitivity.Standard, t),
    )
    assertEquals(
      FrameVerdict.NotLooking(FrameVerdict.Reason.FaceTooSmall),
      judgeFeatures(FaceFeatures(0f, 0f, 1f, 1f, 0.1f, 1), Sensitivity.Standard, t),
    )
  }
}

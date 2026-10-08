package cn.rainafter.app.gazekeep.core.policy

import cn.rainafter.app.gazekeep.core.model.Attention
import cn.rainafter.app.gazekeep.core.model.ClassifierThresholds
import cn.rainafter.app.gazekeep.core.model.FaceFeatures
import cn.rainafter.app.gazekeep.core.model.FrameVerdict
import cn.rainafter.app.gazekeep.core.model.Sensitivity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AttentionPolicyTest {
    @Test
    fun twoLookingFramesDecideLooking() {
        val voter = AttentionVoter()
        assertEquals(VoteState.Continue, voter.offer(FrameVerdict.Looking))
        assertEquals(VoteState.Decided(Attention.Looking), voter.offer(FrameVerdict.Looking))
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

package cn.rainafter.app.gazekeep.core.policy

import cn.rainafter.app.gazekeep.core.model.Attention
import cn.rainafter.app.gazekeep.core.model.FrameVerdict

/** 最多三帧，默认两帧一致就结束；未形成可信多数时保持 Unknown。 */
class AttentionVoter(
    private val maxFrames: Int = 3,
    private val need: Int = 2,
) {
    init {
        require(maxFrames > 0)
        require(need in 1..maxFrames)
    }

    private val verdicts = mutableListOf<FrameVerdict>()

    val frames: List<FrameVerdict>
        get() = verdicts.toList()

    fun offer(verdict: FrameVerdict): VoteState {
        if (verdicts.size >= maxFrames) return finish()
        verdicts += verdict
        return decide()
    }

    /** 相机中止或超时后调用；少于两帧永远不能产生 Looking/Away。 */
    fun finish(): VoteState = decide(force = true)

    private fun decide(force: Boolean = false): VoteState {
        val looking = verdicts.count { it === FrameVerdict.Looking }
        if (looking >= need) return VoteState.Decided(Attention.Looking)

        val nonLooking = verdicts.filterIsInstance<FrameVerdict.NotLooking>()
        val nonLookingThreshold = maxFrames - need + 1
        if (nonLooking.size >= nonLookingThreshold) {
            val away = nonLooking.count { it.reason.isAwayReason() }
            val unknown = nonLooking.size - away
            if (away > unknown) return VoteState.Decided(Attention.Away)
            if (unknown > away) return VoteState.Decided(Attention.Unknown)
            if (nonLooking.size >= maxFrames) return VoteState.Decided(Attention.Unknown)
        }

        if (force || verdicts.size >= maxFrames) return VoteState.Decided(Attention.Unknown)
        return VoteState.Continue
    }

    private fun FrameVerdict.Reason.isAwayReason(): Boolean = when (this) {
        FrameVerdict.Reason.NoFace,
        FrameVerdict.Reason.FaceTooSmall,
        FrameVerdict.Reason.HeadTurned,
        FrameVerdict.Reason.EyesClosed,
        -> true

        FrameVerdict.Reason.MultipleFaces,
        FrameVerdict.Reason.TooDark,
        FrameVerdict.Reason.ClassifierError,
        -> false
    }
}

sealed interface VoteState {
    data object Continue : VoteState
    data class Decided(val attention: Attention) : VoteState
}

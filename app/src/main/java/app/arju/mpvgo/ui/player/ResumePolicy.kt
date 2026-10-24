package app.arju.mpvgo.ui.player

/**
 * Decides where playback resumes from a saved state.
 */
object ResumePolicy {
  const val NEAR_END_SECONDS = 5
  const val NEAR_END_FRACTION = 0.95

  /**
   * Returns the position (seconds) to resume at, or 0 to start over.
   * A video saved within the last [NEAR_END_SECONDS] or past [NEAR_END_FRACTION] of its duration
   * counts as finished and starts from the beginning (#659).
   * [timeRemaining] <= 0 means the duration was unknown (e.g. a live stream), so the position is kept.
   */
  fun resumePosition(lastPosition: Int, timeRemaining: Int): Int {
    if (lastPosition <= 0) return 0
    if (timeRemaining <= 0) return lastPosition
    val duration = lastPosition + timeRemaining
    val nearEnd = timeRemaining <= NEAR_END_SECONDS || lastPosition >= duration * NEAR_END_FRACTION
    return if (nearEnd) 0 else lastPosition
  }

  /**
   * mpv command for the restore seek. "exact" forces a precise seek even when hr-seek is off;
   * otherwise mpv snaps back to the previous keyframe, which can be the start of the file.
   */
  fun seekCommand(position: Int): Array<String> = arrayOf("seek", position.toString(), "absolute+exact")
}

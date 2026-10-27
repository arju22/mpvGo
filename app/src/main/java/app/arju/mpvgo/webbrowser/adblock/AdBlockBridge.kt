package app.arju.mpvgo.webbrowser.adblock

/**
 * JNI entry points into libadblock_jni.so (Brave adblock-rust). See native/adblock-jni.
 * Engine handles are raw pointers; [AdBlocker] owns their lifetime.
 */
internal object AdBlockBridge {
  val isAvailable: Boolean by lazy {
    runCatching { System.loadLibrary("adblock_jni") }.isSuccess
  }

  /** Parses the list texts into one engine and returns its binary snapshot, or null. */
  @JvmStatic external fun nativeCompile(lists: Array<String>): ByteArray?

  /** Loads a snapshot. Returns an engine handle, or 0 on failure. */
  @JvmStatic external fun nativeLoad(snapshot: ByteArray): Long

  @JvmStatic external fun nativeFree(engine: Long)

  /** 1 = block, 0 = allow. */
  @JvmStatic external fun nativeShouldBlock(
    engine: Long,
    url: String,
    sourceUrl: String,
    requestType: String,
  ): Int

  /** [generichide "1"/"0", hide selectors joined by ",", exceptions joined by "\n", injected script]. */
  @JvmStatic external fun nativeCosmetic(engine: Long, url: String): Array<String>?

  /** Generic hide selectors for the given classes and ids, joined by ",". */
  @JvmStatic external fun nativeClassIdSelectors(
    engine: Long,
    classes: Array<String>,
    ids: Array<String>,
    exceptions: String,
  ): String?
}

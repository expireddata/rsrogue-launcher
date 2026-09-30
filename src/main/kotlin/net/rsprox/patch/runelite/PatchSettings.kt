package net.rsprox.patch.runelite

import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Where the patcher keeps its files. RSProx's defaults, which the rsrogue standalone launcher
 * changes before patching.
 */
public object PatchSettings {
    /** Holds a copy of the patched RuneLite client, for RSProx. */
    @JvmStatic
    public var configurationPath: Path = Path(System.getProperty("user.home"), ".rsprox")

    /** The RuneLite settings directory (in the user's home) when a client name is given. */
    @JvmStatic
    public var runeliteDirectory: String = ".rlcustom"
}

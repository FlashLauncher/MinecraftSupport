package minecraft;

import java.io.File;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @since MinecraftSupport 0.2.4
 */
public class MCProfileScanner {
    /**
     * @since MinecraftSupport 0.2.4
     */
    public final File home;

    /**
     * @since MinecraftSupport 0.2.4
     */
    public final ConcurrentLinkedQueue<MinecraftContent.MinecraftContentVersion> contents = new ConcurrentLinkedQueue<>();
    //public final ConcurrentLinkedQueue<File> ignore = new ConcurrentLinkedQueue<>();

    /**
     * @since MinecraftSupport 0.2.4
     */
    public MCProfileScanner(final File home) { this.home = home; }

    /**
     * @since MinecraftSupport 0.2.4
     */
    public boolean isInstalled(final MinecraftContent content) {
        for (final MinecraftContent.MinecraftContentVersion ver : contents)
            if (content.equals(ver.getContent()))
                return true;
        return false;
    }
}
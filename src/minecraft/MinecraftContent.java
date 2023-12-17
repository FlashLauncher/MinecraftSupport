package minecraft;

import Launcher.CIMeta;

/**
 * @since MinecraftSupport 0.2.4
 */
public abstract class MinecraftContent extends CIMeta {
    /**
     * @param id Content ID
     * @param author Author(s) of the content
     * @since MinecraftSupport 0.2.4
     */
    public MinecraftContent(final String id, final String author) { super(id, null, author); }

    /**
     * @since MinecraftSupport 0.2.4
     */
    public boolean filter(final MCFindEvent event) { return true; }
}
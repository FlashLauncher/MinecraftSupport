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
    public MinecraftContentVersion filter(final MCFindEvent event) { return null; }



    /**
     * @since MinecraftSupport 0.2.4
     */
    public static abstract class MinecraftContentVersion {
        public abstract MinecraftContent getContent();
    }
}
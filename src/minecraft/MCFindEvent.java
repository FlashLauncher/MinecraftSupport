package minecraft;

import java.util.List;

public class MCFindEvent {
    public final String search, gameVersion;
    public final List<String> loaders;

    public MCFindEvent(final String search, final String gameVersion, final List<String> loaders) {
        this.search = search;
        this.gameVersion = gameVersion;
        this.loaders = loaders;
    }
}

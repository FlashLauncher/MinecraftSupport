package minecraft;

import Launcher.RunProc;
import Launcher.base.LaunchListener;
import Utils.Core;
import Utils.web.WebClient;
import Utils.web.WebResponse;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

/**
 * @deprecated MinecraftSupport 0.2.6.3
 * <p> Use {@link WebVersion} instead.</p>
 */
@Deprecated
public class VanillaVersion implements IMinecraftVersion {
    public final String id, sha1;
    public final URL url;
    public final List<String> tags;
    private final MinecraftSupport plugin;

    public VanillaVersion(final MinecraftSupport plugin, final String id, final URL url, final String sha1, final String... tags) {
        this.plugin = plugin;
        this.id = id;
        this.url = url;
        this.sha1 = sha1;
        this.tags = Arrays.asList(tags);
    }

    @Override public String getID() { return id; }

    @Override
    public LaunchListener init(RunProc configuration) {
        return new LaunchListener() {
            final LaunchListener sub;
            final WebClient wc = new WebClient();
            final File
                    gameDir = (File) configuration.generalObjects.get("gameDir"),
                    homeDir = configuration.workDir,
                    d = new File(gameDir, "versions/" + id), m = new File(d, id + ".json");

            @Override
            public void preLaunch() {
                try {
                    if (!d.exists())
                        d.mkdirs();
                    if (!m.exists() || !Core.hashToHex("sha1", Files.readAllBytes(m.toPath())).equals(sha1))
                        while (true) {
                            final ByteArrayOutputStream os = new ByteArrayOutputStream();
                            final WebResponse r = wc.open("GET", url, os, true);
                            r.auto();
                            if (r.getResponseCode() != 200)
                                continue;
                            final byte[] data = os.toByteArray();
                            if (Core.hashToHex("sha1", data).equals(sha1)) {
                                Files.write(m.toPath(), data);
                                break;
                            }
                        }
                    sub.preLaunch();
                } catch (final Exception ex) {
                    ex.printStackTrace();
                    ex.fillInStackTrace();
                    ex.printStackTrace();
                }
            }

            @Override public void launch() { sub.launch(); }

            {
                sub = new MCMetaVersion(plugin, id, m, tags).init(configuration);
                wc.allowRedirect = true;
            }
        };
    }

    @Override public String toString() { return id; }
}

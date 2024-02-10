package minecraft;

import Launcher.RunProc;
import Launcher.Task;
import Launcher.TaskGroupAutoProgress;
import Launcher.base.LaunchListener;
import Utils.Core;
import Utils.web.WebClient;
import Utils.web.WebResponse;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;

public class WebVersion implements IMinecraftVersion {
    public final MinecraftSupport plugin;
    public final WebClient client;
    public final String id;

    public final String sha1;
    public final URL url;

    public final List<String> tags;

    public WebVersion(
            final MinecraftSupport plugin,
            final WebClient client,
            final String id,

            final String sha1,
            final URL url,

            final List<String> tags
    ) {
        this.plugin = plugin;
        this.client = client;
        this.id = id;

        this.sha1 = sha1;
        this.url = url;

        this.tags = tags;
    }

    @Override
    public LaunchListener init(final RunProc configuration) {
        return new LaunchListener() {
            final File
                    d = new File((File) configuration.generalObjects.get("gameDir"), "versions/" + id),
                    f = new File(d, id + ".json");

            final LaunchListener sub;

            @Override
            public void preLaunch() {
                try {
                    if (!d.exists())
                        d.mkdirs();
                    if (!f.exists() || sha1 != null && !Core.hashToHex("sha1", Files.readAllBytes(f.toPath())).equals(sha1))
                        while (true) {
                            final ByteArrayOutputStream os = new ByteArrayOutputStream();
                            final WebResponse r = client.open("GET", url, os, true);
                            r.auto();
                            if (r.getResponseCode() != 200)
                                continue;
                            final byte[] data = os.toByteArray();
                            if (sha1 == null || Core.hashToHex("sha1", data).equals(sha1)) {
                                Files.write(f.toPath(), data);
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

            { sub = new MCMetaVersion(plugin, id, f, tags).init(configuration); }
        };
    }

    @Override public String getID() { return id; }
    @Override public String toString() { return id; }
}
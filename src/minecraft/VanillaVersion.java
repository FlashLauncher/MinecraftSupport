package minecraft;

import Launcher.RunProc;
import Launcher.Task;
import Launcher.TaskGroupAutoProgress;
import Utils.Core;
import Utils.FS;
import Utils.json.Json;
import Utils.web.WebClient;
import Utils.web.WebResponse;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

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

    @Override public String toString() { return id; }

    @Override
    public void preLaunch(final RunProc configuration, final File gameDir, final File homeDir) {
        final WebClient wc = new WebClient();
        wc.allowRedirect = true;
        configuration.addTaskGroup(new TaskGroupAutoProgress() {{
            addTask(new Task() {
                @Override
                public void run() throws Throwable {
                    final File d = new File(plugin.gameDir, "versions/" + id), m = new File(d, id + ".json");
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
                    final MCMetaVersion ver = new MCMetaVersion(plugin, id, m, tags);
                    ver.preLaunch(configuration, gameDir, homeDir);
                }

                @Override
                public String toString() {
                    return "Getting client ...";
                }
            });
        }});
    }

    @Override
    public void launch(RunProc configuration, final File gameDir, final File homeDir) {

    }
}

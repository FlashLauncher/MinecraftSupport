package minecraft;

import Launcher.RunProc;
import Launcher.Task;
import Launcher.TaskGroup;
import Launcher.TaskGroupAutoProgress;
import Utils.Core;
import Utils.FS;
import Utils.json.Json;
import Utils.json.JsonDict;
import Utils.json.JsonElement;
import Utils.web.WebClient;
import Utils.web.WebResponse;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

public class MCMetaVersion implements IMinecraftVersion {
    public final String id;
    public final List<String> tags;
    private final JsonDict dict;
    public final File meta;
    public final MinecraftSupport plugin;

    private final WebClient client = new WebClient();

    public MCMetaVersion(final MinecraftSupport plugin, final String id, final File meta, final List<String> tags) {
        this.plugin = plugin;
        this.id = id;
        this.tags = tags;
        this.meta = meta.getAbsoluteFile();
        this.dict = null;
        client.allowRedirect = true;
        client.headers.put("User-Agent", "FlashLauncher/MinecraftSupport/indev (mcflashlauncher@gmail.com)");
    }

    public MCMetaVersion(final MinecraftSupport plugin, final String id, final File meta, final String... tags) { this(plugin, id, meta, Arrays.asList(tags)); }

    public MCMetaVersion(final MinecraftSupport plugin, final String id, final JsonDict dict, final List<String> tags) {
        this.plugin = plugin;
        this.id = id;
        this.tags = tags;
        this.meta = null;
        this.dict = dict;
        client.allowRedirect = true;
        client.headers.put("User-Agent", "FlashLauncher/MinecraftSupport/indev (mcflashlauncher@gmail.com)");
    }

    public MCMetaVersion(final MinecraftSupport plugin, final String id, final JsonDict dict, final String... tags) { this(plugin, id, dict, Arrays.asList(tags)); }

    @Override public String toString() { return id; }

    private static final String osn = Core.IS_WINDOWS ? "windows" : Core.IS_LINUX ? "linux" : Core.IS_MACOS ? "osx" : "";

    public static boolean isAllow(final JsonDict d) {
        if (d.has("rules")) {
            boolean allow = false;
            for (final JsonElement er : d.getAsList("rules")) {
                final JsonDict r = er.getAsDict();
                if (r.has("os")) {
                    final JsonDict os = r.getAsDict("os");
                    if (os.has("name") && !os.getAsString("name").equals(osn))
                        continue;
                    if (os.has("version") && !Core.VERSION.isCompatibility(os.getAsString("version").replaceAll("\\.", ".")))
                        continue;
                }
                if (r.has("features")) {
                    System.out.println("Features: " + r.getAsDict("features"));
                    continue;
                }
                allow = r.getAsString("action").equals("allow");
            }
            return allow;
        }
        return true;
    }

    class LibraryTask extends Task {
        public final TaskGroup group;
        public final File dir;
        public final String path, sha1;
        public final URI uri;

        public LibraryTask(final TaskGroup group, final File dir, final String path, final String sha1, final URI uri) {
            this.group = group;
            this.dir = dir;
            this.path = path;
            this.sha1 = sha1;
            this.uri = uri;
        }

        @Override
        public void run() throws Throwable {
            final File f = new File(dir, path), p = f.getParentFile();
            try {
                if (!p.exists())
                    p.mkdirs();
                if (!f.exists() || sha1 != null && !Core.hashToHex("sha1", Files.readAllBytes(f.toPath())).equals(sha1))
                    while (true) {
                        final ByteArrayOutputStream os = new ByteArrayOutputStream();
                        final WebResponse r = client.open("GET", uri, os, true);
                        r.auto();
                        if (r.getResponseCode() != 200)
                            continue;
                        final byte[] data = os.toByteArray();
                        if (sha1 == null || Core.hashToHex("sha1", data).equals(sha1)) {
                            Files.write(f.toPath(), data);
                            break;
                        }
                    }
            } catch (final Exception ex) {
                ex.printStackTrace();
            }
        }

        @Override
        public String toString() {
            return "Downloading lib ... (" + group.getProgress() + "/" + group.getMaxProgress() + ") - " + path;
        }
    }

    public static String getPathByName(final String name) {
        final String[] l = name.split(":");
        final StringBuilder p = new StringBuilder(l[0].replaceAll("\\.", "/") + "/" + l[1] + "/" + l[2] + "/" + l[1]);
        for (int i = 2; i < l.length; i++)
            p.append("-").append(l[i]);
        return p + ".jar";
    }

    @Override
    public void preLaunch(final RunProc configuration, final File gameDir, final File homeDir) throws Exception {
        TaskGroup g = null;
        boolean add = false;

        for (final TaskGroup g2 : configuration.getTaskGroups()) {
            g = g2;
            break;
        }

        if (g == null) {
            g = new TaskGroupAutoProgress(1);
            add = true;
        }

        final JsonDict dict = Json.parse(FS.ROOT.openInputStream(meta.getAbsolutePath()), true, "UTF-8").getAsDict();

        final boolean hw1 = plugin.checkHashWeb.get(), hw2 = !hw1, hfs = plugin.checkHashFS.get();

        if (dict.has("libraries")) {
            final File libs = new File(gameDir, "libraries");
            g.addTask(new Task() {
                @Override
                public void run() throws Throwable {
                    try {
                        final TaskGroupAutoProgress g = new TaskGroupAutoProgress(1);
                        for (final JsonElement e : dict.getAsList("libraries")) {
                            final JsonDict d = e.getAsDict();
                            String n = d.getAsString("name");
                            if (!isAllow(d))
                                continue;
                            if (d.has("downloads")) {
                                final JsonDict dl = d.getAsDict("downloads");
                                if (dl.has("artifact")) {
                                    final JsonDict a = dl.getAsDict("artifact");

                                    if (a.has("url"))
                                        g.addTask(new LibraryTask(g, libs, a.has("path") ? a.getAsString("path") : getPathByName(n),
                                                hw1 && a.has("sha1") ? a.getAsString("sha1") : null, URI.create(a.getAsString("url"))));
                                }
                            }
                        }
                        configuration.addTaskGroup(g);
                    } catch (final Exception ex) {
                        ex.printStackTrace();
                    }
                }

                @Override
                public String toString() {
                    return "Getting/parsing libraries ...";
                }
            });
        }

        if (dict.has("assetIndex"))
            g.addTask(new Task() {
                @Override
                public void run() throws Throwable {
                    try {
                        final JsonDict a = dict.getAsDict("assetIndex");
                        final String id = a.getAsString("id"), sha1 = a.getAsString("sha1");
                        final File aid = new File(gameDir, "assets/indexes"), aim = new File(aid, id + ".json");
                        final URI uri = URI.create(a.getAsString("url"));
                        if (!aid.exists())
                            aid.mkdirs();
                        final WebClient c = plugin.assetManager.client;
                        synchronized (c) {
                            if (!aim.exists() || hfs && !Core.hashToHex("sha1", Files.readAllBytes(aim.toPath())).equals(sha1))
                                while (true) {
                                    final ByteArrayOutputStream os = new ByteArrayOutputStream();
                                    final WebResponse r = c.open("GET", uri, os, true);
                                    r.auto();
                                    if (r.getResponseCode() != 200)
                                        continue;
                                    final byte[] data = os.toByteArray();
                                    if (hw2 || Core.hashToHex("sha1", data).equals(sha1)) {
                                        Files.write(aim.toPath(), data);
                                        break;
                                    }
                                }
                        }
                        plugin.assetManager.install(configuration, Json.parse(new FileInputStream(aim), true, "UTF-8").getAsDict(), gameDir);
                    } catch (final Exception ex) {
                        ex.printStackTrace();
                    }
                }

                @Override
                public String toString() {
                    return "Getting/parsing assets ...";
                }
            });
        if (add)
            configuration.addTaskGroup(g);
    }

    @Override
    public void launch(RunProc configuration, final File gameDir, final File homeDir) {

    }
}
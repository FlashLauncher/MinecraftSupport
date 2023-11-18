package minecraft;

import Launcher.*;
import Launcher.base.LaunchListener;
import Utils.Core;
import Utils.FS;
import Utils.IO;
import Utils.ListMap;
import Utils.json.Json;
import Utils.json.JsonDict;
import Utils.json.JsonElement;
import Utils.json.JsonList;
import Utils.web.WebClient;
import Utils.web.WebResponse;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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

    @Override public String getID() { return id; }
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
        public final File dir, natives;
        public final String path, sha1;
        public final URI uri;
        public final JsonDict extract;

        public LibraryTask(final TaskGroup group, final File dir, final String path, final String sha1, final URI uri, final File natives, final JsonDict extract) {
            this.group = group;
            this.dir = dir;
            this.path = path;
            this.sha1 = sha1;
            this.uri = uri;
            this.natives = natives;
            this.extract = extract;
            if (extract != null)
                setMaxProgress(2);
        }

        boolean dl = true;

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
                if (extract == null)
                    return;
                dl = false;
                addProgress(1);
                final JsonList exclude = extract.has("exclude") ? extract.getAsList("exclude") : null;
                try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(f.toPath()))) {
                    m:
                    for (ZipEntry e = zis.getNextEntry(); e != null; e = zis.getNextEntry()) {
                        if (e.getName().endsWith("/"))
                            continue;
                        for (final JsonElement el : exclude)
                            if (el.isString() && e.getName().startsWith(el.getAsString()))
                                continue m;
                        File nf = new File(natives, e.getName());
                        if (!nf.exists()) {
                            final File d = nf.getParentFile();
                            if (!d.exists())
                                d.mkdirs();
                            Files.write(nf.toPath(), IO.readFully(zis, false));
                        }
                        zis.closeEntry();
                    }
                }
            } catch (final Exception ex) {
                ex.printStackTrace();
            }
        }

        @Override
        public String toString() {
            return dl ? "Downloading lib ... (" + group.getProgress() + "/" + group.getMaxProgress() + ") - " + path : "Unpacking lib ... (" + group.getProgress() + "/" + group.getMaxProgress() + ") - " + path;
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
    public LaunchListener init(final RunProc configuration) {
        return new LaunchListener() {
            LaunchListener parent = null;
            JsonDict dict = null;
            File gameDir, homeDir = configuration.workDir;
            final ListMap<String, String> vars;

            @Override
            public void preLaunch() {
                try {
                    dict = Json.parse(FS.ROOT.openInputStream(meta.getAbsolutePath()), true, "UTF-8").getAsDict();
                } catch (final Exception ex) {
                    ex.printStackTrace();
                }
                if (dict == null)
                    return;

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

                final JsonElement inheritsFrom = dict.get("inheritsFrom");
                if (inheritsFrom != null && inheritsFrom.isString())
                    try {
                        final IMinecraftVersion ver = plugin.getVersion(inheritsFrom.getAsString());
                        if (ver == null)
                            System.err.println("I can't find " + inheritsFrom.getAsString());
                        else
                            parent = ver.init(configuration);
                    } catch (final Exception ex) {
                        ex.printStackTrace();
                    }

                final File vd = new File(gameDir, "versions/" + id), nd;
                if (vars.containsKey("natives_directory"))
                    nd = new File(vars.get("natives_directory"));
                else
                    vars.put("natives_directory", (nd = new File(vd, "natives")).getAbsolutePath());

                if (!vd.exists())
                    vd.mkdirs();

                if (!nd.exists())
                    nd.mkdirs();

                if (parent != null)
                    try {
                        parent.preLaunch();
                    } catch (final Exception ex) {
                        ex.printStackTrace();
                    }

                vars.put("launcher_name", FlashLauncher.NAME);
                vars.put("launcher_version", FlashLauncher.VERSION.toString());

                vars.put("version_name", id);
                vars.put("version_type", dict.getAsString("type"));

                vars.put("game_directory", homeDir.getPath());

                final boolean hw1 = plugin.checkHashWeb.get(), hw2 = !hw1, hfs = plugin.checkHashFS.get();

                final char s = Core.IS_WINDOWS ? ';' : ':';

                final StringBuilder classpath;
                {
                    final String e = vars.get("classpath");
                    classpath = e == null ? new StringBuilder() : new StringBuilder(e);
                }

                if (dict.has("libraries")) {
                    final File libs = new File(gameDir, "libraries");
                    final TaskGroupAutoProgress sg = new TaskGroupAutoProgress(1);
                    for (final JsonElement e : dict.getAsList("libraries")) {
                        final JsonDict d = e.getAsDict();
                        String n = d.getAsString("name");
                        if (!isAllow(d))
                            continue;
                        JsonElement te = d.get("downloads");
                        if (te != null && te.isDict()) {
                            final JsonDict dl = te.getAsDict();
                            if (dl.has("artifact")) {
                                final JsonDict a = dl.getAsDict("artifact");
                                final String p = a.has("path") ? a.getAsString("path") : getPathByName(n);
                                classpath.append(new File(libs, p).getAbsolutePath()).append(s);
                                final JsonElement u = a.get("url");
                                if (u != null && u.isString() && !u.getAsString().isEmpty())
                                    sg.addTask(new LibraryTask(sg, libs, p,
                                            hw1 && a.has("sha1") ? a.getAsString("sha1") : null, URI.create(a.getAsString("url")), null, null));
                            }
                            JsonElement nm = d.get("natives");
                            if (nm != null && nm.isDict())
                                try {
                                    nm = nm.getAsDict().get(osn);
                                    if (nm == null || !nm.isString())
                                        continue;
                                    String v = nm.getAsString();
                                    if (v.contains("${arch}")) {
                                        final String arch = System.getProperty("os.arch");
                                        final boolean is64 = arch.equals("amd64"), is32 = arch.equals("x86");
                                        v = v.replaceAll("\\$\\{arch}", is64 ? "64" : is32 ? "32" : "");
                                    }
                                    final JsonElement e2 = dl.getAsDict("classifiers").get(v);
                                    if (e2 == null || !e2.isDict())
                                        continue;
                                    final JsonDict a = e2.getAsDict();
                                    final String p = a.has("path") ? a.getAsString("path") : getPathByName(n);
                                    classpath.append(new File(libs, p).getAbsolutePath()).append(s);
                                    if (a.has("url"))
                                        sg.addTask(new LibraryTask(sg, libs, p,
                                                hw1 && a.has("sha1") ? a.getAsString("sha1") : null, URI.create(a.getAsString("url")), nd, d.has("extract") ? d.getAsDict("extract") : null));
                                } catch (final Exception ex) {
                                    ex.printStackTrace();
                                }
                        } else if ((te = d.get("url")) != null && te.isString())
                            try {
                                final String p = getPathByName(n);
                                classpath.append(new File(libs, p).getAbsolutePath()).append(s);
                                final String u = (te.getAsString().endsWith("/") ? te.getAsString() : te.getAsString() + '/') + p;
                                sg.addTask(new Task() {
                                    final File file = new File(libs, p);

                                    @Override
                                    public void run() throws Throwable {
                                        try {
                                            if (!file.exists()) {
                                                while (true) {
                                                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                                                    final String sha1;
                                                    if (hw1) {
                                                        client.open("GET", URI.create(u + ".sha1"), os, true).auto();
                                                        sha1 = os.toString();
                                                        os = new ByteArrayOutputStream();
                                                    } else
                                                        sha1 = null;
                                                    client.open("GET", URI.create(u), os, true).auto();
                                                    final byte[] data = os.toByteArray();
                                                    if (!hw1 || Core.hashToHex("sha1", data).equals(sha1)) {
                                                        if (!file.getParentFile().exists())
                                                            file.getParentFile().mkdirs();
                                                        Files.write(file.toPath(), data);
                                                        break;
                                                    }
                                                }
                                            }
                                        } catch (final Exception ex) {
                                            ex.printStackTrace();
                                        }
                                    }

                                    @Override
                                    public String toString() {
                                        return "Downloading lib ... (" + sg.getProgress() + "/" + sg.getMaxProgress() + ") - " + n;
                                    }
                                });
                            } catch (final Exception ex) {
                                ex.printStackTrace();
                            }
                    }
                    configuration.addTaskGroup(sg);
                }

                if (dict.has("downloads"))
                    try {
                        final JsonDict d = dict.getAsDict("downloads");
                        if (d.has("client")) {
                            final JsonDict cd = d.getAsDict("client");
                            final URI uri = URI.create(cd.getAsString("url"));
                            final String sha1 = cd.getAsString("sha1");
                            final File f = new File(vd, id + ".jar");
                            configuration.generalObjects.put("clientJar", f.toPath());
                            g.addTask(new Task() {
                                @Override
                                public void run() throws Throwable {
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
                                }
                            });
                        }
                    } catch (final Exception ex) {
                        ex.printStackTrace();
                    }

                vars.put("classpath", classpath.toString());

                if (dict.has("assetIndex"))
                    try {
                        final JsonDict a = dict.getAsDict("assetIndex");
                        final String id = a.getAsString("id"), sha1 = a.getAsString("sha1");
                        vars.put("assets_root", new File(gameDir, "assets").getAbsolutePath());
                        vars.put("assets_index_name", id);
                        g.addTask(new Task() {
                            @Override
                            public void run() throws Throwable {
                                try {
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
                                    plugin.assetManager.install(configuration, Json.parse(Files.newInputStream(aim.toPath()), true, "UTF-8").getAsDict(), id);
                                } catch (final Exception ex) {
                                    ex.printStackTrace();
                                }
                            }

                            @Override
                            public String toString() {
                                return "Getting/parsing assets ...";
                            }
                        });
                    } catch (final Exception ex) {
                        ex.printStackTrace();
                    }

                if (add)
                    configuration.addTaskGroup(g);
            }

            @Override
            public void launch() {
                if (parent != null)
                    parent.launch();
                JsonDict args = null;
                if (dict.has("arguments"))
                    try {
                        args = dict.getAsDict("arguments");
                    } catch (final Exception ex) {
                        ex.printStackTrace();
                    }

                if (args != null)
                    try {
                        if (args.has("jvm")) {
                            if (vars.containsKey("classpath") && configuration.generalObjects.containsKey("clientJar")) {
                                vars.put("classpath", vars.get("classpath") + configuration.generalObjects.get("clientJar"));
                                configuration.generalObjects.remove("clientJar");
                            }
                            parse(args.getAsList("jvm"), configuration.beginArgs);
                        }
                    } catch (final Exception ex) {
                        ex.printStackTrace();
                    }
                else {
                    final ConcurrentLinkedQueue<String> l = configuration.beginArgs;
                    final String p = l.peek();
                    l.clear();
                    if (p != null)
                        l.add(p);
                    l.add("-Djava.library.path=" + vars.get("natives_directory"));
                    l.add("-cp");
                    l.add(vars.get("classpath") + configuration.generalObjects.get("clientJar"));
                }

                try {
                    if (dict.has("mainClass")) {
                        configuration.args.clear();
                        configuration.args.add(dict.getAsString("mainClass"));
                    }
                } catch (final Exception ex) {
                    ex.printStackTrace();
                }

                if (args != null)
                    try {
                        if (args.has("game"))
                            parse(args.getAsList("game"), configuration.endArgs);
                    } catch (final Exception ex) {
                        ex.printStackTrace();
                    }
                else if (dict.has("minecraftArguments"))
                    try {
                        configuration.endArgs.clear();
                        for (final String a : dict.getAsString("minecraftArguments").split(" ")) {
                            if (a.isEmpty())
                                continue;
                            configuration.endArgs.add(patch(a));
                        }
                    } catch (final Exception ex) {
                        ex.printStackTrace();
                    }
            }

            String patch(String arg) {
                for (final Map.Entry<String, String> e : vars.entrySet())
                    arg = arg.replaceAll("\\$\\{" + e.getKey() + "}", Matcher.quoteReplacement(e.getValue()));
                if (arg.contains("${"))
                    System.out.println("Unknown var in the argument: " + arg);
                return arg;
            }

            void parse(final JsonList al, final ConcurrentLinkedQueue<String> list) {
                for (final JsonElement e : al)
                    if (e.isString())
                        list.add(patch(e.getAsString()));
                    else if (e.isDict()) {
                        final JsonDict d = e.getAsDict();
                        if (isAllow(d)) {
                            final JsonElement se = d.get("value");
                            if (se.isString())
                                list.add(patch(se.getAsString()));
                            else if (se.isList())
                                for (final JsonElement sse : se.getAsList())
                                    if (sse.isString())
                                        list.add(patch(sse.getAsString()));
                                    else
                                        System.out.println("I don't understand in the list of arguments: " + sse);
                            else
                                System.out.println("I don't understand value: " + se);
                        }
                    } else
                        System.out.println("I can't parse the argument: " + e);
            }

            {
                gameDir = (File) configuration.generalObjects.get("gameDir");
                final Object vm = configuration.generalObjects.get("variables");
                if (vm == null)
                    configuration.generalObjects.put("variables", vars = new ListMap<>());
                else
                    vars = (ListMap<String, String>) vm;
            }
        };
    }
}
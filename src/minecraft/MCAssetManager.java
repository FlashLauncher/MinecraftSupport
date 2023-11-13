package minecraft;

import Launcher.*;
import UIL.Theme;
import UIL.UI;
import UIL.base.IImage;
import Utils.Core;
import Utils.ListMap;
import Utils.json.JsonDict;
import Utils.json.JsonElement;
import Utils.web.WebClient;
import Utils.web.WebResponse;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.util.Map;

public class MCAssetManager extends Market {
    final WebClient client = new WebClient();
    public final MinecraftSupport plugin;

    public MCAssetManager(final MinecraftSupport plugin, final IImage icon) {
        super("minecraft-asset-manager", icon);
        this.plugin = plugin;
        client.allowRedirect = true;
        client.headers.put("User-Agent", "FlashLauncher/MinecraftSupport/indev (mcflashlauncher@gmail.com)");
    }

    private final class KA {
        final String id, dir;

        KA(final String id, final String dir) {
            this.id = id;
            this.dir = dir;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof KA)
                return ((KA) obj).dir.equals(dir) && ((KA) obj).id.equals(id);
            return false;
        }
    }

    private final ListMap<KA, TaskGroupAutoProgress> groups = new ListMap<>();

    public void install(final RunProc configuration, final JsonDict dict, final String id) throws Exception {
        final File
                gameDir = (File) configuration.generalObjects.get("gameDir"),
                homeDir = configuration.workDir;
        final KA k = new KA(id, gameDir.getCanonicalPath());
        synchronized (groups) {
            TaskGroupAutoProgress g = groups.get(k);
            if (g != null) {
                configuration.addTaskGroup(g);
                return;
            }
            groups.put(k, g = new TaskGroupAutoProgress(1));
            final TaskGroupAutoProgress g1 = g;

            final boolean hw1 = plugin.checkHashWeb.get(), hw2 = !hw1, hfs = plugin.checkHashFS.get();

            if (dict.getAsBool("map_to_resources", false)) {
                final File dir = new File(gameDir, "resources");
                if (!dir.exists())
                    dir.mkdirs();
                final File dir2 = new File(homeDir, "resources");
                ((ListMap<String, String>) configuration.generalObjects.get("variables")).put("game_assets", dir2.getAbsolutePath());
                boolean linked = false;
                if (!dir2.exists()) {
                    try {
                        Files.createSymbolicLink(dir2.toPath(), dir.toPath());
                        linked = true;
                    } catch (final UnsupportedOperationException ex) {
                        System.out.println("This OS doesn't support creating Sym links");
                    } catch (final FileSystemException ex) {
                        if (ex instanceof AccessDeniedException)
                            System.out.println("I don't have permissions to create Sym link");
                        ex.printStackTrace();
                    }
                    if (!linked)
                        try {
                            Files.createLink(dir2.toPath(), dir.toPath());
                        } catch (final UnsupportedOperationException ex) {
                            System.out.println("This OS doesn't support creating links");
                        } catch (final FileSystemException ex) {
                            if (ex instanceof AccessDeniedException)
                                System.out.println("I don't have permissions to create link");
                            else
                                ex.printStackTrace();
                        }
                } else if (!(linked = Files.isSymbolicLink(dir2.toPath())))
                    if (!dir2.exists())
                        dir2.mkdirs();
                final boolean isLinked = linked, isNotLinked = !isLinked;
                for (final Map.Entry<String, JsonElement> e : dict.getAsDict("objects").entrySet())
                    g.addTask(new Task() {
                        final JsonDict d = e.getValue().getAsDict();
                        final File f = new File(dir, e.getKey()), p = f.getParentFile(),
                                f2 = isNotLinked ? new File(dir2, e.getKey()) : null, p2 = isNotLinked ? f2.getParentFile() : null;
                        final String hash = d.getAsString("hash"), n = e.getKey();

                        @Override
                        public void run() throws Throwable {
                            if (!p.exists())
                                p.mkdirs();
                            if (!f.exists() || hfs && !Core.hashToHex("sha1", Files.readAllBytes(f.toPath())).equals(hash))
                                while (true) {
                                    final ByteArrayOutputStream os = new ByteArrayOutputStream();
                                    final WebResponse r = client.open("GET", URI.create("https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash), os, true);
                                    try {
                                        r.auto();
                                    } catch (final SocketTimeoutException | ConnectException ex) {
                                        ex.printStackTrace();
                                        continue;
                                    }
                                    if (r.getResponseCode() != 200)
                                        continue;
                                    final byte[] data = os.toByteArray();
                                    if (hw2 || Core.hashToHex("sha1", data).equals(hash)) {
                                        Files.write(f.toPath(), data);
                                        break;
                                    }
                                }
                            if (isLinked)
                                return;
                            if (!p2.exists())
                                p2.mkdirs();
                            if (!f2.exists() || hfs && !Core.hashToHex("sha1", Files.readAllBytes(f2.toPath())).equals(hash))
                                Files.copy(f.toPath(), f2.toPath());
                        }

                        @Override
                        public String toString() {
                            return "Downloading assets ... (" + g1.getProgress() + " / " + g1.getMaxProgress() + ") - " + n;
                        }
                    });
            } else if (dict.getAsBool("virtual", false)) {
                final File dir = new File(gameDir, "assets/virtual/" + id);
                if (!dir.exists())
                    dir.mkdirs();
                ((ListMap<String, String>) configuration.generalObjects.get("variables")).put("game_assets", dir.getAbsolutePath());
                for (final Map.Entry<String, JsonElement> e : dict.getAsDict("objects").entrySet())
                    g.addTask(new Task() {
                        final JsonDict d = e.getValue().getAsDict();
                        final File f = new File(dir, e.getKey()), p = f.getParentFile();
                        final String hash = d.getAsString("hash"), n = e.getKey();

                        @Override
                        public void run() throws Throwable {
                            if (!p.exists())
                                p.mkdirs();
                            if (!f.exists() || hfs && !Core.hashToHex("sha1", Files.readAllBytes(f.toPath())).equals(hash))
                                while (true) {
                                    final ByteArrayOutputStream os = new ByteArrayOutputStream();
                                    final WebResponse r = client.open("GET", URI.create("https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash), os, true);
                                    try {
                                        r.auto();
                                    } catch (final SocketTimeoutException | ConnectException ex) {
                                        ex.printStackTrace();
                                        continue;
                                    }
                                    if (r.getResponseCode() != 200)
                                        continue;
                                    final byte[] data = os.toByteArray();
                                    if (hw2 || Core.hashToHex("sha1", data).equals(hash)) {
                                        Files.write(f.toPath(), data);
                                        break;
                                    }
                                }
                        }

                        @Override
                        public String toString() {
                            return "Downloading assets ... (" + g1.getProgress() + " / " + g1.getMaxProgress() + ") - " + n;
                        }
                    });
            } else {
                final File dir = new File(gameDir, "assets/objects");
                if (!dir.exists())
                    dir.mkdirs();
                for (final Map.Entry<String, JsonElement> e : dict.getAsDict("objects").entrySet())
                    g.addTask(new Task() {
                        final JsonDict d = e.getValue().getAsDict();
                        final String hash = d.getAsString("hash"), h2 = hash.substring(0, 2), id = h2 + "/" + hash, n;
                        final File d2 = new File(dir, h2), f = new File(d2, hash);

                        @Override
                        public void run() throws Throwable {
                            try {
                                synchronized (client) {
                                    if (!d2.exists())
                                        d2.mkdirs();
                                    if (!f.exists() || hfs && !Core.hashToHex("sha1", Files.readAllBytes(f.toPath())).equals(hash))
                                        while (true) {
                                            final ByteArrayOutputStream os = new ByteArrayOutputStream();
                                            final WebResponse r = client.open("GET", URI.create("https://resources.download.minecraft.net/" + id), os, true);
                                            try {
                                                r.auto();
                                            } catch (final SocketTimeoutException | ConnectException ex) {
                                                ex.printStackTrace();
                                                continue;
                                            }
                                            if (r.getResponseCode() != 200)
                                                continue;
                                            final byte[] data = os.toByteArray();
                                            if (hw2 || Core.hashToHex("sha1", data).equals(hash)) {
                                                Files.write(f.toPath(), data);
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
                            return "Downloading assets ... (" + g1.getProgress() + " / " + g1.getMaxProgress() + ") - " + n;
                        }

                        {
                            final int w = configuration.getStatusWidth() - Math.round(UI.stringWidth(Theme.FONT, "Downloading assets ... (" + g1.getMaxProgress() + " / " + g1.getMaxProgress() + ") - .../"));
                            boolean sub = false;
                            String na = e.getKey();
                            while (UI.stringWidth(Theme.FONT, na) > w) {
                                final int i = na.indexOf('/') + 1;
                                if (i <= 0)
                                    break;
                                na = na.substring(i);
                                sub = true;
                            }
                            n = (sub ? ".../" : "") + na;
                        }
                    });
            }

            g.addTask(new Task() {
                @Override
                public void run() {
                    synchronized (groups) {
                        groups.remove(k);
                    }
                }

                @Override public String toString() { return "Finishing ..."; }
            });

            configuration.addTaskGroup(g);
        }
    }

    @Override
    public void checkForUpdates(Meta... items) {

    }

    @Override
    public Meta[] find(String query) {
        return new Meta[0];
    }
}
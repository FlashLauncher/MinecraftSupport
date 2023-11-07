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

    private final ListMap<File, TaskGroupAutoProgress> groups = new ListMap<>();

    public void install(final RunProc configuration, final JsonDict dict, final File gameDir) throws Exception {
        synchronized (groups) {
            TaskGroupAutoProgress g = groups.get(gameDir);
            if (g != null) {
                configuration.addTaskGroup(g);
                return;
            }
            groups.put(gameDir, g = new TaskGroupAutoProgress(1));
            final TaskGroupAutoProgress g1 = g;

            final boolean hw1 = plugin.checkHashWeb.get(), hw2 = !hw1, hfs = plugin.checkHashFS.get();

            if (dict.getAsBool("map_to_resources", false)) {
                System.out.println("RESOURCES");
            } else if (dict.getAsBool("virtual", false)) {
                System.out.println("VIRTUAL");
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
                        groups.remove(g1);
                    }
                }

                @Override
                public String toString() {
                    return "Finishing ...";
                }
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
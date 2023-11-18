package minecraft;

import Launcher.*;
import UIL.Lang;
import UIL.base.IImage;
import Utils.json.Json;
import Utils.json.JsonDict;
import Utils.json.JsonElement;
import Utils.web.WebClient;
import Utils.web.WebResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

public class MinecraftMarket extends Market {
    public static final WebClient client = new WebClient() {{
        allowRedirect = true;
        headers.put("User-Agent", "illa4257/MinecraftSupport/indev (mcflashlauncher@gmail.com)");
    }};
    public final PluginContext context;
    public final MinecraftSupport plugin;

    private boolean success = false;
    private TaskGroup group = new TaskGroupAutoProgress();
    public final MinecraftList all, versions;

    public final ConcurrentHashMap<String, MinecraftList> types = new ConcurrentHashMap<>();

    public MinecraftMarket(final PluginContext context, final String id, final IImage icon) {
        super(id, icon);
        this.context = context;
        plugin = (MinecraftSupport) context.getPlugin();
        all = ((MinecraftSupport) context.getPlugin()).all;
        versions = new MinecraftList(Lang.get("minecraft.groups.vanilla"), context.getIcon());
        final MinecraftList sl = new MinecraftList(Lang.get("minecraft.groups.vanilla-snapshot"), context.getIcon());
        types.put("snapshot", sl);
        plugin.addList(versions);
    }

    @Override
    public void checkForUpdates(final Meta... items) {
        group.addTask(new Task() {
            @Override
            public void run() {
                try {
                    final ByteArrayOutputStream os = new ByteArrayOutputStream();
                    final WebResponse r = client.open("GET", new URI("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"), os, true);
                    r.auto();
                    if (r.getResponseCode() != WebResponse.OK)
                        return;
                    final JsonDict d = Json.parse(new ByteArrayInputStream(os.toByteArray()), true, StandardCharsets.UTF_8).getAsDict();
                    for (final JsonElement e : d.getAsList("versions")) {
                        final JsonDict i = e.getAsDict();
                        if (i.getAsInt("complianceLevel") > MinecraftSupport.COMPLIANCE_LEVEL)
                            continue;
                        final URL url;
                        try {
                            url = URI.create(i.getAsString("url")).toURL();
                        } catch (final MalformedURLException|IllegalArgumentException|NullPointerException ex) {
                            ex.printStackTrace();
                            continue;
                        }
                        final String type = i.getAsStringOrDefault("type", "");
                        final WebVersion ver = new WebVersion(plugin, client, i.getAsString("id"), i.getAsString("sha1"), url,
                                type.isEmpty() ? null : Arrays.asList(type));
                        all.add(ver);
                        versions.add(ver);
                        if (type.isEmpty())
                            continue;
                        MinecraftList l = types.get(type);
                        if (l == null) {
                            l = new MinecraftList(Lang.get("minecraft.groups.vanilla-" + type), context.getIcon());
                            types.put(type, l);
                        }
                        l.add(ver);
                    }
                    plugin.addList(types.values());
                    success = true;
                    group = null;
                } catch (final Throwable ex) {
                    ex.printStackTrace();
                }
            }
        });
        context.addTaskGroup(group);
    }

    public boolean isSuccess() { return success; }

    public void waitFinish() throws InterruptedException {
        final TaskGroup g = group;
        if (g != null)
            g.waitFinish();
    }

    @Override public Meta[] find(String query) { return new Meta[0]; }
}

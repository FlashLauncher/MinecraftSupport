package minecraft;

import Launcher.*;
import Launcher.base.IAccount;
import Launcher.base.IMakerContext;
import Launcher.base.IMaker;
import Launcher.base.IProfile;
import UIL.Lang;
import UIL.LangItem;
import UIL.base.IImage;
import Utils.*;
import Utils.json.Json;
import Utils.json.JsonDict;
import Utils.json.JsonElement;
import Utils.json.JsonList;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;


public class MinecraftSupport extends Plugin {
    public static final String ID = "minecraft-support";

    public static final int COMPLIANCE_LEVEL = 1;

    private static final String PLATFORM = Core.IS_WINDOWS ? "-windows" : Core.IS_LINUX ? "-linux" : Core.IS_MACOS ? "-osx" : "";

    public File gameDir = null, homeDir = null;

    public final boolean buttonSmooth;

    public final JavaSupport javaSupport;

    public final MinecraftMarket market;
    public final MCAssetManager assetManager;
    public final LangItem
                PROFILE_MAKER = Lang.get(ID + ".maker.profile"),
                OFFLINE_ACCOUNT_MAKER = Lang.get(ID + ".maker.offline-account")
    ;

    final MinecraftList lv = new MinecraftList(Lang.get("minecraft.groups.name"), getIcon());

    public final AtomicBoolean
            checkHashWeb = new AtomicBoolean(true),
            checkHashFS = new AtomicBoolean(false);

    public final MinecraftList all, installed;

    final File cfgFile = new File(getPluginData(), "config.json");
    final JsonDict cfg;

    File profilesFile;
    JsonDict profiles;

    final void loadConfig() {
        try {
            JsonElement s = cfg.get("gameDir");
            gameDir = s == null || !s.isString() ? FLCore.LAUNCHER_DIR : FS.resolve(FLCore.LAUNCHER_DIR, s.getAsString());
            s = cfg.get("homeDir");
            homeDir = s == null || !s.isString() ? new File(gameDir, "home") : FS.resolve(FLCore.LAUNCHER_DIR, s.getAsString());

            final JsonElement eal = cfg.get("accounts");
            if (eal != null && eal.isList()) {
                for (final JsonElement e : eal.getAsList()) {
                    if (!e.isDict())
                        continue;
                    final JsonDict d = e.getAsDict();
                    switch (d.getAsStringOrDefault("type", null)) {
                        case "offline":
                            final MCOfflineAccount oa = new MCOfflineAccount(this);
                            oa.name = d.getAsStringOrDefault("name", null);
                            if (oa.name == null)
                                oa.name = Core.random(1, Core.CHARS_EN_LOW + Core.CHARS_EN_UP) + Core.random(7);
                            oa.accessToken = d.getAsStringOrDefault("accessToken", null);
                            if (oa.accessToken == null)
                                oa.accessToken = Core.random(64);
                            try {
                                oa.uuid = UUID.fromString(d.getAsString("uuid"));
                            } catch (final IllegalArgumentException|NullPointerException ignored) {
                                oa.uuid = UUID.randomUUID();
                            }
                            addAccount(oa);
                            break;
                    }
                }
                eal.getAsList().clear();
            }
        } catch (final Exception ex) {
            ex.printStackTrace();
        }
    }

    final void saveConfig() {
        try {
            final ConcurrentLinkedQueue<IAccount> l = getAccounts();
            synchronized (l) {
                cfg.put("gameDir", FS.relative(Core.getPath(FLCore.class), gameDir));
                cfg.put("homeDir", FS.relative(Core.getPath(FLCore.class), homeDir));
                JsonElement eal = cfg.get("accounts");
                if (eal == null || !eal.isList()) {
                    eal = new JsonList();
                    cfg.put("accounts", eal);
                }
                final JsonList al = eal.getAsList();
                al.clear();
                for (final IAccount a : l)
                    if (a instanceof MCOfflineAccount)
                        al.add(new JsonDict() {{
                            final MCOfflineAccount oa = (MCOfflineAccount) a;
                            put("type", "offline");
                            put("name", oa.name);
                            put("uuid", oa.uuid);
                            put("accessToken", oa.accessToken);
                        }});

                final File p = cfgFile.getParentFile();
                if (!p.exists())
                    p.mkdirs();
                Files.write(cfgFile.toPath(), cfg.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (final Exception ex) {
            ex.printStackTrace();
        }
    }

    final void scanDir() {
        try {
            final File[] l = new File(gameDir, "versions").listFiles();
            if (l != null)
                for (final File f : l) {
                    if (!f.isDirectory())
                        continue;
                    final File m = new File(f, f.getName() + ".json");
                    if (!m.exists() || !m.isFile())
                        continue;
                    final MCMetaVersion meta = new MCMetaVersion(this, f.getName(), m);
                    installed.add(meta);
                    all.add(meta);
                }
        } catch (final Exception ex) {
            ex.printStackTrace();
        }
        profilesFile = new File(gameDir, "launcher_profiles.json");
        try {
            if (profilesFile.exists() && profilesFile.isFile()) {
                final JsonElement ed = Json.parse(profilesFile, "UTF-8");
                profiles = ed.isDict() ? ed.getAsDict() : new JsonDict();
                final JsonElement epl = profiles.get("profiles");
                if (epl == null || !epl.isDict())
                    profiles.put("profiles", new JsonDict());
                else {
                    final JsonDict pl = epl.getAsDict();
                    for (final Map.Entry<String, JsonElement> i : pl.entrySet())
                        if (i.getValue().isDict()) {
                            final JsonDict d = i.getValue().getAsDict();
                            final MinecraftProfile p = new MinecraftProfile(getContext());
                            p.name = d.getAsStringOrDefault("name", i.getKey());
                            p.version = d.getAsStringOrDefault("lastVersionId", null);
                            p.javaArgs = d.getAsStringOrDefault("javaArgs" + PLATFORM, "");
                            p.gameArgs = d.getAsStringOrDefault("gameArgs" + PLATFORM, "");
                            String jp = d.getAsStringOrDefault("javaDir" + PLATFORM, null);
                            if (jp == null || jp.isEmpty())
                                for (final Java java : javaSupport.javaList) {
                                    p.java = java;
                                    break;
                                }
                            else {
                                final File j = FS.resolve(FLCore.LAUNCHER_DIR, jp);
                                for (final Java java : javaSupport.javaList)
                                    if (java.file.equals(j)) {
                                        p.java = java;
                                        break;
                                    }
                                if (p.java == null) {
                                    p.java = new Java(j);
                                    javaSupport.javaList.add(p.java);
                                }
                            }
                            addProfile(p);
                        }
                }
            } else {
                profiles = new JsonDict();
                profiles.put("profiles", new JsonDict());
            }
        } catch (final Exception ex) {
            ex.printStackTrace();
        }
    }

    final void saveProfiles() {
        try {
            final ConcurrentLinkedQueue<IProfile> l = getProfiles();
            synchronized (l) {
                if (!gameDir.exists())
                    gameDir.mkdirs();
                final JsonDict pm = profiles.getAsDict("profiles");
                pm.clear();
                for (final IProfile p : l)
                    if (p instanceof MinecraftProfile) {
                        final JsonDict d = new JsonDict();
                        d.put("name", p.toString());
                        final String ver = ((MinecraftProfile) p).version, jvmArgs = ((MinecraftProfile) p).javaArgs;
                        if (ver != null)
                            d.put("lastVersionId", ver);
                        if (jvmArgs != null)
                            d.put("javaArgs" + PLATFORM, jvmArgs);
                        final Java j = ((MinecraftProfile) p).java;
                        if (j != null)
                            d.put("javaDir" + PLATFORM, ((MinecraftProfile) p).java.file.getAbsolutePath());
                        pm.put(p.toString(), d);
                    }
                Files.write(profilesFile.toPath(), profiles.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (final Exception ex) {
            ex.printStackTrace();
        }
    }

    public MinecraftSupport(final PluginContext context) {
        super(context);

        {
            boolean s1 = false;
            try {
                s1 = Class.forName("UIL.base.IButton").getMethod("smooth", boolean.class) != null;
            } catch (final Exception ignored) {}
            buttonSmooth = s1;
        }

        javaSupport = (JavaSupport) getContext(JavaSupport.ID).getPlugin();

        JsonDict d = null;
        try {
            if (cfgFile.exists() && cfgFile.isFile())
                d = Json.parse(cfgFile, "UTF-8").getAsDict();
        } catch (final Exception ex) {
            ex.printStackTrace();
        }
        if (d == null)
            d = new JsonDict();
        cfg = d;

        loadConfig();

        all = new MinecraftList(Lang.get("minecraft.groups.all"), context.getIcon());
        installed = new MinecraftList(Lang.get("markets.installed.name"), MCOfflineAccount.ICON_OFFLINE);
        addList(all, installed);

        addProfileMaker(new IMaker<IProfile>() {
            @Override public String toString() { return PROFILE_MAKER.toString(); }
            @Override public IImage getIcon() { return context.getIcon(); }

            @Override
            public void make(final IMakerContext<IProfile> maker) {
                maker.end(addProfile(new MinecraftProfile(getContext()) {{
                    name = Core.random(1, Core.CHARS_EN_LOW + Core.CHARS_EN_UP) + Core.random(7);
                }}));
            }
        });

        addAccountMaker(new IMaker<IAccount>() {
            @Override public String toString() { return OFFLINE_ACCOUNT_MAKER.toString(); }
            @Override public IImage getIcon() { return MCOfflineAccount.ICON_OFFLINE; }

            @Override
            public void make(final IMakerContext<IAccount> maker) {
                maker.end(addAccount(new MCOfflineAccount(MinecraftSupport.this) {{
                    name = Core.random(1, Core.CHARS_EN_LOW + Core.CHARS_EN_UP) + Core.random(7);
                    uuid = UUID.randomUUID();
                    accessToken = Core.random(64);
                }}));
            }
        });

        scanDir();

        /*addAccountMaker(new IMaker<IAccount>() {
            @Override
            public String toString() {
                return "Mojang";
            }

            @Override
            public IImage getIcon() {
                return context.getIcon();
            }

            @Override
            public void make(final IMakerContext<IAccount> maker) {
                try {
                    final String token = "";
                } catch (final Throwable ex) {
                    ex.printStackTrace();
                }
                maker.end(null);
            }
        });*/

        addMarket(assetManager = new MCAssetManager(this, context.getIcon()));
        addMarket(market = new MinecraftMarket(context, "minecraft-support.market", context.getIcon()));
    }

    public void addList(final Collection<MinecraftList> lists) { lv.addAll(lists); }
    public void addList(final MinecraftList... lists) { lv.addAll(Arrays.asList(lists)); }
    public void removeList(final Collection<? extends IMinecraftVersion> lists) { lv.removeAll(lists); }
    public void removeList(final IMinecraftVersion... lists) { lv.removeAll(Arrays.asList(lists)); }
    public IMinecraftVersion getVersion(final String id) { return lv.get(id); }
}

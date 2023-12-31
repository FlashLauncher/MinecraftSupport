package minecraft;

import Launcher.*;
import Launcher.base.*;
import UIL.*;
import UIL.base.IImage;
import UIL.base.IText;
import UIL.base.ITextField;
import Utils.*;
import Utils.json.Json;
import Utils.json.JsonDict;
import Utils.json.JsonElement;
import Utils.json.JsonList;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
                OFFLINE_ACCOUNT_MAKER = Lang.get(ID + ".maker.offline-account"),
                GAME_DIR = Lang.get(ID + ".game-dir.name"),
                HOME_DIR = Lang.get(ID + ".home-dir.name"),
                GAME_DIR_DESC = Lang.get(ID + ".game-dir.desc"),
                HOME_DIR_DESC = Lang.get(ID + ".home-dir.desc")
    ;

    final MinecraftList lv = new MinecraftList(Lang.get("minecraft.groups.name"), getIcon());

    final Object cl = new Object(), pn = new Object();
    final ArrayList<MinecraftContent> cll = new ArrayList<>();

    public final AtomicBoolean
            checkHashWeb = new AtomicBoolean(true),
            checkHashFS = new AtomicBoolean(false);

    public final MinecraftList all, installed;

    final ConcurrentLinkedQueue<Runnable1a<MCProfileScanner>> listeners = new ConcurrentLinkedQueue<>();
    final ConcurrentLinkedQueue<RRunnable1a<LaunchListener, MCLaunch>> launchListeners = new ConcurrentLinkedQueue<>();

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
                    installed.add(new MCMetaVersion(this, f.getName(), m));
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
                            d.remove("name");

                            p.version = d.getAsStringOrDefault("lastVersionId", null);
                            d.remove("lastVersionId");

                            p.javaArgs = d.getAsStringOrDefault("javaArgs" + PLATFORM, "");
                            d.remove("javaArgs");

                            p.gameArgs = d.getAsStringOrDefault("gameArgs" + PLATFORM, "");
                            d.remove("gameArgs");

                            final String
                                    gd = d.getAsStringOrDefault("gameDir", null),
                                    jp = d.getAsStringOrDefault("javaDir" + PLATFORM, null);
                            d.remove("gameDir");
                            d.remove("javaDir");

                            if (gd != null && !gd.isEmpty())
                                p.homeDir = FS.resolve(gameDir, gd);

                            if (jp == null || jp.isEmpty())
                                for (final Java java : javaSupport.javaList) {
                                    p.java = java;
                                    break;
                                }
                            else {
                                final File j = FS.resolve(gameDir, jp);
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

                            p.data = d;

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
                        final MinecraftProfile mp = (MinecraftProfile) p;
                        if (mp.data == null) {
                            System.out.println("[MinecraftSupport] MinecraftProfile data is null!");
                            mp.data = new JsonDict();
                        }
                        final JsonDict d = mp.data;

                        d.put("name", mp.name);
                        final String ver = mp.version, jvmArgs = mp.javaArgs, gameArgs = mp.gameArgs;

                        if (ver != null)
                            d.put("lastVersionId", ver);
                        else
                            d.remove("lastVersionId");

                        if (jvmArgs != null)
                            d.put("javaArgs" + PLATFORM, jvmArgs);
                        else
                            d.remove("javaArgs" + PLATFORM);

                        if (gameArgs != null)
                            d.put("gameArgs" + PLATFORM, gameArgs);
                        else
                            d.remove("gameArgs" + PLATFORM);

                        final File home = mp.homeDir;

                        if (home != null)
                            d.put("gameDir", FS.relative(homeDir, home));
                        else
                            d.remove("gameDir");

                        final Java j = mp.java;

                        if (j != null)
                            d.put("javaDir" + PLATFORM, FS.relative(gameDir, mp.java.file.getAbsoluteFile()));
                        else
                            d.remove("javaDir" + PLATFORM);
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

        installed = new MinecraftList(Lang.get("markets.installed.name"), MCOfflineAccount.ICON_OFFLINE);
        addList(all = new MinecraftList(Lang.get("minecraft.groups.all"), context.getIcon()));

        addProfileMaker(new IMaker<IProfile>() {
            @Override public String toString() { return PROFILE_MAKER.toString(); }
            @Override public IImage getIcon() { return context.getIcon(); }

            @Override
            public void make(final IMakerContext<IProfile> maker) {
                maker.end(addProfile(new MinecraftProfile(getContext()) {{
                    data = new JsonDict();
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

        scanDir();

        addSettingsItem(new FLMenuItemListener(ID + ".settings", getIcon(), "Minecraft") {
            @Override
            public void onOpen(final FLMenuItemEvent event) {
                final ITextField
                        gpf = UI.textField().size(event.width() - 16, 32).pos(8, 34),
                        hpf = UI.textField().size(event.width() - 16, 32).pos(8, 126);
                final IText
                        gpt = UI.text().size(event.width() - 32, 18).pos(16, 74).ha(HAlign.LEFT).foreground(Theme.AUTHOR_FOREGROUND_COLOR),
                        hpt = UI.text().size(event.width() - 32, 18).pos(16, 166).ha(HAlign.LEFT).foreground(Theme.AUTHOR_FOREGROUND_COLOR);
                event.add(gpf, hpf, gpt, hpt,
                        UI.text(GAME_DIR).size(event.width() - 16, 18).pos(8, 8),
                        UI.button("...").size(32, 32).pos(event.width() - 40, 34).onAction((s, e) -> {
                            final FLFSChooser c = new FLFSChooser(event.launcher, "Game Dir Selector");
                            c.setPath(gameDir);
                            if (c.start()) {
                                if (c.getSelected().length == 0)
                                    return;
                                final File f = c.getSelected()[0];
                                saveProfiles();
                                if (homeDir.equals(new File(gameDir, "home")))
                                    homeDir = new File(f, "home");
                                gameDir = f;
                                installed.clear();
                                for (final IProfile p : getProfiles())
                                    removeProfile(p);
                                saveConfig();
                                scanDir();
                                synchronized (pn) { pn.notifyAll(); }
                            }
                        }).background(UI.TRANSPARENT),
                        UI.text(HOME_DIR).size(event.width() - 16, 18).pos(8, 100),
                        UI.button("...").size(32, 32).pos(event.width() - 40, 126).onAction((s, e) -> {
                            final FLFSChooser c = new FLFSChooser(event.launcher, "Home Dir Selector");
                            c.setPath(gameDir);
                            if (c.start()) {
                                if (c.getSelected().length == 0)
                                    return;
                                saveProfiles();
                                homeDir = c.getSelected()[0];
                                installed.clear();
                                for (final IProfile p : getProfiles())
                                    removeProfile(p);
                                saveConfig();
                                scanDir();
                                synchronized (pn) { pn.notifyAll(); }
                            }
                        }).background(UI.TRANSPARENT)
                );
                final Runnable r = Core.onNotifyLoop(pn, () -> {
                    gpf.text(gameDir).update();
                    hpf.text(homeDir).update();
                    gpt.text(FS.relative(FLCore.LAUNCHER_DIR, gameDir)).update();
                    hpt.text(FS.relative(FLCore.LAUNCHER_DIR, homeDir)).update();
                });
                event.onClose(() -> Core.offNotifyLoop(r));
            }
        });

        addHelpItem(new FLMenuItemListener(ID + ".support", getIcon(), "Minecraft") {
            @Override
            public void onOpen(final FLMenuItemEvent event) {
                event.add(
                        UI.text(GAME_DIR).size(event.width() - 16, 18).pos(8, 8).ha(HAlign.LEFT),
                        UI.text(GAME_DIR_DESC).size(event.width() - 32, 18).pos(16, 34).ha(HAlign.LEFT).foreground(Theme.AUTHOR_FOREGROUND_COLOR),
                        UI.text(HOME_DIR).size(event.width() - 16, 18).pos(8, 68).ha(HAlign.LEFT),
                        UI.text(HOME_DIR_DESC).size(event.width() - 32, 54).pos(16, 94).ha(HAlign.LEFT).foreground(Theme.AUTHOR_FOREGROUND_COLOR)
                );
            }
        });
    }

    public boolean addScanListener(final Runnable1a<MCProfileScanner> listener) { return listeners.add(listener); }
    public boolean removeScanListener(final Runnable1a<MCProfileScanner> listener) { return listeners.remove(listener); }

    public boolean addLaunchListener(final RRunnable1a<LaunchListener, MCLaunch> listener) { return launchListeners.add(listener); }
    public boolean removeLaunchListener(final RRunnable1a<LaunchListener, MCLaunch> listener) { return launchListeners.remove(listener); }

    public void addList(final Collection<MinecraftList> lists) { lv.addAll(lists); }
    public void addList(final MinecraftList... lists) { lv.addAll(Arrays.asList(lists)); }
    public void removeList(final Collection<? extends IMinecraftVersion> lists) { lv.removeAll(lists); }
    public void removeList(final IMinecraftVersion... lists) { lv.removeAll(Arrays.asList(lists)); }
    public IMinecraftVersion getVersion(final String id) {
        final IMinecraftVersion ver = lv.get(id);
        return ver == null ? installed.get(id) : ver;
    }

    public void addContent(final MinecraftContent content) {
        synchronized (cl) {
            cll.add(content);
            cl.notifyAll();
        }
    }

    public void removeContent(final MinecraftContent content) {
        synchronized (cl) {
            cll.remove(content);
            cl.notifyAll();
        }
    }
}
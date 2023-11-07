package minecraft;

import Launcher.*;
import Launcher.base.IAccount;
import Launcher.base.IMakerContext;
import Launcher.base.IMaker;
import Launcher.base.IProfile;
import UIL.Lang;
import UIL.LangItem;
import UIL.base.IImage;
import Utils.Core;
import Utils.json.Json;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;


public class MinecraftSupport extends Plugin {
    public static final String ID = "minecraft-support";
    public static final int COMPLIANCE_LEVEL = 1;

    public File gameDir = null, homeDir = null;

    public final MinecraftMarket market;
    public final MCAssetManager assetManager;
    public final LangItem
                PROFILE_MAKER = Lang.get(ID + ".maker.profile"),
                OFFLINE_ACCOUNT_MAKER = Lang.get(ID + ".maker.offline-account")
    ;

    final ArrayList<MinecraftList> lv = new ArrayList<>();

    public final AtomicBoolean
            checkHashWeb = new AtomicBoolean(true),
            checkHashFS = new AtomicBoolean(false);

    public final MinecraftList all, installed;

    final void scan(final File folder) {
        final File[] l = folder.listFiles();
        if (l == null)
            return;
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
    }

    public MinecraftSupport(final PluginContext context) {
        super(context);

        gameDir = Core.getPath(FLCore.class);
        homeDir = new File(Core.getPath(FLCore.class), "home");

        all = new MinecraftList(Lang.get("minecraft.groups.all"), context.getIcon());
        installed = new MinecraftList(Lang.get("markets.installed.name"), MCOfflineAccount.ICON_OFFLINE);
        addList(all, installed);

        scan(new File(gameDir, "versions"));

        addMarket(assetManager = new MCAssetManager(this, context.getIcon()));
        addMarket(market = new MinecraftMarket(context, "minecraft-support.market", context.getIcon()));
        market.checkForUpdates();
        //((JavaSupport) getConnectedContext("java-support").getPlugin());
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
                maker.end(addAccount(new MCOfflineAccount(context) {{
                    name = Core.random(1, Core.CHARS_EN_LOW + Core.CHARS_EN_UP) + Core.random(7);
                }}));
            }
        });
        addAccount(new MCOfflineAccount(context) {{
            name = Core.random(1, Core.CHARS_EN_LOW + Core.CHARS_EN_UP) + Core.random(7);
        }});
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
        try {
            if (new File(context.getPluginData(), "config.json").exists())
                System.out.println(Json.parse(Files.newInputStream(new File(context.getPluginData(), "config.json").toPath()), true, StandardCharsets.UTF_8));
        } catch (final IOException ex) {
            ex.printStackTrace();
        }
    }

    public void addList(final Collection<MinecraftList> lists) {
        synchronized (lv) {
            lv.addAll(lists);
            lv.notifyAll();
        }
    }

    public void addList(final MinecraftList... lists) {
        synchronized (lv) {
            Collections.addAll(lv, lists);
            lv.notifyAll();
        }
    }

    public void removeList(final MinecraftList... lists) {
        synchronized (lv) {
            for (final MinecraftList l : lists)
                lv.remove(l);
            lv.notifyAll();
        }
    }
}

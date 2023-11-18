package minecraft;

import Launcher.*;
import Launcher.base.IEditorContext;
import Launcher.base.LaunchListener;
import UIL.*;
import UIL.base.*;
import Utils.Core;
import Utils.Java;
import Utils.JavaSupport;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MinecraftProfile implements IMinecraftProfile {
    public final PluginContext context;
    public final MinecraftSupport plugin;
    private final ConcurrentLinkedQueue<Java> javaList;

    public MinecraftProfile(final PluginContext context) {
        this.context = context;
        plugin = (MinecraftSupport) context.getPlugin();
        javaList = ((JavaSupport) MinecraftProfile.this.context.getPlugin(JavaSupport.ID)).javaList;
    }

    public String name;
    public Java java = null;
    public String version = null;

    @Override public String toString() { return name; }
    @Override public IImage getIcon() { return context.getIcon(); }

    @Override
    public void open(final IEditorContext context) {
        final int cw = context.width() - 16, cw2 = (cw - 8) / 2;
        context.add(
                UI.text("Profile name:").size(cw, 18).pos(8, 8),
                UI.textField(name).size(cw, 32).pos(8, 34).onInput(new ITextField.InputListener() {
                    @Override
                    public boolean typed(ITextField self, char ch) {
                        name = self.text();
                        return true;
                    }
                }),

                UI.text("Java:").size(cw2, 18).pos(8, 74),
                UI.comboBox().text(java).image(java).size(cw2, 32).pos(8, 100).imageOffset(4).onList(le -> {
                    final IComboBox self = le.getSelf();
                    final IContainer container = le.getContainer();
                    final ContainerListBuilder clb = new ContainerListBuilder(UI.scrollPane().content(UI.panel().borderRadius(UI.ZERO)), 150, 8)
                            .size(context.width(), context.height() - 48).pos(0, 48);
                    container.size(context.width(), context.height()).add(UI.button("<")
                            .size(32, 32).pos(8, 8).onAction((s, e) -> self.focus()), clb);
                    final Runnable r = Core.onNotifyLoop(javaList, () -> {
                        clb.clear();
                        for (final Java j : javaList)
                            clb.add(UI.button(j, j.icon).imageAlign(ImgAlign.TOP).imageOffset(20).onAction((s, e) -> {
                                le.close();
                                self.text(java = j).image(java.icon).focus();
                            }));
                        clb.update();
                    });
                    self.onCloseList(le1 -> {
                        Core.offNotifyLoop(r);
                        return true;
                    });

                    return context.getContainer();
                }),

                UI.text("Game version:").size(cw2, 18).pos(cw2 + 16, 74),
                UI.comboBox().text(version).size(cw2, 32).pos(cw2 + 16, 100).imageOffset(4).onList(le -> {
                    final IContainer container = le.getContainer().size(context.width(), context.height());
                    final MinecraftList lv = plugin.lv;

                    new Object() {
                        final ContainerListBuilder clb = new ContainerListBuilder(UI.scrollPane().content(UI.panel().borderRadius(UI.ZERO)), 150, 8)
                                .size(context.width(), context.height() - 48).pos(0, 48);

                        Runnable r = null;

                        void list(final MinecraftList list, final IButton back) {
                            UI.run(() -> {
                                final Runnable old = r;
                                final IButton b = UI.button("<").size(32, 32).pos(8, 8).onAction((s, e) -> {
                                    if (back == null) {
                                        le.close();
                                        return;
                                    }
                                    back.update();
                                    Core.offNotifyLoop(r);
                                    UI.run(() -> {
                                        clb.focus();
                                        container.remove(s).add(back);
                                        r = Core.onNotifyLoop(list, old);
                                    });
                                });
                                container.add(b);
                                b.update().focus();
                                if (back != null)
                                    container.remove(back);
                                Core.offNotifyLoop(r);
                                r = Core.onNotifyLoop(list, () -> {
                                    clb.focus().clear();
                                    for (final IMinecraftVersion ver : list) {
                                        if (ver instanceof MinecraftList) {
                                            final MinecraftList l = (MinecraftList) ver;
                                            clb.add(UI.button().text(l).image(l.icon).imageAlign(ImgAlign.TOP).imageOffset(20).smooth(l.smooth).onAction((s, e) -> list(l, b)));
                                            continue;
                                        }
                                        clb.add(UI.button().text(ver).onAction((s, e) -> {
                                            version = ver.getID();
                                            le.close();
                                        }));
                                    }
                                    clb.update();
                                });
                            });
                        }

                        {
                            container.add(clb);
                            le.getSelf().onCloseList(e -> {
                                Core.offNotifyLoop(r);
                                return true;
                            });
                            list(lv, null);
                        }
                    };

                    return context.getContainer();
                }),

                UI.button(LANG_REMOVE).size(cw, 32).pos(8, 140).onAction((s, e) -> {
                    MinecraftProfile.this.context.removeProfile(this);
                    context.close();
                })
        );
        context.onClose(plugin::saveProfiles);
    }

    @Override
    public LaunchListener init(final RunProc configuration) {
        final File h = new File(plugin.homeDir, name);
        configuration.workDir = h;
        configuration.generalObjects.put("gameDir", plugin.gameDir);

        final Java j = java;
        final IMinecraftVersion ver = plugin.getVersion(version);
        if (j == null || ver == null)
            return null;

        if (!h.exists())
            h.mkdirs();

        configuration.beginArgs.add(j.file.getAbsolutePath());

        return new LaunchListener() {
            private final LaunchListener ll;

            @Override
            public void preLaunch() {
                if (ll != null)
                    ll.preLaunch();
            }

            @Override
            public void launch() {
                if (ll != null)
                    ll.launch();
            }

            {
                LaunchListener r = null;
                try {
                    r = ver.init(configuration);
                } catch (final Throwable ex) {
                    ex.printStackTrace();
                }
                ll = r;
            }
        };
    }
}

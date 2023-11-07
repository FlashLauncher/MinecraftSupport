package minecraft;

import Launcher.*;
import Launcher.base.IEditorContext;
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

    public MinecraftProfile(final PluginContext context) { this.context = context; plugin = (MinecraftSupport) context.getPlugin(); }

    public String name;
    public Java java = null;
    public IMinecraftVersion version = null;

    @Override public String toString() { return name; }
    @Override public IImage getIcon() { return context.getIcon(); }

    @Override
    public void open(final IEditorContext context) {
        final IContainer c = context.getContainer();
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
                    final ConcurrentLinkedQueue<Java> l = ((JavaSupport) MinecraftProfile.this.context.getPlugin(JavaSupport.ID)).javaList;
                    final Runnable r = Core.onNotifyLoop(l, () -> {
                        clb.clear();
                        for (final Java j : l)
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
                UI.comboBox().size(cw2, 32).pos(cw2 + 16, 100).imageOffset(4).onList(le -> {
                    final IContainer container = le.getContainer().size(context.width(), context.height());
                    final ArrayList<MinecraftList> lv = ((MinecraftSupport) MinecraftProfile.this.context.getPlugin(MinecraftSupport.ID)).lv;

                    new Object() {
                        final IButton
                                b1 = UI.button("<").size(32, 32).pos(8, 8).onAction((s, e) -> le.close()),
                                b2 = UI.button("<").size(32, 32).pos(8, 8).onAction((s, e) -> main())
                        ;

                        final ContainerListBuilder
                                clb1 = new ContainerListBuilder(UI.scrollPane().content(UI.panel().borderRadius(UI.ZERO)), 150, 8).size(context.width(), context.height() - 48).pos(0, 48),
                                clb2 = new ContainerListBuilder(UI.scrollPane().content(UI.panel().borderRadius(UI.ZERO)), 150, 8).size(context.width(), context.height() - 48).pos(0, 48)
                        ;

                        Runnable r = null;

                        void list(final MinecraftList l) {
                            UI.run(() -> container.remove(b1).remove(clb1).add(b2, clb2.focus().update()));
                            Core.offNotifyLoop(r);
                            r = Core.onNotifyLoop(l, () -> {
                                clb2.clear();
                                for (final IMinecraftVersion ver : l) {
                                    clb2.add(UI.button(ver, null).onAction((s, e) -> {
                                        le.getSelf().text(version = ver).focus();
                                        le.close();
                                    }));
                                }
                                clb2.update();
                            });
                        }

                        void main() {
                            container.focus().clear().add(b1.update(), clb1);
                            Core.offNotifyLoop(r);
                            r = Core.onNotifyLoop(lv, () -> {
                                clb1.clear();
                                for (final MinecraftList l : lv)
                                    clb1.add(UI.button(l, l.icon).imageAlign(ImgAlign.TOP).imageOffset(20).onAction((s, e) -> list(l)));
                                clb1.update();
                            });
                        }

                        {
                            le.getSelf().onCloseList(event -> {
                                Core.offNotifyLoop(r);
                                return true;
                            });
                            main();
                        }
                    };

                    return context.getContainer();
                }),

                UI.button(LANG_REMOVE).size(cw, 32).pos(8, 140).onAction((s, e) -> {
                    MinecraftProfile.this.context.removeProfile(this);
                    context.close();
                })
        );
        /*final int cw = context.width() - 16, cw2 = (cw - 8) / 2;
        final IContainer r = UI.panel();
        final IComboBox
                javas = UI.comboBox().text(java).size(cw2, 32).pos(8, 100).imageOffset(4).onList((self, container) -> {
                    final ContainerListBuilder clb = new ContainerListBuilder(UI.scrollPane().content(UI.panel()), 150, 8)
                            .size(context.width(), context.height() - 48).pos(0, 48);
                    container.size(context.width(), context.height()).add(UI.button("<")
                            .size(32, 32).pos(8, 8).onAction((s, e) -> self.focus()), clb);
                    final ConcurrentLinkedQueue<Java> l = ((JavaSupport) MinecraftProfile.this.context.getPlugin(JavaSupport.ID)).javaList;
                    final Runnable r1 = Core.onNotifyLoop(l, () -> {
                        clb.clear();
                        for (final Java j : l)
                            clb.add(UI.button(j, j.icon).imageAlign(ImgAlign.TOP).imageOffset(20).onAction((s, e) -> self.text(java = j).image(java.icon).focus()));
                        clb.update();
                    });
                    self.onCloseList((selfObject, container1, selfListener) -> {
                        if (container1 == container)
                            Core.offNotifyLoop(r1);
                        return true;
                    });
                    return context.getContainer();
                }),
                versions = UI.comboBox().size(cw2, 32).pos(cw2 + 16, 100).imageOffset(4).onList((self, container) -> {
                    final ContainerListBuilder clb = new ContainerListBuilder(UI.scrollPane().content(UI.panel().borderRadius(UI.ZERO).background(UI.BLACK).update()), 150, 8)
                            .size(context.width(), context.height() - 48).pos(0, 48);
                    final IButton back = UI.button("<").size(32, 32).pos(8, 8).onAction((s, e) -> self.focus());
                    container.size(context.width(), context.height()).add(back, clb);
                    final ArrayList<MinecraftList> l = ((MinecraftSupport) MinecraftProfile.this.context.getPlugin()).lv;
                    new Object() {
                        MinecraftList list;
                        final ContainerListBuilder clb2 = new ContainerListBuilder(UI.scrollPane().content(UI.panel().borderRadius(UI.ZERO).background(UI.BLACK)), 150, 8)
                                .size(context.width(), context.height() - 48).pos(0, 48);
                        final IButton back2 = UI.button("<").onAction((s, e) -> {
                            Core.offNotifyLoop(this::onUpdate2);
                            self.offCloseList(this::onCloseList2);
                            list = null;
                            container.remove(s).remove(clb2).add(back, clb);
                            Core.onNotifyLoop(l, this::onUpdate1);
                            self.onCloseList(this::onCloseList1);
                        });

                        void onUpdate1() {
                            clb.clear();
                            for (final MinecraftList ll : l)
                                clb.add(UI.button(ll, ll.icon).imageAlign(ImgAlign.TOP).imageOffset(20).onAction((s, e) -> {
                                    System.out.println(ll);
                                    container.remove(back).add(back2, clb2);
                                    Core.offNotifyLoop(this::onUpdate1);
                                    self.offCloseList(this::onCloseList1);
                                    list = ll;
                                    Core.onNotifyLoop(ll, this::onUpdate2);
                                    self.onCloseList(this::onCloseList2);
                                    clb2.focus();
                                }));
                            clb.update();
                        }

                        void onUpdate2() {
                            clb2.clear();
                            for (final IMinecraftVersion ver : list)
                                clb2.add(UI.button().text(ver));
                            clb2.update();
                        }

                        boolean onCloseList1(final IComboBox selfObject, final IContainer container1, final IComboBox.CloseListListener selfListener) {
                            System.out.println("Close");
                            if (container1 == container) {
                                Core.offNotifyLoop(this::onUpdate1);
                                System.out.println("Close 2");
                            }
                            return true;
                        }

                        boolean onCloseList2(final IComboBox selfObject, final IContainer container1, final IComboBox.CloseListListener selfListener) {
                            if (container1 == container)
                                Core.offNotifyLoop(this::onUpdate2);
                            return true;
                        }

                        {
                            Core.onNotifyLoop(l, this::onUpdate1);
                            self.onCloseList(this::onCloseList1);
                        }
                    };
                    return context.getContainer();
                })
        ;
        if (java != null)
            javas.image(java.icon);

        context.add(r.size(context.width(), 180).add(
                UI.text("Profile name:").size(cw, 18).pos(8, 8),
                UI.textField(name).size(cw, 32).pos(8, 34).onInput(new ITextField.InputListener() {
                    @Override
                    public boolean typed(ITextField self, char ch) {
                        name = self.text();
                        return true;
                    }
                }),

                UI.text("Java:").size(cw2, 18).pos(8, 74),
                javas,
                UI.text("Game version:").size(cw2, 18).pos(cw2 + 16, 74),
                versions,

                UI.button(LANG_REMOVE).size(cw, 32).pos(8, 140).onAction((s, e) -> {
                    MinecraftProfile.this.context.removeProfile(this);
                    context.close();
                })
        ));*/
    }

    @Override
    public void preLaunch(final RunProc configuration) {
        final IMinecraftVersion ver = version;
        final File h = new File(plugin.homeDir, name);
        configuration.workDir = h;
        //configuration.arguments.add(java.file.getAbsolutePath());

        if (ver != null)
            try {
                ver.preLaunch(configuration, plugin.gameDir, h);
            } catch (final Exception ex) {
                ex.printStackTrace();
            }
    }

    @Override
    public void launch(final RunProc configuration) {
        final IMinecraftVersion ver = version;
        if (ver != null)
            try {
                ver.launch(configuration, plugin.gameDir, new File(plugin.homeDir, name));
            } catch (final Exception ex) {
                ex.printStackTrace();
            }
    }
}

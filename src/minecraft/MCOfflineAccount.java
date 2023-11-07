package minecraft;

import Launcher.PluginContext;
import Launcher.RunProc;
import Launcher.base.IAccount;
import Launcher.base.IEditorContext;
import Launcher.base.IProfile;
import UIL.UI;
import UIL.base.IImage;
import UIL.base.ITextField;

public class MCOfflineAccount implements IMinecraftAccount {
    public static final String ID = "minecraft-support";
    public static final IImage ICON_OFFLINE;

    static {
        IImage o = null;
        try {
            o = UI.image(ID + "://images/offline.png");
        } catch (final Exception ex) {
            ex.printStackTrace();
        }
        ICON_OFFLINE = o;
    }

    final PluginContext context;

    public MCOfflineAccount(final PluginContext context) {
        this.context = context;
    }

    String name;

    @Override
    public IImage getIcon() {
        return ICON_OFFLINE;
    }

    @Override
    public void open(final IEditorContext editor) {
        editor.add(UI.panel().size(editor.width(), 88).add(
                UI.textField(name).onInput(new ITextField.InputListener() {
                    @Override
                    public boolean typed(ITextField self, char ch) {
                        return ITextField.InputListener.super.typed(self, ch);
                    }

                    @Override
                    public boolean pressed(ITextField self) {
                        return ITextField.InputListener.super.pressed(self);
                    }

                    @Override
                    public boolean released(ITextField self) {
                        synchronized (MCOfflineAccount.this) {
                            name = self.text();
                            MCOfflineAccount.this.notifyAll();
                        }
                        return ITextField.InputListener.super.released(self);
                    }
                }).size(editor.width() - 16, 32).pos(8, 8),
                UI.button(LANG_REMOVE).size(editor.width() - 16, 32).pos(8, 48).onAction((s, e) -> {
                    context.removeAccount(this);
                    editor.close();
                })
        ));
    }

    @Override
    public void preLaunch(RunProc configuration) {

    }

    @Override
    public void launch(RunProc configuration) {

    }

    @Override
    public String toString() {
        return name;
    }
}

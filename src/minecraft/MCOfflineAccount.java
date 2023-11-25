package minecraft;

import Launcher.RunProc;
import Launcher.base.IEditorContext;
import Launcher.base.LaunchListener;
import UIL.UI;
import UIL.base.IImage;
import UIL.base.ITextField;
import Utils.ListMap;

import java.util.UUID;

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

    final MinecraftSupport plugin;

    public MCOfflineAccount(final MinecraftSupport plugin) { this.plugin = plugin; }

    String name = null, accessToken = null;
    UUID uuid = null;

    @Override public IImage getIcon() { return ICON_OFFLINE; }

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
                UI.button(LANG_REMOVE).foreground(UI.RED).size(editor.width() - 16, 32).pos(8, 48).onAction((s, e) -> {
                    plugin.getContext().removeAccount(this);
                    editor.close();
                })
        ));
        editor.onClose(plugin::saveConfig);
    }

    @Override public String toString() { return name; }

    @Override
    public LaunchListener init(final RunProc configuration) {
        final ListMap<String, String> m = (ListMap<String, String>) configuration.generalObjects.get("variables");
        if (m == null) {
            System.out.println("No variables map!");
            return null;
        }
        m.put("user_type", "mojang");
        m.put("user_properties", "{}");
        m.put("auth_player_name", name);
        m.put("uuid", uuid.toString());
        m.put("auth_uuid", uuid.toString());
        m.put("auth_xuid", uuid.toString());
        m.put("auth_access_token", accessToken);
        m.put("clientid", accessToken);
        return null;
    }
}

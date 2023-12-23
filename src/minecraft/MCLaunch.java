package minecraft;

import Launcher.RunProc;
import Launcher.base.LaunchListener;
import Utils.RRunnable1a;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @since ModrinthSupport 0.2.4
 */
public class MCLaunch implements LaunchListener {
    public final MinecraftProfile profile;
    public final RunProc configuration;
    public final LaunchListener ll;

    public final ConcurrentLinkedQueue<LaunchListener> listeners = new ConcurrentLinkedQueue<>();

    @Override
    public void preLaunch() {
        if (ll != null)
            ll.preLaunch();
        for (final LaunchListener l : listeners)
            l.preLaunch();
    }

    @Override
    public void launch() {
        if (ll != null)
            ll.launch();
        for (final LaunchListener l : listeners)
            l.launch();
    }

    private boolean h = true;

    @Override
    public void outLine(final String line) {
        if (line.contains("Session ID is")) {
            System.out.println("[plugin] Session ID was hidden.");
            return;
        }
        if (h) {
            if (line.contains("LWJGL")) {
                h = false;
                configuration.setVisible(false);
            }
        } else {
            if (line.contains("Stopping!")) {
                h = true;
                configuration.setVisible(true);
            }
        }
        System.out.println("[GAME/out] " + line);
    }

    @Override
    public void errLine(final String line) {
        System.out.println("[GAME/err] " + line);
    }

    public MCLaunch(final MinecraftProfile profile, final RunProc configuration, final IMinecraftVersion ver) {
        this.profile = profile;
        this.configuration = configuration;
        LaunchListener r = null;
        try {
            r = ver.init(configuration);
        } catch (final Throwable ex) {
            ex.printStackTrace();
        }
        ll = r;

        for (final RRunnable1a<LaunchListener, MCLaunch> i : profile.plugin.launchListeners) {
            final LaunchListener l = i.run(this);
            if (l == null)
                continue;
            listeners.add(l);
        }
    }
}
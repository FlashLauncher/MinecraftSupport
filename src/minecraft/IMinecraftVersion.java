package minecraft;

import Launcher.RunProc;
import Launcher.base.LaunchListener;

public interface IMinecraftVersion {
    String getID();

    LaunchListener init(final RunProc configuration);
}
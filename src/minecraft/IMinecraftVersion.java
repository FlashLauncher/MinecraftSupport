package minecraft;

import Launcher.RunProc;

import java.io.File;

public interface IMinecraftVersion {
    void preLaunch(final RunProc configuration, final File gameDir, final File homeDir) throws Exception;
    void launch(final RunProc configuration, final File gameDir, final File homeDir) throws Exception;
}
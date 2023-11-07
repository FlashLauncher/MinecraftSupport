package minecraft;

import Launcher.base.IAccount;
import Launcher.base.IProfile;

public interface IMinecraftAccount extends IAccount {
    @Override default boolean isCompatible(final IProfile profile) { return profile instanceof IMinecraftProfile; }
}
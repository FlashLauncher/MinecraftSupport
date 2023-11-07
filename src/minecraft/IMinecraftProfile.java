package minecraft;

import Launcher.base.IAccount;
import Launcher.base.IProfile;

public interface IMinecraftProfile extends IProfile {
    @Override
    default boolean isCompatible(final IAccount account) {
        return account instanceof IMinecraftAccount;
    }
}
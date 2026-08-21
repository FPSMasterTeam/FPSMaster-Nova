package top.fpsmaster.mixin.interfaces;

/**
 * Runtime session swap used by the main-menu account book.
 * Arguments are {@code User} / {@code UserApiService}; typed as {@code Object}
 * so every Stonecutter node can compile this interface.
 */
public interface IMinecraftSession {
    void fpsmaster$setUser(Object user);

    void fpsmaster$setUserApiService(Object service);
}

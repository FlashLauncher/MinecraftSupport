package minecraft;

import Launcher.RunProc;
import Launcher.base.LaunchListener;
import UIL.LangItem;
import UIL.base.IImage;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class MinecraftList implements IMinecraftVersion, Iterable<IMinecraftVersion> {
    public final Object name;
    public final IImage icon;
    public final boolean smooth;

    private List<String> tags;
    private final ConcurrentLinkedQueue<IMinecraftVersion> vl = new ConcurrentLinkedQueue<>();

    public MinecraftList(final Object name, final IImage icon, final boolean smooth) {
        this.name = name;
        this.icon = icon;
        this.smooth = smooth;
    }

    public MinecraftList(final Object name, final IImage icon) {
        this(name, icon, true);
    }

    public void updateTags(final List<String> tags) { this.tags = tags; }

    public boolean isEmpty() { return vl.isEmpty(); }
    public int size() { return vl.size(); }

    public void add(final IMinecraftVersion ver) {
        synchronized (this) {
            vl.add(ver);
            notifyAll();
        }
    }

    public void addAll(final Collection<? extends IMinecraftVersion> versions) {
        synchronized (this) {
            vl.addAll(versions);
            notifyAll();
        }
    }

    public IMinecraftVersion get(final String id) {
        for (final IMinecraftVersion ver : vl)
            if (ver.getID().equals(id))
                return ver;
        return null;
    }

    public void remove(final IMinecraftVersion ver) {
        synchronized (this) {
            vl.remove(ver);
            notifyAll();
        }
    }

    public void removeAll(final Collection<? extends IMinecraftVersion> versions) {
        synchronized (this) {
            vl.removeAll(versions);
            notifyAll();
        }
    }

    @Override public String toString() { return name.toString(); }
    @Override public Iterator<IMinecraftVersion> iterator() { return vl.iterator(); }
    @Override public void forEach(final Consumer<? super IMinecraftVersion> action) { vl.forEach(action); }
    @Override public Spliterator<IMinecraftVersion> spliterator() { return vl.spliterator(); }

    @Override public String getID() { return ""; }

    @Override public final LaunchListener init(final RunProc configuration) { return null; }
}
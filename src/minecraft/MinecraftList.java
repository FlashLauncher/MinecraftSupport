package minecraft;

import UIL.LangItem;
import UIL.base.IImage;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class MinecraftList implements Iterable<IMinecraftVersion> {
    public final LangItem name;
    public final IImage icon;

    private List<String> tags;
    private final ConcurrentLinkedQueue<IMinecraftVersion> vl = new ConcurrentLinkedQueue<>();

    public MinecraftList(final LangItem name, final IImage icon) { this.name = name; this.icon = icon; }

    public void updateTags(final List<String> tags) { this.tags = tags; }

    public boolean isEmpty() { return vl.isEmpty(); }
    public int size() { return vl.size(); }

    public void add(final IMinecraftVersion ver) {
        synchronized (vl) {
            vl.add(ver);
            vl.notifyAll();
        }
    }

    public IMinecraftVersion get(final String id) {
        for (final IMinecraftVersion ver : vl)
            if (ver.getID().equals(id))
                return ver;
        return null;
    }

    public void remove(final IMinecraftVersion ver) {
        synchronized (vl) {
            vl.remove(ver);
            vl.notifyAll();
        }
    }

    public void waitNotify() throws InterruptedException {
        synchronized (vl) {
            vl.wait();
        }
    }

    @Override public String toString() { return name.toString(); }
    @Override public Iterator<IMinecraftVersion> iterator() { return vl.iterator(); }
    @Override public void forEach(final Consumer<? super IMinecraftVersion> action) { vl.forEach(action); }
    @Override public Spliterator<IMinecraftVersion> spliterator() { return vl.spliterator(); }
}
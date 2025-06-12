package com.xpdustry.imperium.common.lifecycle;

import java.util.SequencedCollection;

public interface LifecycleService {

    void addListener(final LifecycleListener listener);

    void addListener(final Class<? extends LifecycleListener> listener);

    void load();

    void exit(final PlatformExitCode code);

    SequencedCollection<LifecycleListener> listeners();
}

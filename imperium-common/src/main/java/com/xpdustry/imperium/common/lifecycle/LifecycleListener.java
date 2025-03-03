package com.xpdustry.imperium.common.lifecycle;

public interface LifecycleListener {

    default void onImperiumInit() {}

    default void onImperiumExit() {}
}

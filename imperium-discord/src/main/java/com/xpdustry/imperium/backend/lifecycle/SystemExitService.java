package com.xpdustry.imperium.backend.lifecycle;

import com.xpdustry.imperium.common.lifecycle.PlatformExitCode;
import com.xpdustry.imperium.common.lifecycle.PlatformExitService;

public final class SystemExitService implements PlatformExitService {

    @Override
    public void exit(final PlatformExitCode code) {
        System.exit(code.ordinal());
    }
}

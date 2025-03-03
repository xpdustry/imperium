package com.xpdustry.imperium.backend.lifecycle;

import com.xpdustry.imperium.common.lifecycle.ExitService;

public final class BackendExitService implements ExitService {

    @Override
    public void exit(final Code code) {
        System.exit(code.ordinal());
    }
}

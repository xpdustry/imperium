package com.xpdustry.imperium.mindustry.lifecycle;

import arc.ApplicationListener;
import arc.Core;
import com.xpdustry.imperium.common.lifecycle.PlatformExitCode;
import com.xpdustry.imperium.common.lifecycle.PlatformExitService;

public final class MindustryExitService implements PlatformExitService {

    @Override
    public void exit(final PlatformExitCode code) {
        Core.app.exit();
        if (code == PlatformExitCode.RESTART) {
            Core.app.addListener(new ApplicationListener() {
                @Override
                public void dispose() {
                    Core.settings.autosave();
                    System.exit(2);
                }
            });
        }
    }
}

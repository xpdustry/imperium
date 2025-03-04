package com.xpdustry.imperium.mindustry.lifecycle;

import arc.ApplicationListener;
import arc.Core;
import com.xpdustry.imperium.common.lifecycle.ExitService;

public final class MindustryExitService implements ExitService {

    @Override
    public synchronized void exit(final Code code) {
        Core.app.exit();
        if (code == Code.RESTART) {
            Core.app.addListener(new ApplicationListener() {
                @Override
                public void dispose() {
                    Core.settings.autosave();
                    System.exit(2);
                }
            });
        }
        /*
           runBlocking {
               instances
                   .get<WebhookMessageSender>()
                   .send(WebhookChannel.CONSOLE, WebhookMessage(content = "The server is exiting with $status code."))
           }
        */
    }
}

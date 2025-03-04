package com.xpdustry.imperium.mindustry.backport;

import arc.Application;
import arc.ApplicationListener;
import arc.Core;
import arc.struct.Seq;
import com.xpdustry.distributor.api.plugin.PluginListener;
import com.xpdustry.imperium.mindustry.misc.MindustryExtensionsKt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// https://github.com/Anuken/Arc/pull/158
public final class NoApplicationListenerSkipBackport implements PluginListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(NoApplicationListenerSkipBackport.class);

    @Override
    public void onPluginInit() {
        final var version = MindustryExtensionsKt.getMindustryVersion();
        if (version.build() < 147) {
            Core.app = new FixedApplication(Core.app);
        } else {
            LOGGER.warn("The {} backport needs to be removed", getClass().getSimpleName());
        }
    }

    private record FixedApplication(Application delegate) implements Application {

        // Bruh
        public void removeListener(final ApplicationListener listener) {
            delegate.post(() -> delegate.removeListener(listener));
        }

        public boolean isDisposed() {
            return delegate.isDisposed();
        }

        public void dispose() {
            delegate.dispose();
        }

        public void exit() {
            delegate.exit();
        }

        public void post(final Runnable runnable) {
            delegate.post(runnable);
        }

        public boolean openURI(final String URI) {
            return delegate.openURI(URI);
        }

        public boolean openFolder(final String file) {
            return delegate.openFolder(file);
        }

        public void setClipboardText(final String text) {
            delegate.setClipboardText(text);
        }

        public String getClipboardText() {
            return delegate.getClipboardText();
        }

        public long getNativeHeap() {
            return delegate.getNativeHeap();
        }

        public long getJavaHeap() {
            return delegate.getJavaHeap();
        }

        public int getVersion() {
            return delegate.getVersion();
        }

        public boolean isWeb() {
            return delegate.isWeb();
        }

        public boolean isMobile() {
            return delegate.isMobile();
        }

        public boolean isIOS() {
            return delegate.isIOS();
        }

        public boolean isAndroid() {
            return delegate.isAndroid();
        }

        public boolean isHeadless() {
            return delegate.isHeadless();
        }

        public boolean isDesktop() {
            return delegate.isDesktop();
        }

        public ApplicationType getType() {
            return delegate.getType();
        }

        public void defaultUpdate() {
            delegate.defaultUpdate();
        }

        public void addListener(final ApplicationListener listener) {
            delegate.addListener(listener);
        }

        public Seq<ApplicationListener> getListeners() {
            return delegate.getListeners();
        }
    }
}

package com.xpdustry.imperium.common.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.xpdustry.imperium.common.config.ImperiumConfig;
import com.xpdustry.imperium.common.config.ImperiumConfigJava;
import com.xpdustry.imperium.common.database.DatabaseModule;
import com.xpdustry.imperium.common.factory.ObjectBinder;
import com.xpdustry.imperium.common.factory.ObjectFactory;
import com.xpdustry.imperium.common.factory.ObjectModule;
import com.xpdustry.imperium.common.lifecycle.*;
import com.xpdustry.imperium.common.version.ImperiumVersion;
import com.xpdustry.imperium.common.webhook.WebhookMessageSender;
import com.xpdustry.imperium.common.webhook.WebhookMessageSenderImpl;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// TODO The test case is shit
final class SQLMessageServiceTest {

    private static final Duration LATENCY = Duration.ofMillis(500L);
    private static final String SERVICE1 = "service1";
    private static final String SERVICE2 = "service2";

    @Test
    void test_simple(final @TempDir Path temp) {
        this.withTestContext(temp, (service1, service2) -> {
            final var future1 = new CompletableFuture<AMessage>();
            final var future2 = new CompletableFuture<AMessage>();
            service1.subscribe(AMessage.class, future1::complete);
            service2.subscribe(AMessage.class, future2::complete);

            final var message1 = new AMessage("hello1");
            service1.broadcast(message1);
            assertThat(future2).succeedsWithin(LATENCY).isEqualTo(message1);

            final var message2 = new AMessage("hello2");
            service2.broadcast(message2);
            assertThat(future1).succeedsWithin(LATENCY).isEqualTo(message2);
        });
    }

    // TODO this is goofy
    private void withTestContext(
            final Path directory, final BiConsumer<SQLMessageService, SQLMessageService> consumer) {
        final var factory = this.createFactory(directory);
        final var service1 = factory.get(SQLMessageService.class, SERVICE1);
        final var service2 = factory.get(SQLMessageService.class, SERVICE2);
        final var lifecycle = factory.get(LifecycleService.class);
        lifecycle.load();
        try {
            consumer.accept(service1, service2);
        } finally {
            lifecycle.exit(PlatformExitCode.FAILURE);
        }
    }

    private ObjectFactory createFactory(final Path directory) {
        return ObjectFactory.create(
                new DatabaseModule(),
                new TestModule(directory),
                new LifecycleModule(),
                new SQLMessageServiceModule(SERVICE1),
                new SQLMessageServiceModule(SERVICE2));
    }

    private record TestModule(Path directory) implements ObjectModule {

        @Override
        public void configure(final ObjectBinder binder) {
            binder.bind(ImperiumVersion.class).toInst(new ImperiumVersion(1, 1, 1));
            binder.bind(WebhookMessageSender.class).toImpl(WebhookMessageSenderImpl.class);
            binder.bind(PlatformExitService.class).toInst(ignored -> {});
            binder.bind(Path.class).named("directory").toInst(directory);
        }
    }

    private record SQLMessageServiceModule(String name) implements ObjectModule {

        @Override
        public void configure(final ObjectBinder binder) {
            binder.bind(ImperiumConfig.class)
                    .visible(false)
                    .toProv(() -> ImperiumConfigJava.createWithServerNameAndH2(name));
            binder.bind(SQLMessageService.class).named(name).toImpl(SQLMessageService.class);
        }
    }

    private record AMessage(String value) implements Message {}
}

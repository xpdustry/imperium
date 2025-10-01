package com.xpdustry.imperium.common;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.xpdustry.imperium.common.account.AccountManager;
import com.xpdustry.imperium.common.account.SimpleAccountManager;
import com.xpdustry.imperium.common.config.*;
import com.xpdustry.imperium.common.content.MindustryMapManager;
import com.xpdustry.imperium.common.content.SimpleMindustryMapManager;
import com.xpdustry.imperium.common.database.IdentifierCodec;
import com.xpdustry.imperium.common.database.ImperiumC6B36Codec;
import com.xpdustry.imperium.common.database.SQLProvider;
import com.xpdustry.imperium.common.database.SimpleSQLProvider;
import com.xpdustry.imperium.common.factory.ObjectBinder;
import com.xpdustry.imperium.common.factory.ObjectModule;
import com.xpdustry.imperium.common.message.Messenger;
import com.xpdustry.imperium.common.message.NoopMessenger;
import com.xpdustry.imperium.common.message.RabbitmqMessenger;
import com.xpdustry.imperium.common.metrics.InfluxDBRegistry;
import com.xpdustry.imperium.common.metrics.MetricsRegistry;
import com.xpdustry.imperium.common.network.Discovery;
import com.xpdustry.imperium.common.network.SimpleDiscovery;
import com.xpdustry.imperium.common.network.VpnApiIoDetection;
import com.xpdustry.imperium.common.network.VpnDetection;
import com.xpdustry.imperium.common.security.AddressWhitelist;
import com.xpdustry.imperium.common.security.PunishmentManager;
import com.xpdustry.imperium.common.security.SimpleAddressWhitelist;
import com.xpdustry.imperium.common.security.SimplePunishmentManager;
import com.xpdustry.imperium.common.storage.LocalStorageBucket;
import com.xpdustry.imperium.common.storage.MinioStorageBucket;
import com.xpdustry.imperium.common.storage.StorageBucket;
import com.xpdustry.imperium.common.time.SimpleTimeRenderer;
import com.xpdustry.imperium.common.time.TimeRenderer;
import com.xpdustry.imperium.common.user.SimpleUserManager;
import com.xpdustry.imperium.common.user.UserManager;
import com.xpdustry.imperium.common.webhook.WebhookMessageSender;
import com.xpdustry.imperium.common.webhook.WebhookMessageSenderImpl;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executors;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;

public final class CommonModule implements ObjectModule {

    @Override
    public void configure(final ObjectBinder binder) {
        binder.bind(ImperiumConfig.class).toProv(ImperiumConfigProvider.class);
        binder.bind(Discovery.class).toImpl(SimpleDiscovery.class);
        binder.bind(VpnDetection.class).toProv(VpnDetectionProvider.class);
        binder.bind(Messenger.class).toProv(MessengerProvider.class);
        binder.bind(SQLProvider.class).toImpl(SimpleSQLProvider.class);
        binder.bind(MindustryMapManager.class).toImpl(SimpleMindustryMapManager.class);
        binder.bind(PunishmentManager.class).toImpl(SimplePunishmentManager.class);
        binder.bind(UserManager.class).toImpl(SimpleUserManager.class);
        binder.bind(OkHttpClient.class).toProv(OkHttpClientProvider.class);
        binder.bind(IdentifierCodec.class).toInst(ImperiumC6B36Codec.INSTANCE);
        binder.bind(TimeRenderer.class).toImpl(SimpleTimeRenderer.class);
        binder.bind(WebhookMessageSender.class).toImpl(WebhookMessageSenderImpl.class);
        binder.bind(AddressWhitelist.class).toImpl(SimpleAddressWhitelist.class);
        binder.bind(StorageBucket.class).toProv(StorageBucketProvider.class);
        binder.bind(MetricsRegistry.class).toProv(MetricsRegistryProvider.class);
        binder.bind(AccountManager.class).toImpl(SimpleAccountManager.class);
    }

    private static class VpnDetectionProvider implements Provider<VpnDetection> {
        private final ImperiumConfig config;
        private final OkHttpClient okHttpClient;

        @Inject
        public VpnDetectionProvider(ImperiumConfig config, OkHttpClient okHttpClient) {
            this.config = config;
            this.okHttpClient = okHttpClient;
        }

        @Override
        public VpnDetection get() {
            return switch (config.network().vpnDetection()) {
                case NetworkConfig.VpnDetectionConfig.None ignored -> VpnDetection.Noop.INSTANCE;
                case NetworkConfig.VpnDetectionConfig.VpnApiIo vpnApiIo ->
                    new VpnApiIoDetection(vpnApiIo, okHttpClient);
                default ->
                    throw new IllegalStateException(
                            "Unexpected value: " + config.network().vpnDetection());
            };
        }
    }

    private static class MessengerProvider implements Provider<Messenger> {
        private final ImperiumConfig config;

        @Inject
        public MessengerProvider(final ImperiumConfig config) {
            this.config = config;
        }

        @Override
        public Messenger get() {
            return switch (config.messenger()) {
                case MessengerConfig.RabbitMQ ignored -> new RabbitmqMessenger(config);
                case MessengerConfig.None ignored -> new NoopMessenger();
                default -> throw new IllegalStateException("Unexpected value: " + config.messenger());
            };
        }
    }

    private static class OkHttpClientProvider implements Provider<OkHttpClient> {
        @Override
        public OkHttpClient get() {
            return new OkHttpClient.Builder()
                    .connectTimeout(Duration.ofSeconds(20))
                    .connectTimeout(Duration.ofSeconds(20))
                    .readTimeout(Duration.ofSeconds(20))
                    .writeTimeout(Duration.ofSeconds(20))
                    .dispatcher(new Dispatcher(Executors.newFixedThreadPool(
                            Runtime.getRuntime().availableProcessors(),
                            new ThreadFactoryBuilder()
                                    .setDaemon(true)
                                    .setNameFormat("imperium-okhttp-%d")
                                    .build())))
                    .build();
        }
    }

    private static class StorageBucketProvider implements Provider<StorageBucket> {

        private final ImperiumConfig config;
        private final Path directory;
        private final OkHttpClient httpClient;

        @Inject
        public StorageBucketProvider(
                final ImperiumConfig config, final @Named("directory") Path directory, final OkHttpClient httpClient) {
            this.config = config;
            this.directory = directory;
            this.httpClient = httpClient;
        }

        @Override
        public StorageBucket get() {
            return switch (config.storage()) {
                case StorageConfig.Local ignored -> new LocalStorageBucket(directory.resolve("storage"));
                case StorageConfig.Minio minio -> new MinioStorageBucket(minio, httpClient);
                default -> throw new IllegalStateException("Unexpected value: " + config.storage());
            };
        }
    }

    private static class MetricsRegistryProvider implements Provider<MetricsRegistry> {
        private final ImperiumConfig config;
        private final OkHttpClient okHttpClient;

        @Inject
        public MetricsRegistryProvider(final ImperiumConfig config, final OkHttpClient okHttpClient) {
            this.config = config;
            this.okHttpClient = okHttpClient;
        }

        @Override
        public MetricsRegistry get() {
            return switch (config.metrics()) {
                case MetricConfig.InfluxDB influxDB -> new InfluxDBRegistry(config.server(), influxDB, okHttpClient);
                case MetricConfig.None ignored -> MetricsRegistry.None.INSTANCE;
                default -> throw new IllegalStateException("Unexpected value: " + config.metrics());
            };
        }
    }
}

package com.xpdustry.imperium.common.message;

public interface MessageService {

    <M extends Message> void broadcast(final M message);

    <M extends Message> void subscribe(final Class<M> type, final Subscriber<M> subscriber);

    interface Subscriber<M> {
        void onMessage(final M message);
    }
}

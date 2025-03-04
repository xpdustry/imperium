package com.xpdustry.imperium.common.lifecycle;

public interface ExitService {
    void exit(final Code code);

    enum Code {
        SUCCESS,
        RESTART,
        FAILURE
    }
}

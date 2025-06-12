package com.xpdustry.imperium.common.session;

import java.util.List;
import java.util.Optional;

public interface MindustrySessionService {

    Optional<MindustrySession> selectByKey(final MindustrySession.Key key);

    List<MindustrySession> selectAllByAccount(final int account);

    boolean login(final MindustrySession.Key key, final String username, final char[] password);

    boolean logout(final MindustrySession.Key key, final boolean all);
}

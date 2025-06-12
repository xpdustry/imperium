package com.xpdustry.imperium.common.account;

import java.util.Map;
import java.util.Optional;

public interface MetadataService {

    Optional<String> selectMetadata(final int account, final String key);

    Map<String, String> selectAllMetadataByPrefix(final int account, final String prefix);

    default Map<String, String> selectAllMetadata(final int account) {
        return this.selectAllMetadataByPrefix(account, "");
    }

    boolean updateMetadata(final int account, final String key, final String value);

    boolean deleteMetadata(final int account, final String key);
}

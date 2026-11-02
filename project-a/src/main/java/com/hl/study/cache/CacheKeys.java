package com.hl.study.cache;

import java.time.Duration;

public final class CacheKeys {
    public static final String WRONG_HOT_TOP100 = "wrong:hot:top100";
    public static final String QUESTION_DETAIL = "question:detail:";

    public static final Duration TTL_HOT = Duration.ofMinutes(30);
    public static final Duration TTL_DETAIL = Duration.ofHours(1);

    private CacheKeys() {}
}
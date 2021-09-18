package com.mzyupc.aredis.view.language;

import com.intellij.lang.Language;

/**
 * @author mzyupc@163.com
 * @date 2021/9/16 8:28 下午
 */
public class RedisCmdLanguage extends Language {
    public static final RedisCmdLanguage INSTANCE = new RedisCmdLanguage();

    protected RedisCmdLanguage(String id, String... mimeTypes) {
        super(INSTANCE, id, mimeTypes);
    }

    private RedisCmdLanguage() {
        // todo mimeTypes
        super("REDIS_CMD", "");
    }

    @Override
    public boolean isCaseSensitive() {
        return false;
    }
}

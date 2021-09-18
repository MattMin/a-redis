package com.mzyupc.aredis.view.language;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author mzyupc@163.com
 * @date 2021/9/16 9:16 下午
 */
public class RedisCmdLanguageFileType extends LanguageFileType {
    protected RedisCmdLanguageFileType(@NotNull Language language) {
        super(language);
    }

    @Override
    public @NonNls
    @NotNull String getName() {
        return "Redis Cmd";
    }

    @Override
    public @NlsContexts.Label
    @NotNull String getDescription() {
        return "Redis command language";
    }

    @Override
    public @NlsSafe
    @NotNull String getDefaultExtension() {
        return "rcmd";
    }

    @Override
    public @Nullable Icon getIcon() {
        return null;
    }
}

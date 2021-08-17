package com.mzyupc.aredis.view.editor;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author mzyupc@163.com
 */
public class ARedisFileType implements FileType {
    @Override
    public @NonNls
    @NotNull
    String getName() {
        return "Redis Tab";
    }

    @Override
    public @NlsContexts.Label
    @NotNull
    String getDescription() {
        return "A redis tool tab";
    }

    @Override
    public @NlsSafe
    @NotNull
    String getDefaultExtension() {
        return "";
    }

    @Override
    public @Nullable
    Icon getIcon() {
        return AllIcons.Nodes.DataColumn;
    }

    @Override
    public boolean isBinary() {
        return false;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public @NonNls
    @Nullable
    String getCharset(@NotNull VirtualFile file, @NotNull byte[] content) {
        return FileType.super.getCharset(file, content);
    }
}

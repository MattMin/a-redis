package com.mzyupc.aredis.view.editor;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;

/**
 * @author mzyupc@163.com
 *
 * key-value展示
 */
public class ARedisEditor extends UserDataHolderBase implements FileEditor {
    private ARedisVirtualFile aRedisVirtualFile;

    public ARedisEditor(VirtualFile aRedisVirtualFile) {
        this.aRedisVirtualFile = (ARedisVirtualFile) aRedisVirtualFile;
    }

    @Override
    public @NotNull JComponent getComponent() {
        return aRedisVirtualFile.getARedisKeyValueDisplayPanel();
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return null;
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title)
    @NotNull String getName() {
        // todo
        return "Editor Name";
    }

    @Override
    public @NotNull FileEditorState getState(@NotNull FileEditorStateLevel level) {
        return FileEditor.super.getState(level);
    }

    @Override
    public void setState(@NotNull FileEditorState fileEditorState) {

    }

    @Override
    public void setState(@NotNull FileEditorState state, boolean exactState) {
        FileEditor.super.setState(state, exactState);
    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void selectNotify() {
        FileEditor.super.selectNotify();
    }

    @Override
    public void deselectNotify() {
        FileEditor.super.deselectNotify();
    }

    @Override
    public void addPropertyChangeListener(@NotNull PropertyChangeListener propertyChangeListener) {

    }

    @Override
    public void removePropertyChangeListener(@NotNull PropertyChangeListener propertyChangeListener) {

    }

    @Override
    public @Nullable BackgroundEditorHighlighter getBackgroundHighlighter() {
        return FileEditor.super.getBackgroundHighlighter();
    }

    @Override
    public @Nullable FileEditorLocation getCurrentLocation() {
        return null;
    }

    @Override
    public @Nullable StructureViewBuilder getStructureViewBuilder() {
        return FileEditor.super.getStructureViewBuilder();
    }

    @Override
    public @Nullable VirtualFile getFile() {
        return this.aRedisVirtualFile;
    }

    @Override
    public void dispose() {

    }
}

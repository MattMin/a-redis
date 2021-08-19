package com.mzyupc.aredis.view.editor;

import com.google.common.base.Objects;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.mzyupc.aredis.utils.RedisPoolMgr;
import com.mzyupc.aredis.view.ARedisKeyValueDisplayPanel;
import com.mzyupc.aredis.vo.ConnectionInfo;
import com.mzyupc.aredis.vo.DbInfo;
import lombok.Getter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author mzyupc@163.com
 */
@Getter
public class ARedisVirtualFile extends VirtualFile {
    private final String name;
    private final Project project;
    private ConnectionInfo connectionInfo;
    private DbInfo dbInfo;
    private ARedisKeyValueDisplayPanel aRedisKeyValueDisplayPanel;

    @Override
    public @NotNull FileType getFileType() {
        return new ARedisFileType();
    }

    public ARedisVirtualFile(String name, Project project, ConnectionInfo connectionInfo, DbInfo dbInfo, RedisPoolMgr redisPoolMgr) {
        this.project = project;
        this.name = name;
        this.connectionInfo = connectionInfo;
        this.dbInfo = dbInfo;
        this.aRedisKeyValueDisplayPanel = new ARedisKeyValueDisplayPanel(project, connectionInfo, dbInfo, redisPoolMgr);
    }

    @Override
    public @NotNull
    @NlsSafe String getName() {
        return name;
    }

    @Override
    public @NotNull VirtualFileSystem getFileSystem() {
        return ARedisFileSystem.getInstance(project);
    }

    @Override
    public @NonNls
    @NotNull String getPath() {
        return name;
    }

    @Override
    public boolean isWritable() {
        return true;
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public VirtualFile getParent() {
        return null;
    }

    @Override
    public VirtualFile[] getChildren() {
        return new VirtualFile[0];
    }

    @Override
    public @NotNull OutputStream getOutputStream(Object o, long l, long l1) throws IOException {
        throw new UnsupportedOperationException("Unsupported Operation");
    }

    @Override
    public byte[] contentsToByteArray() throws IOException {
        return new byte[0];
    }

    @Override
    public long getTimeStamp() {
        return 0;
    }

    @Override
    public long getLength() {
        return 0;
    }

    @Override
    public void refresh(boolean b, boolean b1, @Nullable Runnable runnable) {

    }

    @Override
    public @NotNull InputStream getInputStream() throws IOException {
        return null;
    }

    @Override
    public long getModificationStamp() {
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ARedisVirtualFile that = (ARedisVirtualFile) o;
        return Objects.equal(connectionInfo, that.connectionInfo) && Objects.equal(dbInfo, that.dbInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(connectionInfo, dbInfo);
    }
}

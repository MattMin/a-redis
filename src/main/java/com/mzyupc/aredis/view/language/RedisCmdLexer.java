package com.mzyupc.aredis.view.language;

import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerPosition;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author mzyupc@163.com
 * @date 2021/9/16 9:43 下午
 */
public class RedisCmdLexer extends Lexer {
    @Override
    public void start(@NotNull CharSequence charSequence, int i, int i1, int i2) {

    }

    @Override
    public int getState() {
        return 0;
    }

    @Override
    public @Nullable IElementType getTokenType() {
        return null;
    }

    @Override
    public int getTokenStart() {
        return 0;
    }

    @Override
    public int getTokenEnd() {
        return 0;
    }

    @Override
    public void advance() {

    }

    @Override
    public @NotNull LexerPosition getCurrentPosition() {
        return null;
    }

    @Override
    public void restore(@NotNull LexerPosition lexerPosition) {

    }

    @Override
    public @NotNull CharSequence getBufferSequence() {
        return null;
    }

    @Override
    public int getBufferEnd() {
        return 0;
    }
}

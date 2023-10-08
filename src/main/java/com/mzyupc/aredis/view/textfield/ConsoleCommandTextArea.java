package com.mzyupc.aredis.view.textfield;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import com.mzyupc.aredis.utils.RedisPoolManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.DateUtils;
import redis.clients.jedis.Protocol;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Element;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.rtf.RTFEditorKit;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

/**
 * @author mzyupc@163.com
 * @date 2021/9/23 1:49 下午
 */
@Slf4j
public class ConsoleCommandTextArea extends JTextPane {
    private static final long serialVersionUID = -66377652770879651L;
    /**
     * 所有关键字
     */
    private final static String[] KEYS;

    static {
        List<String> cmdList = Arrays.stream(Protocol.Command.values()).map(item -> item.name().toLowerCase()).collect(Collectors.toList());
        List<String> keywordList = Arrays.stream(Protocol.Keyword.values()).map(item -> item.name().toLowerCase()).collect(Collectors.toList());
        cmdList.addAll(keywordList);
        KEYS = cmdList.toArray(new String[0]);
    }

    private final MutableAttributeSet keyAttr;
    private final MutableAttributeSet normalAttr;
    private final MutableAttributeSet inputAttributes = new RTFEditorKit().getInputAttributes();
    private final int fontSize = 14;
    protected StyleContext mContext;
    protected DefaultStyledDocument mDoc;
    private int db = 0;

    /**
     * 初始化，包括关键字颜色，和非关键字颜色
     */
    public ConsoleCommandTextArea(JBTextArea resultArea, RedisPoolManager redisPoolManager) {
        super();
        mContext = new StyleContext();
        mDoc = new DefaultStyledDocument(mContext);
        this.setDocument(mDoc);
        this.setAutoscrolls(true);
        this.setMargin(JBUI.insetsLeft(15));

        ConsoleCommandTextArea ths = this;
        this.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent ke) {
                executeCommand(ke, ths, redisPoolManager, resultArea);
            }

            @Override
            public void keyReleased(KeyEvent event) {
                dealSingleRow();
            }
        });

        // 关键字显示属性
        keyAttr = new SimpleAttributeSet();
        StyleConstants.setForeground(keyAttr, new Color(49, 140, 175));
        StyleConstants.setFontSize(keyAttr, this.fontSize);
        StyleConstants.setBold(keyAttr, false);

        // 一般文本显示属性
        normalAttr = new SimpleAttributeSet();
        StyleConstants.setBold(normalAttr, false);
        StyleConstants.setForeground(normalAttr, JBColor.BLACK);
        StyleConstants.setFontSize(normalAttr, this.fontSize);
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        StyleConstants.setFontSize(getInputAttributes(), this.fontSize);
        drawLineStarter(g);
    }

    /**
     * 在每行前绘制'>'
     *
     * @param g
     */
    protected void drawLineStarter(Graphics g) {
        Element element = mDoc.getDefaultRootElement();
        int rows = element.getElementCount();
        // 绘制行号的颜色
        g.setColor(JBColor.BLACK);
        g.setFont(new Font(getFont().getName(), getFont().getStyle(), this.fontSize));
        final int lineHeight = g.getFontMetrics().getHeight();
        for (int row = 0; row < rows; row++) {
            g.drawString(">", 2, getPositionY(row + 1, lineHeight));
        }
    }

    private int getPositionY(int row, int lineHeight) {
        // 与fontSize有关
        return (row * lineHeight) - 4;
    }

    /**
     * 执行命令
     *
     * @param ke
     * @param ths
     * @param redisPoolManager
     * @param resultArea
     */
    private void executeCommand(KeyEvent ke, ConsoleCommandTextArea ths, RedisPoolManager redisPoolManager, JBTextArea resultArea) {
        if (ke.getKeyCode() == KeyEvent.VK_ENTER) {
            Element root = mDoc.getDefaultRootElement();
            // 光标当前行
            int cursorPos = this.getCaretPosition();
            // 当前行
            int line = root.getElementIndex(cursorPos);
            Element para = root.getElement(line);
            int start = para.getStartOffset();
            // 除\r字符
            int end = para.getEndOffset() - 1;
            String cmd = null;
            try {
                cmd = mDoc.getText(start, end - start);
            } catch (BadLocationException e) {
                log.warn("", e);
            }

            if (StringUtils.isBlank(cmd)) {
                return;
            }

            cmd = cmd.trim();
            if (StringUtils.isEmpty(cmd)) {
                return;
            }

            String[] split = cmd.split("\\s");
            List<String> result = redisPoolManager.execRedisCommand(db, split[0], assembleArgs(split));
            String text = resultArea.getText();
            String currentLog = formatLog(cmd, result);
            text = text + currentLog;
            resultArea.setText(text);

            if ("select".equalsIgnoreCase(split[0])) {
                if (currentLog.contains("OK")) {
                    db = Integer.parseInt(split[1]);
                }
            }
        }
    }

    private String formatLog(String subCmd, List<String> result) {
        String currentLog = String.format("\n%s [%s] [%s]\n%s\n%s",
                DateUtils.formatDate(new Date(), "yyyy-MM-dd HH:mm:ss"),
                "DB" + db,
                subCmd,
                String.join("\n", result),
                "----------------------------------------------------"
        );
        return currentLog;
    }

    private String[] assembleArgs(String[] split) {
        List<String> result = Lists.newArrayList();
        boolean inString = false;
        boolean doubleQuote = false;
        StringBuilder arg = new StringBuilder();
        for (int i = 1; i < split.length; i++) {
            String s = split[i];
            if (s.matches("^[\"'].*[\"']$")) {
                result.add(s.replaceAll("[\"']", ""));
                continue;
            }
            if (!StringUtils.isEmpty(arg.toString())) {
                arg.append(" ");
            }

            if (s.startsWith("\"") && !inString) {
                arg.append(s.replace("\"", ""));
                inString = true;
                doubleQuote = true;
                continue;
            }
            if (s.startsWith("'") && !inString) {
                arg.append(s.replace("'", ""));
                inString = true;
                doubleQuote = false;
                continue;
            }
            if (s.endsWith("\"") && inString && doubleQuote) {
                arg.append(s.replace("\"", ""));
                inString = false;
                result.add(arg.toString());
                arg = new StringBuilder();
                continue;
            }
            if (s.endsWith("'") && inString && !doubleQuote) {
                arg.append(s.replace("'", ""));
                inString = false;
                result.add(arg.toString());
                arg = new StringBuilder();
                continue;
            }
            if (inString) {
                arg.append(s);
                continue;
            }
            result.add(s);
        }
        return result.toArray(new String[0]);
    }

    /**
     * 设置关键字颜色
     *
     * @param key
     * @param start
     * @return
     */
    private void setKeyColor(String key, int start) {
        for (String s : KEYS) {
            int indexOf = key.indexOf(s);
            if (indexOf < 0) {
                continue;
            }
            int liLegnth = indexOf + s.length();
            if (liLegnth == key.length()) {
                //处理单独一个关键字的情况，例如：if else 等
                if (indexOf == 0) {
                    mDoc.setCharacterAttributes(start, s.length(), keyAttr, true);
                }
            }
        }
    }

    /**
     * 处理一行的数据
     *
     * @param start
     * @param end
     */
    private void dealText(int start, int end) {
        String text = "";
        try {
            text = mDoc.getText(start, end - start).toUpperCase();
        } catch (BadLocationException e) {
            log.warn("", e);
        }
        if (StringUtils.isEmpty(text)) {
            return;
        }
        int xStart;
        // 析关键字---
        mDoc.setCharacterAttributes(start, text.length(), normalAttr, true);
        MyStringTokenizer st = new MyStringTokenizer(text);
        while (st.hasMoreTokens()) {
            String s = st.nextToken();
            if (s == null) {
                return;
            }
            xStart = st.getCurrPosition();
            setKeyColor(s.toLowerCase(), start + xStart);
        }
        inputAttributes.addAttributes(normalAttr);
    }

    /**
     * 在进行文本修改的时候
     * 获得光标所在行，只对该行进行处理
     */
    private void dealSingleRow() {
        Element root = mDoc.getDefaultRootElement();
        // 光标当前行
        int cursorPos = this.getCaretPosition();
        // 当前行
        int line = root.getElementIndex(cursorPos);
        Element para = root.getElement(line);
        int start = para.getStartOffset();
        // 除\r字符
        int end = para.getEndOffset() - 1;
        dealText(start, end);
    }

    /**
     * 在初始化面板的时候调用该方法，
     * 查找整个篇幅的关键字
     */
    public void syntaxParse() {
        Element root = mDoc.getDefaultRootElement();
        int li_count = root.getElementCount();
        for (int i = 0; i < li_count; i++) {
            Element para = root.getElement(i);
            int start = para.getStartOffset();
            int end = para.getEndOffset() - 1;// 除\r字符
            dealText(start, end);
        }
    }
}

/**
 * 在分析字符串的同时，记录每个token所在的位置
 */
class MyStringTokenizer extends StringTokenizer {
    String sVal = " ";
    String oldStr, str;
    int mCurrPosition = 0, m_beginPosition = 0;

    MyStringTokenizer(String str) {
        super(str, " ");
        this.oldStr = str;
        this.str = str;
    }

    @Override
    public String nextToken() {
        try {
            String s = super.nextToken();
            int pos;
            if (oldStr.equals(s)) {
                return s;
            }
            pos = str.indexOf(s + sVal);
            if (pos == -1) {
                pos = str.indexOf(sVal + s);
                if (pos == -1) {
                    return null;
                } else {
                    pos += 1;
                }
            }
            int xBegin = pos + s.length();
            str = str.substring(xBegin);
            mCurrPosition = m_beginPosition + pos;
            m_beginPosition = m_beginPosition + xBegin;
            return s;
        } catch (java.util.NoSuchElementException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    /**
     * 返回token在字符串中的位置
     */
    public int getCurrPosition() {
        return mCurrPosition;
    }
}

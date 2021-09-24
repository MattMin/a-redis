package com.mzyupc.aredis.view.textfield;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import com.mzyupc.aredis.utils.RedisPoolManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.utils.DateUtils;
import redis.clients.jedis.Protocol;

import javax.swing.*;
import javax.swing.text.*;
import javax.swing.text.rtf.RTFEditorKit;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

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
    private final static String[] KEYS = Arrays.stream(Protocol.Command.values()).map(item -> item.name().toLowerCase()).toArray(String[]::new);

    private final MutableAttributeSet keyAttr;
    private final MutableAttributeSet normalAttr;
    private final MutableAttributeSet inputAttributes = new RTFEditorKit().getInputAttributes();
    protected StyleContext mContext;
    protected DefaultStyledDocument mDoc;

    /**
     * 是否实现行号，默认不显示
     */
    private boolean showLineNumber = false;
    private int fontSize = 16;//默认为16号字体

    /**
     * 初始化，包括关键字颜色，和非关键字颜色
     */
    public ConsoleCommandTextArea(JBTextArea resultArea, RedisPoolManager redisPoolManager) {
        super();
        mContext = new StyleContext();
        mDoc = new DefaultStyledDocument(mContext);
        this.setDocument(mDoc);
        this.setAutoscrolls(true);
        this.setMargin(JBUI.insetsLeft(5));
        this.setShowLineNumber(true);
        this.setFontSize(14);

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
        // 义关键字显示属性
        keyAttr = new SimpleAttributeSet();
        StyleConstants.setForeground(keyAttr, JBColor.GREEN);
        StyleConstants.setFontSize(keyAttr, 14);
        StyleConstants.setBold(keyAttr, false);
        // 义一般文本显示属性
        normalAttr = new SimpleAttributeSet();
        StyleConstants.setBold(normalAttr, false);
        StyleConstants.setForeground(normalAttr, JBColor.BLACK);
        StyleConstants.setFontSize(normalAttr, 14);
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        StyleConstants.setFontSize(getInputAttributes(), getFontSize());
        if (getShowLineNumber()) {
            drawLineNumber(g);
        }
    }

    public void setShowLineNumber(boolean isShow) {
        this.showLineNumber = isShow;
    }
    public boolean getShowLineNumber() {
        return this.showLineNumber;
    }

    protected void drawLineNumber(Graphics g) {
        setMargin(new Insets(0, 35, 0, 0));
        // 绘制行号的背景色
        g.setColor(new Color(180, 180, 180));
        g.fillRect(0, 0, 30, getHeight());
        // 获得有多少行
        StyledDocument docu = getStyledDocument();
        Element element = docu.getDefaultRootElement();
        int rows = element.getElementCount();
        // 绘制行号的颜色
        //System.out.println("y:" + getY());
        g.setColor(new Color(90, 90, 90));
        g.setFont(new Font(getFont().getName(), getFont().getStyle(), 16));
        for (int row = 0; row < rows; row++) {
            g.drawString((row + 1)+"",2, getPositionY(row + 1));
        }
    }
    public void setFontSize(int fontSize) {
        if(fontSize!=12 &&
                fontSize!=14 &&
                fontSize!=16 &&
                fontSize!=18 &&
                fontSize!=20 &&
                fontSize!=22 &&
                fontSize!=24 ){
            throw new RuntimeException("该行号不能识别");
        }
        this.fontSize = fontSize;
    }
    public int getFontSize() {
        return fontSize;
    }
    /**
     * 获得行号中y坐标的位置<br/>
     * 在计算的过程中，有一个比率值，该比率值是根据getY()的返回值之差决定的。
     * @param row 第几行
     * @return 该行的y坐标位置
     */
    private int getPositionY(int row) {
        int y = 0;
        switch (getFontSize()) {
            case 12:
                y = (row * 18) - 4;
                break;
            case 14:
                y = (row * 20) - 5;
                break;
            case 16:
                y = (row * 23) - 6;
                break;
            case 18:
                y = (row * 26) - 8;
                break;
            case 20:
                y = (row * 29) - 10;
                break;
            case 22:
                y = (row * 31) - 11;
                break;
            case 24:
                y = (row * 34) - 12;
                break;
        }
        return y;
    }

    /**
     * 执行命令
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

            cmd = cmd.trim();
            if (StringUtils.isEmpty(cmd)) {
                return;
            }
            for (String subCmd : cmd.split(";")) {
                subCmd = subCmd.trim();
                if (StringUtils.isEmpty(subCmd)) {
                    continue;
                }

                String[] split = subCmd.split("\\s");
                List<String> result = redisPoolManager.execRedisCommand(0, split[0], Arrays.copyOfRange(split, 1, split.length));
                String text = resultArea.getText();
                text = text + String.format("\n%s  [%s]\n%s\n%s",
                        DateUtils.formatDate(new Date(), "yyyy-MM-dd HH:mm:ss"),
                        subCmd,
                        String.join("\n", result),
                        "----------------------------------------------------"
                );
                resultArea.setText(text);
            }

        }
    }

    /**
     * 设置关键字颜色
     *
     * @param key
     * @param start
     * @param length
     * @return
     */
    private int setKeyColor(String key, int start, int length) {
        for (int i = 0; i < KEYS.length; i++) {
            int indexOf = key.indexOf(KEYS[i]);
            if (indexOf < 0) {
                continue;
            }
            int liLegnth = indexOf + KEYS[i].length();
            if (liLegnth == key.length()) {
                //处理单独一个关键字的情况，例如：if else 等
                if (indexOf == 0) {
                    mDoc.setCharacterAttributes(start, KEYS[i].length(), keyAttr, true);
                }
            }
        }
        return length + 1;
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
            setKeyColor(s.toLowerCase(), start + xStart, s.length());
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

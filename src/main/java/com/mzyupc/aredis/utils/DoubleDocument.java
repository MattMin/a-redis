package com.mzyupc.aredis.utils;


import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;

/**
 * copy from apache package
 */
public class DoubleDocument extends PlainDocument {
    public DoubleDocument() {
    }

    public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
        if (str != null) {
            String curVal = this.getText(0, this.getLength());
            boolean hasDot = curVal.indexOf(46) != -1;
            char[] buffer = str.toCharArray();
            char[] digit = new char[buffer.length];
            int j = 0;
            if (offs == 0 && buffer.length > 0 && buffer[0] == '-') {
                digit[j++] = buffer[0];
            }

            char[] var9 = buffer;
            int var10 = buffer.length;

            for(int var11 = 0; var11 < var10; ++var11) {
                char aBuffer = var9[var11];
                if (Character.isDigit(aBuffer)) {
                    digit[j++] = aBuffer;
                }

                if (!hasDot && aBuffer == '.') {
                    digit[j++] = '.';
                    hasDot = true;
                }
            }

            String added = new String(digit, 0, j);

            try {
                StringBuilder val = new StringBuilder(curVal);
                val.insert(offs, added);
                String valStr = val.toString();
                if (!valStr.equals(".") && !valStr.equals("-") && !valStr.equals("-.")) {
                    Double.valueOf(valStr);
                    super.insertString(offs, added, a);
                } else {
                    super.insertString(offs, added, a);
                }
            } catch (NumberFormatException var13) {
            }

        }
    }

    public void setValue(double d) {
        try {
            this.remove(0, this.getLength());
            this.insertString(0, String.valueOf(d), (AttributeSet)null);
        } catch (BadLocationException var4) {
        }

    }

    public double getValue() {
        try {
            String t = this.getText(0, this.getLength());
            return t != null && t.length() > 0 ? Double.parseDouble(t) : 0.0;
        } catch (BadLocationException var2) {
            throw new RuntimeException(var2.getMessage());
        }
    }
}

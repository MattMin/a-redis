package com.mzyupc.aredis.utils;

import com.alibaba.fastjson.JSON;
import com.mzyupc.aredis.enums.ValueFormatEnum;
import org.apache.commons.lang.StringUtils;

/**
 * @author mzyupc@163.com
 * @date 2021/8/21 4:31 下午
 *
 * 字符串格式化工具
 */
public class FormatUtil {

    public static String format(String text, ValueFormatEnum formatEnum){
        if (formatEnum == null) {
            return text;
        }

        switch (formatEnum) {
            case JSON: return format2Json(text);
            case PLAIN: return text;
            default:
                throw new IllegalArgumentException("Unsupported format");
        }
    }

    private static String format2Json(String text) {
        if (StringUtils.isEmpty(text)) {
            return text;
        }

        return JSON.toJSONString(JSON.parseObject(text), true);
    }
}

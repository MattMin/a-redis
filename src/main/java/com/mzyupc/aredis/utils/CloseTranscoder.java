package com.mzyupc.aredis.utils;


import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;


@Slf4j
public class CloseTranscoder {

	public static void close(Closeable closeable) {
		if (closeable != null) {
			try {
				closeable.close();
			} catch (Exception e) {
				log.error("关闭资源失败", e);
				throw new RuntimeException(e);
			}
		}
	}

}

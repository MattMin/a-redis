package com.mzyupc.aredis.utils;

import java.io.Closeable;

public class CloseTranscoder {

	public static void close(Closeable closeable) {
		if (closeable != null) {
			try {
				closeable.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

}

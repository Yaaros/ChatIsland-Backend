package io.g8.customai.common.constants;

import java.util.List;

public class KnowLedgeEnvs {
    public static final String JEDIS_URL = "47.117.128.101";
    public static final int REDIS_DATABASE_NUM = 4;
    public static final List<String> SUPPORTED_FILES = List.of(
            "md","txt","xls","doc","docx","xlsx","csv","pdf",
            "java","cpp","c","cs","js","ts","vue","py","html","xml","yml","properties"
    );
    public static final String MIN_SCORE = "0.1";
}

package com.xyzw.webhelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.charset.Charset;

@SpringBootApplication
@EnableScheduling
public class WebHelperApplication {
    private static final Logger logger = LoggerFactory.getLogger(WebHelperApplication.class);

    public static void main(String[] args) {
        String fileEncoding = System.getProperty("file.encoding");
        String jnuEncoding = System.getProperty("sun.jnu.encoding");
        String defaultCharset = Charset.defaultCharset().name();
        if (!"UTF-8".equalsIgnoreCase(defaultCharset)
            || !"UTF-8".equalsIgnoreCase(fileEncoding)
            || !"UTF-8".equalsIgnoreCase(jnuEncoding)) {
            logger.warn(
                "当前运行时编码不是 UTF-8：file.encoding={}, sun.jnu.encoding={}, defaultCharset={}。建议增加 JVM 参数 -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8",
                fileEncoding,
                jnuEncoding,
                defaultCharset
            );
        } else {
            logger.info("运行时编码检查通过：UTF-8");
        }
        SpringApplication.run(WebHelperApplication.class, args);
    }
}

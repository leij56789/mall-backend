package com.mall;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;

//(exclude = RabbitAutoConfiguration.class)
@SpringBootApplication
@MapperScan("com.mall.mapper")
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableScheduling
public class MallApplication implements ApplicationRunner {

    public static void main(String[] args) {
        SpringApplication.run(MallApplication.class, args);
    }
    private final ConfigurableEnvironment environment;

    public MallApplication(ConfigurableEnvironment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        System.out.println("========== 支付配置 (pay.*) ==========");

        // 方法1：精确查找 pay 前缀的所有属性
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (source instanceof EnumerablePropertySource) {
                EnumerablePropertySource<?> enumerable = (EnumerablePropertySource<?>) source;
                for (String name : enumerable.getPropertyNames()) {
                    if (name.startsWith("pay.")) {
                        String value = environment.getProperty(name);
                        // 对敏感信息（如密钥）进行脱敏
                        if (name.contains("private-key") || name.contains("secret") || name.contains("key")) {
                            value = maskSensitive(value);
                        }
                        System.out.println(name + " = " + value);
                    }
                }
            }
        }
        System.out.println("=======================================");
    }

    private String maskSensitive(String value) {
        if (value == null || value.length() <= 8) return "****";
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }

}

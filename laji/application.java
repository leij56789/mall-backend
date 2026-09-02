@SpringBootApplication
public class MallBackendApplication implements ApplicationRunner {

    private final ConfigurableEnvironment environment;

    public MallBackendApplication(ConfigurableEnvironment environment) {
        this.environment = environment;
    }

    public static void main(String[] args) {
        SpringApplication.run(MallBackendApplication.class, args);
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
@Aspect
@Component
@Slf4j
public class LogAspect {

    // 从 YAML 注入开关（默认关闭）
    @Value("${call.seq.enabled:false}")
    private boolean callSeqEnabled;

    @PostConstruct
    public void init() {
        CallSeqContext.setEnabled(callSeqEnabled);
    }

    @Around("@annotation(com.mall.common.annotation.Log) || " +
            "@within(org.springframework.stereotype.Service) || " +
            "@within(org.springframework.web.bind.annotation.RestController)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        // 1. 进入方法，生成编号并放入 MDC
        String callSeq = CallSeqContext.enter();

        // 2. 记录入口日志（可选，仅当 callSeq 不为 null 时额外打印）
        if (callSeq != null) {
            log.debug("[{}] 进入 {}", callSeq, pjp.getSignature().toShortString());
        }

        long start = System.currentTimeMillis();
        Object result = null;
        try {
            result = pjp.proceed();
            return result;
        } finally {
            long cost = System.currentTimeMillis() - start;
            if (callSeq != null) {
                log.debug("[{}] 退出 {} | 耗时={}ms", callSeq, pjp.getSignature().toShortString(), cost);
            }
            // 3. 退出方法，弹出栈并恢复父级编号
            CallSeqContext.exit();
        }
    }
}
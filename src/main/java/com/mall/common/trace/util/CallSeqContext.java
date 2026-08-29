package com.mall.common.trace.util;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import java.util.Stack;
@Slf4j
public final class CallSeqContext {

    private static final String MDC_KEY = "callSeq";

    // 是否启用
    private static volatile boolean enabled = false;

    // 当前线程的调用栈（存每层的序号）
    private static final ThreadLocal<Stack<Integer>> STACK = ThreadLocal.withInitial(Stack::new);

    // 🔥 新增：父级编号前缀（用于异步线程继承）
    private static final ThreadLocal<String> PARENT_PREFIX = new ThreadLocal<>();

    private CallSeqContext() {}

    public static void setEnabled(boolean enabled) {
        CallSeqContext.enabled = enabled;
    }

    /**
     * 设置父级编号前缀（子线程异步任务调用）
     * 例如父级是 "1-2"，则子线程的新编号会以 "1-2" 作为前缀
     */
    public static void setParentPrefix(String prefix) {
        if (!enabled) return;
        PARENT_PREFIX.set(prefix);
    }

    /**
     * 进入方法：压栈，生成新编号
     */
    public static String enter() {
        if (!enabled) return null;

        Stack<Integer> stack = STACK.get();
        int seq = stack.size() + 1;
        stack.push(seq);

        String fullSeq = buildFullSeq(stack);
        MDC.put(MDC_KEY, fullSeq);
//        log.info("CurrentSeq={}",fullSeq);
        return fullSeq;
    }

    /**
     * 退出方法：弹栈，恢复父级编号
     */
    public static void exit() {
        if (!enabled) return;

        Stack<Integer> stack = STACK.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }

        if (!stack.isEmpty()) {
            String parentSeq = buildFullSeq(stack);
            MDC.put(MDC_KEY, parentSeq);
        } else {
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * 🔥 构建完整编号：如果有父级前缀，则拼接在栈之前
     */
    private static String buildFullSeq(Stack<Integer> stack) {
        String prefix = PARENT_PREFIX.get();
        StringBuilder sb = new StringBuilder();

        // 如果有父级前缀（异步场景），先加上
        if (prefix != null && !prefix.isEmpty()) {
            sb.append(prefix);
        }

        // 拼接当前栈的数字
        for (int i = 0; i < stack.size(); i++) {
            if (sb.length() > 0) sb.append('-');
            sb.append(stack.get(i));
        }
        return sb.toString();
    }

    /**
     * 获取当前线程的编号（用于传递）
     */
    public static String getCurrentSeq() {
        return MDC.get(MDC_KEY);
    }
    // 🔥 新增：存储当前线程的“正在执行的方法名”
    private static final ThreadLocal<String> CURRENT_METHOD_NAME = new ThreadLocal<>();

    public static void setCurrentMethodName(String methodName) {
        if (enabled) {
            CURRENT_METHOD_NAME.set(methodName);
        }
    }

    public static String getCurrentMethodName() {
        return enabled ? CURRENT_METHOD_NAME.get() : null;
    }


    /**
     * 清理上下文（请求结束后调用）
     */
    public static void clear() {
        if (enabled) {
            STACK.remove();
            PARENT_PREFIX.remove(); // 🔥 必须清理
            MDC.remove(MDC_KEY);
            CURRENT_METHOD_NAME.remove();
        }
    }

    public static boolean isEnabled() {

        return enabled;
    }
}
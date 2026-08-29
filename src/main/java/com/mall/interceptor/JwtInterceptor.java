package com.mall.interceptor;

import com.mall.annotation.Auth;
import com.mall.common.trace.constant.TraceConstants;
import com.mall.common.trace.context.TraceContext;
import com.mall.common.trace.util.CallSeqContext;
import com.mall.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    private static final ThreadLocal<String> currentUserHolder = new ThreadLocal<>();
    private static final ThreadLocal<String> currentUserIdHolder = new ThreadLocal<>();
    private static final ThreadLocal<String> currentTokenHolder = new ThreadLocal<>();

    @Autowired
    JwtUtil jwtUtil;

    public static String getCurrentUser() {
        return currentUserHolder.get();
    }
    public static String getCurrentUserId() {
        return currentUserIdHolder.get();
    }

    public static String getCurrentToken() {
        return currentTokenHolder.get();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. 获取客户端 IP
        String clientIp = getClientIp(request);

        // 2. 获取 User-Agent
        String userAgent = request.getHeader("User-Agent");
        // 1. 获取 token
        String authHeader = request.getHeader("Authorization");

        // 2. 有 token 且格式正确 → 解析并存入 ThreadLocal
        Long userId=null;
        String username=null;
        if (authHeader != null && authHeader.startsWith("Token ")) {
            String token = authHeader.substring(6);
            if (jwtUtil.validateToken(token)) {
                username = jwtUtil.getUsernameFromToken(token);
                userId = jwtUtil.getUserIdFromToken(token);
                currentUserHolder.set(username);
                currentUserIdHolder.set(userId.toString());
                currentTokenHolder.set(token);
            }
        }
        // 3. 初始化 TraceContext（包含审计字段）
        TraceContext.initFromRequest(
                null,                           // traceId（自动生成）
                userId != null ? userId.toString() : TraceConstants.SYSTEM_USER_ID,
                null,                           // tenantId
                null,                           // grayTag
                clientIp,
                userAgent
        );

//        //MDC
//        if(userId!=null){
//            TraceContext.initFromRequest(null,String.valueOf(userId),null,null);
//        }
        // 4. 认证拦截（不变）
        if (handler instanceof HandlerMethod) {
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            Auth auth = handlerMethod.getMethodAnnotation(Auth.class);

            if (auth != null && getCurrentUser() == null) {
                // 需要认证但未登录 → 返回 401
                response.setStatus(401);
                return false;
            }
        }
        return true;
    }
    /**
     * 获取客户端真实 IP（支持代理）
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 如果通过多层代理，取第一个 IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 清理 MDC（已有）
        TraceContext.clear();
        // 清理调用编号上下文
        CallSeqContext.clear();
        currentUserHolder.remove();
        currentTokenHolder.remove();
    }
}
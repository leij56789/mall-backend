package com.mall.interceptor;

import com.mall.annotation.Auth;
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
    private static final ThreadLocal<String> currentTokenHolder = new ThreadLocal<>();

    @Autowired
    JwtUtil jwtUtil;

    public static String getCurrentUser() {
        return currentUserHolder.get();
    }

    public static String getCurrentToken() {
        return currentTokenHolder.get();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. 获取 token
        String authHeader = request.getHeader("Authorization");

        // 2. 有 token 且格式正确 → 解析并存入 ThreadLocal
        if (authHeader != null && authHeader.startsWith("Token ")) {
            String token = authHeader.substring(6);
            if (jwtUtil.validateToken(token)) {
                String username = jwtUtil.getUsernameFromToken(token);
                currentUserHolder.set(username);
                currentTokenHolder.set(token);
            }
        }

        // 3. 无论是否有 token，都放行（让 Controller 自己决定是否需要登录）
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

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        currentUserHolder.remove();
        currentTokenHolder.remove();
    }
}
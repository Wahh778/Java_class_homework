package com.boda.canteen.config;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

public class OnlineUserListener implements HttpSessionListener {

    // 在线用户数量
    private static int onlineCount = 0;

    // 当会话创建时调用
    @Override
    public void sessionCreated(HttpSessionEvent se) {
        onlineCount++;
        System.out.println("新用户上线，当前在线人数: " + onlineCount);
    }

    // 当会话销毁时调用
    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        if (onlineCount > 0) {
            onlineCount--;
        }
        System.out.println("用户下线，当前在线人数: " + onlineCount);
    }

    // 获取在线用户数量
    public static int getOnlineCount() {
        return onlineCount;
    }
}

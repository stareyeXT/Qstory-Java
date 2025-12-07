// == QStory 脚本 ==
// name = 智能回复助手
// type = 1
// version = 1.0
// author = Assistant
// id = com.assistant.smartreply
// time = 2025-12-04

import java.util.Random;

// 全局变量
String ConfigName = "SmartReplySettings";
String EnabledKey = "Enabled"; // 总开关
String ReplyProbabilityKey = "ReplyProbability"; // 回复概率设置
String ApiUrl = "https://oiapi.net/api/FeifeiMsgRob?msg="; // API地址

// 随机数生成器
java.util.Random random = new java.util.Random();

// 监听收到消息
void onMsg(MessageData msg) {
    // 排除自己发送的消息
    if (msg.IsSend) {
        return;
    }
    
    // 获取当前聊天的开关状态
    String chatKey = msg.IsGroup ? msg.GroupUin : msg.PeerUin;
    boolean enabled = getBoolean(ConfigName, chatKey + "_" + EnabledKey, true);
    
    // 如果功能未开启，则不处理
    if (!enabled) {
        return;
    }
    
    // 获取消息内容
    String content = msg.MessageContent;
    
    // 如果消息以"看看"开头，则不处理
    if (content.startsWith("看看")) {
        return;
    }
    
    // 获取回复概率设置，默认为50%
    int replyProbability = getInt(ConfigName, ReplyProbabilityKey, 50);
    
    // 使用随机函数决定是否回复
    if (random.nextInt(100) < replyProbability) {
        try {
            // 调用API获取回复内容，注意不要把自己发送的消息传给API
            String encodedContent = java.net.URLEncoder.encode(content, "UTF-8");
            String apiUrl = ApiUrl + encodedContent;
            String response = httpGet(apiUrl, msg);
            
            // 发送回复消息
            if (msg.IsGroup) {
                // 群聊回复
                sendMsg(msg.GroupUin, "", response);
            } else {
                // 私聊回复
                sendMsg("", msg.PeerUin, response);
            }
        } catch (Exception e) {
            showError(msg, "回复消息失败: " + e.getMessage());
        }
    }
}

// 悬浮窗菜单
void onClickFloatingWindow(int type, String uin) {
    // 获取当前聊天的开关状态
    boolean enabled = getBoolean(ConfigName, uin + "_" + EnabledKey, true);
    addTemporaryItem("智能回复: " + (enabled ? "✔已开启" : "✖已关闭"), "toggleSwitch");
    
    // 获取当前回复概率
    int probability = getInt(ConfigName, ReplyProbabilityKey, 50);
    addTemporaryItem("回复概率: " + probability + "%", "adjustProbability");
    
    // 添加概率调节选项
    addTemporaryItem("增加概率 (+10%)", "increaseProbability");
    addTemporaryItem("减少概率 (-10%)", "decreaseProbability");
    addTemporaryItem("重置概率 (50%)", "resetProbability");
    
    // 帮助信息
    addTemporaryItem("使用帮助", "showHelp");
}

// 功能开关切换
void toggleSwitch(String group, String user, int type) {
    String uin = group.isEmpty() ? user : group;
    String key = uin + "_" + EnabledKey;
    boolean current = getBoolean(ConfigName, key, true);
    putBoolean(ConfigName, key, !current);
    toast("智能回复功能已" + (!current ? "开启" : "关闭"));
}

// 调整回复概率菜单
void adjustProbability(String group, String user, int type) {
    int probability = getInt(ConfigName, ReplyProbabilityKey, 50);
    toast("当前回复概率: " + probability + "%");
}

// 增加回复概率
void increaseProbability(String group, String user, int type) {
    int current = getInt(ConfigName, ReplyProbabilityKey, 50);
    int newProbability = Math.min(100, current + 10); // 最大100%
    putInt(ConfigName, ReplyProbabilityKey, newProbability);
    toast("回复概率已调整为: " + newProbability + "%");
}

// 减少回复概率
void decreaseProbability(String group, String user, int type) {
    int current = getInt(ConfigName, ReplyProbabilityKey, 50);
    int newProbability = Math.max(0, current - 10); // 最小0%
    putInt(ConfigName, ReplyProbabilityKey, newProbability);
    toast("回复概率已调整为: " + newProbability + "%");
}

// 重置回复概率
void resetProbability(String group, String user, int type) {
    putInt(ConfigName, ReplyProbabilityKey, 50);
    toast("回复概率已重置为: 50%");
}

// 显示帮助信息
void showHelp(String group, String user, int type) {
    String helpMessage = "【智能回复助手使用说明】\n"
            + "1. 功能默认开启，可在悬浮窗中单独控制每个聊天窗口的开关\n"
            + "2. 回复概率默认为50%，可通过悬浮窗调节\n"
            + "3. 自动过滤以\"看看\"开头的消息\n"
            + "4. 不会回复自己发送的消息\n"
            + "5. 通过API获取智能回复内容";
    
    if (group != null && !group.isEmpty()) {
        sendMsg(group, "", helpMessage);
    } else {
        sendMsg("", user, helpMessage);
    }
}

// 增强版toast：显示提示并发送错误消息
void showError(MessageData msg, String errorMsg) {
    toast(errorMsg);

    try {
        if (msg.IsGroup) {
            sendMsg(msg.GroupUin, "", "【系统提示】" + errorMsg);
        } else {
            sendMsg("", msg.PeerUin, "【系统提示】" + errorMsg);
        }
    } catch (Exception e) {
        // 发送消息失败时仅保证toast显示
    }
}

// HTTP GET请求
String httpGet(String urlStr, MessageData msg) {
    try {
        java.net.URL url = new java.net.URL(urlStr);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

        int responseCode = conn.getResponseCode();
        if (responseCode != java.net.HttpURLConnection.HTTP_OK) {
            showError(msg, "API请求失败: HTTP " + responseCode);
            return null;
        }

        java.io.InputStream in = conn.getInputStream();
        java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(in, "UTF-8"));

        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }

        reader.close();
        conn.disconnect();

        // 检查API返回的内容是否为错误信息
        String responseStr = response.toString();
        if (isApiErrorResponse(responseStr)) {
            String errorMessage = parseApiErrorMessage(responseStr);
            showError(msg, "API返回错误: " + errorMessage);
            return null;
        }

        // 如果是成功的响应，提取其中的消息内容
        return parseApiSuccessMessage(responseStr);
    } catch (Exception e) {
        showError(msg, "HTTP GET失败: " + e.getMessage());
        return null;
    }
}

// 检查API响应是否为错误信息
boolean isApiErrorResponse(String response) {
    try {
        // 检查是否为JSON格式的响应
        if (response.startsWith("{") && response.endsWith("}")) {
            // 检查是否包含错误字段
            if (response.contains("\"code\"") && response.contains("\"message\"")) {
                // 更精确地检查是否有负数错误代码
                return response.contains("\"code\": -3") || response.contains("\"code\": -1") || response.contains("\"code\": -2");
            }
        }
        return false;
    } catch (Exception e) {
        return false;
    }
}

// 解析API错误信息
String parseApiErrorMessage(String response) {
    try {
        // 这里可以使用JSON解析库来提取错误信息
        // 简化处理，直接通过字符串查找提取错误信息
        int messageIndex = response.indexOf("\"message\"");
        if (messageIndex > -1) {
            int colonIndex = response.indexOf(":", messageIndex);
            if (colonIndex > -1) {
                int startIndex = response.indexOf("\"", colonIndex);
                if (startIndex > -1) {
                    int endIndex = response.indexOf("\"", startIndex + 1);
                    if (endIndex > -1) {
                        return response.substring(startIndex + 1, endIndex);
                    }
                }
            }
        }
        return "未知错误";
    } catch (Exception e) {
        return "解析错误信息失败";
    }
}

// 解析API成功响应中的消息内容
String parseApiSuccessMessage(String response) {
    try {
        // 检查是否为JSON格式的响应
        if (response.startsWith("{") && response.endsWith("}")) {
            // 检查是否包含code和message字段
            if (response.contains("\"code\"") && response.contains("\"message\"")) {
                // 检查code是否为正数（成功状态）
                if (response.contains("\"code\": 1") || response.contains("\"code\": 2") || response.contains("\"code\": 0")) {
                    // 提取message字段内容
                    int messageIndex = response.indexOf("\"message\"");
                    if (messageIndex > -1) {
                        int colonIndex = response.indexOf(":", messageIndex);
                        if (colonIndex > -1) {
                            int startIndex = response.indexOf("\"", colonIndex);
                            if (startIndex > -1) {
                                int endIndex = response.indexOf("\"", startIndex + 1);
                                if (endIndex > -1) {
                                    return response.substring(startIndex + 1, endIndex);
                                }
                            }
                        }
                    }
                    // 如果找到了code但没有正确提取message，返回原始内容
                    return response;
                }
            }
        }
        // 如果不是预期的成功响应格式，返回原始内容
        return response;
    } catch (Exception e) {
        // 出现异常时返回原始内容
        return response;
    }
}
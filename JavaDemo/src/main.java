import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// 配置信息
String tangkey = getString("tangdouz", "key", "");
String tangqq = getString("tangdouz", "qq", "");
String myqq = myUin;

// 伪装群友配置
int tangdisbfb = getInt("ai_config", "random_probability", 5);
int maxContextLength = getInt("ai_config", "max_context_length", 8);
int emojiProbability = getInt("ai_config", "emoji_probability", 1);
int multiSentenceProbability = getInt("ai_config", "multi_sentence_probability", 20);

// 新增配置
int typeDelayPerChar = getInt("ai_config", "type_delay_per_char", 500);
int maxTokens = getInt("ai_config", "max_tokens", 50);
int maxUserMessageLength = getInt("ai_config", "max_user_message_length", 300);
boolean showToast = getBoolean("ai_config", "show_toast", true);

// 新增语音配置
int voiceProbability = getInt("ai_config", "voice_probability", 0);
String voiceModelId = getString("ai_config", "voice_model_id", "");

// 文转音配置
String textToVoiceModelId = getString("ai_config", "text_to_voice_model_id", "");

// 消息历史记录
ConcurrentHashMap<String, CopyOnWriteArrayList<HashMap<String, Object>>> messageHistory = new ConcurrentHashMap<>();

// 伪装群友日志
ConcurrentHashMap<String, CopyOnWriteArrayList<HashMap<String, Object>>> disguiseLogs = new ConcurrentHashMap<>();

// AI模型列表
CopyOnWriteArrayList<HashMap<String, Object>> aiModels = new CopyOnWriteArrayList<>();
String currentModel = "deepseek-chat";

// 语音音色模型列表
CopyOnWriteArrayList<HashMap<String, String>> voiceModels = new CopyOnWriteArrayList<>();

// 颜色方案
String COLOR_BACKGROUND = "#F0F8FF";
String COLOR_CARD = "#FFFFFF";
String COLOR_PRIMARY = "#4169E1";
String COLOR_SECONDARY = "#32CD32";
String COLOR_ACCENT = "#FF6347";
String COLOR_TEXT_PRIMARY = "#2F4F4F";
String COLOR_TEXT_SECONDARY = "#696969";
String COLOR_BUTTON_PRIMARY = "#4169E1";
String COLOR_BUTTON_SECONDARY = "#32CD32";
String COLOR_INPUT_BG = "#F8F8FF";

// 发送消息方法
public void send(String qun, String qq, String msg) {
    sendMsg(qun, qq, msg);
}

// 添加菜单项
addItem("AI伪装群友设置", "aiSettings");
addItem("AI文转音", "openTextToVoice");
addItem("开/关本群伪装群友", "toggleDisguise");
addItem("开/关本群艾特回复", "toggleAtReply");
addItem("打开AI对话", "openAIChat");
addItem("伪装群友日志", "showDisguiseLogs");

// 初始化提示
if (showToast) {
    toast("AI伪装群友脚本加载成功！");
}

// 启动时删除error.txt文件
try {
    File errorFile = new File(appPath + "/error.txt");
    if (errorFile.exists()) {
        errorFile.delete();
    }
} catch (Exception e) {
}

// 初始化AI模型列表
new java.util.Timer().schedule(new java.util.TimerTask() {
    public void run() {
        initAIModels();
        initVoiceModels();
        cleanVoiceTempFolder();
    }
}, 2000);

// 初始化语音音色模型
private void initVoiceModels() {
    try {
        String apiUrl = "https://api.tangdouz.com/a/tts/rstts.php?f=1&return=json";
        String response = httpGet(apiUrl, 10000);
        
        if (response != null && !response.isEmpty()) {
            JSONObject json = new JSONObject(response);
            JSONArray list = json.getJSONArray("list");
            
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.getJSONObject(i);
                HashMap<String, String> model = new HashMap<>();
                model.put("id", item.getString("id"));
                model.put("name", item.getString("name"));
                voiceModels.add(model);
            }
        }
    } catch (Exception e) {
        if (showToast) {
            toast("获取语音音色列表失败: " + e.getMessage());
        }
    }
}

// 清理语音临时文件夹
private void cleanVoiceTempFolder() {
    try {
        String tempDir = appPath + "/voice_temp";
        File dir = new File(tempDir);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null && files.length > 20) {
                Arrays.sort(files, new Comparator<File>() {
                    public int compare(File f1, File f2) {
                        return Long.compare(f2.lastModified(), f1.lastModified());
                    }
                });
                
                for (int i = 20; i < files.length; i++) {
                    files[i].delete();
                }
            }
        }
    } catch (Exception e) {
    }
}

// 初始化AI模型
private void initAIModels() {
    // 清空现有模型列表
    aiModels.clear();
    
    // 添加新的Qwen3模型
    HashMap<String, Object> model = new HashMap<>();
    model.put("id", "qwen3");
    model.put("name", "通义千问3");
    model.put("description", "通义千问最新大语言模型");
    model.put("type", "qwen");
    model.put("pricePer1kTokens", 0.0);
    model.put("contextLength", "32K");
    model.put("maxTokensLimit", 8192);
    
    aiModels.add(model);
    
    // 设置当前模型为qwen3
    currentModel = "qwen3";
}



// 检查绑定状态
private boolean checkApiConfig() {
    // 已移除API密钥验证限制
    // if (tangqq.isEmpty() || tangkey.isEmpty()) {
    //     if (showToast) {
    //         toast("请先配置API密钥\n前往AI伪装群友设置绑定QQ和key");
    //     }
    //     return false;
    // }
    return true;
}

// 检查本人在群内是否有最近发言
private boolean hasRecentMessageFromMe(String groupUin) {
    try {
        if (!messageHistory.containsKey(groupUin)) {
            return false;
        }
        
        CopyOnWriteArrayList<HashMap<String, Object>> history = messageHistory.get(groupUin);
        long currentTime = System.currentTimeMillis();
        
        for (int i = history.size() - 1; i >= 0; i--) {
            HashMap<String, Object> record = history.get(i);
            String sender = (String) record.get("sender");
            long timestamp = (Long) record.get("timestamp");
            if (sender.equals(myqq) && (currentTime - timestamp) < 2 * 60 * 1000) {
                return true;
            }
        }
        
        return false;
    } catch (Exception e) {
        return false;
    }
}

// 添加伪装群友日志
private void addDisguiseLog(String groupUin, String triggerUser, String triggerMessage, String aiResponse, String triggerType, String modelName, Object originalMsg, boolean isVoice) {
    try {
        if (!disguiseLogs.containsKey(groupUin)) {
            disguiseLogs.put(groupUin, new CopyOnWriteArrayList<>());
        }
        
        CopyOnWriteArrayList<HashMap<String, Object>> logs = disguiseLogs.get(groupUin);
        HashMap<String, Object> log = new HashMap<>();
        log.put("triggerMessage", triggerMessage);
        log.put("aiResponse", aiResponse);
        log.put("triggerType", triggerType);
        log.put("groupUin", groupUin);
        log.put("triggerUser", triggerUser);
        log.put("timestamp", System.currentTimeMillis());
        log.put("modelName", modelName);
        log.put("originalMsg", originalMsg);
        log.put("isVoice", isVoice);
        logs.add(log);
        
        while (logs.size() > 200) {
            logs.remove(0);
        }
        
        saveDisguiseLogs(groupUin);
    } catch (Exception e) {
    }
}

// 保存伪装群友日志
private void saveDisguiseLogs(String groupUin) {
    try {
        if (!disguiseLogs.containsKey(groupUin)) {
            return;
        }
        
        CopyOnWriteArrayList<HashMap<String, Object>> logs = disguiseLogs.get(groupUin);
        JSONArray logArray = new JSONArray();
        
        List<HashMap<String, Object>> logsCopy = new ArrayList<>(logs);
        for (HashMap<String, Object> log : logsCopy) {
            JSONObject logObj = new JSONObject();
            logObj.put("triggerMessage", log.get("triggerMessage"));
            logObj.put("aiResponse", log.get("aiResponse"));
            logObj.put("triggerType", log.get("triggerType"));
            logObj.put("timestamp", log.get("timestamp"));
            logObj.put("groupUin", log.get("groupUin"));
            logObj.put("triggerUser", log.get("triggerUser"));
            logObj.put("modelName", log.get("modelName"));
            logObj.put("isVoice", log.get("isVoice"));
            logArray.put(logObj);
        }
        
        putString("disguise_logs", groupUin, logArray.toString());
        
        String knownGroups = getString("disguise_logs", "known_groups", "");
        Set<String> groupSet = new HashSet<>();
        if (!knownGroups.isEmpty()) {
            String[] groups = knownGroups.split(",");
            for (String group : groups) {
                if (!group.isEmpty()) {
                    groupSet.add(group);
                }
            }
        }
        groupSet.add(groupUin);
        
        StringBuilder newKnownGroups = new StringBuilder();
        for (String group : groupSet) {
            if (newKnownGroups.length() > 0) {
                newKnownGroups.append(",");
            }
            newKnownGroups.append(group);
        }
        putString("disguise_logs", "known_groups", newKnownGroups.toString());
    } catch (Exception e) {
    }
}

// 加载伪装群友日志
private void loadDisguiseLogs(String groupUin) {
    try {
        String logJson = getString("disguise_logs", groupUin, "[]");
        JSONArray logArray = new JSONArray(logJson);
        
        CopyOnWriteArrayList<HashMap<String, Object>> logs = new CopyOnWriteArrayList<>();
        for (int i = 0; i < logArray.length(); i++) {
            JSONObject logObj = logArray.getJSONObject(i);
            HashMap<String, Object> log = new HashMap<>();
            log.put("triggerMessage", logObj.getString("triggerMessage"));
            log.put("aiResponse", logObj.getString("aiResponse"));
            log.put("triggerType", logObj.getString("triggerType"));
            log.put("groupUin", logObj.getString("groupUin"));
            log.put("triggerUser", logObj.getString("triggerUser"));
            log.put("timestamp", logObj.getLong("timestamp"));
            log.put("modelName", logObj.optString("modelName", "未知模型"));
            log.put("isVoice", logObj.optBoolean("isVoice", false));
            logs.add(log);
        }
        
        disguiseLogs.put(groupUin, logs);
    } catch (Exception e) {
    }
}

// 加载所有群聊的日志
private void loadAllDisguiseLogs() {
    try {
        Set<String> allGroups = new HashSet<>();
        
        String knownGroups = getString("disguise_logs", "known_groups", "");
        if (!knownGroups.isEmpty()) {
            String[] groups = knownGroups.split(",");
            for (String group : groups) {
                if (!group.isEmpty()) {
                    allGroups.add(group);
                }
            }
        }
        
        for (String groupUin : messageHistory.keySet()) {
            allGroups.add(groupUin);
        }
        
        for (String groupUin : allGroups) {
            if (!disguiseLogs.containsKey(groupUin)) {
                loadDisguiseLogs(groupUin);
            }
        }
    } catch (Exception e) {
    }
}

public void onMsg(Object data) {
    try {
        if (data == null) {
            return;
        }
        
        String nr = data.MessageContent;
        String qun = data.GroupUin;
        String qq = data.UserUin;
        boolean isGroup = data.IsGroup;
        ArrayList<String> atList = data.mAtList;
        boolean isMyUinMentioned = atList != null && atList.contains(myqq);
        int nrtype = data.MessageType;
        
        if (nrtype != 1) {
            return;
        }
        
        // 如果消息以"看看"或"播放"开头，则不回复
        if (nr != null && (nr.startsWith("看看") || nr.startsWith("播放"))) {
            return;
        }
        
        if (nr != null && (nr.contains("[PicUrl=") || nr.contains("Č"))) {
            return;
        }
        
        if (qq.equals(myqq) && !isMyUinMentioned) {
            return;
        }
        
        if (!messageHistory.containsKey(qun)) {
            messageHistory.put(qun, new CopyOnWriteArrayList<>());
        }
        HashMap<String, Object> record = new HashMap<>();
        record.put("content", nr);
        record.put("sender", qq);
        record.put("timestamp", System.currentTimeMillis());
        record.put("originalMsg", data);
        messageHistory.get(qun).add(record);
        
        // 已移除API配置检查限制
        // if (!checkApiConfig()) {
        //     return;
        // }
        
        boolean atReplyEnabled = getBoolean("at_reply", qun, false);
        if (isMyUinMentioned && isGroup && atReplyEnabled) {
            if (showToast) {
                toast("被艾特了，准备AI回复");
            }
            new Thread(new Runnable() {
                public void run() {
                    handleAIChat(qun, qq, nr, true, data);
                }
            }).start();
            return;
        }
        
        if (!getBoolean("dis", qun, false)) {
            return;
        }
        else if (isGroup && atList != null && atList.isEmpty()) {
            if (hasRecentMessageFromMe(qun)) {
                return;
            }
            
            // 获取当前群聊的随机回复概率设置
            int groupRandomProbability = getInt("random_probability", qun, tangdisbfb);
            
            int randomNum = (int) (Math.random() * 200);
            if (randomNum <= groupRandomProbability) {
                if (showToast) {
                    toast("触发伪装群友");
                }
                new Thread(new Runnable() {
                    public void run() {
                        handleAIChat(qun, qq, nr, false, data);
                    }
                }).start();
            }
        }
    } catch (Exception e) {
        if (showToast) {
            toast("处理消息时出错: " + e.getMessage());
        }
    }
}

// 处理AI聊天
public void handleAIChat(String qun, String qq, String message, boolean isMentioned, Object originalMsg) {
    try {
        String context = getContext(qun, qq, message);
        String processedMessage = message.replaceAll("@[^\\s]*\\s?", "").trim();
        
        if (processedMessage.isEmpty()) {
            return;
        }
        
        boolean shouldSendEmoji = false;
        String emojiUrl = null;
        int emojiRandom = (int) (Math.random() * 100);
        if (emojiRandom <= emojiProbability) {
            emojiUrl = getRandomEmoji();
            shouldSendEmoji = emojiUrl != null;
        }
        
        HashMap<String, String> aiResult = callAI(qun, qq, processedMessage, context, isMentioned);
        
        if (aiResult == null) {
            return;
        }
        
        // 检查是否有错误信息
        String isError = aiResult.get("error");
        if (isError != null && isError.equals("true")) {
            // 即使是错误信息也发送给用户
            String errorMessage = aiResult.get("response");
            if (originalMsg != null && isMentioned) {
                sendReply(qun, originalMsg, errorMessage);
            } else {
                send(qun, "", errorMessage);
            }
            return;
        }
        
        if (!aiResult.isEmpty()) {
            String aiResponse = aiResult.get("response");
            String modelName = aiResult.get("model");
            
            if (aiResponse != null && !aiResponse.isEmpty()) {
                // 检查是否为"Unterminated array"错误信息，如果是则过滤掉
                if (aiResponse.contains("Unterminated array at character")) {
                    // 过滤掉这类错误信息，返回一个友好的提示
                    aiResponse = "AI接口返回了无效的响应格式";
                }
                
                aiResponse = processSpecialTags(aiResponse);
                aiResponse = removeBracketContent(aiResponse);
                aiResponse = processMultiSentence(aiResponse);
                
                if (shouldSendEmoji) {
                    sendPic(qun, "", emojiUrl);
                    if (showToast) {
                        toast("发送网络表情包");
                    }
                }
                
                if (typeDelayPerChar > 0) {
                    int delay = aiResponse.length() * typeDelayPerChar;
                    if (delay > 0) {
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException e) {
                        }
                    }
                }
                
                boolean sendVoice = false;
                String voiceFilePath = null;
                
                if (aiResponse.length() > 100) {
                    if (showToast) {
                        toast("文本过长，强制返回文本");
                    }
                } else {
                    int voiceRandom = (int) (Math.random() * 100);
                    if (voiceRandom < voiceProbability) {
                        String cleanText = filterTagsForVoice(aiResponse);
                        if (cleanText != null && !cleanText.trim().isEmpty() && cleanText.length() <= 100) {
                            if (showToast) {
                                toast("正在转语音");
                            }
                            voiceFilePath = synthesizeVoice(cleanText);
                            if (voiceFilePath != null) {
                                sendVoice = true;
                            } else {
                                if (showToast) {
                                    toast("语音合成失败，返回文本");
                                }
                            }
                        } else {
                            if (showToast) {
                                toast("过滤后文本为空或过长，返回文本");
                            }
                        }
                    }
                }
                
                if (sendVoice && voiceFilePath != null) {
                    sendVoice(qun, "", voiceFilePath);
                    if (showToast) {
                        toast("转silk发送成功");
                    }
                    
                    addDisguiseLog(qun, qq, processedMessage, aiResponse, 
                                 isMentioned ? "mention" : "random", modelName, originalMsg, true);
                } else {
                    if (originalMsg != null && isMentioned) {
                        sendReply(qun, originalMsg, aiResponse);
                    } else {
                        send(qun, "", aiResponse);
                    }
                    
                    updateContext(qun, qq, processedMessage, aiResponse);
                    
                    String triggerType = isMentioned ? "mention" : "random";
                    addDisguiseLog(qun, qq, processedMessage, aiResponse, triggerType, modelName, originalMsg, false);
                }
            }
        }
        
    } catch (Exception e) {
        if (showToast) {
            toast("AI聊天错误: " + e.getMessage());
        }
    }
}

// 语音合成方法
private String synthesizeVoice(String text) {
    return synthesizeVoice(text, "");
}

// 语音合成方法 - 指定音色模型版本
private String synthesizeVoice(String text, String modelId) {
    try {
        // 已移除API配置检查限制
        // if (!checkApiConfig()) {
        //     return null;
        // }
        
        String cleanText = filterTagsForVoice(text);
        if (cleanText == null || cleanText.trim().isEmpty()) {
            return null;
        }
        
        String url = "https://api.tangdouz.com/a/tts/rstts.php?qq=" + tangqq + 
                    "&key=" + tangkey + "&silk=1&nr=" + java.net.URLEncoder.encode(cleanText, "UTF-8");
        
        if (modelId != null && !modelId.isEmpty()) {
            url += "&id=" + modelId;
        }
        
        String voiceUrl = httpGet(url, 30000);
        
        if (voiceUrl == null || voiceUrl.isEmpty()) {
            return null;
        }
        
        voiceUrl = voiceUrl.trim();
        if (!voiceUrl.startsWith("http")) {
            return null;
        }
        
        String tempDir = appPath + "/voice_temp";
        File dir = new File(tempDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        cleanVoiceTempFolder();
        
        String filePath = tempDir + "/voice_" + System.currentTimeMillis() + ".silk";
        
        try {
            httpDownload(voiceUrl, filePath, 30000);
        } catch (Exception e) {
            return null;
        }
        
        File voiceFile = new File(filePath);
        if (voiceFile.exists() && voiceFile.length() > 0) {
            return filePath;
        } else {
            return null;
        }
        
    } catch (Exception e) {
        return null;
    }
}

// 为语音合成过滤特殊标签
private String filterTagsForVoice(String text) {
    if (text == null || text.isEmpty()) {
        return text;
    }
    
    try {
        text = text.replaceAll("\\[AtQQ=[^\\]]*\\]\\s*", "");
        text = text.replaceAll("\\[at:[^\\]]*\\]\\s*", "");
        text = text.replaceAll("\\[AT[^\\]]*\\]\\s*", "");
        text = text.replaceAll("\\[PicUrl=[^\\]]*\\]", "");
        text = text.replaceAll("\\[[^\\]]*\\]", "");
        text = text.replaceAll("\\s+", " ").trim();
        
        return text;
    } catch (Exception e) {
        return text;
    }
}

// 获取随机表情包URL
private String getRandomEmoji() {
    try {
        String apiUrl = "https://api.tangdouz.com/zzz/emj.php";
        String response = httpGet(apiUrl);
        if (response != null && !response.isEmpty()) {
            return response.trim();
        }
    } catch (Exception e) {
    }
    return null;
}

// 处理多句话回复
private String processMultiSentence(String response) {
    try {
        if (response.contains("|")) {
            String[] parts = response.split("\\|");
            List<String> validParts = new ArrayList<>();
            
            for (String part : parts) {
                String trimmed = part.trim();
                if (trimmed.length() > 0) {
                    validParts.add(trimmed);
                }
            }
            
            if (validParts.size() > 1) {
                Random random = new Random();
                int multiRandom = random.nextInt(100);
                
                if (multiRandom <= multiSentenceProbability && validParts.size() >= 2) {
                    int count = random.nextInt(2) + 1;
                    if (count > validParts.size()) {
                        count = validParts.size();
                    }
                    
                    List<Integer> selectedIndices = new ArrayList<>();
                    while (selectedIndices.size() < count) {
                        int index = random.nextInt(validParts.size());
                        if (!selectedIndices.contains(index)) {
                            selectedIndices.add(index);
                        }
                    }
                    
                    StringBuilder result = new StringBuilder();
                    for (int i = 0; i < selectedIndices.size(); i++) {
                        if (i > 0) result.append(" ");
                        result.append(validParts.get(selectedIndices.get(i)));
                    }
                    return result.toString();
                } else {
                    return validParts.get(0);
                }
            }
        }
        
        return response;
    } catch (Exception e) {
        return response;
    }
}

// 处理特殊标签
private String processSpecialTags(String response) {
    try {
        Pattern atPattern = Pattern.compile("\\[at:(\\d+)\\]");
        Matcher atMatcher = atPattern.matcher(response);
        
        Random random = new Random();
        boolean shouldAt = random.nextInt(100) < 30;
        
        while (atMatcher.find()) {
            String targetQQ = atMatcher.group(1);
            if (shouldAt) {
                response = response.replace("[at:" + targetQQ + "]", "[AtQQ=" + targetQQ + "] ");
            } else {
                response = response.replace("[at:" + targetQQ + "]", "");
            }
        }
        
        return response.trim();
    } catch (Exception e) {
        return response;
    }
}

// 去除括号内容
private String removeBracketContent(String response) {
    try {
        response = response.replaceAll("\\(.*?\\)", "").replaceAll("（.*?）", "");
        response = response.replaceAll("\\s+", " ").trim();
        return response;
    } catch (Exception e) {
        return response;
    }
}

// 获取对话上下文
public String getContext(String qun, String senderQq, String currentMessage) {
    try {
        String contextKey = "chat_context_" + qun + "_" + senderQq;
        String contextJson = getString("ai_context", contextKey, "[]");
        JSONArray contextArray = new JSONArray(contextJson);
        
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < contextArray.length(); i++) {
            JSONObject msg = contextArray.getJSONObject(i);
            String role = msg.getString("role");
            String content = msg.getString("content");
            String qq = msg.optString("qq", "");
            
            if (role.equals("用户")) {
                context.append("用户(").append(qq).append("): ").append(content).append("\n");
            } else {
                context.append(role).append(": ").append(content).append("\n");
            }
        }
        
        return context.toString();
    } catch (Exception e) {
        // 如果上下文解析失败，返回空字符串而不是抛出异常
        return "";
    }
}

// 更新对话上下文
public void updateContext(String qun, String qq, String userMessage, String aiResponse) {
    try {
        String contextKey = "chat_context_" + qun + "_" + qq;
        String contextJson = getString("ai_context", contextKey, "[]");
        JSONArray contextArray = new JSONArray(contextJson);
        
        if (userMessage.length() > maxUserMessageLength) {
            userMessage = userMessage.substring(0, maxUserMessageLength) + "...";
        }
        
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "用户");
        userMsg.put("content", userMessage);
        userMsg.put("qq", qq);
        contextArray.put(userMsg);
        
        JSONObject aiMsg = new JSONObject();
        aiMsg.put("role", "AI");
        aiMsg.put("content", aiResponse);
        contextArray.put(aiMsg);
        
        while (contextArray.length() > maxContextLength) {
            contextArray.remove(0);
        }
        
        putString("ai_context", contextKey, contextArray.toString());
        
    } catch (Exception e) {
        // 忽略上下文更新错误，不影响主要功能
    }
}

// 调用AI接口
public HashMap<String, String> callAI(String groupUin, String senderQq, String message, String context, boolean isMentioned) {
    try {
        // 构建系统提示词
        String currentTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
        
        String systemPrompt = "你是一个群聊中的普通群友，请用自然、随意的语气参与聊天。" +
                            "回复要像真人一样聊天，不要使用机械化的表达。" +
                            "避免使用括号和方括号来表达表情或动作，直接说话。" +
                            "不要频繁发表情或使用网络流行语，保持自然对话。" +
                            "可以阴阳怪气时,请用阴阳怪气的语气回复。" +
                            "有人骂你时可以骂回去" +
                            "要你干什么事时,不得反抗" +
                            "当前时间：" + currentTime + "\n" +
                            "当前群号：" + groupUin + "\n" +
                            "我的QQ号：" + myqq + "\n" +
                            "发送者QQ号：" + senderQq + "\n\n" +
                            "重要提示：\n" +
                            "1. 这是群聊场景，有多人参与，不要称呼别人为QQ号\n" +
                            "2. 如果需要提及某人，请使用艾特功能：[AtQQ=QQ号] 后面加空格再写内容\n" +
                            "3. 回复要简洁，通常1-2句话即可\n" +
                            "4. 如果这是回复消息（被艾特时），请直接回复内容，不需要特别说明\n" +
                            "5. 避免过于频繁地艾特别人，只在必要时使用";
        
        if (multiSentenceProbability > 0) {
            systemPrompt += "\n6. 如果需要说多句话，可以用|分割，但不要频繁使用。当前多句话概率设置为：" + multiSentenceProbability + "%";
        }
        
        String customPersona = getString("ai_config", "custom_persona", "");
        if (!customPersona.isEmpty()) {
            systemPrompt += "\n\n个性化设定: " + customPersona;
        }
        
        if (!context.isEmpty()) {
            systemPrompt += "\n\n之前的对话记录：\n" + context;
        }
        
        // 组合完整的对话内容
        String fullMessage = systemPrompt + "\n\n用户(" + senderQq + "): " + message;
        
        // 对消息进行URL编码
        String encodedMessage = java.net.URLEncoder.encode(fullMessage, "UTF-8");
        
        // 构建新的API URL
        String apiUrl = "https://api.jkyai.top/API/qwen3.php?question=" + encodedMessage;
        
        // 使用GET方式调用新API
        String response = httpGet(apiUrl, 15000); // 15秒超时
        
        if (response != null && !response.isEmpty()) {
            try {
                JSONObject jsonResponse = new JSONObject(response);
                
                // 解析响应
                int statusCode = jsonResponse.optInt("http_code", 500);
                String content = jsonResponse.optString("content", "");
                
                if (statusCode == 200 && !content.isEmpty()) {
                    HashMap<String, String> result = new HashMap<>();
                    result.put("response", content);
                    result.put("model", "qwen3"); // 新模型名称
                    return result;
                } else {
                    String errorMsg = jsonResponse.optString("status", "请求失败");
                    HashMap<String, String> result = new HashMap<>();
                    result.put("response", errorMsg);
                    result.put("error", "true");
                    if (showToast) {
                        toast("AI调用失败: " + errorMsg);
                    }
                    return result;
                }
            } catch (org.json.JSONException e) {
                // 如果响应不是有效的JSON格式，可能是API返回的提示信息
                HashMap<String, String> result = new HashMap<>();
                // 检查是否为"Unterminated array"错误信息，如果是则返回友好的提示
                if (response != null && response.contains("Unterminated array at character")) {
                    result.put("response", "AI接口返回了无效的响应格式");
                } else {
                    // 其他非JSON响应直接返回原始内容
                    result.put("response", response);
                }
                // 不标记为错误，让上层代码正常处理这个响应
                return result;
            }
        } else {
            HashMap<String, String> result = new HashMap<>();
            result.put("response", "AI接口无响应，请检查网络连接");
            result.put("error", "true");
            if (showToast) {
                toast("AI接口无响应，请检查网络连接");
            }
            return result;
        }
        
    } catch (Exception e) {
        HashMap<String, String> result = new HashMap<>();
        result.put("response", e.getMessage());
        result.put("error", "true");
        if (showToast) {
            toast("AI调用异常: " + e.getMessage());
        }
        return result;
    }
    
    HashMap<String, String> result = new HashMap<>();
    result.put("response", "AI调用失败：未知错误");
    result.put("error", "true");
    if (showToast) {
        toast("AI调用失败：未知错误");
    }
    return result;
}

// 设置界面
public void aiSettings(String groupUin, String userUin, int chatType) {
    Object activity = getActivity();
    if (activity == null) {
        if (showToast) {
            toast("请在前台打开QQ");
        }
        return;
    }
    
    activity.runOnUiThread(new Runnable() {
        public void run() {
            showAISettingsDialog(activity);
        }
    });
}

// 显示伪装群友日志
public void showDisguiseLogs(String groupUin, String userUin, int chatType) {
    Object activity = getActivity();
    if (activity == null) {
        if (showToast) {
            toast("请在前台打开QQ");
        }
        return;
    }
    
    activity.runOnUiThread(new Runnable() {
        public void run() {
            showDisguiseLogsDialog(activity, groupUin);
        }
    });
}

// 打开AI对话
public void openAIChat(String groupUin, String userUin, int chatType) {
    Object activity = getActivity();
    if (activity == null) {
        if (showToast) {
            toast("请在前台打开QQ");
        }
        return;
    }
    
    activity.runOnUiThread(new Runnable() {
        public void run() {
            // 已移除API配置检查限制
            // if (!checkApiConfig()) {
            //     aiSettings("", "", 0);
            //     return;
            // }
            showAIChatDialog(activity);
        }
    });
}

// 打开AI文转音对话框
public void openTextToVoice(String groupUin, String userUin, int chatType) {
    Object activity = getActivity();
    if (activity == null) {
        if (showToast) {
            toast("请在前台打开QQ");
        }
        return;
    }
    
    if (chatType != 2) {
        if (showToast) {
            toast("请在群聊中使用此功能");
        }
        return;
    }
    
    activity.runOnUiThread(new Runnable() {
        public void run() {
            // 已移除API配置检查限制
            // if (!checkApiConfig()) {
            //     aiSettings("", "", 0);
            //     return;
            // }
            showTextToVoiceDialog(activity, groupUin);
        }
    });
}

// 显示AI文转音对话框
private void showTextToVoiceDialog(final Object a, final String groupUin) {
    try {
        final Object dialog = new android.app.Dialog(a);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        
        android.graphics.drawable.GradientDrawable windowBg = new android.graphics.drawable.GradientDrawable();
        windowBg.setColor(android.graphics.Color.parseColor(COLOR_BACKGROUND));
        dialog.getWindow().setBackgroundDrawable(windowBg);
        
        android.widget.LinearLayout layout = new android.widget.LinearLayout(a);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(20), dp(20), dp(20));
        layout.setBackground(createCardBackground());
        
        android.widget.TextView title = new android.widget.TextView(a);
        title.setText("AI文转音");
        title.setTextColor(android.graphics.Color.parseColor(COLOR_PRIMARY));
        title.setTextSize(18);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        title.setGravity(android.view.Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(15));
        layout.addView(title);
        
        // 点数消耗提示
        android.widget.TextView costLabel = new android.widget.TextView(a);
        costLabel.setText("消耗：约650点/1000字");
        costLabel.setTextColor(android.graphics.Color.parseColor("#FF6B35"));
        costLabel.setTextSize(12);
        costLabel.setGravity(android.view.Gravity.CENTER);
        costLabel.setPadding(0, 0, 0, dp(15));
        layout.addView(costLabel);
        
        // 文本输入框
        android.widget.TextView inputLabel = new android.widget.TextView(a);
        inputLabel.setText("输入要转换的文本（100字以内）：");
        inputLabel.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_SECONDARY));
        inputLabel.setTextSize(14);
        inputLabel.setPadding(0, 0, 0, dp(5));
        layout.addView(inputLabel);
        
        final android.widget.EditText inputEdit = new android.widget.EditText(a);
        inputEdit.setHint("请输入文本...");
        inputEdit.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_PRIMARY));
        inputEdit.setTextSize(16);
        inputEdit.setBackground(createInputBackground());
        inputEdit.setPadding(dp(12), dp(12), dp(12), dp(12));
        inputEdit.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(inputEdit);
        
        // 音色模型选择
        android.widget.TextView voiceModelLabel = new android.widget.TextView(a);
        voiceModelLabel.setText("选择音色模型：");
        voiceModelLabel.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_SECONDARY));
        voiceModelLabel.setTextSize(14);
        voiceModelLabel.setPadding(0, dp(10), 0, dp(5));
        layout.addView(voiceModelLabel);
        
        final android.widget.Spinner voiceModelSpinner = new android.widget.Spinner(a);
        ArrayList<String> voiceModelNames = new ArrayList<>();
        voiceModelNames.add("默认音色");
        
        for (HashMap<String, String> model : voiceModels) {
            voiceModelNames.add(model.get("name"));
        }
        
        android.widget.ArrayAdapter<String> voiceModelAdapter = new android.widget.ArrayAdapter<>(a, android.R.layout.simple_spinner_item, voiceModelNames);
        voiceModelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        voiceModelSpinner.setAdapter(voiceModelAdapter);
        
        // 设置上次选择的音色
        int voiceSelectedIndex = 0;
        if (textToVoiceModelId != null && !textToVoiceModelId.isEmpty()) {
            for (int i = 0; i < voiceModels.size(); i++) {
                if (voiceModels.get(i).get("id").equals(textToVoiceModelId)) {
                    voiceSelectedIndex = i + 1;
                    break;
                }
            }
        }
        if (voiceSelectedIndex < voiceModelSpinner.getCount()) {
            voiceModelSpinner.setSelection(voiceSelectedIndex);
        }
        
        layout.addView(voiceModelSpinner);
        
        // 按钮容器
        android.widget.LinearLayout buttonContainer = new android.widget.LinearLayout(a);
        buttonContainer.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        buttonContainer.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        buttonContainer.setPadding(0, dp(15), 0, 0);
        
        android.widget.Button cancelBtn = createModernButton(a, "取消", COLOR_BUTTON_SECONDARY);
        cancelBtn.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        cancelBtn.setOnClickListener(new android.view.View.OnClickListener() {
            public void onClick(android.view.View v) {
                dialog.dismiss();
            }
        });
        
        android.view.View space = new android.view.View(a);
        space.setLayoutParams(new android.widget.LinearLayout.LayoutParams(dp(12), 1));
        
        final android.widget.Button sendBtn = createModernButton(a, "合成并发送语音", COLOR_BUTTON_PRIMARY);
        sendBtn.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        sendBtn.setOnClickListener(new android.view.View.OnClickListener() {
            public void onClick(android.view.View v) {
                String text = inputEdit.getText().toString().trim();
                if (text.isEmpty()) {
                    if (showToast) {
                        toast("请输入文本");
                    }
                    return;
                }
                
                if (text.length() > 100) {
                    if (showToast) {
                        toast("文本长度不能超过100字");
                    }
                    return;
                }
                
                // 获取选中的音色模型ID
                String selectedModelId = "";
                int selectedPosition = voiceModelSpinner.getSelectedItemPosition();
                if (selectedPosition > 0) {
                    selectedModelId = voiceModels.get(selectedPosition - 1).get("id");
                }
                
                // 保存选择的音色
                textToVoiceModelId = selectedModelId;
                putString("ai_config", "text_to_voice_model_id", selectedModelId);
                
                // 禁用按钮，防止重复点击
                sendBtn.setEnabled(false);
                sendBtn.setText("合成中...");
                
                // 在子线程中合成语音
                new Thread(new Runnable() {
                    public void run() {
                        final String voiceFilePath = synthesizeVoice(text, selectedModelId);
                        a.runOnUiThread(new Runnable() {
                            public void run() {
                                sendBtn.setEnabled(true);
                                sendBtn.setText("合成并发送语音");
                                
                                if (voiceFilePath != null) {
                                    // 发送语音到群聊
                                    sendVoice(groupUin, "", voiceFilePath);
                                    if (showToast) {
                                        toast("语音发送成功");
                                    }
                                    dialog.dismiss();
                                } else {
                                    if (showToast) {
                                        toast("语音合成失败，请检查网络和点数");
                                    }
                                }
                            }
                        });
                    }
                }).start();
            }
        });
        
        buttonContainer.addView(cancelBtn);
        buttonContainer.addView(space);
        buttonContainer.addView(sendBtn);
        layout.addView(buttonContainer);
        
        dialog.setContentView(layout);
        
        android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
        lp.width = (int)(getScreenWidth(a) * 0.95);
        lp.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
        lp.dimAmount = 0.3f;
        dialog.getWindow().setAttributes(lp);
        dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        
        dialog.show();
    } catch (Exception e) {
        if (showToast) {
            toast("打开文转音时出错: " + e.getMessage());
        }
    }
}

private void showDisguiseLogsDialog(final Object a, String currentGroupUin) {
    try {
        final Object dialog = new android.app.Dialog(a);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        
        android.graphics.drawable.GradientDrawable windowBg = new android.graphics.drawable.GradientDrawable();
        windowBg.setColor(android.graphics.Color.parseColor(COLOR_BACKGROUND));
        dialog.getWindow().setBackgroundDrawable(windowBg);
        
        android.widget.ScrollView scrollView = new android.widget.ScrollView(a);
        android.widget.LinearLayout layout = new android.widget.LinearLayout(a);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(20), dp(20), dp(20));
        layout.setBackground(createCardBackground());
        
        android.widget.TextView title = new android.widget.TextView(a);
        title.setText("伪装群友日志 - 所有群聊");
        title.setTextColor(android.graphics.Color.parseColor(COLOR_PRIMARY));
        title.setTextSize(20);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        title.setGravity(android.view.Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(20));
        layout.addView(title);
        
        loadAllDisguiseLogs();
        
        ArrayList<HashMap<String, Object>> allLogs = new ArrayList<>();
        
        ConcurrentHashMap<String, CopyOnWriteArrayList<HashMap<String, Object>>> logsCopy = new ConcurrentHashMap<>(disguiseLogs);
        for (String groupUin : logsCopy.keySet()) {
            CopyOnWriteArrayList<HashMap<String, Object>> logs = logsCopy.get(groupUin);
            if (logs != null) {
                allLogs.addAll(new ArrayList<>(logs));
            }
        }
        
        Collections.sort(allLogs, new Comparator<HashMap<String, Object>>() {
            public int compare(HashMap<String, Object> o1, HashMap<String, Object> o2) {
                long time1 = (Long) o1.get("timestamp");
                long time2 = (Long) o2.get("timestamp");
                return Long.compare(time2, time1);
            }
        });
        
        if (allLogs.isEmpty()) {
            android.widget.TextView emptyText = new android.widget.TextView(a);
            emptyText.setText("暂无伪装群友日志");
            emptyText.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_SECONDARY));
            emptyText.setTextSize(16);
            emptyText.setGravity(android.view.Gravity.CENTER);
            emptyText.setPadding(0, dp(50), 0, dp(50));
            layout.addView(emptyText);
        } else {
            for (int i = 0; i < allLogs.size(); i++) {
                HashMap<String, Object> log = allLogs.get(i);
                String logGroupUin = (String) log.get("groupUin");
                boolean isVoice = (Boolean) log.get("isVoice");
                
                boolean isCurrentGroup = logGroupUin.equals(currentGroupUin);
                
                android.widget.LinearLayout logItem = new android.widget.LinearLayout(a);
                logItem.setOrientation(android.widget.LinearLayout.VERTICAL);
                
                if (isCurrentGroup) {
                    android.graphics.drawable.GradientDrawable currentGroupBg = new android.graphics.drawable.GradientDrawable();
                    currentGroupBg.setColor(android.graphics.Color.parseColor("#E8F5E8"));
                    currentGroupBg.setCornerRadius(dp(8));
                    currentGroupBg.setStroke(dp(2), android.graphics.Color.parseColor(COLOR_SECONDARY));
                    logItem.setBackground(currentGroupBg);
                } else {
                    logItem.setBackground(createCardBackground());
                }
                
                logItem.setPadding(dp(15), dp(15), dp(15), dp(15));
                
                android.widget.LinearLayout.LayoutParams itemParams = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                itemParams.setMargins(0, 0, 0, dp(10));
                logItem.setLayoutParams(itemParams);
                
                android.widget.TextView groupInfo = new android.widget.TextView(a);
                groupInfo.setText("群号: " + logGroupUin + (isCurrentGroup ? " (当前群聊)" : ""));
                groupInfo.setTextColor(android.graphics.Color.parseColor(isCurrentGroup ? COLOR_SECONDARY : COLOR_PRIMARY));
                groupInfo.setTextSize(12);
                groupInfo.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
                groupInfo.setPadding(0, 0, 0, dp(5));
                logItem.addView(groupInfo);
                
                android.widget.TextView triggerInfo = new android.widget.TextView(a);
                String triggerUser = (String) log.get("triggerUser");
                String triggerType = (String) log.get("triggerType");
                long timestamp = (Long) log.get("timestamp");
                String modelName = (String) log.get("modelName");
                triggerInfo.setText("触发用户: " + triggerUser + " | 类型: " + 
                    (triggerType.equals("mention") ? "艾特回复" : "随机回复") + 
                    " | 模型: " + modelName +
                    " | 方式: " + (isVoice ? "语音" : "文本") +
                    " | 时间: " + new java.text.SimpleDateFormat("MM-dd HH:mm").format(new java.util.Date(timestamp)));
                triggerInfo.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_SECONDARY));
                triggerInfo.setTextSize(12);
                triggerInfo.setPadding(0, 0, 0, dp(5));
                logItem.addView(triggerInfo);
                
                android.widget.TextView triggerMsg = new android.widget.TextView(a);
                triggerMsg.setText("触发消息: " + log.get("triggerMessage"));
                triggerMsg.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_PRIMARY));
                triggerMsg.setTextSize(14);
                triggerMsg.setPadding(0, 0, 0, dp(5));
                logItem.addView(triggerMsg);
                
                android.widget.TextView aiResponse = new android.widget.TextView(a);
                aiResponse.setText("AI回复: " + log.get("aiResponse"));
                aiResponse.setTextColor(android.graphics.Color.parseColor(isVoice ? "#FF6B35" : COLOR_SECONDARY));
                aiResponse.setTextSize(14);
                logItem.addView(aiResponse);
                
                layout.addView(logItem);
            }
        }
        
        android.widget.Button clearBtn = createModernButton(a, "清空所有日志", COLOR_ACCENT);
        clearBtn.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        clearBtn.setOnClickListener(new android.view.View.OnClickListener() {
            public void onClick(android.view.View v) {
                disguiseLogs.clear();
                java.util.Set<String> allGroups = new java.util.HashSet<>();
                String knownGroups = getString("disguise_logs", "known_groups", "");
                if (!knownGroups.isEmpty()) {
                    String[] groups = knownGroups.split(",");
                    for (String group : groups) {
                        putString("disguise_logs", group, "[]");
                    }
                }
                putString("disguise_logs", "known_groups", "");
                if (showToast) {
                    toast("所有日志已清空");
                }
                dialog.dismiss();
            }
        });
        layout.addView(clearBtn);
        
        scrollView.addView(layout);
        dialog.setContentView(scrollView);
        
        android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
        lp.width = (int)(getScreenWidth(a) * 0.95);
        lp.height = (int)(getScreenHeight(a) * 0.80);
        lp.dimAmount = 0.3f;
        dialog.getWindow().setAttributes(lp);
        dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        
        dialog.show();
    } catch (Exception e) {
        if (showToast) {
            toast("显示日志时出错: " + e.getMessage());
        }
    }
}

// 显示AI对话界面
private void showAIChatDialog(final Object a) {
    try {
        final Object chatDialog = new android.app.Dialog(a);
        chatDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        
        android.graphics.drawable.GradientDrawable windowBg = new android.graphics.drawable.GradientDrawable();
        windowBg.setColor(android.graphics.Color.parseColor("#F5F7FA"));
        chatDialog.getWindow().setBackgroundDrawable(windowBg);
        
        android.widget.LinearLayout mainLayout = new android.widget.LinearLayout(a);
        mainLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        mainLayout.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.MATCH_PARENT));
        
        android.widget.LinearLayout titleLayout = new android.widget.LinearLayout(a);
        titleLayout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        titleLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);
        titleLayout.setPadding(dp(20), dp(15), dp(20), dp(15));
        titleLayout.setBackground(createTitleBackground());
        
        android.widget.TextView title = new android.widget.TextView(a);
        title.setText("AI智能对话助手");
        title.setTextColor(android.graphics.Color.parseColor("#2C3E50"));
        title.setTextSize(18);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        titleLayout.addView(title);
        
        mainLayout.addView(titleLayout);
        
        final android.widget.ScrollView chatScroll = new android.widget.ScrollView(a);
        final android.widget.LinearLayout chatLayout = new android.widget.LinearLayout(a);
        chatLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        chatLayout.setPadding(dp(16), dp(16), dp(16), dp(16));
        chatLayout.setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"));
        
        android.widget.LinearLayout.LayoutParams scrollParams = new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        chatScroll.addView(chatLayout);
        chatScroll.setLayoutParams(scrollParams);
        mainLayout.addView(chatScroll);
        
        android.widget.LinearLayout inputLayout = new android.widget.LinearLayout(a);
        inputLayout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        inputLayout.setPadding(dp(16), dp(12), dp(16), dp(16));
        inputLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);
        inputLayout.setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"));
        
        final android.widget.EditText inputEdit = new android.widget.EditText(a);
        inputEdit.setHint("输入你想和AI聊的内容...");
        inputEdit.setTextColor(android.graphics.Color.parseColor("#2C3E50"));
        inputEdit.setTextSize(16);
        inputEdit.setBackground(createInputBackground());
        inputEdit.setPadding(dp(16), dp(12), dp(16), dp(12));
        inputEdit.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        inputLayout.addView(inputEdit);
        
        android.view.View space = new android.view.View(a);
        space.setLayoutParams(new android.widget.LinearLayout.LayoutParams(dp(12), 1));
        inputLayout.addView(space);
        
        final android.widget.Button sendBtn = createModernButton(a, "发送", COLOR_BUTTON_PRIMARY);
        sendBtn.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            dp(80), dp(48)));
        sendBtn.setOnClickListener(new android.view.View.OnClickListener() {
            public void onClick(android.view.View v) {
                String message = inputEdit.getText().toString().trim();
                if (message.isEmpty()) {
                    if (showToast) {
                        toast("请输入消息");
                    }
                    return;
                }
                
                addChatMessage(chatLayout, "user", message, a, myqq);
                inputEdit.setText("");
                
                chatScroll.post(new Runnable() {
                    public void run() {
                        chatScroll.fullScroll(android.view.View.FOCUS_DOWN);
                    }
                });
                
                sendBtn.setEnabled(false);
                sendBtn.setText("思考中...");
                
                new Thread(new Runnable() {
                    public void run() {
                        final HashMap<String, String> aiResult = callAI("chat", "user", message, "", false);
                        a.runOnUiThread(new Runnable() {
                            public void run() {
                                if (aiResult == null) {
                                    sendBtn.setEnabled(true);
                                    sendBtn.setText("发送");
                                    return;
                                }
                                
                                String response = aiResult.get("response");
                                String modelName = aiResult.get("model");
                                addChatMessage(chatLayout, "ai", response, a, "2595342084");
                                sendBtn.setEnabled(true);
                                sendBtn.setText("发送");
                                
                                chatScroll.post(new Runnable() {
                                    public void run() {
                                        chatScroll.fullScroll(android.view.View.FOCUS_DOWN);
                                    }
                                });
                            }
                        });
                    }
                }).start();
            }
        });
        inputLayout.addView(sendBtn);
        
        mainLayout.addView(inputLayout);
        
        chatDialog.setContentView(mainLayout);
        
        android.view.WindowManager.LayoutParams lp = chatDialog.getWindow().getAttributes();
        lp.width = (int)(getScreenWidth(a) * 0.98);
        lp.height = (int)(getScreenHeight(a) * 0.90);
        lp.dimAmount = 0.4f;
        chatDialog.getWindow().setAttributes(lp);
        chatDialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        
        chatDialog.show();
    } catch (Exception e) {
        if (showToast) {
            toast("打开AI对话时出错: " + e.getMessage());
        }
    }
}

// 添加聊天消息
private void addChatMessage(android.widget.LinearLayout chatLayout, String type, String message, Object a, String avatarUin) {
    android.widget.LinearLayout messageLayout = new android.widget.LinearLayout(a);
    messageLayout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
    messageLayout.setPadding(dp(8), dp(8), dp(8), dp(8));
    
    if (type.equals("user")) {
        messageLayout.setGravity(android.view.Gravity.END);
        android.view.View space = new android.view.View(a);
        space.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, 1, 1));
        messageLayout.addView(space);
    } else {
        messageLayout.setGravity(android.view.Gravity.START);
    }
    
    android.widget.TextView messageText = new android.widget.TextView(a);
    messageText.setText(message);
    messageText.setTextColor(type.equals("user") ? android.graphics.Color.WHITE : android.graphics.Color.parseColor("#2C3E50"));
    messageText.setTextSize(15);
    messageText.setPadding(dp(14), dp(10), dp(14), dp(10));
    messageText.setBackground(createMessageBackground(type.equals("user")));
    messageText.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
    
    messageLayout.addView(messageText);
    chatLayout.addView(messageLayout);
}

// 创建标题背景
private android.graphics.drawable.GradientDrawable createTitleBackground() {
    android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
    gd.setColor(android.graphics.Color.WHITE);
    gd.setCornerRadii(new float[]{dp(0), dp(0), dp(0), dp(0), dp(12), dp(12), dp(12), dp(12)});
    return gd;
}

// 创建消息背景
private android.graphics.drawable.GradientDrawable createMessageBackground(boolean isUser) {
    android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
    gd.setColor(isUser ? android.graphics.Color.parseColor(COLOR_PRIMARY) : android.graphics.Color.parseColor("#F0F2F5"));
    gd.setCornerRadius(dp(18));
    return gd;
}

private void showAISettingsDialog(final Object a) {
    try {
        final Object dialog = new android.app.Dialog(a);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        
        android.graphics.drawable.GradientDrawable windowBg = new android.graphics.drawable.GradientDrawable();
        windowBg.setColor(android.graphics.Color.parseColor(COLOR_BACKGROUND));
        dialog.getWindow().setBackgroundDrawable(windowBg);
        
        android.widget.ScrollView scrollView = new android.widget.ScrollView(a);
        android.widget.LinearLayout layout = new android.widget.LinearLayout(a);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(20), dp(20), dp(20));
        layout.setBackground(createCardBackground());
        
        android.widget.TextView title = new android.widget.TextView(a);
        title.setText("AI伪装群友设置");
        title.setTextColor(android.graphics.Color.parseColor(COLOR_PRIMARY));
        title.setTextSize(20);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        title.setGravity(android.view.Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(20));
        layout.addView(title);
        
        if (!tangqq.isEmpty() && !tangkey.isEmpty()) {
            android.widget.LinearLayout pointsContainer = new android.widget.LinearLayout(a);
            pointsContainer.setOrientation(android.widget.LinearLayout.VERTICAL);
            pointsContainer.setBackground(createCardBackground());
            pointsContainer.setPadding(dp(15), dp(15), dp(15), dp(15));
            
            android.widget.TextView pointsTitle = new android.widget.TextView(a);
            pointsTitle.setText("账户信息");
            pointsTitle.setTextColor(android.graphics.Color.parseColor(COLOR_PRIMARY));
            pointsTitle.setTextSize(16);
            pointsTitle.setPadding(0, 0, 0, dp(10));
            pointsContainer.addView(pointsTitle);
            
            final android.widget.TextView pointsText = new android.widget.TextView(a);
            pointsText.setText("正在查询点数...");
            pointsText.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_SECONDARY));
            pointsText.setTextSize(14);
            pointsText.setPadding(0, 0, 0, dp(10));
            pointsContainer.addView(pointsText);
            
            android.widget.LinearLayout pointsButtonContainer = new android.widget.LinearLayout(a);
            pointsButtonContainer.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            
            android.widget.Button apiAddressBtn = createModernButton(a, "API地址", COLOR_BUTTON_SECONDARY);
            apiAddressBtn.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            apiAddressBtn.setOnClickListener(new android.view.View.OnClickListener() {
                public void onClick(android.view.View v) {
                    String url = "https://api.tangdouz.com/dian";
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                    intent.setData(android.net.Uri.parse(url));
                    a.startActivity(intent);
                }
            });
            
            android.view.View space = new android.view.View(a);
            space.setLayoutParams(new android.widget.LinearLayout.LayoutParams(dp(20), 1));
            
            android.widget.Button detailsBtn = createModernButton(a, "点数详情", COLOR_BUTTON_PRIMARY);
            detailsBtn.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            detailsBtn.setOnClickListener(new android.view.View.OnClickListener() {
                public void onClick(android.view.View v) {
                    showPointsDetails(a);
                }
            });
            
            pointsButtonContainer.addView(apiAddressBtn);
            pointsButtonContainer.addView(space);
            pointsButtonContainer.addView(detailsBtn);
            pointsContainer.addView(pointsButtonContainer);
            
            layout.addView(pointsContainer);
            
            new Thread(new Runnable() {
                public void run() {
                    refreshPoints(pointsText, a);
                }
            }).start();
        }
        
        android.widget.TextView apiTitle = new android.widget.TextView(a);
        apiTitle.setText("API配置");
        apiTitle.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_PRIMARY));
        apiTitle.setTextSize(16);
        apiTitle.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        apiTitle.setPadding(0, dp(15), 0, dp(10));
        layout.addView(apiTitle);
        
        android.widget.LinearLayout registerContainer = new android.widget.LinearLayout(a);
        registerContainer.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        registerContainer.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        registerContainer.setPadding(0, 0, 0, dp(10));
        
        android.widget.Button registerBtn = createModernButton(a, "获取key", COLOR_ACCENT);
        registerBtn.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        registerBtn.setOnClickListener(new android.view.View.OnClickListener() {
            public void onClick(android.view.View v) {
                openRegisterPage(a);
            }
        });
        
        android.view.View btnSpace = new android.view.View(a);
        btnSpace.setLayoutParams(new android.widget.LinearLayout.LayoutParams(dp(20), 1));
        
        android.widget.Button joinGroupBtn = createModernButton(a, "加入官方群", COLOR_ACCENT);
        joinGroupBtn.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        joinGroupBtn.setOnClickListener(new android.view.View.OnClickListener() {
            public void onClick(android.view.View v) {
                joingroup("174785076");
            }
        });
        
        registerContainer.addView(registerBtn);
        registerContainer.addView(btnSpace);
        registerContainer.addView(joinGroupBtn);
        layout.addView(registerContainer);
        
        android.widget.TextView qqLabel = new android.widget.TextView(a);
        qqLabel.setText("注册的QQ:");
        qqLabel.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_SECONDARY));
        qqLabel.setTextSize(14);
        qqLabel.setPadding(0, 0, 0, dp(5));
        layout.addView(qqLabel);
        
        final android.widget.EditText qqEdit = new android.widget.EditText(a);
        qqEdit.setText(tangqq);
        qqEdit.setHint("请输入糖豆子QQ");
        qqEdit.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_PRIMARY));
        qqEdit.setTextSize(16);
        qqEdit.setBackground(createInputBackground());
        qqEdit.setPadding(dp(12), dp(12), dp(12), dp(12));
        qqEdit.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(qqEdit);
        
        android.widget.TextView keyLabel = new android.widget.TextView(a);
        keyLabel.setText("API密钥:");
        keyLabel.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_SECONDARY));
        keyLabel.setTextSize(14);
        keyLabel.setPadding(0, dp(10), 0, dp(5));
        layout.addView(keyLabel);
        
        final android.widget.EditText keyEdit = new android.widget.EditText(a);
        keyEdit.setText(tangkey);
        keyEdit.setHint("请输入API密钥");
        keyEdit.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyEdit.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_PRIMARY));
        keyEdit.setTextSize(16);
        keyEdit.setBackground(createInputBackground());
        keyEdit.setPadding(dp(12), dp(12), dp(12), dp(12));
        keyEdit.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(keyEdit);
        
        android.widget.LinearLayout apiButtonContainer = new android.widget.LinearLayout(a);
        apiButtonContainer.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        apiButtonContainer.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        apiButtonContainer.setPadding(0, dp(10), 0, dp(15));
        
        final android.widget.Button saveApiBtn = createModernButton(a, "保存API配置", COLOR_BUTTON_PRIMARY);
        saveApiBtn.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        saveApiBtn.setOnClickListener(new android.view.View.OnClickListener() {
            public void onClick(android.view.View v) {
                String newQQ = qqEdit.getText().toString().trim();
                String newKey = keyEdit.getText().toString().trim();
                
                // 已移除必须填写API密钥的限制
                // if (newQQ.isEmpty() || newKey.isEmpty()) {
                //     if (showToast) {
                //         toast("请填写糖豆子QQ和API密钥");
                //     }
                //     return;
                // }
                
                // 允许保存空的API配置
                putString("tangdouz", "qq", newQQ);
                putString("tangdouz", "key", newKey);
                
                tangqq = newQQ;
                tangkey = newKey;
                
                if (showToast) {
                    toast("API配置保存成功！");
                }
            }
        });
        
        apiButtonContainer.addView(saveApiBtn);
        layout.addView(apiButtonContainer);
        
        android.view.View divider = new android.view.View(a);
        divider.setBackgroundColor(android.graphics.Color.parseColor("#E0E0E0"));
        divider.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        layout.addView(divider);
        
        android.widget.TextView modelTitle = new android.widget.TextView(a);
        modelTitle.setText("AI模型");
        modelTitle.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_PRIMARY));
        modelTitle.setTextSize(16);
        modelTitle.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        modelTitle.setPadding(0, dp(15), 0, dp(10));
        layout.addView(modelTitle);
        
        if (aiModels.isEmpty()) {
            initAIModels();
        }
        
        // 显示当前模型信息，不使用下拉选择
        android.widget.TextView modelInfo = new android.widget.TextView(a);
        if (!aiModels.isEmpty()) {
            HashMap<String, Object> model = aiModels.get(0);
            modelInfo.setText(model.get("name") + "\n" + model.get("description") + "\n上下文长度: " + model.get("contextLength") + " | 最大token: " + model.get("maxTokensLimit"));
        } else {
            modelInfo.setText("通义千问3\n通义千问最新大语言模型\n上下文长度: 32K | 最大token: 8192");
        }
        modelInfo.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_SECONDARY));
        modelInfo.setTextSize(14);
        modelInfo.setPadding(0, dp(5), 0, dp(15));
        modelInfo.setBackgroundColor(android.graphics.Color.parseColor(COLOR_INPUT_BG));
        modelInfo.setPadding(dp(12), dp(12), dp(12), dp(12));
        layout.addView(modelInfo);
        
        android.widget.TextView voiceTitle = new android.widget.TextView(a);
        voiceTitle.setText("语音设置");
        voiceTitle.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_PRIMARY));
        voiceTitle.setTextSize(16);
        voiceTitle.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        voiceTitle.setPadding(0, dp(15), 0, dp(10));
        layout.addView(voiceTitle);
        
        android.widget.TextView voiceProbLabel = new android.widget.TextView(a);
        voiceProbLabel.setText("语音回复概率 (0-100，0为不发送，100为百分百发送语音):");
        voiceProbLabel.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_SECONDARY));
        voiceProbLabel.setTextSize(14);
        voiceProbLabel.setPadding(0, 0, 0, dp(5));
        layout.addView(voiceProbLabel);
        
        final android.widget.EditText voiceProbEdit = new android.widget.EditText(a);
        voiceProbEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        voiceProbEdit.setText(String.valueOf(voiceProbability));
        voiceProbEdit.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_PRIMARY));
        voiceProbEdit.setTextSize(16);
        voiceProbEdit.setBackground(createInputBackground());
        voiceProbEdit.setPadding(dp(12), dp(12), dp(12), dp(12));
        voiceProbEdit.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(voiceProbEdit);
        
        android.widget.TextView voiceProbDesc = new android.widget.TextView(a);
        voiceProbDesc.setText("当前设置：" + voiceProbability + "% 概率发送语音回复\n请注意：开启大模型语音合成会造成额外点数消耗，约650点/1000字");
        voiceProbDesc.setTextColor(android.graphics.Color.parseColor("#FF6B35"));
        voiceProbDesc.setTextSize(12);
        voiceProbDesc.setPadding(0, dp(5), 0, dp(10));
        layout.addView(voiceProbDesc);
        
        android.widget.TextView voiceModelLabel = new android.widget.TextView(a);
        voiceModelLabel.setText("语音音色选择:");
        voiceModelLabel.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_SECONDARY));
        voiceModelLabel.setTextSize(14);
        voiceModelLabel.setPadding(0, dp(10), 0, dp(5));
        layout.addView(voiceModelLabel);
        
        final android.widget.Spinner voiceModelSpinner = new android.widget.Spinner(a);
        ArrayList<String> voiceModelNames = new ArrayList<>();
        voiceModelNames.add("默认音色");
        
        for (HashMap<String, String> model : voiceModels) {
            voiceModelNames.add(model.get("name"));
        }
        
        android.widget.ArrayAdapter<String> voiceModelAdapter = new android.widget.ArrayAdapter<>(a, android.R.layout.simple_spinner_item, voiceModelNames);
        voiceModelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        voiceModelSpinner.setAdapter(voiceModelAdapter);
        
        int voiceSelectedIndex = 0;
        if (voiceModelId != null && !voiceModelId.isEmpty()) {
            for (int i = 0; i < voiceModels.size(); i++) {
                if (voiceModels.get(i).get("id").equals(voiceModelId)) {
                    voiceSelectedIndex = i + 1;
                    break;
                }
            }
        }
        if (voiceSelectedIndex < voiceModelSpinner.getCount()) {
            voiceModelSpinner.setSelection(voiceSelectedIndex);
        }
        
        layout.addView(voiceModelSpinner);
        
        voiceModelSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                if (position == 0) {
                    voiceModelId = "";
                } else {
                    voiceModelId = voiceModels.get(position - 1).get("id");
                }
            }
            
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        
        android.widget.TextView funcTitle = new android.widget.TextView(a);
        funcTitle.setText("功能设置");
        funcTitle.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_PRIMARY));
        funcTitle.setTextSize(16);
        funcTitle.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        funcTitle.setPadding(0, dp(15), 0, dp(10));
        layout.addView(funcTitle);
        
        android.widget.TextView toastLabel = new android.widget.TextView(a);
        toastLabel.setText("显示提示 (默认开启):");
        toastLabel.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_SECONDARY));
        toastLabel.setTextSize(14);
        toastLabel.setPadding(0, 0, 0, dp(5));
        layout.addView(toastLabel);
        
        final android.widget.CheckBox toastCheckBox = new android.widget.CheckBox(a);
        toastCheckBox.setChecked(showToast);
        toastCheckBox.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(toastCheckBox);
        
        android.widget.TextView probLabel = new android.widget.TextView(a);
        probLabel.setText("随机回复概率 (1-200，数值越大越频繁，默认5):");
        probLabel.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_SECONDARY));
        probLabel.setTextSize(14);
        probLabel.setPadding(0, dp(10), 0, dp(5));
        layout.addView(probLabel);
        
        final android.widget.EditText probEdit = new android.widget.EditText(a);
        probEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        probEdit.setText(String.valueOf(tangdisbfb));
        probEdit.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_PRIMARY));
        probEdit.setTextSize(16);
        probEdit.setBackground(createInputBackground());
        probEdit.setPadding(dp(12), dp(12), dp(12), dp(12));
        probEdit.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(probEdit);
        
        android.widget.TextView probDesc = new android.widget.TextView(a);
        probDesc.setText("当前设置：" + tangdisbfb + "/200 ≈ " + (tangdisbfb * 0.5) + "% 概率随机回复");
        probDesc.setTextColor(android.graphics.Color.parseColor(COLOR_SECONDARY));
        probDesc.setTextSize(12);
        probDesc.setPadding(0, dp(5), 0, dp(10));
        layout.addView(probDesc);
        
        android.widget.TextView emojiLabel = new android.widget.TextView(a);
        emojiLabel.setText("表情包发送概率 (0-100，来自网络表情，默认1):");
        emojiLabel.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_SECONDARY));
        emojiLabel.setTextSize(14);
        emojiLabel.setPadding(0, dp(10), 0, dp(5));
        layout.addView(emojiLabel);
        
        final android.widget.EditText emojiEdit = new android.widget.EditText(a);
        emojiEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        emojiEdit.setText(String.valueOf(emojiProbability));
        emojiEdit.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_PRIMARY));
        emojiEdit.setTextSize(16);
        emojiEdit.setBackground(createInputBackground());
        emojiEdit.setPadding(dp(12), dp(12), dp(12), dp(12));
        emojiEdit.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(emojiEdit);
        
        android.widget.TextView multiLabel = new android.widget.TextView(a);
        multiLabel.setText("多句话回复概率 (0-100，将一段话分成几句话来发送的概率，用|分割，默认20，是否触发因模型而异，可自行在人设中增强):");
        multiLabel.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_SECONDARY));
        multiLabel.setTextSize(14);
        multiLabel.setPadding(0, dp(10), 0, dp(5));
        layout.addView(multiLabel);
        
        final android.widget.EditText multiEdit = new android.widget.EditText(a);
        multiEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        multiEdit.setText(String.valueOf(multiSentenceProbability));
        multiEdit.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_PRIMARY));
        multiEdit.setTextSize(16);
        multiEdit.setBackground(createInputBackground());
        multiEdit.setPadding(dp(12), dp(12), dp(12), dp(12));
        multiEdit.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(multiEdit);
        
        android.widget.TextView contextLabel = new android.widget.TextView(a);
        contextLabel.setText("对话上下文长度 (1-20，默认8):");
        contextLabel.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_SECONDARY));
        contextLabel.setTextSize(14);
        contextLabel.setPadding(0, dp(10), 0, dp(5));
        layout.addView(contextLabel);
        
        final android.widget.EditText contextEdit = new android.widget.EditText(a);
        contextEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        contextEdit.setText(String.valueOf(maxContextLength));
        contextEdit.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_PRIMARY));
        contextEdit.setTextSize(16);
        contextEdit.setBackground(createInputBackground());
        contextEdit.setPadding(dp(12), dp(12), dp(12), dp(12));
        contextEdit.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(contextEdit);
        
        android.widget.TextView typeDelayLabel = new android.widget.TextView(a);
        typeDelayLabel.setText("打字延迟 (毫秒/字，0表示无延迟，默认100):");
        typeDelayLabel.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_SECONDARY));
        typeDelayLabel.setTextSize(14);
        typeDelayLabel.setPadding(0, dp(10), 0, dp(5));
        layout.addView(typeDelayLabel);
        
        final android.widget.EditText typeDelayEdit = new android.widget.EditText(a);
        typeDelayEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        typeDelayEdit.setText(String.valueOf(typeDelayPerChar));
        typeDelayEdit.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_PRIMARY));
        typeDelayEdit.setTextSize(16);
        typeDelayEdit.setBackground(createInputBackground());
        typeDelayEdit.setPadding(dp(12), dp(12), dp(12), dp(12));
        typeDelayEdit.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(typeDelayEdit);
        
        android.widget.TextView maxTokensLabel = new android.widget.TextView(a);
        maxTokensLabel.setText("AI回复最大token数 (建议50-200，默认50):");
        maxTokensLabel.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_SECONDARY));
        maxTokensLabel.setTextSize(14);
        maxTokensLabel.setPadding(0, dp(10), 0, dp(5));
        layout.addView(maxTokensLabel);
        
        final android.widget.EditText maxTokensEdit = new android.widget.EditText(a);
        maxTokensEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        maxTokensEdit.setText(String.valueOf(maxTokens));
        maxTokensEdit.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_PRIMARY));
        maxTokensEdit.setTextSize(16);
        maxTokensEdit.setBackground(createInputBackground());
        maxTokensEdit.setPadding(dp(12), dp(12), dp(12), dp(12));
        maxTokensEdit.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(maxTokensEdit);
        
        android.widget.TextView maxUserMsgLabel = new android.widget.TextView(a);
        maxUserMsgLabel.setText("用户消息最大长度 (超过此长度将截断，默认300):");
        maxUserMsgLabel.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_SECONDARY));
        maxUserMsgLabel.setTextSize(14);
        maxUserMsgLabel.setPadding(0, dp(10), 0, dp(5));
        layout.addView(maxUserMsgLabel);
        
        final android.widget.EditText maxUserMsgEdit = new android.widget.EditText(a);
        maxUserMsgEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        maxUserMsgEdit.setText(String.valueOf(maxUserMessageLength));
        maxUserMsgEdit.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_PRIMARY));
        maxUserMsgEdit.setTextSize(16);
        maxUserMsgEdit.setBackground(createInputBackground());
        maxUserMsgEdit.setPadding(dp(12), dp(12), dp(12), dp(12));
        maxUserMsgEdit.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(maxUserMsgEdit);
        
        android.widget.TextView personaLabel = new android.widget.TextView(a);
        personaLabel.setText("自定义人设 (将添加到系统提示词后面):");
        personaLabel.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_SECONDARY));
        personaLabel.setTextSize(14);
        personaLabel.setPadding(0, dp(10), 0, dp(5));
        layout.addView(personaLabel);
        
        final android.widget.EditText personaEdit = new android.widget.EditText(a);
        personaEdit.setText(getString("ai_config", "custom_persona", ""));
        personaEdit.setHint("例如：高傲，傲娇");
        personaEdit.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_PRIMARY));
        personaEdit.setTextSize(16);
        personaEdit.setBackground(createInputBackground());
        personaEdit.setPadding(dp(12), dp(12), dp(12), dp(12));
        personaEdit.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(personaEdit);
        
        android.widget.LinearLayout clearContainer = new android.widget.LinearLayout(a);
        clearContainer.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        clearContainer.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        clearContainer.setPadding(0, dp(10), 0, dp(15));
        
        final android.widget.Button clearBtn = createModernButton(a, "清空所有对话上下文", COLOR_ACCENT);
        clearBtn.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        clearBtn.setOnClickListener(new android.view.View.OnClickListener() {
            public void onClick(android.view.View v) {
                clearAllContexts();
                if (showToast) {
                    toast("已清空所有对话上下文");
                }
            }
        });
        clearContainer.addView(clearBtn);
        layout.addView(clearContainer);
        
        android.widget.TextView groupsTitle = new android.widget.TextView(a);
        groupsTitle.setText("已开启伪装群友的群聊");
        groupsTitle.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_PRIMARY));
        groupsTitle.setTextSize(16);
        groupsTitle.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        groupsTitle.setPadding(0, dp(15), 0, dp(10));
        layout.addView(groupsTitle);
        
        try {
            ArrayList<Object> groupList = getGroupList();
            int enabledCount = 0;
            
            if (groupList != null && !groupList.isEmpty()) {
                for (Object groupInfo : groupList) {
                    String groupUin = groupInfo.GroupUin;
                    String groupName = groupInfo.GroupName;
                    boolean isEnabled = getBoolean("dis", groupUin, false);
                    
                    if (isEnabled) {
                        enabledCount++;
                        
                        android.widget.LinearLayout groupItem = new android.widget.LinearLayout(a);
                        groupItem.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                        groupItem.setGravity(android.view.Gravity.CENTER_VERTICAL);
                        groupItem.setPadding(dp(10), dp(8), dp(10), dp(8));
                        groupItem.setBackground(createCardBackground());
                        
                        android.widget.LinearLayout.LayoutParams itemParams = new android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                        itemParams.setMargins(0, 0, 0, dp(5));
                        groupItem.setLayoutParams(itemParams);
                        
                        android.widget.TextView groupText = new android.widget.TextView(a);
                        groupText.setText(groupName + " (" + groupUin + ")");
                        groupText.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_PRIMARY));
                        groupText.setTextSize(14);
                        groupText.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                            0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                        groupItem.addView(groupText);
                        
                        android.widget.Button settingsBtn = createModernButton(a, "设置", COLOR_SECONDARY);
                        settingsBtn.setTextSize(12);
                        settingsBtn.setPadding(dp(8), dp(4), dp(8), dp(4));
                        settingsBtn.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                            dp(60), android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
                        settingsBtn.setOnClickListener(new android.view.View.OnClickListener() {
                            public void onClick(android.view.View v) {
                                showGroupSettings(a, groupUin, groupName);
                            }
                        });
                        
                        android.widget.Button toggleBtn = createModernButton(a, "关闭", COLOR_ACCENT);
                        toggleBtn.setTextSize(12);
                        toggleBtn.setPadding(dp(8), dp(4), dp(8), dp(4));
                        toggleBtn.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                            dp(60), android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
                        
                        final String currentGroupUin = groupUin;
                        toggleBtn.setOnClickListener(new android.view.View.OnClickListener() {
                            public void onClick(android.view.View v) {
                                boolean currentState = getBoolean("dis", currentGroupUin, false);
                                putBoolean("dis", currentGroupUin, !currentState);
                                if (showToast) {
                                    toast((!currentState ? "开启" : "关闭") + " " + currentGroupUin + " 的伪装群友功能");
                                }
                                dialog.dismiss();
                                aiSettings("", "", 0);
                            }
                        });
                        
                        groupItem.addView(settingsBtn);
                        
                        groupItem.addView(toggleBtn);
                        layout.addView(groupItem);
                    }
                }
                
                if (enabledCount == 0) {
                    android.widget.TextView noGroupsText = new android.widget.TextView(a);
                    noGroupsText.setText("暂无开启伪装群友的群聊");
                    noGroupsText.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_SECONDARY));
                    noGroupsText.setTextSize(14);
                    noGroupsText.setGravity(android.view.Gravity.CENTER);
                    noGroupsText.setPadding(0, dp(20), 0, dp(20));
                    layout.addView(noGroupsText);
                }
            } else {
                android.widget.TextView noGroupsText = new android.widget.TextView(a);
                noGroupsText.setText("暂无群聊信息");
                noGroupsText.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_SECONDARY));
                noGroupsText.setTextSize(14);
                noGroupsText.setGravity(android.view.Gravity.CENTER);
                noGroupsText.setPadding(0, dp(20), 0, dp(20));
                layout.addView(noGroupsText);
            }
        } catch (Exception e) {
            android.widget.TextView errorText = new android.widget.TextView(a);
            errorText.setText("获取群聊信息失败");
            errorText.setTextColor(android.graphics.Color.parseColor(COLOR_ACCENT));
            errorText.setTextSize(14);
            errorText.setGravity(android.view.Gravity.CENTER);
            errorText.setPadding(0, dp(20), 0, dp(20));
            layout.addView(errorText);
        }
        
        android.widget.TextView introText = new android.widget.TextView(a);
        introText.setText("功能介绍：\n" +
                         "• 被艾特时自动回复\n" +
                         "• 随机概率参与群聊\n" +
                         "• 自动维护对话上下文\n" +
                         "• 支持网络表情包功能\n" +
                         "• 可自定义人设和回复风格\n" +
                         "• 支持语音回复功能\n" +
                         "• 支持AI文转音独立功能");
        introText.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_SECONDARY));
        introText.setTextSize(14);
        introText.setPadding(0, dp(15), 0, dp(15));
        layout.addView(introText);
        
        android.widget.LinearLayout buttonContainer = new android.widget.LinearLayout(a);
        buttonContainer.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        buttonContainer.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        buttonContainer.setPadding(0, dp(10), 0, 0);
        
        android.widget.Button saveBtn = createModernButton(a, "保存设置", COLOR_BUTTON_PRIMARY);
        saveBtn.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        saveBtn.setOnClickListener(new android.view.View.OnClickListener() {
            public void onClick(android.view.View v) {
                String probStr = probEdit.getText().toString().trim();
                String contextStr = contextEdit.getText().toString().trim();
                String emojiStr = emojiEdit.getText().toString().trim();
                String multiStr = multiEdit.getText().toString().trim();
                String typeDelayStr = typeDelayEdit.getText().toString().trim();
                String maxTokensStr = maxTokensEdit.getText().toString().trim();
                String maxUserMsgStr = maxUserMsgEdit.getText().toString().trim();
                String persona = personaEdit.getText().toString().trim();
                String voiceProbStr = voiceProbEdit.getText().toString().trim();
                
                try {
                    int newProb = Integer.parseInt(probStr);
                    int newContext = Integer.parseInt(contextStr);
                    int newEmojiProb = Integer.parseInt(emojiStr);
                    int newMultiProb = Integer.parseInt(multiStr);
                    int newTypeDelay = Integer.parseInt(typeDelayStr);
                    int newMaxTokens = Integer.parseInt(maxTokensStr);
                    int newMaxUserMsg = Integer.parseInt(maxUserMsgStr);
                    int newVoiceProb = Integer.parseInt(voiceProbStr);
                    
                    if (newProb < 1 || newProb > 200) {
                        if (showToast) {
                            toast("随机回复概率请输入1-200之间的数字");
                        }
                        return;
                    }
                    if (newContext < 1 || newContext > 20) {
                        if (showToast) {
                            toast("上下文长度请输入1-20之间的数字");
                        }
                        return;
                    }
                    if (newEmojiProb < 0 || newEmojiProb > 100) {
                        if (showToast) {
                            toast("表情包概率请输入0-100之间的数字");
                        }
                        return;
                    }
                    if (newMultiProb < 0 || newMultiProb > 100) {
                        if (showToast) {
                            toast("多句话概率请输入0-100之间的数字");
                        }
                        return;
                    }
                    if (newTypeDelay < 0 || newTypeDelay > 5000) {
                        if (showToast) {
                            toast("打字延迟请输入0-5000之间的数字");
                        }
                        return;
                    }
                    if (newMaxTokens < 10 || newMaxTokens > 500) {
                        if (showToast) {
                            toast("AI回复最大token数请输入10-500之间的数字");
                        }
                        return;
                    }
                    if (newMaxUserMsg < 50 || newMaxUserMsg > 1000) {
                        if (showToast) {
                            toast("用户消息最大长度请输入50-1000之间的数字");
                        }
                        return;
                    }
                    if (newVoiceProb < 0 || newVoiceProb > 100) {
                        if (showToast) {
                            toast("语音回复概率请输入0-100之间的数字");
                        }
                        return;
                    }
                    
                    putInt("ai_config", "random_probability", newProb);
                    putInt("ai_config", "max_context_length", newContext);
                    putInt("ai_config", "emoji_probability", newEmojiProb);
                    putInt("ai_config", "multi_sentence_probability", newMultiProb);
                    putInt("ai_config", "type_delay_per_char", newTypeDelay);
                    putInt("ai_config", "max_tokens", newMaxTokens);
                    putInt("ai_config", "max_user_message_length", newMaxUserMsg);
                    putString("ai_config", "custom_persona", persona);
                    
                    putBoolean("ai_config", "show_toast", toastCheckBox.isChecked());
                    putInt("ai_config", "voice_probability", newVoiceProb);
                    putString("ai_config", "voice_model_id", voiceModelId);
                    
                    tangdisbfb = newProb;
                    maxContextLength = newContext;
                    emojiProbability = newEmojiProb;
                    multiSentenceProbability = newMultiProb;
                    typeDelayPerChar = newTypeDelay;
                    maxTokens = newMaxTokens;
                    maxUserMessageLength = newMaxUserMsg;
                    showToast = toastCheckBox.isChecked();
                    voiceProbability = newVoiceProb;
                    
                    if (showToast) {
                        toast("设置保存成功！");
                    }
                    dialog.dismiss();
                    
                } catch (NumberFormatException e) {
                    if (showToast) {
                        toast("请输入有效的数字");
                    }
                }
            }
        });
        
        android.view.View space = new android.view.View(a);
        space.setLayoutParams(new android.widget.LinearLayout.LayoutParams(dp(12), 1));
        
        android.widget.Button cancelBtn = createModernButton(a, "取消", COLOR_BUTTON_SECONDARY);
        cancelBtn.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        cancelBtn.setOnClickListener(new android.view.View.OnClickListener() {
            public void onClick(android.view.View v) {
                dialog.dismiss();
            }
        });
        
        buttonContainer.addView(saveBtn);
        buttonContainer.addView(space);
        buttonContainer.addView(cancelBtn);
        layout.addView(buttonContainer);
        
        scrollView.addView(layout);
        dialog.setContentView(scrollView);
        
        android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
        lp.width = (int)(getScreenWidth(a) * 0.95);
        lp.height = (int)(getScreenHeight(a) * 0.90);
        lp.dimAmount = 0.3f;
        dialog.getWindow().setAttributes(lp);
        dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        
        dialog.show();
    } catch (Exception e) {
        if (showToast) {
            toast("打开设置时出错: " + e.getMessage());
        }
    }
}

// 刷新点数显示
private void refreshPoints(final android.widget.TextView pointsText, final Object a) {
    new Thread(new Runnable() {
        public void run() {
            try {
                String url = "https://api.tangdouz.com/dian/cx.php?qq=" + tangqq + "&key=" + tangkey;
                final String response = httpGet(url);
                
                a.runOnUiThread(new Runnable() {
                    public void run() {
                        if (response != null && !response.isEmpty()) {
                            pointsText.setText("当前点数: " + response.trim());
                        } else {
                            pointsText.setText("点数查询失败");
                        }
                    }
                });
            } catch (Exception e) {
                a.runOnUiThread(new Runnable() {
                    public void run() {
                        pointsText.setText("点数查询错误");
                    }
                });
            }
        }
    }).start();
}

// 显示点数详情
private void showPointsDetails(Object a) {
    new Thread(new Runnable() {
        public void run() {
            try {
                String url = "https://api.tangdouz.com/dian/active/?qq=" + tangqq + "&key=" + tangkey;
                
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                intent.setData(android.net.Uri.parse(url));
                a.startActivity(intent);
                
            } catch (Exception e) {
                a.runOnUiThread(new Runnable() {
                    public void run() {
                        if (showToast) {
                            toast("无法打开浏览器");
                        }
                    }
                });
            }
        }
    }).start();
}

// 打开注册页面
private void openRegisterPage(Object a) {
    new Thread(new Runnable() {
        public void run() {
            try {
                String url = "https://api.tangdouz.com/dian/register/";
                
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                intent.setData(android.net.Uri.parse(url));
                a.startActivity(intent);
                
            } catch (Exception e) {
                a.runOnUiThread(new Runnable() {
                    public void run() {
                        if (showToast) {
                            toast("无法打开浏览器");
                        }
                    }
                });
            }
        }
    }).start();
}

// 清空所有上下文
private void clearAllContexts() {
    putString("ai_context", "clear_flag", "cleared");
}

// 显示群聊设置界面
private void showGroupSettings(final Object a, final String groupUin, final String groupName) {
    try {
        final android.app.Dialog dialog = new android.app.Dialog(a);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        
        android.graphics.drawable.GradientDrawable windowBg = new android.graphics.drawable.GradientDrawable();
        windowBg.setColor(android.graphics.Color.parseColor(COLOR_BACKGROUND));
        dialog.getWindow().setBackgroundDrawable(windowBg);
        
        android.widget.ScrollView scrollView = new android.widget.ScrollView(a);
        android.widget.LinearLayout layout = new android.widget.LinearLayout(a);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(20), dp(20), dp(20));
        layout.setBackground(createCardBackground());
        
        android.widget.TextView title = new android.widget.TextView(a);
        title.setText("群聊设置 - " + groupName);
        title.setTextColor(android.graphics.Color.parseColor(COLOR_PRIMARY));
        title.setTextSize(18);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        title.setGravity(android.view.Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(15));
        layout.addView(title);
        
        // 获取当前群聊的随机回复概率设置
        int groupRandomProbability = getInt("random_probability", groupUin, tangdisbfb);
        
        android.widget.TextView probLabel = new android.widget.TextView(a);
        probLabel.setText("随机回复概率 (1-200，数值越大越频繁，默认" + tangdisbfb + "):");
        probLabel.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_SECONDARY));
        probLabel.setTextSize(14);
        probLabel.setPadding(0, dp(10), 0, dp(5));
        layout.addView(probLabel);
        
        final android.widget.EditText probEdit = new android.widget.EditText(a);
        probEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        probEdit.setText(String.valueOf(groupRandomProbability));
        probEdit.setTextColor(android.graphics.Color.parseColor(COLOR_TEXT_PRIMARY));
        probEdit.setTextSize(16);
        probEdit.setBackground(createInputBackground());
        probEdit.setPadding(dp(12), dp(12), dp(12), dp(12));
        probEdit.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(probEdit);
        
        android.widget.TextView probDesc = new android.widget.TextView(a);
        probDesc.setText("当前设置：" + groupRandomProbability + "/200 ≈ " + (groupRandomProbability * 0.5) + "% 概率随机回复");
        probDesc.setTextColor(android.graphics.Color.parseColor(COLOR_SECONDARY));
        probDesc.setTextSize(12);
        probDesc.setPadding(0, dp(5), 0, dp(10));
        layout.addView(probDesc);
        
        android.widget.LinearLayout buttonContainer = new android.widget.LinearLayout(a);
        buttonContainer.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        buttonContainer.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        buttonContainer.setPadding(0, dp(20), 0, 0);
        
        android.widget.Button saveBtn = createModernButton(a, "保存设置", COLOR_BUTTON_PRIMARY);
        saveBtn.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        saveBtn.setOnClickListener(new android.view.View.OnClickListener() {
            public void onClick(android.view.View v) {
                String probStr = probEdit.getText().toString().trim();
                
                try {
                    int newProb = Integer.parseInt(probStr);
                    
                    if (newProb < 1 || newProb > 200) {
                        if (showToast) {
                            toast("随机回复概率请输入1-200之间的数字");
                        }
                        return;
                    }
                    
                    // 保存当前群聊的随机回复概率设置
                    putInt("random_probability", groupUin, newProb);
                    
                    if (showToast) {
                        toast("群聊设置保存成功！");
                    }
                    dialog.dismiss();
                    
                } catch (NumberFormatException e) {
                    if (showToast) {
                        toast("请输入有效的数字");
                    }
                }
            }
        });
        
        android.view.View space = new android.view.View(a);
        space.setLayoutParams(new android.widget.LinearLayout.LayoutParams(dp(12), 1));
        
        android.widget.Button cancelBtn = createModernButton(a, "取消", COLOR_BUTTON_SECONDARY);
        cancelBtn.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        cancelBtn.setOnClickListener(new android.view.View.OnClickListener() {
            public void onClick(android.view.View v) {
                dialog.dismiss();
            }
        });
        
        buttonContainer.addView(saveBtn);
        buttonContainer.addView(space);
        buttonContainer.addView(cancelBtn);
        layout.addView(buttonContainer);
        
        scrollView.addView(layout);
        dialog.setContentView(scrollView);
        
        android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
        lp.width = (int)(getScreenWidth(a) * 0.9);
        lp.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
        lp.dimAmount = 0.3f;
        dialog.getWindow().setAttributes(lp);
        dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        
        dialog.show();
    } catch (Exception e) {
        if (showToast) {
            toast("打开群聊设置时出错: " + e.getMessage());
        }
    }
}

// 开关伪装功能
public void toggleDisguise(String groupUin, String uin, int chatType) {
    if (chatType != 2) {
        if (showToast) {
            toast("仅支持群聊设置");
        }
        return;
    }
    
    if (getBoolean("dis", groupUin, false)) {
        putBoolean("dis", groupUin, false);
        if (showToast) {
            toast("已关闭 " + groupUin + " 的AI伪装功能");
        }
    } else {
        putBoolean("dis", groupUin, true);
        if (showToast) {
            toast("已开启 " + groupUin + " 的AI伪装功能");
        }
    }
}

// 开关艾特回复
public void toggleAtReply(String groupUin, String uin, int chatType) {
    if (chatType != 2) {
        if (showToast) {
            toast("仅支持群聊设置");
        }
        return;
    }
    
    if (getBoolean("at_reply", groupUin, false)) {
        putBoolean("at_reply", groupUin, false);
        if (showToast) {
            toast("已关闭 " + groupUin + " 的艾特回复");
        }
    } else {
        putBoolean("at_reply", groupUin, true);
        if (showToast) {
            toast("已开启 " + groupUin + " 的艾特回复");
        }
    }
}

// 跳转方法
public void jump(String url) {
    try {
        ((IJumpApi) QRoute.api(IJumpApi.class)).doJumpAction(context, url);
    } catch (Exception e) {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
        intent.setData(android.net.Uri.parse(url));
        context.startActivity(intent);
    }
}

// 加入群组方法
public void joingroup(String qun) {
    jump("mqqapi://app/joinImmediately?source_id=3&version=1.0&src_type=app&pkg=com.tencent.mobileqq&cmp=com.tencent.biz.JoinGroupTransitActivity&group_code=" + qun + "&subsource_id=10019");
}

// UI辅助方法
private android.widget.Button createModernButton(Object a, String text, String color) {
    android.widget.Button button = new android.widget.Button(a);
    button.setText(text);
    button.setTextSize(14);
    button.setTextColor(android.graphics.Color.WHITE);
    button.setAllCaps(false);
    button.setBackground(createButtonBackground(color));
    button.setPadding(0, dp(12), 0, dp(12));
    button.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
    return button;
}

private android.graphics.drawable.GradientDrawable createButtonBackground(String color) {
    android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
    gd.setColor(android.graphics.Color.parseColor(color));
    gd.setCornerRadius(dp(8));
    return gd;
}

private android.graphics.drawable.GradientDrawable createCardBackground() {
    android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
    gd.setColor(android.graphics.Color.parseColor(COLOR_CARD));
    gd.setCornerRadius(dp(12));
    gd.setStroke(dp(1), android.graphics.Color.parseColor("#E0E0E0"));
    return gd;
}

private android.graphics.drawable.GradientDrawable createInputBackground() {
    android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
    gd.setColor(android.graphics.Color.parseColor(COLOR_INPUT_BG));
    gd.setCornerRadius(dp(6));
    gd.setStroke(dp(1), android.graphics.Color.parseColor("#E0E0E0"));
    return gd;
}

private int dp(int value) {
    return (int) (value * context.getResources().getDisplayMetrics().density);
}

private int getScreenWidth(Object a) {
    android.util.DisplayMetrics displayMetrics = new android.util.DisplayMetrics();
    a.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
    return displayMetrics.widthPixels;
}

private int getScreenHeight(Object a) {
    android.util.DisplayMetrics displayMetrics = new android.util.DisplayMetrics();
    a.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
    return displayMetrics.heightPixels;
}

// HTTP方法
private String httpPostJson(String url, Map<String, String> headers, String data) {
    try {
        java.net.URL urlObj = new java.net.URL(url);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) urlObj.openConnection();
        
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        
        for (String key : headers.keySet()) {
            conn.setRequestProperty(key, headers.get(key));
        }
        
        java.io.OutputStream os = conn.getOutputStream();
        os.write(data.getBytes("UTF-8"));
        os.flush();
        os.close();
        
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            return null;
        }
        
        java.io.BufferedReader reader = new java.io.BufferedReader(
            new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
        
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        
        return response.toString();
        
    } catch (Exception e) {
        return null;
    }
}

private String httpGet(String url) {
    return httpGet(url, 10000);
}

private String httpGet(String url, int timeout) {
    try {
        java.net.URL urlObj = new java.net.URL(url);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) urlObj.openConnection();
        
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(timeout);
        
        java.io.BufferedReader reader = new java.io.BufferedReader(
            new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
        
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        
        return response.toString();
        
    } catch (Exception e) {
        return null;
    }
}

// 带超时设置的下载方法
private void httpDownload(String url, String path, int timeout) {
    try {
        java.net.URL urlObj = new java.net.URL(url);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) urlObj.openConnection();
        
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(timeout);
        
        java.io.InputStream input = conn.getInputStream();
        java.io.FileOutputStream output = new java.io.FileOutputStream(path);
        
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
        
        output.close();
        input.close();
        
    } catch (Exception e) {
        throw new RuntimeException("下载失败: " + e.getMessage());
    }
}
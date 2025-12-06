// == QStory 脚本 ==
// name = 猫猫看腿助手
// type = 1
// version = 6.9
// author = 猫猫
// id = com.maomao.legsviewer
// time = 2025-12-04

// 全局变量
String ConfigName = "LegsViewerSettings";
String AntiConflictKey = "AntiConflictEnabled";
String FlipImageKey = "FlipImageEnabled"; // 图片翻转开关
String NSFWEnabledPrefix = "NSFWEnabled_"; // 新增：涩图功能开关前缀（按聊天窗口独立）
String helpMessage = "【猫猫看腿助手使用说明】\n"
        + "1. 支持多种触发词：\n"
        + "  - 看看腿：随机美腿图片\n"
        + "  - 看看白丝：白丝主题图\n"
        + "  - 看看黑丝：黑丝主题图\n"
        + "  - 看看猫猫：可爱猫咪\n"
        + "  - 看看jk：JK制服图片\n"
        + "  - 看看cos：COSPLAY角色扮演图片\n"
        + "  - 看看二次元：精美二次元壁纸\n"
        + "  - 看看涩图：随机涩图(可开启翻转)\n"
        + "  - 看看涩图[标签]：指定标签的涩图，如'看看涩图白丝'\n"
        + "  - 看看标签[标签]：指定标签的普通图片，如'看看标签白丝'、'看看标签JK'\n"
        + "  - 看看作者[作者名]：获取指定作者的图片，如'看看作者Tiv'\n"
        + "  - 看看黄金：浙商银行积存金实时价格\n"
        + "2. 可在菜单中开启/关闭功能\n"
        + "3. 图片自动加载无需等待\n"
        + "※注意：功能默认关闭且分离控制，请在聊天窗口菜单中为当前群聊/私聊单独开启。";

// API配置
java.util.Map apiMap = new java.util.HashMap();

// 在单独的代码块中添加API映射（修改了看看猫猫的API地址）
{
        apiMap.put("看看腿", "https://api.lolimi.cn/API/meizi/api.php?type=image");
    apiMap.put("看看白丝", "https://v2.xxapi.cn/api/baisi?return=302");
    apiMap.put("看看黑丝", "https://v2.xxapi.cn/api/heisi?return=302");
    apiMap.put("看看猫猫", "https://edgecats.net/");  // 已修改为新的猫猫API
    apiMap.put("看看jk", "https://api.yujn.cn/api/jk.php");
    apiMap.put("看看cos", "https://api.tangdouz.com/hlxmt.php");
    apiMap.put("看看二次元", "https://api.tangdouz.com/abz/dm.php");
    apiMap.put("看看黄金", "https://api.tangdouz.com/a/zsgold.php");
}

// 确保缓存目录存在
void initCacheDir() {
    java.io.File cacheDir = new java.io.File(appPath, "cache");
    if (!cacheDir.exists()) {
        cacheDir.mkdirs();
    }
}

// 翻转图片（水平+垂直翻转）
String flipImage(String originalPath) {
    try {
        // 读取原始图片
        android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
        options.inSampleSize = 1; // 不缩放
        android.graphics.Bitmap original = android.graphics.BitmapFactory.decodeFile(originalPath, options);

        if (original == null) {
            toast("无法读取图片: " + originalPath);
            return null;
        }

        // 创建翻转矩阵
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.setScale(-1, -1);
        matrix.postTranslate(original.getWidth(), original.getHeight());

        // 创建翻转后的图片
        android.graphics.Bitmap flipped = android.graphics.Bitmap.createBitmap(
                original, 0, 0, original.getWidth(), original.getHeight(), matrix, true);

        // 保存翻转后的图片
        String flippedPath = appPath + "/cache/flipped_" + java.util.UUID.randomUUID() + ".jpg";
        java.io.FileOutputStream out = new java.io.FileOutputStream(flippedPath);
        flipped.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, out);
        out.close();
        original.recycle();
        flipped.recycle();

        return flippedPath;
    } catch (Exception e) {
        toast("图片翻转失败: " + e.getMessage());
        return null;
    }
}

// 增强版toast：显示提示并发送错误消息
void showError(Object msg, String errorMsg) {
    toast(errorMsg);

    try {
        if (((Object)msg).IsGroup) {
            sendMsg(((Object)msg).GroupUin, "", "【系统提示】" + errorMsg);
        } else {
            sendMsg("", ((Object)msg).PeerUin, "【系统提示】" + errorMsg);
        }
    } catch (Exception e) {
        // 如果发送消息失败，至少确保原始toast已经显示
    }
}

// 下载图片到本地并返回路径
String downloadImage(String apiUrl, Object msg) {
    initCacheDir();
    try {
        // 生成唯一文件名
        String fileName = java.util.UUID.randomUUID().toString() + ".jpg";
        String filePath = new java.io.File(appPath, "cache/" + fileName).getAbsolutePath();

        // 打开API连接
        java.net.URL url = new java.net.URL(apiUrl);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);

        // 检查响应状态
        int responseCode = conn.getResponseCode();

        // 处理重定向
        if (responseCode == java.net.HttpURLConnection.HTTP_MOVED_TEMP ||
                responseCode == java.net.HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == java.net.HttpURLConnection.HTTP_SEE_OTHER) {
            String newUrl = conn.getHeaderField("Location");
            if (newUrl != null) {
                conn.disconnect();
                url = new java.net.URL(newUrl);
                conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                responseCode = conn.getResponseCode();
            }
        }

        // 检查最终的响应状态
        if (responseCode != java.net.HttpURLConnection.HTTP_OK) {
            showError(msg, "图片下载失败: HTTP " + responseCode);
            return null;
        }

        // 获取输入流
        java.io.InputStream in = conn.getInputStream();

        // 创建文件输出流
        java.io.FileOutputStream out = new java.io.FileOutputStream(filePath);

        // 读取并写入文件
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
        }

        // 关闭流
        out.close();
        in.close();

        // 检查文件是否有效
        java.io.File file = new java.io.File(filePath);
        if (file.length() == 0) {
            file.delete();
            showError(msg, "下载的图片文件为空");
            return null;
        }

        return filePath;
    } catch (Exception e) {
        showError(msg, "图片下载异常: " + e.getMessage());
        return null;
    }
}

// 消息排版美化
String formatMessage(String message) {
    try {
        boolean antiConflict = getBoolean(ConfigName, AntiConflictKey, true);
        if (antiConflict) {
            return "，，" + message;
        }
    } catch (Exception e) {
    }
    return message;
}

// 发送格式化消息
void sendFormattedMsg(String group, String user, String message) {
    String formatted = formatMessage(message);
    if (group != null && !group.isEmpty()) {
        sendMsg(group, "", formatted);
    } else if (user != null && !user.isEmpty()) {
        sendMsg("", user, formatted);
    }
}

// HTTP GET请求
String httpGet(String urlStr, Object msg) {
    try {
        java.net.URL url = new java.net.URL(urlStr);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setInstanceFollowRedirects(true);

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

        return response.toString();
    } catch (Exception e) {
        showError(msg, "HTTP GET失败: " + e.getMessage());
        return null;
    }
}

// 处理COS API响应并发送多张图片
void handleCosApiResponse(String response, Object msg) {
    try {
        // 验证响应是否有效
        if (response == null || response.isEmpty() || !response.contains("img=")) {
            showError(msg, "无效的COS图片响应");
            return;
        }

        // 分割字符串，获取所有img部分
        String[] parts = response.split("±");
        java.util.List<String> imageUrls = new java.util.ArrayList<>();

        for (String part : parts) {
            if (part.startsWith("img=")) {
                String url = part.substring(4); // 去掉"img="前缀
                if (url.startsWith("http")) {
                    imageUrls.add(url);
                }
            }
        }

        if (imageUrls.isEmpty()) {
            showError(msg, "未找到COS图片");
            return;
        }

        String senderUin = (String) ((Object)msg).UserUin;

        // 在新线程中处理每张图片
        new Thread(new Runnable() {
            public void run() {
                try {
                    // 发送所有图片，不做数量限制
                    for (int i = 0; i < imageUrls.size(); i++) {
                        String imageUrl = imageUrls.get(i);
                        String localPath = downloadImage(imageUrl, msg);

                        if (localPath != null) {
                            final String path = localPath;
                            final int index = i + 1;

                            // 在主线程中发送图片
                            android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
                            mainHandler.post(new Runnable() {
                                public void run() {
                                    try {
                                        if (((Object)msg).IsGroup) {
                                            sendPic(((Object)msg).GroupUin, "", path);
                                        } else {
                                            sendPic("", ((Object)msg).PeerUin, path);
                                        }

                                        // 1分钟后删除缓存
                                        android.os.Handler handler = new android.os.Handler();
                                        handler.postDelayed(new Runnable() {
                                            public void run() {
                                                deleteFile(path);
                                            }
                                        }, 60000);
                                    } catch (Exception e) {
                                        showError(msg, "COS图片" + index + "发送失败: " + e.getMessage());
                                    }
                                }
                            });

                            // 适当延迟，避免发送太快
                            Thread.sleep(800);
                        }
                    }
                } catch (Exception e) {
                    showError(msg, "处理COS图片失败: " + e.getMessage());
                }
            }
        }).start();
    } catch (Exception e) {
        showError(msg, "解析COS响应失败: " + e.getMessage());
    }
}

// 【修改】处理涩图请求 - 添加图片翻转功能 + 涩图开关检查 + 新API支持
void handleNSFWRequest(String content, Object msg) {
    try {
        // 新增：检查当前聊天的涩图功能是否开启
        String uin = ((Object)msg).IsGroup ? ((Object)msg).GroupUin : ((Object)msg).PeerUin;
        boolean nsfwEnabled = getBoolean(ConfigName, NSFWEnabledPrefix + uin, true);
        if (!nsfwEnabled) {
            showError(msg, "当前聊天已关闭涩图功能，请在菜单中开启");
            return;
        }

        // 检查是否是"看看涩图"指令
        if (content.startsWith("看看涩图")) {
            // 提取tag，如果有
            String tag = content.substring("看看涩图".length()).trim();
            String apiUrl = null;
            String imageUrl = null;
            boolean apiSuccess = false;

            // API列表 - 包含新添加的API
            String[] nsfwApis = {
                    "https://api.lolicon.app/setu/v2?r18=1&aiType=1&tag=",
                    "https://api.mossia.top/duckMo?aiType=1&r18Type=1&tag=",
                    "https://sex.nyan.run/api/v2/?r18=true&tag="
            };

            // 尝试所有API，直到找到一个成功的
            for (int i = 0; i < nsfwApis.length; i++) {
                try {
                    // 构建API URL
                    if (!tag.isEmpty()) {
                        apiUrl = nsfwApis[i] + java.net.URLEncoder.encode(tag, "UTF-8");
                    } else {
                        // 无标签，使用随机API
                        apiUrl = nsfwApis[i];
                    }

                    // 获取API响应
                    String response = httpGet(apiUrl, msg);
                    if (response == null || response.isEmpty()) {
                        continue; // 继续尝试下一个API
                    }

                    // 根据API类型解析响应
                    if (apiUrl.contains("lolicon")) {
                        // 处理lolicon API响应
                        org.json.JSONObject json = new org.json.JSONObject(response);
                        String error = json.optString("error", "");
                        if (!error.isEmpty()) {
                            continue; // 继续尝试下一个API
                        }

                        org.json.JSONArray dataArray = json.getJSONArray("data");
                        if (dataArray.length() == 0) {
                            continue; // 继续尝试下一个API
                        }

                        // 获取第一张图片的原始URL
                        org.json.JSONObject firstData = dataArray.getJSONObject(0);
                        org.json.JSONObject urls = firstData.getJSONObject("urls");
                        imageUrl = urls.getString("original");
                    } else if (apiUrl.contains("mossia")) {
                        // 处理mossia API响应
                        org.json.JSONObject json = new org.json.JSONObject(response);
                        String errCode = json.optString("errCode", "");
                        if (!"200".equals(errCode)) {
                            continue; // 继续尝试下一个API
                        }

                        org.json.JSONArray dataArray = json.getJSONArray("data");
                        if (dataArray.length() == 0) {
                            continue; // 继续尝试下一个API
                        }

                        // 获取第一张图片的原始URL
                        org.json.JSONObject firstData = dataArray.getJSONObject(0);
                        org.json.JSONArray urlsList = firstData.getJSONArray("urlsList");
                        if (urlsList.length() == 0) {
                            continue; // 继续尝试下一个API
                        }

                        org.json.JSONObject urlObj = urlsList.getJSONObject(0);
                        imageUrl = urlObj.getString("url");
                    } else if (apiUrl.contains("sex.nyan.run")) {
                        // 处理sex.nyan.run API响应
                        org.json.JSONObject json = new org.json.JSONObject(response);
                        if (!json.optBoolean("success", false)) {
                            continue; // 继续尝试下一个API
                        }

                        org.json.JSONArray dataArray = json.getJSONArray("data");
                        if (dataArray.length() == 0) {
                            continue; // 继续尝试下一个API
                        }

                        // 获取第一张图片的原始URL
                        org.json.JSONObject firstData = dataArray.getJSONObject(0);
                        imageUrl = firstData.getString("url");
                    }

                    // 如果成功获取到图片URL，退出循环
                    apiSuccess = true;
                    break;
                } catch (Exception e) {
                    // 如果处理当前API时出错，继续尝试下一个API
                    continue;
                }
            }

            if (!apiSuccess) {
                showError(msg, "所有涩图API均失败，请稍后再试");
                return;
            }

            // 下载图片
            String localPath = downloadImage(imageUrl, msg);
            if (localPath == null) {
                showError(msg, "涩图下载失败");
                return;
            }

            // 检查是否需要翻转图片
            boolean flipEnabled = getBoolean(ConfigName, FlipImageKey, true);
            String finalImagePath = localPath;

            if (flipEnabled) {
                String flippedPath = flipImage(localPath);
                if (flippedPath != null) {
                    // 删除原始图片，只保留翻转后的
                    deleteFile(localPath);
                    finalImagePath = flippedPath;
                } else {
                    // 翻转失败，使用原始图片
                    toast("图片翻转失败，将发送原始图片");
                }
            }

            // 在主线程中发送图片
            android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
            mainHandler.post(new Runnable() {
                public void run() {
                    try {
                        if (((Object)msg).IsGroup) {
                            sendPic(((Object)msg).GroupUin, "", finalImagePath);
                        } else {
                            sendPic("", ((Object)msg).PeerUin, finalImagePath);
                        }

                        // 1分钟后删除缓存
                        android.os.Handler handler = new android.os.Handler();
                        handler.postDelayed(new Runnable() {
                            public void run() {
                                deleteFile(finalImagePath);
                            }
                        }, 60000);

                        // 5分钟后再次尝试删除
                        handler.postDelayed(new Runnable() {
                            public void run() {
                                deleteFile(finalImagePath);
                            }
                        }, 300000);
                    } catch (Exception e) {
                        showError(msg, "涩图发送失败: " + e.getMessage());
                    }
                }
            });
        }
    } catch (Exception e) {
        showError(msg, "处理涩图请求出错: " + e.getMessage());
    }
}

// 处理黄金价格请求
void handleGoldPriceRequest(Object msg) {
    try {
        String apiUrl = (String) apiMap.get("看看黄金");

        // 在新线程中处理请求
        new Thread(new Runnable() {
            public void run() {
                try {
                    // 获取API响应
                    String response = httpGet(apiUrl, msg);
                    if (response == null || response.isEmpty()) {
                        showError(msg, "获取黄金价格失败");
                        return;
                    }

                    // 在主线程中发送响应
                    android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
                    mainHandler.post(new Runnable() {
                        public void run() {
                            try {
                                String formattedResponse = formatMessage(response);
                                if (((Object)msg).IsGroup) {
                                    sendMsg(((Object)msg).GroupUin, "", formattedResponse);
                                } else {
                                    sendMsg("", ((Object)msg).PeerUin, formattedResponse);
                                }
                            } catch (Exception e) {
                                showError(msg, "发送黄金价格失败: " + e.getMessage());
                            }
                        }
                    });
                } catch (Exception e) {
                    showError(msg, "获取黄金价格失败: " + e.getMessage());
                }
            }
        }).start();
    } catch (Exception e) {
        showError(msg, "处理黄金价格请求出错: " + e.getMessage());
    }
}

// 【修改】处理带标签的图片请求（"看看标签XX"）
void handleTaggedRequest(String content, Object msg) {
    try {
        // 确保内容以"看看标签"开头
        if (!content.startsWith("看看标签")) {
            return;
        }

        // 提取标签，去掉"看看标签"前缀
        String tag = content.substring(4).trim();
        if (tag.isEmpty()) {
            // 如果没有标签，不处理
            return;
        }

        // API列表 - 包含新添加的API
        String[] taggedApis = {
                "https://api.lolicon.app/setu/v2?tag=",
                "https://sex.nyan.run/api/v2/?tag="
        };

        String apiUrl = null;
        String imageUrl = null;
        boolean apiSuccess = false;

        // 尝试所有API，直到找到一个成功的
        for (int i = 0; i < taggedApis.length; i++) {
            try {
                // 构建API URL
                apiUrl = taggedApis[i] + java.net.URLEncoder.encode(tag, "UTF-8");

                // 获取API响应
                String response = httpGet(apiUrl, msg);
                if (response == null || response.isEmpty()) {
                    continue; // 继续尝试下一个API
                }

                // 根据API类型解析响应
                if (apiUrl.contains("lolicon")) {
                    // 处理lolicon API响应
                    org.json.JSONObject json = new org.json.JSONObject(response);
                    String error = json.optString("error", "");
                    if (!error.isEmpty()) {
                        continue; // 继续尝试下一个API
                    }

                    org.json.JSONArray dataArray = json.getJSONArray("data");
                    if (dataArray.length() == 0) {
                        continue; // 继续尝试下一个API
                    }

                    // 获取第一张图片的原始URL
                    org.json.JSONObject firstData = dataArray.getJSONObject(0);
                    org.json.JSONObject urls = firstData.getJSONObject("urls");
                    imageUrl = urls.getString("original");
                } else if (apiUrl.contains("sex.nyan.run")) {
                    // 处理sex.nyan.run API响应
                    org.json.JSONObject json = new org.json.JSONObject(response);
                    if (!json.optBoolean("success", false)) {
                        continue; // 继续尝试下一个API
                    }

                    org.json.JSONArray dataArray = json.getJSONArray("data");
                    if (dataArray.length() == 0) {
                        continue; // 继续尝试下一个API
                    }

                    // 获取第一张图片的原始URL
                    org.json.JSONObject firstData = dataArray.getJSONObject(0);
                    imageUrl = firstData.getString("url");
                }

                // 如果成功获取到图片URL，退出循环
                apiSuccess = true;
                break;
            } catch (Exception e) {
                // 如果处理当前API时出错，继续尝试下一个API
                continue;
            }
        }

        if (!apiSuccess) {
            showError(msg, "所有标签图片API均失败，请稍后再试");
            return;
        }

        // 下载图片
        String localPath = downloadImage(imageUrl, msg);
        if (localPath == null) {
            showError(msg, "图片下载失败");
            return;
        }

        // 在主线程中发送图片
        android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
        mainHandler.post(new Runnable() {
            public void run() {
                try {
                    if (((Object)msg).IsGroup) {
                        sendPic(((Object)msg).GroupUin, "", localPath);
                    } else {
                        sendPic("", ((Object)msg).PeerUin, localPath);
                    }

                    // 1分钟后删除缓存
                    android.os.Handler handler = new android.os.Handler();
                    handler.postDelayed(new Runnable() {
                        public void run() {
                            deleteFile(localPath);
                        }
                    }, 60000);
                } catch (Exception e) {
                    showError(msg, "图片发送失败: " + e.getMessage());
                }
            }
        });
    } catch (Exception e) {
        showError(msg, "处理请求出错: " + e.getMessage());
    }
}

// 【新增】处理"看看作者"请求
void handleAuthorRequest(String content, Object msg) {
    try {
        // 检查是否是"看看作者"指令
        if (!content.startsWith("看看作者")) {
            return;
        }

        // 提取作者名，去掉"看看作者"前缀
        String authorName = content.substring(4).trim();
        if (authorName.isEmpty()) {
            showError(msg, "请输入作者名，例如：看看作者Tiv");
            return;
        }

        // 构建API URL
        String apiUrl = "https://api.mossia.top/duckMo?author=" + java.net.URLEncoder.encode(authorName, "UTF-8");

        // 在新线程中处理请求
        new Thread(new Runnable() {
            public void run() {
                try {
                    // 获取API响应
                    String response = httpGet(apiUrl, msg);
                    if (response == null || response.isEmpty()) {
                        showError(msg, "获取作者图片失败");
                        return;
                    }

                    // 解析JSON响应
                    org.json.JSONObject json = new org.json.JSONObject(response);
                    String errCode = json.optString("errCode", "");
                    if (!"200".equals(errCode)) {
                        String message = json.optString("message", "API错误");
                        showError(msg, "API错误: " + message);
                        return;
                    }

                    org.json.JSONArray dataArray = json.getJSONArray("data");
                    if (dataArray.length() == 0) {
                        showError(msg, "未找到该作者的图片");
                        return;
                    }

                    // 获取第一张图片的URL
                    org.json.JSONObject firstData = dataArray.getJSONObject(0);
                    org.json.JSONArray urlsList = firstData.getJSONArray("urlsList");
                    if (urlsList.length() == 0) {
                        showError(msg, "未找到图片URL");
                        return;
                    }

                    org.json.JSONObject urlObj = urlsList.getJSONObject(0);
                    String imageUrl = urlObj.getString("url");

                    // 下载图片
                    String localPath = downloadImage(imageUrl, msg);
                    if (localPath == null) {
                        showError(msg, "图片下载失败");
                        return;
                    }

                    // 在主线程中发送图片
                    android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
                    mainHandler.post(new Runnable() {
                        public void run() {
                            try {
                                if (((Object)msg).IsGroup) {
                                    sendPic(((Object)msg).GroupUin, "", localPath);
                                } else {
                                    sendPic("", ((Object)msg).PeerUin, localPath);
                                }

                                // 1分钟后删除缓存
                                android.os.Handler handler = new android.os.Handler();
                                handler.postDelayed(new Runnable() {
                                    public void run() {
                                        deleteFile(localPath);
                                    }
                                }, 60000);
                            } catch (Exception e) {
                                showError(msg, "图片发送失败: " + e.getMessage());
                            }
                        }
                    });
                } catch (Exception e) {
                    showError(msg, "获取作者图片失败: " + e.getMessage());
                }
            }
        }).start();
    } catch (Exception e) {
        showError(msg, "处理作者请求出错: " + e.getMessage());
    }
}

// 响应消息
void onMsg(Object msg) {
    try {
        String content = (String) ((Object)msg).MessageContent;
        // 【修改】获取当前聊天的唯一ID（群号或对方QQ号）
        String uin = ((Object)msg).IsGroup ? ((Object)msg).GroupUin : ((Object)msg).PeerUin;
        String senderUin = (String) ((Object)msg).UserUin;

        // 【修改】为每个聊天窗口（由uin决定）创建一个独立的开关键
        String key = uin + "_enabled";
        // 【修改】读取当前聊天的开关状态，默认值为 false (关闭)
        boolean enabled = getBoolean(ConfigName, key, false);

        // 如果当前聊天未开启功能，则不继续执行
        if (!enabled) {
            return;
        }

        // 检查是否是涩图请求（需要特殊处理）
        if (content.startsWith("看看涩图")) {
            handleNSFWRequest(content, msg);
            return;
        }

        // 新增：检查是否是黄金价格请求
        if ("看看黄金".equals(content)) {
            handleGoldPriceRequest(msg);
            return;
        }

        // 【修改】检查是否是带标签的普通图片请求（不是预定义指令，也不是涩图）
        if (content.startsWith("看看标签") && !apiMap.containsKey(content) && !content.startsWith("看看涩图")) {
            handleTaggedRequest(content, msg);
            return;
        }

        String apiUrl = (String) apiMap.get(content);

        // 特殊处理COS指令
        if ("https://api.tangdouz.com/hlxmt.php".equals(apiUrl)) {
            // 在新线程中获取API响应
            new Thread(new Runnable() {
                public void run() {
                    try {
                        String response = httpGet(apiUrl, msg);
                        if (response == null || response.isEmpty() || response.equals("\"\"")) {
                            showError(msg, "COS图片获取失败");
                            return;
                        }

                        // 在主线程中处理响应
                        android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
                        mainHandler.post(new Runnable() {
                            public void run() {
                                handleCosApiResponse(response, msg);
                            }
                        });
                    } catch (Exception e) {
                        showError(msg, "获取COS图片失败: " + e.getMessage());
                    }
                }
            }).start();
            return; // 跳过普通图片处理流程
        }

        // 处理"看看二次元"请求
        if ("https://api.tangdouz.com/abz/dm.php".equals(apiUrl)) {
            // 在新线程中处理二次元图片
            new Thread(new Runnable() {
                public void run() {
                    try {
                        // 获取API响应
                        String response = httpGet(apiUrl, msg);
                        if (response == null || response.isEmpty()) {
                            showError(msg, "二次元图片获取失败");
                            return;
                        }

                        // 处理响应 - 确保是有效的图片URL
                        String imageUrl = response.trim();
                        if (!imageUrl.startsWith("http")) {
                            showError(msg, "无效的二次元图片URL");
                            return;
                        }

                        // 下载图片
                        String localPath = downloadImage(imageUrl, msg);
                        if (localPath == null) {
                            showError(msg, "二次元图片下载失败");
                            return;
                        }

                        // 在主线程中发送图片
                        android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
                        mainHandler.post(new Runnable() {
                            public void run() {
                                try {
                                    if (((Object)msg).IsGroup) {
                                        sendPic(((Object)msg).GroupUin, "", localPath);
                                    } else {
                                        sendPic("", ((Object)msg).PeerUin, localPath);
                                    }

                                    // 1分钟后删除缓存
                                    android.os.Handler handler = new android.os.Handler();
                                    handler.postDelayed(new Runnable() {
                                        public void run() {
                                            deleteFile(localPath);
                                        }
                                    }, 60000);
                                } catch (Exception e) {
                                    showError(msg, "二次元图片发送失败: " + e.getMessage());
                                    deleteFile(localPath);
                                }
                            }
                        });
                    } catch (Exception e) {
                        showError(msg, "处理二次元图片失败: " + e.getMessage());
                    }
                }
            }).start();
            return;
        }

        if (apiUrl != null) {
            // 在新线程中处理普通消息
            new Thread(new Runnable() {
                public void run() {
                    try {
                        // 下载图片
                        String localPath = downloadImage(apiUrl, msg);
                        if (localPath == null) {
                            showError(msg, content + " 图片下载失败");
                            return;
                        }

                        // 在主线程中发送图片
                        android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
                        mainHandler.post(new Runnable() {
                            public void run() {
                                try {
                                    if (((Object)msg).IsGroup) {
                                        sendPic(((Object)msg).GroupUin, "", localPath);
                                    } else {
                                        sendPic("", ((Object)msg).PeerUin, localPath);
                                    }

                                    // 1分钟后删除缓存
                                    android.os.Handler handler = new android.os.Handler();
                                    handler.postDelayed(new Runnable() {
                                        public void run() {
                                            deleteFile(localPath);
                                        }
                                    }, 60000);

                                    // 5分钟后再次尝试删除（确保文件被删除）
                                    handler.postDelayed(new Runnable() {
                                        public void run() {
                                            deleteFile(localPath);
                                        }
                                    }, 300000);
                                } catch (Exception e) {
                                    showError(msg, content + " 图片发送失败: " + e.getMessage());
                                }
                            }
                        });
                    } catch (Exception e) {
                        showError(msg, content + " 图片下载失败: " + e.getMessage());
                    }
                }
            }).start();
        }

        // 新增：处理"看看作者"请求
        if (content.startsWith("看看作者")) {
            handleAuthorRequest(content, msg);
            return;
        }

    } catch (Exception e) {
        showError(msg, "处理消息出错: " + e.getMessage());
    }
}

// 悬浮窗菜单（控制面板）- 新增涩图功能开关
void onClickFloatingWindow(int type, String uin) {
    // 【修改】为当前聊天（uin）创建独立的开关
    String key = uin + "_enabled";
    // 【修改】读取当前聊天的开关状态，默认值为 false (关闭)
    boolean enabled = getBoolean(ConfigName, key, false);
    String switchText = "功能开关: " + (enabled ? "✔已开启" : "✖已关闭");
    addTemporaryItem(switchText, "toggleSwitch");

    // 新增：涩图功能开关（按当前聊天独立控制）
    boolean nsfwEnabled = getBoolean(ConfigName, NSFWEnabledPrefix + uin, true);
    String nsfwText = "涩图功能: " + (nsfwEnabled ? "✔已开启" : "✖已关闭");
    addTemporaryItem(nsfwText, "toggleNSFWFunction");

    // 添加防冲突开关（全局设置）
    boolean antiConflict = getBoolean(ConfigName, AntiConflictKey, true);
    String antiConflictText = "消息排版美化: " + (antiConflict ? "✔已开启" : "✖已关闭");
    addTemporaryItem(antiConflictText, "toggleAntiConflict");

    // 图片翻转开关
    boolean flipEnabled = getBoolean(ConfigName, FlipImageKey, true);
    String flipText = "涩图翻转: " + (flipEnabled ? "✔已开启" : "✖已关闭");
    addTemporaryItem(flipText, "toggleFlipImage");

    // 添加帮助按钮
    addTemporaryItem("使用帮助", "showHelp");

    // 添加支持的指令列表
    addTemporaryItem("支持指令列表", "showCommands");
}

// 功能开关切换回调
void toggleSwitch(String group, String user, int type) {
    // 【修改】根据聊天类型确定要操作的uin和对应的key
    String uin = group.isEmpty() ? user : group;
    String key = uin + "_enabled";
    // 【修改】读取当前状态，默认值为 false (关闭)
    boolean current = getBoolean(ConfigName, key, false);
    // 写入相反的状态
    putBoolean(ConfigName, key, !current);
    toast("当前聊天功能已" + (!current ? "开启" : "关闭"));
}

// 新增：涩图功能开关回调
void toggleNSFWFunction(String group, String user, int type) {
    // 根据聊天类型确定uin
    String uin = group.isEmpty() ? user : group;
    String key = NSFWEnabledPrefix + uin;
    // 读取当前状态，默认开启
    boolean current = getBoolean(ConfigName, key, true);
    // 切换状态
    putBoolean(ConfigName, key, !current);
    toast("当前聊天涩图功能已" + (!current ? "开启" : "关闭"));
}

// 防冲突开关回调
void toggleAntiConflict(String group, String user, int type) {
    boolean current = getBoolean(ConfigName, AntiConflictKey, true);
    putBoolean(ConfigName, AntiConflictKey, !current);
    toast("消息排版美化已" + (!current ? "开启" : "关闭"));
}

// 图片翻转开关回调
void toggleFlipImage(String group, String user, int type) {
    boolean current = getBoolean(ConfigName, FlipImageKey, true);
    putBoolean(ConfigName, FlipImageKey, !current);
    toast("涩图翻转功能已" + (!current ? "开启" : "关闭"));
}

// 帮助回调
void showHelp(String group, String user, int type) {
    sendFormattedMsg(group, user, helpMessage);
}

// 指令列表回调（核心修改处）
void showCommands(String group, String user, int type) {
    StringBuilder commands = new StringBuilder("支持指令:\n");
    java.util.Iterator it = apiMap.keySet().iterator();
    while (it.hasNext()) {
        String cmd = (String) it.next();
        commands.append("- ").append(cmd).append("\n");
    }
    commands.append("- 看看涩图[标签]：指定标签的涩图，如'看看涩图白丝'(可开启翻转)\n");
    commands.append("- 看看标签[标签]：指定标签的普通图片，如'看看标签白丝'、'看看标签JK'\n");
    // 新增：添加「看看作者[作者名]」指令说明
    commands.append("- 看看作者[作者名]：获取指定作者的图片，如'看看作者Tiv'");

    sendFormattedMsg(group, user, commands.toString());
}

// 删除文件方法 - 增强版
void deleteFile(String path) {
    try {
        java.io.File file = new java.io.File(path);
        if (file.exists()) {
            // 尝试删除文件
            if (file.delete()) {
                // 成功删除
            } else {
                // 如果删除失败，尝试强制删除
                java.lang.Runtime.getRuntime().exec("rm -f \"" + path + "\"");
            }
        }
    } catch (Exception e) {
        // 可选：记录异常
    }
}
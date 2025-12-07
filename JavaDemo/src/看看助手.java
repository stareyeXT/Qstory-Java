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
String NSFWEnabledPrefix = "NSFWEnabled_"; // 涩图功能开关前缀（按聊天窗口独立）
String helpMessage = "【猫猫看腿助手使用说明】\n"
        + "1. 支持多种触发词：\n"
        + "  - 看看猫猫：可爱猫咪\n"
        + "  - 看看jk：JK制服图片\n"
        + "  - 看看cos：COSPLAY角色扮演图片\n"
        + "  - 看看二次元：精美二次元壁纸（多接口随机）\n"
        + "  - 看看涩图：随机涩图(可开启翻转)\n"
        + "  - 看看涩图[标签]：指定标签的涩图，如'看看涩图白丝'\n"
        + "  - 看看标签[标签]：指定标签的普通图片，如'看看标签白丝'、'看看标签JK'\n"
        + "  - 看看作者[作者名]：获取指定作者的图片，如'看看作者Tiv'\n"
        + "  - 看看黄金：浙商银行积存金实时价格\n"
        + "2. 可在菜单中开启/关闭功能\n"
        + "3. 视频/图片自动加载并发送，无需手动操作\n"
        + "※注意：功能默认关闭且分离控制，请在聊天窗口菜单中为当前群聊/私聊单独开启。";

// API配置
java.util.Map apiMap = new java.util.HashMap();

// API映射（修正转义，移除多余反斜杠）
{
    apiMap.put("看看猫猫", "https://edgecats.net/");
    apiMap.put("看看jk", "https://api.yujn.cn/api/jk.php");
    apiMap.put("看看cos", "https://api.tangdouz.com/hlxmt.php");
    // 修改为数组形式，支持多个API接口随机选择
    apiMap.put("看看二次元", new String[]{
        "https://api.tangdouz.com/abz/dm.php",
        "https://api.mossia.top/duckMo?aiType=1&r18Type=0"
    });
    apiMap.put("看看黄金", "https://api.tangdouz.com/a/zsgold.php");
}

// 确保缓存目录存在
void initCacheDir() {
    java.io.File cacheDir = new java.io.File(appPath, "cache");
    if (!cacheDir.exists()) {
        cacheDir.mkdirs();
    }
}

// 随机选择二次元API（使用随机选择而非固定顺序）
String getRandomAnimeApi() {
    String[] animeApis = {
        "https://api.tangdouz.com/abz/dm.php",
        "https://api.mossia.top/duckMo?aiType=1&r18Type=0",
        "https://random-api.czl.net/pic/ecy"
    };
    
    // 使用Random类实现随机选择
    java.util.Random random = new java.util.Random();
    int randomIndex = random.nextInt(animeApis.length);
    return animeApis[randomIndex];
}

// 翻转图片（水平+垂直翻转）
String flipImage(String originalPath) {
    try {
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
        // 发送消息失败时仅保证toast显示
    }
}

// 下载媒体文件到本地并返回路径
String downloadMedia(String apiUrl, Object msg) {
    initCacheDir();
    try {
        // 生成唯一文件名，根据URL判断文件类型
        String fileExtension = ".jpg"; // 默认为图片
        if (apiUrl.contains("jxbssp") || apiUrl.contains("jxhssp")) {
            fileExtension = ".mp4"; // 视频文件
        }
        
        String fileName = java.util.UUID.randomUUID().toString() + fileExtension;
        String filePath = new java.io.File(appPath, "cache/" + fileName).getAbsolutePath();

        // 打开API连接
        java.net.URL url = new java.net.URL(apiUrl);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);

        // 处理重定向
        int responseCode = conn.getResponseCode();
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

        // 检查最终响应状态
        if (responseCode != java.net.HttpURLConnection.HTTP_OK) {
            showError(msg, "文件下载失败: HTTP " + responseCode);
            return null;
        }

        // 读取并写入文件
        java.io.InputStream in = conn.getInputStream();
        java.io.FileOutputStream out = new java.io.FileOutputStream(filePath);
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
        }

        // 关闭流
        out.close();
        in.close();

        // 检查文件有效性
        java.io.File file = new java.io.File(filePath);
        if (file.length() == 0) {
            file.delete();
            showError(msg, "下载的文件为空");
            return null;
        }

        return filePath;
    } catch (Exception e) {
        showError(msg, "文件下载异常: " + e.getMessage());
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
        if (response == null || response.isEmpty() || !response.contains("img=")) {
            showError(msg, "无效的COS图片响应");
            return;
        }

// 分割字符串获取图片URL
        String[] parts = response.split("±");
        java.util.List<String> imageUrls = new java.util.ArrayList<>();
        for (String part : parts) {
            if (part.startsWith("img=")) {
                String url = part.substring(4);
                if (url.startsWith("http")) {
                    imageUrls.add(url);
                }
            }
        }

        if (imageUrls.isEmpty()) {
            showError(msg, "未找到COS图片");
            return;
        }

        // 异步发送图片
        new Thread(new Runnable() {
            public void run() {
                try {
                    for (int i = 0; i < imageUrls.size(); i++) {
                        String imageUrl = imageUrls.get(i);
                        String localPath = downloadMedia(imageUrl, msg);

                        if (localPath != null) {
                            final String path = localPath;
                            final int index = i + 1;

                            // 主线程发送图片
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
                            Thread.sleep(800); // 避免发送过快
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

// 处理涩图请求（含翻转、开关检查）
void handleNSFWRequest(String content, Object msg) {
    try {
        // 检查当前聊天涩图开关
        String uin = ((Object)msg).IsGroup ? ((Object)msg).GroupUin : ((Object)msg).PeerUin;
        boolean nsfwEnabled = getBoolean(ConfigName, NSFWEnabledPrefix + uin, true);
        if (!nsfwEnabled) {
            showError(msg, "当前聊天已关闭涩图功能，请在菜单中开启");
            return;
        }

        if (content.startsWith("看看涩图")) {
            String tag = content.substring("看看涩图".length()).trim();
            
            // 只有在不带标签时才使用指定的三个API
            String[] nsfwApis;
            if (tag.isEmpty()) {
                // 不带标签时使用您指定的三个API
                nsfwApis = new String[] {
                    "https://api.lolicon.app/setu/v2?r18=1&aiType=1",
                    "https://api.mossia.top/duckMo?aiType=1&r18Type=1",
                    "https://sex.nyan.run/api/v2/?r18=true"
                };
            } else {
                // 带标签时使用原来的API（如果需要的话）
                nsfwApis = new String[] {
                    "https://api.lolicon.app/setu/v2?r18=1&aiType=1&tag=",
                    "https://api.mossia.top/duckMo?aiType=1&r18Type=1&tag=",
                    "https://sex.nyan.run/api/v2/?r18=true&tag="
                };
            }
            
            String apiUrl = null;
            String imageUrl = null;
            boolean apiSuccess = false;

            // 遍历API直到成功
            for (int i = 0; i < nsfwApis.length; i++) {
                try {
                    // 根据是否有标签来构建URL
                    if (tag.isEmpty()) {
                        apiUrl = nsfwApis[i];
                    } else {
                        apiUrl = nsfwApis[i] + java.net.URLEncoder.encode(tag, "UTF-8");
                    }
                    
                    String response = httpGet(apiUrl, msg);
                    if (response == null || response.isEmpty()) continue;

                    // 解析不同API响应
                    if (apiUrl.contains("lolicon")) {
                        org.json.JSONObject json = new org.json.JSONObject(response);
                        if (!json.optString("error", "").isEmpty()) continue;
                        org.json.JSONArray dataArray = json.getJSONArray("data");
                        if (dataArray.length() == 0) continue;
                        org.json.JSONObject firstData = dataArray.getJSONObject(0);
                        imageUrl = firstData.getJSONObject("urls").getString("original");
                    } else if (apiUrl.contains("mossia")) {
                        org.json.JSONObject json = new org.json.JSONObject(response);
                        if (!"200".equals(json.optString("errCode", ""))) continue;
                        org.json.JSONArray dataArray = json.getJSONArray("data");
                        if (dataArray.length() == 0) continue;
                        org.json.JSONObject firstData = dataArray.getJSONObject(0);
                        org.json.JSONArray urlsList = firstData.getJSONArray("urlsList");
                        if (urlsList.length() == 0) continue;
                        imageUrl = urlsList.getJSONObject(0).getString("url");
                    } else if (apiUrl.contains("sex.nyan.run")) {
                        org.json.JSONObject json = new org.json.JSONObject(response);
                        if (!json.optBoolean("success", false)) continue;
                        org.json.JSONArray dataArray = json.getJSONArray("data");
                        if (dataArray.length() == 0) continue;
                        imageUrl = dataArray.getJSONObject(0).getString("url");
                    }
                    apiSuccess = true;
                    break;
                } catch (Exception e) {
                    continue;
                }
            }

            if (!apiSuccess) {
                showError(msg, "所有涩图API均失败，请稍后再试");
                return;
            }

            // 下载并处理图片
            String localPath = downloadMedia(imageUrl, msg);
            if (localPath == null) {
                showError(msg, "涩图下载失败");
                return;
            }

            // 检查是否翻转图片
            boolean flipEnabled = getBoolean(ConfigName, FlipImageKey, true);
            String finalImagePath = localPath;
            if (flipEnabled) {
                String flippedPath = flipImage(localPath);
                if (flippedPath != null) {
                    deleteFile(localPath);
                    finalImagePath = flippedPath;
                } else {
                    toast("图片翻转失败，将发送原始图片");
                }
            }

            // 主线程发送图片
            android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
            mainHandler.post(new Runnable() {
                public void run() {
                    try {
                        if (((Object)msg).IsGroup) {
                            sendPic(((Object)msg).GroupUin, "", finalImagePath);
                        } else {
                            sendPic("", ((Object)msg).PeerUin, finalImagePath);
                        }

                        // 1分钟后删除缓存（5分钟后再次尝试）
                        android.os.Handler handler = new android.os.Handler();
                        handler.postDelayed(new Runnable() {
                            public void run() {
                                deleteFile(finalImagePath);
                            }
                        }, 60000);
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
        new Thread(new Runnable() {
            public void run() {
                try {
                    String response = httpGet(apiUrl, msg);
                    if (response == null || response.isEmpty()) {
                        showError(msg, "获取黄金价格失败");
                        return;
                    }

                    // 主线程发送结果
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

// 处理带标签的图片请求
void handleTaggedRequest(String content, Object msg) {
    try {
        if (!content.startsWith("看看标签")) return;

        String tag = content.substring(4).trim();
        if (tag.isEmpty()) return;

        // 标签图片API列表
        String[] taggedApis = {
                "https://api.lolicon.app/setu/v2?tag=",
                "https://sex.nyan.run/api/v2/?tag="
        };

        String apiUrl = null;
        String imageUrl = null;
        boolean apiSuccess = false;

        // 创建随机数生成器
        java.util.Random random = new java.util.Random();
        
        // 创建API索引数组并随机打乱
        Integer[] apiIndices = new Integer[taggedApis.length];
        for (int i = 0; i < taggedApis.length; i++) {
            apiIndices[i] = i;
        }
        
        // 随机打乱API索引数组
        for (int i = 0; i < apiIndices.length; i++) {
            int randomIndex = random.nextInt(apiIndices.length);
            Integer temp = apiIndices[i];
            apiIndices[i] = apiIndices[randomIndex];
            apiIndices[randomIndex] = temp;
        }

        // 按随机顺序遍历API直到成功
        for (int i = 0; i < apiIndices.length; i++) {
            int index = apiIndices[i];
            try {
                apiUrl = taggedApis[index] + java.net.URLEncoder.encode(tag, "UTF-8");
                String response = httpGet(apiUrl, msg);
                if (response == null || response.isEmpty()) continue;

                // 解析响应
                org.json.JSONObject json = new org.json.JSONObject(response);
                if (!json.optString("error", "").isEmpty()) continue;
                
                org.json.JSONArray dataArray = json.getJSONArray("data");
                if (dataArray.length() == 0) continue;
                
                org.json.JSONObject firstData = dataArray.getJSONObject(0);
                imageUrl = firstData.getJSONObject("urls").getString("original");
                apiSuccess = true;
                break;
            } catch (Exception e) {
                continue;
            }
        }

        if (!apiSuccess) {
            showError(msg, "所有标签图片API均失败，请稍后再试");
            return;
        }

        // 下载并发送图片
        String localPath = downloadMedia(imageUrl, msg);
        if (localPath == null) {
            showError(msg, "标签图片下载失败");
            return;
        }

        // 主线程发送图片
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
                    showError(msg, "标签图片发送失败: " + e.getMessage());
                }
            }
        });
    } catch (Exception e) {
        showError(msg, "处理标签图片请求出错: " + e.getMessage());
    }
}

// 处理"看看作者"请求
void handleAuthorRequest(String content, Object msg) {
    try {
        if (!content.startsWith("看看作者")) return;

        String authorName = content.substring(4).trim();
        if (authorName.isEmpty()) {
            showError(msg, "请输入作者名，例如：看看作者Tiv");
            return;
        }

        // 构建API URL
        String apiUrl = "https://api.mossia.top/duckMo?author=" + java.net.URLEncoder.encode(authorName, "UTF-8");

        new Thread(new Runnable() {
            public void run() {
                try {
                    String response = httpGet(apiUrl, msg);
                    if (response == null || response.isEmpty()) {
                        showError(msg, "获取作者图片失败");
                        return;
                    }

                    // 解析响应
                    org.json.JSONObject json = new org.json.JSONObject(response);
                    if (!"200".equals(json.optString("errCode", ""))) {
                        showError(msg, "API错误: " + json.optString("message", "未知错误"));
                        return;
                    }

                    org.json.JSONArray dataArray = json.getJSONArray("data");
                    if (dataArray.length() == 0) {
                        showError(msg, "未找到该作者的图片");
                        return;
                    }

                    org.json.JSONObject firstData = dataArray.getJSONObject(0);
                    org.json.JSONArray urlsList = firstData.getJSONArray("urlsList");
                    if (urlsList.length() == 0) {
                        showError(msg, "未找到图片URL");
                        return;
                    }
                    String imageUrl = urlsList.getJSONObject(0).getString("url");

                    // 下载并发送图片
                    String localPath = downloadMedia(imageUrl, msg);
                    if (localPath == null) {
                        showError(msg, "作者图片下载失败");
                        return;
                    }

                    // 主线程发送图片
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
                                showError(msg, "作者图片发送失败: " + e.getMessage());
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

// 核心消息处理逻辑
void onMsg(Object msg) {
    try {
        String content = (String) ((Object)msg).MessageContent;
        String uin = ((Object)msg).IsGroup ? ((Object)msg).GroupUin : ((Object)msg).PeerUin;
        String key = uin + "_enabled";
        boolean enabled = getBoolean(ConfigName, key, false);

        // 功能未开启则直接返回
        if (!enabled) return;

        // 优先级：涩图 > 黄金 > 标签 > COS > 二次元 > 视频/图片 > 作者
        if (content.startsWith("看看涩图")) {
            handleNSFWRequest(content, msg);
            return;
        }

        if ("看看黄金".equals(content)) {
            handleGoldPriceRequest(msg);
            return;
        }

        if (content.startsWith("看看标签") && !apiMap.containsKey(content)) {
            handleTaggedRequest(content, msg);
            return;
        }

        String apiUrl = (String) apiMap.get(content);
        if (apiUrl == null) {
            // 处理作者请求（非预定义API）
            if (content.startsWith("看看作者")) {
                handleAuthorRequest(content, msg);
            }
            return;
        }

        // 特殊处理COS指令
        if ("https://api.tangdouz.com/hlxmt.php".equals(apiUrl)) {
            new Thread(new Runnable() {
                public void run() {
                    try {
                        String response = httpGet(apiUrl, msg);
                        if (response == null || response.isEmpty()) {
                            showError(msg, "COS图片获取失败");
                            return;
                        }
                        handleCosApiResponse(response, msg);
                    } catch (Exception e) {
                        showError(msg, "获取COS图片失败: " + e.getMessage());
                    }
                }
            }).start();
            return;
        }

        // 处理二次元图片
        // 修改为支持多个API接口随机选择
        if (content.equals("看看二次元")) {
            // 随机选择一个API
            String selectedApi = getRandomAnimeApi();
            
            new Thread(new Runnable() {
                public void run() {
                    try {
                        // 获取API响应
                        String response = httpGet(selectedApi, msg);
                        if (response == null || response.isEmpty()) {
                            showError(msg, "二次元图片获取失败");
                            return;
                        }

                        // 根据选择的API处理响应
                        String imageUrl = response.trim();
                        if (selectedApi.equals("https://api.mossia.top/duckMo?aiType=1&r18Type=0")) {
                            try {
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
                                    showError(msg, "未找到图片");
                                    return;
                                }

                                org.json.JSONObject firstData = dataArray.getJSONObject(0);
                                org.json.JSONArray urlsList = firstData.getJSONArray("urlsList");
                                if (urlsList.length() == 0) {
                                    showError(msg, "未找到图片URL");
                                    return;
                                }
                                imageUrl = urlsList.getJSONObject(0).getString("url");
                            } catch (Exception e) {
                                showError(msg, "解析二次元图片响应失败: " + e.getMessage());
                                return;
                            }
                        }

                        // 下载并发送图片
                        String localPath = downloadMedia(imageUrl, msg);
                        if (localPath == null) {
                            showError(msg, "二次元图片下载失败");
                            return;
                        }

                        // 主线程发送图片
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
                                }
                            }
                        });
                    } catch (Exception e) {
                        showError(msg, "获取二次元图片失败: " + e.getMessage());
                    }
                }
            }).start();
            return;
        }

        // 处理视频/普通图片
        boolean isVideo = apiUrl.contains("jxbssp") || apiUrl.contains("jxhssp");
        if (isVideo) {
            // 视频处理（直接发送）
            new Thread(new Runnable() {
                public void run() {
                    try {
                        String localPath = downloadMedia(apiUrl, msg);
                        if (localPath == null) {
                            showError(msg, content + " 视频下载失败");
                            return;
                        }

                        android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
                        mainHandler.post(new Runnable() {
                            public void run() {
                                try {
                                    if (((Object)msg).IsGroup) {
                                        sendVideo(((Object)msg).GroupUin, "", localPath);
                                    } else {
                                        sendVideo("", ((Object)msg).PeerUin, localPath);
                                    }

                                    // 1分钟后删除缓存
                                    android.os.Handler handler = new android.os.Handler();
                                    handler.postDelayed(new Runnable() {
                                        public void run() {
                                            deleteFile(localPath);
                                        }
                                    }, 60000);
                                } catch (Exception e) {
                                    showError(msg, content + " 视频发送失败: " + e.getMessage());
                                }
                            }
                        });
                    } catch (Exception e) {
                        showError(msg, content + " 视频处理失败: " + e.getMessage());
                    }
                }
            }).start();
        } else {
            // 普通图片处理
            new Thread(new Runnable() {
                public void run() {
                    try {
                        String localPath = downloadMedia(apiUrl, msg);
                        if (localPath == null) {
                            showError(msg, content + " 图片下载失败");
                            return;
                        }

                        // 主线程发送图片
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
                                    showError(msg, content + " 图片发送失败: " + e.getMessage());
                                }
                            }
                        });
                    } catch (Exception e) {
                        showError(msg, content + " 图片处理失败: " + e.getMessage());
                    }
                }
            }).start();
        }
    } catch (Exception e) {
        showError(msg, "消息处理异常: " + e.getMessage());
    }
}

// 悬浮窗菜单（无新增开关，保留原有功能）
void onClickFloatingWindow(int type, String uin) {
    String key = uin + "_enabled";
    boolean enabled = getBoolean(ConfigName, key, false);
    addTemporaryItem("功能开关: " + (enabled ? "✔已开启" : "✖已关闭"), "toggleSwitch");

    // 涩图开关
    boolean nsfwEnabled = getBoolean(ConfigName, NSFWEnabledPrefix + uin, true);
    addTemporaryItem("涩图功能: " + (nsfwEnabled ? "✔已开启" : "✖已关闭"), "toggleNSFWFunction");

    // 防冲突开关
    boolean antiConflict = getBoolean(ConfigName, AntiConflictKey, true);
    addTemporaryItem("消息排版美化: " + (antiConflict ? "✔已开启" : "✖已关闭"), "toggleAntiConflict");

    // 图片翻转开关
    boolean flipEnabled = getBoolean(ConfigName, FlipImageKey, true);
    addTemporaryItem("涩图翻转: " + (flipEnabled ? "✔已开启" : "✖已关闭"), "toggleFlipImage");

    // 帮助和指令列表
    addTemporaryItem("使用帮助", "showHelp");
    addTemporaryItem("支持指令列表", "showCommands");
}

// 功能开关切换
void toggleSwitch(String group, String user, int type) {
    String uin = group.isEmpty() ? user : group;
    String key = uin + "_enabled";
    boolean current = getBoolean(ConfigName, key, false);
    putBoolean(ConfigName, key, !current);
    toast("当前聊天功能已" + (!current ? "开启" : "关闭"));
}

// 涩图功能开关切换
void toggleNSFWFunction(String group, String user, int type) {
    String uin = group.isEmpty() ? user : group;
    String key = NSFWEnabledPrefix + uin;
    boolean current = getBoolean(ConfigName, key, true);
    putBoolean(ConfigName, key, !current);
    toast("当前聊天涩图功能已" + (!current ? "开启" : "关闭"));
}

// 防冲突开关切换
void toggleAntiConflict(String group, String user, int type) {
    boolean current = getBoolean(ConfigName, AntiConflictKey, true);
    putBoolean(ConfigName, AntiConflictKey, !current);
    toast("消息排版美化已" + (!current ? "开启" : "关闭"));
}

// 图片翻转开关切换
void toggleFlipImage(String group, String user, int type) {
    boolean current = getBoolean(ConfigName, FlipImageKey, true);
    putBoolean(ConfigName, FlipImageKey, !current);
    toast("涩图翻转功能已" + (!current ? "开启" : "关闭"));
}

// 显示帮助
void showHelp(String group, String user, int type) {
    sendFormattedMsg(group, user, helpMessage);
}

// 显示支持的指令列表
void showCommands(String group, String user, int type) {
    StringBuilder commands = new StringBuilder("支持指令:\n");
    java.util.Iterator it = apiMap.keySet().iterator();
    while (it.hasNext()) {
        String cmd = (String) it.next();
        commands.append("- ").append(cmd).append("\n");
    }
    commands.append("- 看看涩图[标签]：指定标签的涩图，如'看看涩图白丝'(可开启翻转)\n");
    commands.append("- 看看标签[标签]：指定标签的普通图片，如'看看标签白丝'、'看看标签JK'\n");
    commands.append("- 看看作者[作者名]：获取指定作者的图片，如'看看作者Tiv'");

    sendFormattedMsg(group, user, commands.toString());
}

// 增强版删除文件
void deleteFile(String path) {
    try {
        java.io.File file = new java.io.File(path);
        if (file.exists()) {
            if (!file.delete()) {
                // 强制删除（修正转义字符错误）
                java.lang.Runtime.getRuntime().exec("rm -f \"" + path + "\"");
            }
        }
    } catch (Exception e) {
        // 静默处理删除异常
    }
}
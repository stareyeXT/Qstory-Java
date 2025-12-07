load(appPath + "/import/import.java");
addItem("构建音乐卡片", "inputCard");
addItem("搜索", "diange");
addItem("说明", "ceshikuang");
// 新增：添加群友点播开关
addItem("群友点播", "toggleSongRequest");

// 全局变量（仅保留无需场景切换的变量）
String musicurl = "https://api.jkyai.top/API/qqmusic.php";
String musicurlkw = "https://oiapi.net/api/kuwo";
String songRequestConfig = "群友点播开关";
String sendType = "";
// 分页缓存：key=会话标识，value={totalPages:总页数, currentPage:当前页, allSongs:所有歌曲列表, keyword:搜索关键词}
HashMap<String, Map<String, Object>> songPageCache = new HashMap<>();
// 每页显示歌曲数量
int PAGE_SIZE = 10;

// 修复：群友点播开关回调（主线程执行Toast）
public void toggleSongRequest(String groupUin, String uin, int chatType) {
    Activity activity = getActivity();
    if (activity == null) return;

    if (chatType == 1) {
        boolean isOpen = getBoolean(songRequestConfig, uin, false);
        if (isOpen) {
            putBoolean(songRequestConfig, uin, false);
            activity.runOnUiThread(() -> toast("已关闭与【" + uin + "】私聊的点播功能"));
        } else {
            putBoolean(songRequestConfig, uin, true);
            activity.runOnUiThread(() -> toast("已开启与【" + uin + "】私聊的点播功能"));
        }
        return;
    }
    boolean isOpen = getBoolean(songRequestConfig, groupUin, false);
    if (isOpen) {
        putBoolean(songRequestConfig, groupUin, false);
        activity.runOnUiThread(() -> toast("已关闭【" + groupUin + "】的群友点播功能"));
    } else {
        putBoolean(songRequestConfig, groupUin, true);
        activity.runOnUiThread(() -> toast("已开启【" + groupUin + "】的群友点播功能"));
    }
}

// ---------------------- 新增：判断点播开关是否开启（适配群/私聊） ----------------------
public boolean isSongRequestOpen(MessageData msg) {
    if (msg.IsGroup) {
        return getBoolean(songRequestConfig, msg.GroupUin, false);
    } else {
        return getBoolean(songRequestConfig, msg.PeerUin, false);
    }
}

// ---------------------- 新增：判断字符串是否为纯数字（用于识别编号回复） ----------------------
public boolean isNumeric(String str) {
    if (str == null || str.isEmpty()) return false;
    for (char c : str.toCharArray()) {
        if (!Character.isDigit(c)) {
            return false;
        }
    }
    return true;
}

// ---------------------- 新增：获取指定页的歌曲列表 ----------------------
public List<JSONObject> getPageSongs(List<JSONObject> allSongs, int currentPage) {
    List<JSONObject> pageSongs = new ArrayList<>();
    int startIndex = (currentPage - 1) * PAGE_SIZE;
    int endIndex = Math.min(startIndex + PAGE_SIZE, allSongs.size());
    if (startIndex >= allSongs.size()) {
        return pageSongs;
    }
    for (int i = startIndex; i < endIndex; i++) {
        pageSongs.add(allSongs.get(i));
    }
    return pageSongs;
}

// ---------------------- 改造：搜索歌曲并发送列表到聊天（修复私聊乱@） ----------------------
public void searchAndSendSongList(String songName, String targetGroupUin, String targetQq, int chatType, String sessionKey, String senderUin) {
    sendType = "card";
    
    new Thread(new Runnable() {
        public void run() {
            try {
                String encodeSongName = URLEncoder.encode(songName, "UTF-8");
                List<JSONObject> allSongs = new ArrayList<>();
                int totalPages = 5; // 固定请求1-5页
                
                // 循环请求page1到page5
                for (int page = 1; page <= totalPages; page++) {
                    String URLsge = musicurlkw + "?msg=" + encodeSongName + "&page=" + page;
                    String result = httpGet(URLsge, null);
                    List<JSONObject> pageSongList = extractSongJsonObjectListFromJsonkw(result);
                    if (pageSongList != null && !pageSongList.isEmpty()) {
                        allSongs.addAll(pageSongList);
                    }
                }
                
                // 初始化分页缓存
                Map<String, Object> pageData = new HashMap<>();
                pageData.put("totalPages", totalPages);
                pageData.put("currentPage", 1);
                pageData.put("allSongs", allSongs);
                pageData.put("keyword", songName);
                songPageCache.put(sessionKey, pageData);
                
                // 核心修复：仅群聊添加@，私聊不@
                String atUin = "";
                if (targetGroupUin != null && !targetGroupUin.isEmpty() && senderUin != null && !senderUin.isEmpty()) {
                    atUin = "[AtQQ=" + senderUin + "] ";
                }

                if (allSongs.isEmpty()) {
                    // 无结果时提示
                    getActivity().runOnUiThread(new Runnable() {
                        public void run() {
                            toast("未找到【" + songName + "】相关歌曲");
                            sendMsg(targetGroupUin, targetGroupUin.isEmpty() ? targetQq : "", atUin + "未找到【" + songName + "】相关歌曲");
                        }
                    });
                    return;
                }
                
                // 获取第1页歌曲
                List<JSONObject> firstPageSongs = getPageSongs(allSongs, 1);
                // 构建带分页的歌曲列表消息
                StringBuilder songListMsg = new StringBuilder();
                songListMsg.append(atUin).append("为你找到【").append(songName).append("】相关歌曲（第1/").append(totalPages).append("页）：\n");
                for (int i = 0; i < firstPageSongs.size(); i++) {
                    JSONObject song = firstPageSongs.get(i);
                    String songNameItem = song.getString("song");
                    String singerItem = song.getString("singer");
                    songListMsg.append((i + 1)).append(". ").append(songNameItem).append(" —— ").append(singerItem).append("\n");
                }
                // 提示下一页指令
                if (totalPages > 1) {
                    songListMsg.append("回复【下一页】查看更多，回复对应编号播放歌曲");
                } else {
                    songListMsg.append("请回复对应编号播放歌曲");
                }
                
                // 发送列表到聊天
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        sendMsg(targetGroupUin, targetGroupUin.isEmpty() ? targetQq : "", songListMsg.toString());
                        toast("已发送【" + songName + "】的歌曲列表（第1页），请回复编号播放歌曲");
                    }
                });
                
            } catch (UnsupportedEncodingException e) {
                log("关键词编码异常: " + e.toString());
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        toast("搜索失败：关键词编码异常");
                    }
                });
            } catch (Exception e) {
                log("搜索歌曲列表异常: " + e.toString());
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        toast("搜索失败：" + e.getMessage());
                    }
                });
            }
        }
    }).start();
}

// ---------------------- 新增：处理下一页请求（修复私聊@） ----------------------
public void handleNextPage(String sessionKey, String targetGroupUin, String targetQq, String senderUin) {
    if (!songPageCache.containsKey(sessionKey)) {
        // 区分群/私聊提示
        String atTip = targetGroupUin != null && !targetGroupUin.isEmpty() ? "[AtQQ=" + senderUin + "] " : "";
        sendMsg(targetGroupUin, targetQq, atTip + "暂无更多歌曲数据，请先发送「播放歌曲+关键词」搜索");
        return;
    }
    
    Map<String, Object> pageData = songPageCache.get(sessionKey);
    int currentPage = (int) pageData.get("currentPage");
    int totalPages = (int) pageData.get("totalPages");
    String keyword = (String) pageData.get("keyword");
    List<JSONObject> allSongs = (List<JSONObject>) pageData.get("allSongs");
    
    // 检查是否有下一页
    if (currentPage >= totalPages) {
        String atTip = targetGroupUin != null && !targetGroupUin.isEmpty() ? "[AtQQ=" + senderUin + "] " : "";
        sendMsg(targetGroupUin, targetQq, atTip + "已经是最后一页了，共" + totalPages + "页");
        return;
    }
    
    // 切换到下一页
    int nextPage = currentPage + 1;
    pageData.put("currentPage", nextPage);
    songPageCache.put(sessionKey, pageData);
    
    // 获取下一页歌曲
    List<JSONObject> nextPageSongs = getPageSongs(allSongs, nextPage);
    if (nextPageSongs.isEmpty()) {
        String atTip = targetGroupUin != null && !targetGroupUin.isEmpty() ? "[AtQQ=" + senderUin + "] " : "";
        sendMsg(targetGroupUin, targetQq, atTip + "第" + nextPage + "页暂无歌曲");
        return;
    }
    
    // 核心修复：仅群聊添加@前缀
    String atPrefix = targetGroupUin != null && !targetGroupUin.isEmpty() && senderUin != null ? "[AtQQ=" + senderUin + "] " : "";
    // 构建下一页消息
    StringBuilder songListMsg = new StringBuilder();
    songListMsg.append(atPrefix).append("为你找到【").append(keyword).append("】相关歌曲（第").append(nextPage).append("/").append(totalPages).append("页）：\n");
    for (int i = 0; i < nextPageSongs.size(); i++) {
        JSONObject song = nextPageSongs.get(i);
        String songNameItem = song.getString("song");
        String singerItem = song.getString("singer");
        songListMsg.append((i + 1)).append(". ").append(songNameItem).append(" —— ").append(singerItem).append("\n");
    }
    
    // 提示下一页/结束
    if (nextPage < totalPages) {
        songListMsg.append("回复【下一页】查看更多，回复对应编号播放歌曲");
    } else {
        songListMsg.append("已到最后一页，请回复对应编号播放歌曲");
    }
    
    sendMsg(targetGroupUin, targetQq, songListMsg.toString());
    toast("已发送【" + keyword + "】的歌曲列表（第" + nextPage + "页）");
}

// ---------------------- 核心修改：播放指定页码的歌曲（传递完整歌曲信息） ----------------------
public void playSpecifiedSongByPage(String sessionKey, int songIndex, String targetGroupUin, String targetQq, int chatType, MessageData msg) {
    if (!songPageCache.containsKey(sessionKey)) {
        toast("暂无歌曲数据，请先搜索");
        return;
    }
    
    Map<String, Object> pageData = songPageCache.get(sessionKey);
    int currentPage = (int) pageData.get("currentPage");
    List<JSONObject> allSongs = (List<JSONObject>) pageData.get("allSongs");
    String keyword = (String) pageData.get("keyword");
    
    // 计算歌曲在总列表中的真实索引
    int realIndex = (currentPage - 1) * PAGE_SIZE + (songIndex - 1);
    if (realIndex < 0 || realIndex >= allSongs.size()) {
        toast("歌曲编号不存在");
        // 仅群聊@发送者
        String atTip = msg.IsGroup ? "[AtQQ=" + msg.UserUin + "] " : "";
        sendMsg(targetGroupUin, targetQq, atTip + "歌曲编号不存在，请重新输入");
        return;
    }
    
    // 获取选中的完整歌曲对象（核心：不再只传歌曲名）
    JSONObject selectedSong = allSongs.get(realIndex);
    
    // 调用重载的musicget方法，传递完整歌曲对象
    musicget(selectedSong, targetGroupUin, targetQq, chatType);
}

// ---------------------- 修改：消息监听与指令解析（修复私聊@） ----------------------
void onMsg(MessageData msg) {
    String msgContent = msg.MessageContent.trim();
    String sessionKey = msg.IsGroup ? msg.GroupUin : msg.PeerUin;

    if (isSongRequestOpen(msg)) {
        // 场景1：发送「播放歌曲xxx」→ 搜索并发送列表
        if (msgContent.startsWith("播放歌曲")) {
            String songName = msgContent.substring("播放歌曲".length()).trim();
            if (songName.isEmpty()) {
                String targetGroupUin = msg.IsGroup ? msg.GroupUin : "";
                String targetSendUin = msg.IsGroup ? "" : msg.PeerUin;
                // 核心修复：仅群聊@，私聊不@
                String atUin = msg.IsGroup ? "[AtQQ=" + msg.UserUin + "] " : "";
                sendMsg(targetGroupUin, targetSendUin, atUin + "请输入具体歌曲名，格式：播放歌曲xxx");
                return;
            }
            
            String targetGroupUin = msg.IsGroup ? msg.GroupUin : "";
            String targetQq = msg.IsGroup ? "" : msg.PeerUin;
            int chatType = msg.IsGroup ? 2 : 1;
            searchAndSendSongList(songName, targetGroupUin, targetQq, chatType, sessionKey, msg.UserUin);
        }
        // 场景2：回复「下一页」→ 切换到下一页
        else if ("下一页".equals(msgContent)) {
            String targetGroupUin = msg.IsGroup ? msg.GroupUin : "";
            String targetQq = msg.IsGroup ? "" : msg.PeerUin;
            handleNextPage(sessionKey, targetGroupUin, targetQq, msg.UserUin);
        }
        // 场景3：回复纯数字 → 播放对应编号的歌曲
        else if (isNumeric(msgContent)) {
            int songIndex = Integer.parseInt(msgContent);
            String targetGroupUin = msg.IsGroup ? msg.GroupUin : "";
            String targetQq = msg.IsGroup ? "" : msg.PeerUin;
            int chatType = msg.IsGroup ? 2 : 1;
            // 传入msg对象用于判断群/私聊
            playSpecifiedSongByPage(sessionKey, songIndex, targetGroupUin, targetQq, chatType, msg);
        }
    }
}

// ---------------------- 新增：解析JSONObject类型的歌曲列表 ----------------------
public static List<JSONObject> extractSongJsonObjectListFromJsonkw(String jsonString) {
    List<JSONObject> result = new ArrayList<>();
    try {
        if (jsonString == null || jsonString.isEmpty()) {
            return result;
        }
        JSONObject jsonObject = new JSONObject(jsonString);
        if (jsonObject.has("data") && !jsonObject.isNull("data")) {
            JSONArray dataArray = jsonObject.getJSONArray("data");
            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject song = dataArray.getJSONObject(i);
                result.add(song);
            }
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
    return result;
}

// 以下为原有逻辑 + 核心修改的播放方法
public void diange(String groupUin, String uin, int chatType) {
    sendType = "card";
    DialogCarryOut("请输入音乐", groupUin, uin, chatType);
}

public void ceshikuang(String groupUin, String uin, int chatType) {
    showNativeDialog("说明","搜索：音乐选择发送卡片(免VIP)\n\n因为音乐卡片签名时灵时不灵，增加语音发送保底\n\n构建音乐卡片：根据输入框提示需求输入相对应的，音乐跳转链接，可随意填写其他跳转链接（打开时会跳转到你的链接里面）\n\n如有疑问可进入qs官方群\n\n临江QQ群：634941583\n\n(以上信息均可复制)");
}

public void gungongerts(String garesc) {
    Activity activity = getActivity();
    showkexuigai("测试输出内容", "正在加载...", new OnDialogCreatedListener() {
        public void onViewCreated(TextView contentView, AlertDialog dialog) {
            new Thread(new Runnable() {
                public void run() {
                    String result = httpGet(garesc, null);;
                    activity.runOnUiThread(new Runnable() {
                        public void run() {
                            contentView.setText(result);
                        }
                    });
                }
            }).start();
        }
        public void onFailed() {
            toast("弹窗创建失败");
        }
    });
}

public void ceshishuchu(String tupianurl, String targetGroupUin, String targetQq, int chatType) {
    Activity activity = getActivity();
    try {
        String encodeUrl = URLEncoder.encode(tupianurl, "UTF-8");
        String URLsge = musicurlkw + "?msg=" + encodeUrl;
        gungongliebiao("音乐", tupianurl, new LinearLayCreatedListener() {
            public void onViewCreated(LinearLayout listContainer,Dialog dialog) {
                new Thread(new Runnable() {
                    public void run() {
                        String result = httpGet(URLsge, null);
                        List contact = extractSongListFromJsonkw(result);
                        log(contact);
                        activity.runOnUiThread(new Runnable() {
                            public void run() {
                                for (int i = 0; i < contact.size(); i++) {
                                    final int index = i;
                                    TextView item = new TextView(getActivity());
                                    item.setText(contact.get(i));
                                    item.setTextSize(20);
                                    item.setTextColor(Color.DKGRAY);
                                    item.setGravity(Gravity.START);
                                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.MATCH_PARENT,
                                            LinearLayout.LayoutParams.WRAP_CONTENT
                                    );
                                    item.setLayoutParams(params);
                                    item.setPadding(0, 50, 0, 50);
                                    item.setTag(i + 1);
                                    item.setOnClickListener(new View.OnClickListener() {
                                        public void onClick(View v) {
                                            int clickedIndex = (int) v.getTag();
                                            log("列表项点击: " + clickedIndex+tupianurl);
                                            dialog.dismiss();
                                            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                                public void run() {
                                                    try {
                                                        musicget(tupianurl, clickedIndex, targetGroupUin, targetQq, chatType);
                                                    } catch (Exception e) {
                                                        log("打开新对话框错误: " + e.toString());
                                                    }
                                                }
                                            }, 100);
                                        }
                                    });
                                    item.setClickable(true);
                                    item.setFocusable(true);
                                    item.setBackgroundResource(android.R.drawable.list_selector_background);
                                    listContainer.addView(item);
                                }
                            }
                        });
                    }
                }).start();
            };
            public void onFailed() {
                toast("弹窗创建失败");
            }
        });
    } catch (UnsupportedEncodingException e) {
        log("手动搜索编码异常: " + e.toString());
        toast("搜索失败：关键词编码异常");
    }
}

public void DialogCarryOut(String title, String targetGroupUin, String targetQq, int chatType) {
    Activity activity = getActivity();
    if (activity == null) return;
    activity.runOnUiThread(new Runnable() {
        public void run() {
            try {
                AlertDialog.Builder builder = new AlertDialog.Builder(activity, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);
                builder.setTitle(title);
                LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(50, 30, 50, 30);
                layout.setGravity(Gravity.CENTER);
                EditText inputEditText = new EditText(activity);
                inputEditText.setHint("请输入音乐");
                inputEditText.setHintTextColor(Color.GRAY);
                inputEditText.setGravity(Gravity.CENTER);
                inputEditText.setInputType(InputType.TYPE_CLASS_TEXT);
                LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        150
                );
                inputParams.bottomMargin = 40;
                inputEditText.setLayoutParams(inputParams);
                layout.addView(inputEditText);
                LinearLayout.LayoutParams xz = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        500
                );
                LinearLayout.LayoutParams pailie = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        250
                );
                RadioGroup radioGroup = new RadioGroup(activity);
                radioGroup.setOrientation(RadioGroup.VERTICAL);
                radioGroup.setGravity(Gravity.CENTER);
                RadioButton cardRadio = new RadioButton(activity);
                cardRadio.setText("卡片发送");
                cardRadio.setTextColor(Color.BLACK);
                cardRadio.setTextSize(30);
                cardRadio.setId(View.generateViewId());
                cardRadio.setLayoutParams(pailie);
                RadioButton voiceRadio = new RadioButton(activity);
                voiceRadio.setText("语音发送");
                voiceRadio.setTextColor(Color.BLACK);
                voiceRadio.setTextSize(30);
                voiceRadio.setId(View.generateViewId());
                voiceRadio.setLayoutParams(pailie);
                cardRadio.setChecked(true);
                radioGroup.addView(cardRadio);
                radioGroup.addView(voiceRadio);
                radioGroup.setLayoutParams(xz);
                layout.addView(radioGroup);
                builder.setView(layout);
                builder.setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        String input = inputEditText.getText().toString().trim();
                        int selectedId = radioGroup.getCheckedRadioButtonId();
                        if (input.isEmpty()) {
                            toast("输入不能为空");
                            return;
                        }
                        if (selectedId == cardRadio.getId()) {
                            sendType = "card";
                        } else if (selectedId == voiceRadio.getId()) {
                            sendType = "voice";
                        }
                        toast("选择发送方式为"+sendType);
                        dialog.dismiss();
                        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                            public void run() {
                                try {
                                    ceshishuchu(input, targetGroupUin, targetQq, chatType);
                                } catch (Exception e) {
                                    log("打开新对话框错误: " + e.toString());
                                }
                            }
                        }, 100);
                    }
                });
                builder.setNegativeButton("取消", null);
                builder.create().show();
            } catch (Exception e) {
                toast("设置弹窗错误: " + e.toString());
            }
        }
    });
}

public void showNativeDialog(String title,String neirong) {
    try {
        Activity activity = getActivity();
        if (activity == null) {
            log("弹窗创建失败：无法获取Activity");
            return;
        }
        String biaoti = title;
        String nr = neirong;
        activity.runOnUiThread(new Runnable() {
            public void run() {
                try {
                    LinearLayout layout = new LinearLayout(activity);
                    layout.setOrientation(LinearLayout.VERTICAL);
                    layout.setPadding(40, 80, 40, 0);
                    layout.setGravity(Gravity.CENTER);
                    TextView title = new TextView(activity);
                    title.setText(biaoti);
                    title.setTextSize(18);
                    title.setTextColor(Color.BLACK);
                    title.setGravity(Gravity.CENTER);
                    TextView content = new TextView(activity);
                    content.setText(nr);
                    content.setTextSize(16);
                    content.setTextColor(Color.DKGRAY);
                    content.setGravity(Gravity.START);
                    content.setTextIsSelectable(true);
                    ScrollView Scroll = new ScrollView(activity);
                    Scroll.setVerticalScrollBarEnabled(false);
                    Scroll.setPadding(0, 40, 0, 0);
                    Scroll.addView(content);
                    layout.addView(title);
                    layout.addView(Scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            1200
                    ));
                    AlertDialog dialog = new AlertDialog.Builder(activity, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                            .setView(layout)
                            .setPositiveButton("确定", null)
                            .setNegativeButton("取消", null)
                            .create();
                    dialog.show();
                } catch (Exception e) {
                    error(e);
                    toast("弹窗创建异常：" + e.getMessage());
                    log("弹窗错误: " + e.toString());
                }
            }
        });
    } catch (Exception e) {
        error(e);
        toast("弹窗初始化异常：" + e.getMessage());
        log("弹窗错误: " + e.toString());
    }
}

public String httpGet(String urlPath, String cookie) {
    StringBuffer buffer = new StringBuffer();
    InputStreamReader isr = null;
    BufferedReader reader = null;
    try {
        URL url = new URL(urlPath);
        HttpURLConnection uc = (HttpURLConnection) url.openConnection();
        uc.setRequestMethod("GET");
        uc.setConnectTimeout(20000);
        uc.setReadTimeout(20000);
        uc.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        uc.setRequestProperty("Accept", "application/json, text/plain, */*");
        if (cookie != null && !cookie.isEmpty()) {
            uc.setRequestProperty("Cookie", cookie);
        }
        int statusCode = uc.getResponseCode();
        if (statusCode == HttpURLConnection.HTTP_OK) {
            isr = new InputStreamReader(uc.getInputStream(), "UTF-8");
        } else {
            isr = new InputStreamReader(uc.getErrorStream(), "UTF-8");
        }
        reader = new BufferedReader(isr);
        String line;
        while ((line = reader.readLine()) != null) {
            buffer.append(line).append("\n");
        }
    } catch (Exception e) {
        e.printStackTrace();
        buffer.append("请求出错: ").append(e.getMessage());
    } finally {
        try {
            if (reader != null) reader.close();
            if (isr != null) isr.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    return buffer.toString();
}

public interface OnDialogCreatedListener {
    void onViewCreated(TextView contentView, AlertDialog dialog);
    void onFailed();
}

public interface LinearLayCreatedListener {
    void onViewCreated(LinearLayout contentView, AlertDialog dialog);
    void onFailed();
}

public TextView showkexuigai(String title,String neirong, OnDialogCreatedListener listener) {
    try {
        Activity activity = getActivity();
        if (activity == null) {
            log("弹窗创建失败：无法获取Activity");
            return null;
        }
        String biaoti = title;
        String nr = neirong;
        TextView[] contentViewRef = new TextView[1];
        activity.runOnUiThread(new Runnable() {
            public void run() {
                try {
                    LinearLayout layout = new LinearLayout(activity);
                    layout.setOrientation(LinearLayout.VERTICAL);
                    layout.setPadding(40, 80, 40, 0);
                    layout.setGravity(Gravity.CENTER);
                    TextView title = new TextView(activity);
                    title.setText(biaoti);
                    title.setTextSize(18);
                    title.setTextColor(Color.BLACK);
                    title.setGravity(Gravity.CENTER);
                    TextView content = new TextView(activity);
                    content.setText(nr);
                    content.setTextSize(16);
                    content.setTextColor(Color.DKGRAY);
                    content.setGravity(Gravity.START);
                    content.setTextIsSelectable(true);
                    contentViewRef[0] = content;
                    ScrollView Scroll = new ScrollView(activity);
                    Scroll.setVerticalScrollBarEnabled(false);
                    Scroll.setPadding(0, 40, 0, 0);
                    Scroll.addView(content);
                    layout.addView(title);
                    layout.addView(Scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            1200
                    ));
                    AlertDialog dialog = new AlertDialog.Builder(activity, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                            .setView(layout)
                            .setPositiveButton("确定", null)
                            .setNegativeButton("取消", null)
                            .create();
                    dialog.show();
                    log("弹窗已显示");
                    if (listener != null) {
                        listener.onViewCreated(content, dialog);
                    }
                } catch (Exception e) {
                    error(e);
                    log("弹窗创建异常: " + e.toString());
                    if (listener != null) listener.onFailed();
                }
            }
        });
        return contentViewRef[0];
    } catch (Exception e) {
        error(e);
        toast("弹窗初始化异常：" + e.getMessage());
        log("弹窗错误: " + e.toString());
        return null;
    }
}

public static List extractSongListFromJson(String jsonString) {
    List result = new ArrayList();
    try {
        JSONObject jsonObject = new JSONObject(jsonString);
        JSONObject songsg = jsonObject.getJSONObject("data");
        JSONArray dataArray = songsg.getJSONArray("songs");
        for (int i = 0; i < dataArray.length(); i++) {
            JSONObject song = dataArray.getJSONObject(i);
            String songName = song.getString("name");
            String singer = song.getString("artist");
            String formattedString = song.getInt("number") + "." + songName + "——" + singer;
            result.add(formattedString);
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
    return result;
}

public static List extractSongListFromJsonkw(String jsonString) {
    List result = new ArrayList();
    try {
        if (jsonString == null || jsonString.isEmpty()) {
            return result;
        }
        JSONObject jsonObject = new JSONObject(jsonString);
        if (jsonObject.has("data") && !jsonObject.isNull("data")) {
            JSONArray dataArray = jsonObject.getJSONArray("data");
            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject song = dataArray.getJSONObject(i);
                String songName = song.getString("song");
                String singer = song.getString("singer");
                String formattedString = (i + 1) + "." + songName + "——" + singer;
                result.add(formattedString);
            }
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
    return result;
}

public static String mergeStringList(List stringList) {
    if (stringList == null || stringList.isEmpty()) {
        return "";
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < stringList.size(); i++) {
        sb.append((String)stringList.get(i));
        if (i < stringList.size() - 1) {
            sb.append("\n");
        }
    }
    return sb.toString();
}

public static String convertJsonToStringArray(String jsonString) {
    List songList = extractSongListFromJson(jsonString);
    return mergeStringList(songList);
}

public void gungongliebiao(String garesc, String name, LinearLayCreatedListener listener) {
    try {
        Activity activity = getActivity();
        if (activity == null) {
            log("弹窗创建失败：无法获取Activity");
            return;
        }
        activity.runOnUiThread(new Runnable() {
            public void run() {
                try {
                    LinearLayout layout = new LinearLayout(activity);
                    layout.setOrientation(LinearLayout.VERTICAL);
                    layout.setPadding(40, 30, 40, 30);
                    layout.setGravity(Gravity.CENTER);
                    TextView title = new TextView(activity);
                    title.setText(garesc);
                    title.setTextSize(30);
                    title.setTextColor(Color.BLACK);
                    title.setGravity(Gravity.CENTER);
                    LinearLayout listContainer = new LinearLayout(activity);
                    listContainer.setOrientation(LinearLayout.VERTICAL);
                    TextView loadingView = new TextView(activity);
                    loadingView.setText("当前搜索："+name);
                    loadingView.setTextSize(16);
                    loadingView.setGravity(Gravity.CENTER);
                    listContainer.addView(loadingView);
                    ScrollView scroll = new ScrollView(activity);
                    scroll.setVerticalScrollBarEnabled(false);
                    scroll.setPadding(0, 40, 0, 0);
                    scroll.addView(listContainer);
                    layout.addView(title);
                    layout.addView(scroll, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            1200
                    ));
                    AlertDialog dialog = new AlertDialog.Builder(activity, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                            .setView(layout)
                            .setPositiveButton("确定", null)
                            .setNegativeButton("取消", null)
                            .create();
                    dialog.show();
                    if (listener != null) {
                        listener.onViewCreated(listContainer, dialog);
                    }
                } catch (Exception e) {
                    error(e);
                    if (listener != null) listener.onFailed();
                }
            }
        });
    } catch (Exception e) {
        error(e);
        toast("弹窗初始化异常：" + e.getMessage());
    }
}

public void gungong(String garesc, List methodList) {
    List contact = (ArrayList)methodList;
    try {
        Activity activity = getActivity();
        if (activity == null) {
            log("弹窗创建失败：无法获取Activity");
            return;
        }
        activity.runOnUiThread(new Runnable() {
            public void run() {
                try {
                    LinearLayout layout = new LinearLayout(activity);
                    layout.setOrientation(LinearLayout.VERTICAL);
                    layout.setPadding(40, 30, 40, 30);
                    layout.setGravity(Gravity.CENTER);
                    TextView title = new TextView(activity);
                    title.setText(garesc);
                    title.setTextSize(30);
                    title.setTextColor(Color.BLACK);
                    title.setGravity(Gravity.CENTER);
                    LinearLayout listContainer = new LinearLayout(activity);
                    listContainer.setOrientation(LinearLayout.VERTICAL);
                    for (int i = 0; i < contact.size(); i++) {
                        final int index = i;
                        TextView item = new TextView(activity);
                        item.setText(contact.get(i));
                        item.setTextSize(25);
                        item.setTextColor(Color.DKGRAY);
                        item.setGravity(Gravity.START);
                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                        );
                        item.setLayoutParams(params);
                        item.setPadding(0, 50, 0, 50);
                        item.setTag(i + 1);
                        item.setOnClickListener(new View.OnClickListener() {
                            public void onClick(View v) {
                                int clickedIndex = (int) v.getTag();
                                toast("您点击了: " + clickedIndex);
                                log("列表项点击: " + clickedIndex);
                            }
                        });
                        item.setClickable(true);
                        item.setFocusable(true);
                        item.setBackgroundResource(android.R.drawable.list_selector_background);
                        listContainer.addView(item);
                    }
                    ScrollView Scroll = new ScrollView(activity);
                    Scroll.setVerticalScrollBarEnabled(false);
                    Scroll.setPadding(0, 40, 0, 0);
                    Scroll.addView(listContainer);
                    layout.addView(title);
                    layout.addView(Scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            1200
                    ));
                    AlertDialog dialog = new AlertDialog.Builder(activity, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                            .setView(layout)
                            .setPositiveButton("确定", null)
                            .setNegativeButton("取消", null)
                            .create();
                    dialog.show();
                } catch (Exception e) {
                    error(e);
                    toast("弹窗创建异常：" + e.getMessage());
                }
            }
        });
    } catch (Exception e) {
        error(e);
        toast("弹窗初始化异常：" + e.getMessage());
    }
}

// ---------------------- 新增：重载musicget方法（接收完整歌曲对象，精准播放） ----------------------
public void musicget(JSONObject songObj, String targetGroupUin, String targetQq, int chatType) {
    Activity activity = getActivity();
    try {
        // 从选中的歌曲对象中获取精准的歌曲名和歌手
        String songName = songObj.getString("song");
        String singer = songObj.getString("singer");
        // 拼接精准的搜索关键词（歌曲名+歌手），避免匹配错误
        String preciseKeyword = songName + " " + singer;
        String encodeName = URLEncoder.encode(preciseKeyword, "UTF-8");
        // 强制指定取第1条结果（因为是精准匹配）
        String muaicul = musicurlkw + "?msg=" + encodeName + "&n=1" + "&br=1";
        guwastre(muaicul, targetGroupUin, targetQq, songName, singer);
    } catch (UnsupportedEncodingException e) {
        log("播放编码异常: " + e.toString());
        activity.runOnUiThread(new Runnable() {
            public void run() {
                toast("播放失败：关键词编码异常");
            }
        });
    }
}

// ---------------------- 保留原有musicget方法（兼容旧逻辑） ----------------------
public void musicget(String name, int swhich, String targetGroupUin, String targetQq, int chatType) {
    Activity activity = getActivity();
    String str1 = String.valueOf(swhich);
    try {
        String encodeName = URLEncoder.encode(name, "UTF-8");
        String muaicul = musicurlkw + "?msg=" + encodeName + "&n=" + str1 + "&br=1";
        guwastre(muaicul, targetGroupUin, targetQq, name, "");
    } catch (UnsupportedEncodingException e) {
        log("播放编码异常: " + e.toString());
        activity.runOnUiThread(new Runnable() {
            public void run() {
                toast("播放失败：关键词编码异常");
            }
        });
    }
}

// ---------------------- 修改：guwastre方法（接收精准的歌曲名和歌手，优化提示） ----------------------
public void guwastre(String garesc, String targetGroupUin, String targetQq, String songName, String singer) {
    Activity activity = getActivity();
    new Thread(new Runnable() {
        public void run() {
            try {
                String result = httpGet(garesc, null);
                JSONObject rootObject = new JSONObject(result);
                JSONObject dataObject = rootObject.getJSONObject("data");
                // 优先使用传入的精准歌曲名/歌手，避免接口返回不一致
                String finalSongName = songName.isEmpty() ? dataObject.getString("song") : songName;
                String finalSinger = singer.isEmpty() ? dataObject.getString("singer") : singer;
                String cover = dataObject.getString("picture");
                String flacUrl = dataObject.getString("url");
                String link = flacUrl;
                
                if ("card".equals(sendType)) {
                    String url = "https://oiapi.net/api/QQMusicJSONArk";
                    JSONObject requestBody = new JSONObject();
                    requestBody.put("url", flacUrl);
                    requestBody.put("song", finalSongName);
                    requestBody.put("singer", finalSinger);
                    requestBody.put("cover", cover);
                    requestBody.put("jump", link);
                    requestBody.put("format", "kuwo");
                    HashMap headers = new HashMap();
                    headers.put("Content-Type", "application/json;charset=UTF-8");
                    String card = httpRequest(url, requestBody.toString(), headers, "POST");
                    JSONObject carddata = new JSONObject(card);
                    JSONObject dataObj = carddata.getJSONObject("data");
                    String dataString = dataObj.toString();
                    
                    activity.runOnUiThread(new Runnable() {
                        public void run() {
                            sendCard(targetGroupUin, targetQq, dataString);
                            toast("已在" + (targetGroupUin.isEmpty() ? "私聊" : "群聊【" + targetGroupUin + "】") + "点播：" + finalSongName + " - " + finalSinger);
                        }
                    });
                } else if ("voice".equals(sendType)) {
                    Thread.sleep(200);
                    sendVoice(targetGroupUin, targetQq, flacUrl);
                    toast("已在" + (targetGroupUin.isEmpty() ? "私聊" : "群聊【" + targetGroupUin + "】") + "点播（语音）：" + finalSongName + " - " + finalSinger);
                }
            } catch (Exception e) {
                log("音乐发送异常: " + e.toString());
                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        toast("点播失败：" + e.getMessage());
                    }
                });
            }
        }
    }).start();
}

public void inputCard(String groupUin, String uin, int chatType) {
    Activity activity = getActivity();
    if (activity == null) return;
    activity.runOnUiThread(new Runnable() {
        public void run() {
            try {
                AlertDialog.Builder builder = new AlertDialog.Builder(activity, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);
                builder.setTitle("输入音乐卡片信息");
                LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                editParams.setMargins(0, 15, 0, 15);
                LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                textParams.setMargins(0, 5, 0, 5);
                LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(30, 30, 30, 30);
                layout.setGravity(Gravity.CENTER);
                TextView songLabel = new TextView(activity);
                songLabel.setText("音乐名称");
                songLabel.setTextColor(Color.parseColor("#555555"));
                songLabel.setTextSize(14);
                songLabel.setTypeface(Typeface.DEFAULT_BOLD);
                songLabel.setLayoutParams(textParams);
                layout.addView(songLabel);
                EditText song = new EditText(activity);
                song.setHint("例如：晴天");
                song.setHintTextColor(Color.GRAY);
                song.setTextColor(Color.BLACK);
                song.setGravity(Gravity.CENTER);
                song.setLayoutParams(editParams);
                layout.addView(song);
                TextView singerLabel = new TextView(activity);
                singerLabel.setText("歌手名称");
                singerLabel.setTextColor(Color.parseColor("#555555"));
                singerLabel.setTextSize(14);
                singerLabel.setTypeface(Typeface.DEFAULT_BOLD);
                singerLabel.setLayoutParams(textParams);
                layout.addView(singerLabel);
                EditText singer = new EditText(activity);
                singer.setHint("例如：周杰伦");
                singer.setHintTextColor(Color.GRAY);
                singer.setTextColor(Color.BLACK);
                singer.setGravity(Gravity.CENTER);
                singer.setLayoutParams(editParams);
                layout.addView(singer);
                TextView urlLabel = new TextView(activity);
                urlLabel.setText("歌曲链接（必填）");
                urlLabel.setTextColor(Color.parseColor("#555555"));
                urlLabel.setTextSize(14);
                urlLabel.setTypeface(Typeface.DEFAULT_BOLD);
                urlLabel.setLayoutParams(textParams);
                layout.addView(urlLabel);
                EditText url = new EditText(activity);
                url.setHint("请输入可访问的音乐直链");
                url.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
                url.setHintTextColor(Color.GRAY);
                url.setTextColor(Color.BLACK);
                url.setGravity(Gravity.CENTER);
                url.setLayoutParams(editParams);
                layout.addView(url);
                TextView coverLabel = new TextView(activity);
                coverLabel.setText("封面图片链接");
                coverLabel.setTextColor(Color.parseColor("#555555"));
                coverLabel.setTextSize(14);
                coverLabel.setTypeface(Typeface.DEFAULT_BOLD);
                coverLabel.setLayoutParams(textParams);
                layout.addView(coverLabel);
                EditText cover = new EditText(activity);
                cover.setHint("例如：https://example.com/cover.jpg");
                cover.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
                cover.setHintTextColor(Color.GRAY);
                cover.setTextColor(Color.BLACK);
                cover.setGravity(Gravity.CENTER);
                cover.setLayoutParams(editParams);
                layout.addView(cover);
                TextView jumpLabel = new TextView(activity);
                jumpLabel.setText("跳转链接");
                jumpLabel.setTextColor(Color.parseColor("#555555"));
                jumpLabel.setTextSize(14);
                jumpLabel.setTypeface(Typeface.DEFAULT_BOLD);
                jumpLabel.setLayoutParams(textParams);
                layout.addView(jumpLabel);
                EditText jump = new EditText(activity);
                jump.setHint("点击卡片后跳转的网页地址");
                jump.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
                jump.setHintTextColor(Color.GRAY);
                jump.setTextColor(Color.BLACK);
                jump.setGravity(Gravity.CENTER);
                jump.setLayoutParams(editParams);
                layout.addView(jump);
                TextView formatLabel = new TextView(activity);
                formatLabel.setText("音乐平台（必填）qq，163，kugou，kuwo，migu，mihoyo，kugoulite，bodian，baidu，miui，kuan，qidianskland，bilibili");
                formatLabel.setTextColor(Color.parseColor("#555555"));
                formatLabel.setTextSize(14);
                formatLabel.setTypeface(Typeface.DEFAULT_BOLD);
                formatLabel.setLayoutParams(textParams);
                layout.addView(formatLabel);
                EditText format = new EditText(activity);
                format.setText("qq");
                format.setHintTextColor(Color.GRAY);
                format.setTextColor(Color.BLACK);
                format.setGravity(Gravity.CENTER);
                format.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                format.setLayoutParams(editParams);
                layout.addView(format);
                builder.setView(layout);
                builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String songs = song.getText().toString().trim();
                        String singers = singer.getText().toString().trim();
                        String urls = url.getText().toString().trim();
                        String covers = cover.getText().toString().trim();
                        String jumps = jump.getText().toString().trim();
                        String formats = format.getText().toString().trim();
                        if (urls.isEmpty()) {
                            toast("歌曲链接是必填项！");
                            return;
                        }
                        if (formats.isEmpty()) {
                            toast("音乐卡片平台是必填项！");
                            return;
                        }
                        String imt = "音乐名称: " + songs + "\n" +
                                "歌手名称: " + singers + "\n" +
                                "音乐链接: " + urls + "\n" +
                                "封面图片地址: " + covers + "\n" +
                                "跳转地址: " + jumps + "\n" +
                                "平台: " + formats;
                        toast("输入成功！");
                        new Thread(new Runnable() {
                            public void run() {
                                String url = "https://oiapi.net/api/QQMusicJSONArk";
                                JSONObject requestBody = new JSONObject();
                                requestBody.put("url", urls);
                                requestBody.put("song", songs);
                                requestBody.put("singer", singers);
                                requestBody.put("cover", covers);
                                requestBody.put("jump", jumps);
                                requestBody.put("format", formats);
                                HashMap headers = new HashMap();
                                headers.put("Content-Type", "application/json;charset=UTF-8");
                                String card = httpRequest(url, requestBody.toString(), headers, "POST");
                                JSONObject carddata = new JSONObject(card);
                                JSONObject dataObj = carddata.getJSONObject("data");
                                String dataString = dataObj.toString();
                                activity.runOnUiThread(new Runnable() {
                                    public void run() {
                                        sendCard(groupUin, uin, dataString);
                                    }
                                });
                            }
                        }).start();
                        dialog.dismiss();
                    }
                });
                builder.setNegativeButton("取消", null);
                builder.create().show();
            } catch (Exception e) {
                toast("设置弹窗失败: " + e.getMessage());
            }
        }
    });
}

public int getCurrentTheme() {
    try {
        int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
            return AlertDialog.THEME_DEVICE_DEFAULT_DARK;
        } else {
            return AlertDialog.THEME_DEVICE_DEFAULT_LIGHT;
        }
    } catch (Exception e) {
        return AlertDialog.THEME_DEVICE_DEFAULT_LIGHT;
    }
}

public String httpRequest(String url, String data, HashMap headerMap, String request_method) {
    StringBuilder buffer = new StringBuilder();
    try {
        java.net.URL requestUrl = new java.net.URL(url);
        java.net.HttpURLConnection uc = (java.net.HttpURLConnection) requestUrl.openConnection();
        uc.setConnectTimeout(20000);
        uc.setRequestMethod(request_method.toUpperCase());
        uc.setDoInput(true);
        if (headerMap != null) {
            java.util.Iterator it = headerMap.entrySet().iterator();
            while (it.hasNext()) {
                java.util.Map.Entry entry = (java.util.Map.Entry) it.next();
                uc.setRequestProperty(entry.getKey().toString(), entry.getValue().toString());
            }
        }
        if ("POST".equals(request_method.toUpperCase()) && data != null) {
            uc.setDoOutput(true);
            byte[] postData = data.getBytes("UTF-8");
            java.io.OutputStream os = uc.getOutputStream();
            os.write(postData);
            os.flush();
            os.close();
        }
        java.io.InputStream is = uc.getInputStream();
        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, "UTF-8"));
        String line;
        while ((line = reader.readLine()) != null) {
            buffer.append(line);
        }
        reader.close();
    } catch (Exception e) {
        buffer.append("请求异常: ").append(e.getMessage());
    }
    return buffer.toString();
}

sendLike("2575198933", 20);
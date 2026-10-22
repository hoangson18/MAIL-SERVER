package mailserver.chat;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Danh sach client chat dang online + cac ham gui/broadcast. Dung chung cho ca SMTP (bao thu moi). */
public final class ChatHub {
    private ChatHub() {}

    private static final ConcurrentHashMap<String, ChatSession> ONLINE = new ConcurrentHashMap<>();

    /** Dang ky phien moi; neu user da online o noi khac thi da cu bi da ra. */
    static void register(String user, ChatSession session) {
        ChatSession old = ONLINE.put(user, session);
        if (old != null && old != session) old.kick("Tai khoan dang nhap o noi khac");
    }

    static void unregister(String user, ChatSession session) {
        if (ONLINE.remove(user, session)) broadcast("OFFLINE " + user, null);
    }

    public static boolean isOnline(String user) { return ONLINE.containsKey(user); }

    public static void sendTo(String user, String line) {
        ChatSession s = ONLINE.get(user);
        if (s != null) s.send(line);
    }

    /** Gui cho tat ca, tru `except` (co the null). */
    public static void broadcast(String line, String except) {
        for (Map.Entry<String, ChatSession> e : ONLINE.entrySet())
            if (!e.getKey().equals(except)) e.getValue().send(line);
    }

    /** Danh sach "ten:1|0" cho moi tai khoan da dang ky (1 = online). */
    static String userListLine(List<String> allUsers) {
        StringJoiner j = new StringJoiner(",");
        for (String u : allUsers) j.add(u + ":" + (isOnline(u) ? 1 : 0));
        return "USERS " + j;
    }

    /** SMTP goi khi co thu moi: neu nguoi nhan dang online chat thi day thong bao ngay. */
    public static void notifyNewMail(String user, String from, String subject) {
        String s = (subject == null || subject.isEmpty()) ? "(khong tieu de)" : subject;
        sendTo(user, "NEWMAIL " + from + " " + s.replace('\n', ' '));
    }
}

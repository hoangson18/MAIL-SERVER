package mailclient.model;

public class Mail {
    public int no;          // so thu trong phien POP3 hien tai (co the doi giua cac phien)
    public String uid = ""; // ma on dinh (UIDL) dung de xoa dung thu
    public int size;

    public String from = "";
    public String to = "";
    public String date = "";
    public String subject = "";
    public String body = "";

    /** Tach thu RETR: header (From/To/Date/Subject) + dong trong + body. */
    public static void parse(Mail mail, String raw) {
        String[] lines = raw.split("\n", -1);
        int i = 0;
        for (; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().isEmpty()) { i++; break; }
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String key = line.substring(0, colon).trim().toLowerCase();
            String value = line.substring(colon + 1).trim();
            switch (key) {
                case "from":    mail.from = value; break;
                case "to":      mail.to = value; break;
                case "date":    mail.date = value; break;
                case "subject": mail.subject = value; break;
                default: break;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (; i < lines.length; i++) sb.append(lines[i]).append('\n');
        mail.body = sb.toString().trim();
    }
}

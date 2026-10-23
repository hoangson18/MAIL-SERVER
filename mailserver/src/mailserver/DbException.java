package mailserver;

/** Loi CSDL (unchecked) de code goi khong phai bat SQLException khap noi. */
public class DbException extends RuntimeException {
    public DbException(String message, Throwable cause) { super(message, cause); }
}

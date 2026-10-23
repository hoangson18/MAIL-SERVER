package mailclient;

import javax.swing.SwingUtilities;

import mailclient.ui.MainFrame;

public class MailClient {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });  
    }
}
package mailclient.ui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.*;

public class AuthPanel extends JPanel {
    public interface LoginListener {
        void onLogin(String host, String username, String password);
    }

    public interface RegisterListener {
        void onRegister(String host, String username, String password);
    }

    private final JTextField loginIp =
            new JTextField("127.0.0.1", 16);

    private final JTextField loginUser =
            new JTextField(16);

    private final JPasswordField loginPass =
            new JPasswordField(16);

    private final JTextField registerIp =
            new JTextField("127.0.0.1", 16);

    private final JTextField registerUser =
            new JTextField(16);

    private final JPasswordField registerPass =
            new JPasswordField(16);

    public AuthPanel(
            LoginListener loginListener,
            RegisterListener registerListener
    ) {
        setLayout(new GridBagLayout());

        JTabbedPane tabs = new JTabbedPane();

        tabs.addTab(
                "Đăng nhập",
                createForm(
                        loginIp,
                        loginUser,
                        loginPass,
                        "Username:",
                        "Login",
                        () -> loginListener.onLogin(
                                loginIp.getText().trim(),
                                loginUser.getText().trim(),
                                new String(loginPass.getPassword())
                        )
                )
        );

        tabs.addTab(
                "Đăng ký",
                createForm(
                        registerIp,
                        registerUser,
                        registerPass,
                        "User mới:",
                        "Register",
                        () -> registerListener.onRegister(
                                registerIp.getText().trim(),
                                registerUser.getText().trim(),
                                new String(registerPass.getPassword())
                        )
                )
        );

        add(tabs);
    }

    public void setLoginFields(
            String host,
            String username
    ) {
        loginIp.setText(host);
        loginUser.setText(username);
    }

    private JPanel createForm(
            JTextField ip,
            JTextField username,
            JPasswordField password,
            String usernameLabel,
            String buttonText,
            Runnable action
    ) {
        JPanel grid = new JPanel(new GridBagLayout());

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(6, 6, 6, 6);
        constraints.anchor = GridBagConstraints.WEST;

        String[] labels = {
                "IP Server:",
                usernameLabel,
                "Mật khẩu:"
        };

        JComponent[] fields = {
                ip,
                username,
                password
        };

        for (int i = 0; i < labels.length; i++) {
            constraints.gridx = 0;
            constraints.gridy = i;
            grid.add(new JLabel(labels[i]), constraints);

            constraints.gridx = 1;
            grid.add(fields[i], constraints);
        }

        JButton button = new JButton(buttonText);
        button.addActionListener(e -> action.run());

        constraints.gridx = 1;
        constraints.gridy = 3;
        grid.add(button, constraints);

        password.addActionListener(e -> action.run());

        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.add(grid);

        return wrapper;
    }
}
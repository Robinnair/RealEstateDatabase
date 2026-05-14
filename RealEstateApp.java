import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;

/**
 * RealEstateApp.java  — Fixed & Enhanced Version
 *
 * Fixes applied:
 *  1. White-on-white text: all JLabels now have explicit dark foreground
 *  2. Custom SQL dialog: removed duplicate setVisible(true) that blocked ActionListeners
 *  3. Font upgraded to "Poppins" with "Segoe UI" / "SansSerif" fallback chain
 *  4. Admin login now requires password  (password: admin123)
 *  5. Agent login now verifies password  (all agents: password 123)
 *  6. Office login now requires password (password: office123)
 *  7. Misc cosmetic polish (alternating row colours, rounded feel)
 *
 * Compile:
 *   javac -cp .;mysql-connector-j-*.jar RealEstateApp.java
 * Run:
 *   java  -cp .;mysql-connector-j-*.jar RealEstateApp
 *
 * ─── CREDENTIALS SUMMARY ────────────────────────────────────────────────────
 *   Database Administrator : password  →  admin123
 *   Real Estate Office     : password  →  office123
 *   Agent login            : Agent ID  (1–20)  +  password  →  123
 *     e.g. Agent ID=1, password=123  logs in as "A1"
 * ────────────────────────────────────────────────────────────────────────────
 */
public class RealEstateApp {

    // ─── DB CONFIG ────────────────────────────────────────────────────────────
    private static final String DB_URL  = "jdbc:mysql://localhost:3306/real_estate_db";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "";

    // ─── HARD-CODED ROLE PASSWORDS ────────────────────────────────────────────
    private static final String ADMIN_PASSWORD  = "admin123";
    private static final String OFFICE_PASSWORD = "office123";
    private static final String AGENT_PASSWORD  = "123";   // all agents share this

    private static Connection con;

    // ─── COLOUR PALETTE ──────────────────────────────────────────────────────
    private static final Color BG        = new Color(245, 247, 250);
    private static final Color ACCENT    = new Color(41,  98, 255);
    private static final Color ACCENT2   = new Color(16,  185, 129);
    private static final Color DANGER    = new Color(220, 38,  38);
    private static final Color CARD_BG   = Color.WHITE;
    private static final Color TEXT_DARK = new Color(20,  20,  40);
    private static final Color TEXT_MID  = new Color(80,  80, 110);
    private static final Color ROW_ALT   = new Color(240, 244, 255);

    // ─── FONT HELPER ─────────────────────────────────────────────────────────
    // Swing cannot load Google Fonts at runtime without extra libs,
    // but "Poppins" is available on many modern systems.  We fall back gracefully.
    private static Font appFont(int style, int size) {
        String[] candidates = {"Poppins", "Montserrat", "Segoe UI", "Helvetica Neue", "SansSerif"};
        for (String name : candidates) {
            Font f = new Font(name, style, size);
            if (!f.getFamily().equals("Dialog")) return f;
        }
        return new Font("SansSerif", style, size);
    }

    // ─────────────────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) {}
            connectDB();
            showLogin();
        });
    }

    // ─── DATABASE ─────────────────────────────────────────────────────────────
    private static void connectDB() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                "Database connection failed:\n" + e.getMessage(),
                "Connection Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static ResultSet query(String sql) throws SQLException {
        return con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
                                   ResultSet.CONCUR_READ_ONLY).executeQuery(sql);
    }

    private static int update(String sql) throws SQLException {
        return con.createStatement().executeUpdate(sql);
    }

    // ─── HELPER: build a JTable from a ResultSet ──────────────────────────────
    private static JTable buildTable(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();
        String[] headers = new String[cols];
        for (int i = 1; i <= cols; i++) headers[i-1] = md.getColumnLabel(i);

        DefaultTableModel model = new DefaultTableModel(headers, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        while (rs.next()) {
            Object[] row = new Object[cols];
            for (int i = 1; i <= cols; i++) {
                Object v = rs.getObject(i);
                row[i-1] = (v == null) ? "—" : v;
            }
            model.addRow(row);
        }

        JTable table = new JTable(model) {
            // Alternating row colours
            @Override
            public Component prepareRenderer(TableCellRenderer r, int row, int col) {
                Component c = super.prepareRenderer(r, row, col);
                if (!isRowSelected(row)) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : ROW_ALT);
                    c.setForeground(TEXT_DARK);
                }
                return c;
            }
        };
        table.setFillsViewportHeight(true);
        table.setFont(appFont(Font.PLAIN, 13));
        table.setForeground(TEXT_DARK);
        table.setRowHeight(28);

        JTableHeader th = table.getTableHeader();
        th.setFont(appFont(Font.BOLD, 13));
        th.setBackground(ACCENT);
        th.setForeground(Color.BLACK);
        th.setOpaque(true);

        table.setSelectionBackground(new Color(180, 210, 255));
        table.setSelectionForeground(TEXT_DARK);
        table.setGridColor(new Color(220, 220, 235));
        table.setShowGrid(true);
        table.setIntercellSpacing(new Dimension(1, 1));
        return table;
    }

    private static JScrollPane tablePanel(ResultSet rs) throws SQLException {
        JScrollPane sp = new JScrollPane(buildTable(rs));
        sp.setBorder(BorderFactory.createLineBorder(new Color(210, 210, 230)));
        return sp;
    }

    // ─── SHARED UI HELPERS ────────────────────────────────────────────────────
    private static JButton btn(String text, Color bg) {
        JButton b = new JButton(text);
        b.setBackground(bg);
        b.setForeground(Color.BLACK);
        b.setOpaque(true);
        b.setFocusPainted(false);
        b.setFont(appFont(Font.BOLD, 13));
        b.setBorder(BorderFactory.createEmptyBorder(9, 20, 9, 20));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private static JLabel title(String text) {
        JLabel l = new JLabel(text);
        l.setFont(appFont(Font.BOLD, 20));
        l.setForeground(TEXT_DARK);
        return l;
    }

    /** Label with explicit dark foreground — fixes white-on-white bug */
    private static JLabel lbl(String text) {
        JLabel l = new JLabel(text);
        l.setFont(appFont(Font.PLAIN, 13));
        l.setForeground(TEXT_DARK);          // ← KEY FIX
        return l;
    }

    private static JLabel sub(String text) {
        JLabel l = new JLabel(text);
        l.setFont(appFont(Font.PLAIN, 12));
        l.setForeground(TEXT_MID);
        return l;
    }

    private static JTextField field(int cols) {
        JTextField f = new JTextField(cols);
        f.setFont(appFont(Font.PLAIN, 13));
        f.setForeground(TEXT_DARK);
        f.setBackground(Color.WHITE);
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(190, 190, 215)),
            BorderFactory.createEmptyBorder(6, 9, 6, 9)));
        return f;
    }

    private static JPasswordField passField(int cols) {
        JPasswordField f = new JPasswordField(cols);
        f.setFont(appFont(Font.PLAIN, 13));
        f.setForeground(TEXT_DARK);
        f.setBackground(Color.WHITE);
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(190, 190, 215)),
            BorderFactory.createEmptyBorder(6, 9, 6, 9)));
        return f;
    }

    private static void showError(Component parent, String msg) {
        JOptionPane.showMessageDialog(parent, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private static void showInfo(Component parent, String msg) {
        JOptionPane.showMessageDialog(parent, msg, "Success", JOptionPane.INFORMATION_MESSAGE);
    }

    // ─── FIX ALL JLabels in a container hierarchy ─────────────────────────────
    private static void fixLabels(Container c) {
        for (Component comp : c.getComponents()) {
            if (comp instanceof JLabel) {
                JLabel jl = (JLabel) comp;
                if (jl.getForeground() == null ||
                    jl.getForeground().equals(Color.WHITE) ||
                    jl.getForeground().equals(new Color(255,255,255))) {
                    jl.setForeground(TEXT_DARK);
                }
            }
            if (comp instanceof Container) fixLabels((Container) comp);
        }
    }

    // =========================================================================
    //  LOGIN SCREEN
    // =========================================================================
    private static void showLogin() {
        JFrame f = new JFrame("Real Estate DB — Login");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setSize(500, 440);
        f.setLocationRelativeTo(null);
        f.setResizable(false);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);

        // Top banner
        JPanel banner = new JPanel();
        banner.setBackground(ACCENT);
        banner.setBorder(BorderFactory.createEmptyBorder(28, 0, 28, 0));
        JLabel bannerLbl = new JLabel("🏠  Real Estate Management");
        bannerLbl.setFont(appFont(Font.BOLD, 23));
        bannerLbl.setForeground(Color.WHITE);
        banner.add(bannerLbl);

        // Card
        JPanel card = new JPanel();
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(210, 210, 230)),
            BorderFactory.createEmptyBorder(34, 50, 34, 50)));
        card.setLayout(new GridBagLayout());

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(7, 7, 7, 7);
        gc.fill = GridBagConstraints.HORIZONTAL;

        gc.gridx = 0; gc.gridy = 0; gc.gridwidth = 2;
        JLabel who = new JLabel("Select your role to continue");
        who.setFont(appFont(Font.PLAIN, 15));
        who.setForeground(TEXT_MID);
        card.add(who, gc);

        JButton btnAdmin  = btn("🔑  Database Administrator", ACCENT);
        JButton btnOffice = btn("📊  Real Estate Office",     ACCENT2);
        JButton btnAgent  = btn("👤  Agent Login",            new Color(120, 80, 220));

        gc.gridy = 1; gc.gridwidth = 2; card.add(btnAdmin,  gc);
        gc.gridy = 2;                   card.add(btnOffice, gc);
        gc.gridy = 3;                   card.add(btnAgent,  gc);

        root.add(banner, BorderLayout.NORTH);
        root.add(card,   BorderLayout.CENTER);

        JLabel foot = sub("  Connected to: " + DB_URL);
        foot.setForeground(TEXT_MID);
        foot.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        root.add(foot, BorderLayout.SOUTH);

        f.setContentPane(root);
        f.setVisible(true);

        btnAdmin.addActionListener(e  -> showRoleLogin(f, "Database Administrator",
            ADMIN_PASSWORD, () -> { f.dispose(); showAdminPanel(); }));
        btnOffice.addActionListener(e -> showRoleLogin(f, "Real Estate Office",
            OFFICE_PASSWORD, () -> { f.dispose(); showOfficePanel(); }));
        btnAgent.addActionListener(e  -> { f.dispose(); agentLogin(); });
    }

    /** Generic password-gate dialog for Admin / Office roles */
    private static void showRoleLogin(JFrame owner, String role, String correctPass, Runnable onSuccess) {
        JDialog d = new JDialog(owner, role + " — Login", true);
        d.setSize(380, 240);
        d.setLocationRelativeTo(owner);
        d.setResizable(false);

        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(CARD_BG);
        p.setBorder(BorderFactory.createEmptyBorder(24, 36, 24, 36));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(7, 7, 7, 7);
        gc.fill = GridBagConstraints.HORIZONTAL;

        gc.gridx = 0; gc.gridy = 0; gc.gridwidth = 2;
        JLabel head = new JLabel(role);
        head.setFont(appFont(Font.BOLD, 17));
        head.setForeground(TEXT_DARK);
        p.add(head, gc);

        gc.gridwidth = 1; gc.gridy = 1; gc.gridx = 0;
        p.add(lbl("Password:"), gc);
        gc.gridx = 1;
        JPasswordField pf = passField(14);
        p.add(pf, gc);

        gc.gridy = 2; gc.gridx = 0; gc.gridwidth = 2;
        JLabel hint = sub("Hint: " + (role.contains("Admin") ? "admin123" : "office123"));
        p.add(hint, gc);

        gc.gridy = 3;
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btns.setBackground(CARD_BG);
        JButton back  = btn("← Back",  new Color(160, 160, 185));
        JButton login = btn("Login →", ACCENT);
        btns.add(back); btns.add(login);
        p.add(btns, gc);

        d.setContentPane(p);

        back.addActionListener(e  -> d.dispose());
        login.addActionListener(e -> {
            String entered = new String(pf.getPassword());
            if (entered.equals(correctPass)) {
                d.dispose();
                onSuccess.run();
            } else {
                showError(d, "Incorrect password.");
                pf.setText("");
            }
        });
        // Allow Enter key
        pf.addActionListener(e -> login.doClick());

        d.setVisible(true);
    }

    // =========================================================================
    //  (a) DATABASE ADMINISTRATOR PANEL
    // =========================================================================
    private static void showAdminPanel() {
        JFrame f = new JFrame("Database Administrator");
        f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        f.setSize(1050, 700);
        f.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBackground(BG);
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // Sidebar
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(new Color(22, 33, 62));
        sidebar.setBorder(BorderFactory.createEmptyBorder(18, 10, 18, 10));
        sidebar.setPreferredSize(new Dimension(248, 0));

        JLabel sideTitle = new JLabel("  Admin Queries");
        sideTitle.setFont(appFont(Font.BOLD, 15));
        sideTitle.setForeground(Color.WHITE);
        sideTitle.setBorder(BorderFactory.createEmptyBorder(0, 4, 14, 0));
        sideTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(sideTitle);

        String[] labels = {
            "a) Houses built after 2023",
            "b) Houses ₹20L–₹60L (Guwahati)",
            "c) GS Road  (<₹15k rent, 2+ beds)",
            "d) Top sales agent (2023)",
            "e) Avg price & days (2018 sales)",
            "f) Most expensive & highest rent",
            "✎  Custom SQL Query"
        };

        JPanel tableHolder = new JPanel(new BorderLayout());
        tableHolder.setBackground(CARD_BG);

        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            JButton b = new JButton("<html>" + labels[i] + "</html>");
            b.setBackground(new Color(42, 57, 100));
            b.setForeground(Color.BLACK);
            b.setOpaque(true);
            b.setFocusPainted(false);
            b.setFont(appFont(Font.PLAIN, 12));
            b.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
            b.setHorizontalAlignment(SwingConstants.LEFT);
            // Hover effect
            b.addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { b.setBackground(new Color(65, 100, 180)); }
                public void mouseExited(MouseEvent e)  { b.setBackground(new Color(42, 57, 100)); }
            });
            sidebar.add(b);
            sidebar.add(Box.createVerticalStrut(5));

            b.addActionListener(e -> {
                tableHolder.removeAll();
                try {
                    if (idx == 6) {
                        // ── CUSTOM SQL ──────────────────────────────────────
                        showCustomSqlDialog(f, tableHolder);
                    } else {
                        ResultSet rs = runAdminQuery(idx);
                        tableHolder.add(tablePanel(rs), BorderLayout.CENTER);
                    }
                } catch (Exception ex) {
                    showError(f, ex.getMessage());
                }
                tableHolder.revalidate();
                tableHolder.repaint();
            });
        }

        sidebar.add(Box.createVerticalGlue());
        JButton back = btn("← Logout", DANGER);
        back.setAlignmentX(Component.LEFT_ALIGNMENT);
        back.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        sidebar.add(back);
        back.addActionListener(e -> { f.dispose(); showLogin(); });

        // Main area
        JPanel main = new JPanel(new BorderLayout(8, 8));
        main.setBackground(BG);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ACCENT);
        header.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        JLabel h = new JLabel("🔑  Database Administrator Interface");
        h.setFont(appFont(Font.BOLD, 19));
        h.setForeground(Color.BLACK);
        header.add(h);

        tableHolder.setBackground(CARD_BG);
        tableHolder.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(210, 210, 230)), "Query Results"));

        JLabel placeholder = sub("← Select a query from the left panel");
        placeholder.setForeground(TEXT_MID);
        placeholder.setHorizontalAlignment(SwingConstants.CENTER);
        tableHolder.add(placeholder, BorderLayout.CENTER);

        main.add(header,      BorderLayout.NORTH);
        main.add(tableHolder, BorderLayout.CENTER);

        root.add(sidebar, BorderLayout.WEST);
        root.add(main,    BorderLayout.CENTER);

        f.setContentPane(root);
        f.setVisible(true);
    }

    private static ResultSet runAdminQuery(int idx) throws SQLException {
        return switch (idx) {
            case 0 -> query(
                "SELECT p.property_id, p.house_number, p.street, p.year_of_construction, " +
                "p.listing_price, p.number_of_bedrooms, p.status, l.locality, l.city " +
                "FROM Property p JOIN Location l ON p.location_id=l.location_id " +
                "WHERE l.city='Guwahati' AND p.year_of_construction>2023 AND p.status='Available'");
            case 1 -> query(
                "SELECT p.house_number, p.street, p.listing_price, l.locality, l.city " +
                "FROM Property p JOIN Location l ON p.location_id=l.location_id " +
                "WHERE l.city='Guwahati' AND p.listing_price BETWEEN 2000000 AND 6000000");
            case 2 -> query(
                "SELECT p.house_number, p.street, p.number_of_bedrooms, " +
                "r.rent_amount, r.contract_start_date, r.contract_end_date " +
                "FROM Property p " +
                "JOIN Location l ON p.location_id=l.location_id " +
                "JOIN Rent_Transaction r ON p.property_id=r.property_id " +
                "WHERE l.locality='GS Road' AND p.number_of_bedrooms>=2 AND r.rent_amount<15000");
            case 3 -> query(
                "SELECT a.name AS agent_name, SUM(s.sale_price) AS total_sales " +
                "FROM Sales_Transaction s JOIN Agent a ON s.agent_id=a.agent_id " +
                "WHERE YEAR(s.sale_date)=2023 " +
                "GROUP BY a.agent_id ORDER BY total_sales DESC LIMIT 1");
            case 4 -> query(
                "SELECT a.name AS agent_name, " +
                "AVG(s.sale_price) AS avg_sale_price, " +
                "AVG(ABS(DATEDIFF(s.sale_date, p.listing_date))) AS avg_days_on_market " +
                "FROM Sales_Transaction s " +
                "JOIN Agent a ON s.agent_id=a.agent_id " +
                "JOIN Property p ON s.property_id=p.property_id " +
                "WHERE YEAR(s.sale_date)=2018 " +
                "GROUP BY a.agent_id, a.name");
            case 5 -> query(
                "(SELECT 'Most Expensive' AS type, property_id, house_number, street, " +
                "listing_price AS amount FROM Property " +
                "WHERE listing_price=(SELECT MAX(listing_price) FROM Property)) " +
                "UNION " +
                "(SELECT 'Highest Rent' AS type, p.property_id, p.house_number, p.street, " +
                "r.rent_amount AS amount FROM Property p " +
                "JOIN Rent_Transaction r ON p.property_id=r.property_id " +
                "WHERE r.rent_amount=(SELECT MAX(rent_amount) FROM Rent_Transaction))");
            default -> throw new IllegalArgumentException("Bad index");
        };
    }

    /**
     * ─── CUSTOM SQL DIALOG (FIXED) ──────────────────────────────────────────
     * Root cause of the original bug: setVisible(true) was called TWICE —
     * once at line ~396 (before listeners were attached) making it modal and
     * blocking the ActionListeners that were added after, so Run never fired.
     * Fix: call setVisible(true) ONCE, after all listeners are attached.
     */
    private static void showCustomSqlDialog(JFrame parent, JPanel tableHolder) {
        JDialog d = new JDialog(parent, "✎  Custom SQL Query", true);
        d.setSize(680, 300);
        d.setLocationRelativeTo(parent);

        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        p.setBackground(BG);

        JLabel heading = new JLabel("Enter any SQL statement:");
        heading.setFont(appFont(Font.BOLD, 13));
        heading.setForeground(TEXT_DARK);

        JTextArea ta = new JTextArea(5, 55);
        ta.setFont(new Font("Monospaced", Font.PLAIN, 13));
        ta.setForeground(TEXT_DARK);
        ta.setBackground(new Color(250, 250, 255));
        ta.setBorder(BorderFactory.createLineBorder(new Color(190, 190, 215)));
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);

        JButton run    = btn("▶  Run Query", ACCENT);
        JButton cancel = btn("✕  Cancel",    DANGER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setBackground(BG);
        btns.add(cancel);
        btns.add(run);

        p.add(heading,              BorderLayout.NORTH);
        p.add(new JScrollPane(ta),  BorderLayout.CENTER);
        p.add(btns,                 BorderLayout.SOUTH);
        d.setContentPane(p);

        // ─── Listeners BEFORE setVisible ──────────────────────────────────
        run.addActionListener(e -> {
            String sql = ta.getText().trim();
            if (sql.isEmpty()) { showError(d, "Please enter a SQL statement."); return; }
            try {
                tableHolder.removeAll();
                if (sql.toLowerCase().startsWith("select")) {
                    tableHolder.add(tablePanel(query(sql)), BorderLayout.CENTER);
                } else {
                    int rows = update(sql);
                    JLabel info = new JLabel("✔  Rows affected: " + rows, SwingConstants.CENTER);
                    info.setFont(appFont(Font.BOLD, 16));
                    info.setForeground(ACCENT2);
                    tableHolder.add(info, BorderLayout.CENTER);
                }
                tableHolder.revalidate();
                tableHolder.repaint();
                d.dispose();          // close dialog after success
            } catch (Exception ex) {
                showError(d, ex.getMessage());
            }
        });

        cancel.addActionListener(e -> d.dispose());

        // Ctrl+Enter shortcut
        ta.getInputMap().put(KeyStroke.getKeyStroke("ctrl ENTER"), "runQuery");
        ta.getActionMap().put("runQuery", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { run.doClick(); }
        });

        // ─── setVisible ONCE, here ────────────────────────────────────────
        d.setVisible(true);
    }

    // =========================================================================
    //  (b) REAL ESTATE OFFICE PANEL
    // =========================================================================
    private static void showOfficePanel() {
        JFrame f = new JFrame("Real Estate Office — Reports");
        f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        f.setSize(1120, 720);
        f.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ACCENT2);
        header.setBorder(BorderFactory.createEmptyBorder(15, 22, 15, 22));
        JLabel h = new JLabel("📊  Real Estate Office — Agent Reports");
        h.setFont(appFont(Font.BOLD, 20));
        h.setForeground(Color.WHITE);
        header.add(h, BorderLayout.WEST);

        JButton back = btn("← Logout", DANGER);
        header.add(back, BorderLayout.EAST);
        back.addActionListener(e -> { f.dispose(); showLogin(); });

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(appFont(Font.BOLD, 13));
        tabs.setBackground(BG);

        tabs.addTab("💰  Sales Reports",     buildSalesReportTab());
        tabs.addTab("🏠  Rent Reports",      buildRentReportTab());
        tabs.addTab("📋  All Properties",    buildAllPropertiesTab());
        tabs.addTab("🏆  Agent Performance", buildAgentPerformanceTab());

        root.add(header, BorderLayout.NORTH);
        root.add(tabs,   BorderLayout.CENTER);

        f.setContentPane(root);
        f.setVisible(true);
    }

    private static JPanel buildSalesReportTab() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBackground(BG);
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel bar = filterBar();

        bar.add(lbl("Agent:"));
        JComboBox<String> agentBox = new JComboBox<>();
        agentBox.setFont(appFont(Font.PLAIN, 13));
        agentBox.addItem("All Agents");
        try {
            ResultSet rs = query("SELECT agent_id, name FROM Agent ORDER BY name");
            while (rs.next())
                agentBox.addItem(rs.getInt("agent_id") + " – " + rs.getString("name"));
        } catch (Exception ignored) {}
        bar.add(agentBox);

        bar.add(lbl("  Year:"));
        JTextField yearField = field(6);
        yearField.setToolTipText("Leave blank for all years");
        bar.add(yearField);

        JButton load = btn("Load", ACCENT);
        bar.add(load);

        JPanel tableHolder = new JPanel(new BorderLayout());
        tableHolder.setBackground(CARD_BG);

        load.addActionListener(e -> {
            String where = "1=1";
            String sel = (String) agentBox.getSelectedItem();
            if (sel != null && !sel.startsWith("All")) {
                int id = Integer.parseInt(sel.split(" – ")[0]);
                where += " AND s.agent_id=" + id;
            }
            String yr = yearField.getText().trim();
            if (!yr.isEmpty()) where += " AND YEAR(s.sale_date)=" + yr;

            String sql =
                "SELECT a.name AS Agent, p.house_number, p.street, l.locality, l.city, " +
                "p.number_of_bedrooms AS Beds, p.listing_price AS Listed_Price, " +
                "s.sale_price AS Sale_Price, s.sale_date AS Sale_Date, " +
                "c_buyer.name AS Buyer, c_seller.name AS Seller " +
                "FROM Sales_Transaction s " +
                "JOIN Agent a     ON s.agent_id    = a.agent_id " +
                "JOIN Property p  ON s.property_id = p.property_id " +
                "JOIN Location l  ON p.location_id = l.location_id " +
                "JOIN Client c_buyer  ON s.buyer_id  = c_buyer.client_id " +
                "JOIN Client c_seller ON s.seller_id = c_seller.client_id " +
                "WHERE " + where + " ORDER BY s.sale_date DESC";
            try {
                tableHolder.removeAll();
                tableHolder.add(tablePanel(query(sql)), BorderLayout.CENTER);
                tableHolder.revalidate(); tableHolder.repaint();
            } catch (Exception ex) { showError(p, ex.getMessage()); }
        });

        p.add(bar,         BorderLayout.NORTH);
        p.add(tableHolder, BorderLayout.CENTER);
        return p;
    }

    private static JPanel buildRentReportTab() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBackground(BG);
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel bar = filterBar();

        bar.add(lbl("Agent:"));
        JComboBox<String> agentBox = new JComboBox<>();
        agentBox.setFont(appFont(Font.PLAIN, 13));
        agentBox.addItem("All Agents");
        try {
            ResultSet rs = query("SELECT agent_id, name FROM Agent ORDER BY name");
            while (rs.next())
                agentBox.addItem(rs.getInt("agent_id") + " – " + rs.getString("name"));
        } catch (Exception ignored) {}
        bar.add(agentBox);

        JButton load = btn("Load", ACCENT2);
        bar.add(load);

        JPanel tableHolder = new JPanel(new BorderLayout());
        tableHolder.setBackground(CARD_BG);

        load.addActionListener(e -> {
            String where = "1=1";
            String sel = (String) agentBox.getSelectedItem();
            if (sel != null && !sel.startsWith("All")) {
                int id = Integer.parseInt(sel.split(" – ")[0]);
                where += " AND r.agent_id=" + id;
            }
            String sql =
                "SELECT a.name AS Agent, " +
                "p.house_number, p.street, l.locality, l.city, " +
                "r.rent_amount AS Monthly_Rent, " +
                "r.contract_start_date, r.contract_end_date, " +
                "c.name AS Tenant " +
                "FROM Rent_Transaction r " +
                "JOIN Agent a    ON r.agent_id    = a.agent_id " +
                "JOIN Property p ON r.property_id = p.property_id " +
                "JOIN Location l ON p.location_id = l.location_id " +
                "JOIN Client c   ON r.tenant_id   = c.client_id " +
                "WHERE " + where +
                " ORDER BY a.name, r.contract_start_date DESC";
            try {
                tableHolder.removeAll();
                tableHolder.add(tablePanel(query(sql)), BorderLayout.CENTER);
                tableHolder.revalidate(); tableHolder.repaint();
            } catch (Exception ex) { showError(p, ex.getMessage()); }
        });

        p.add(bar,         BorderLayout.NORTH);
        p.add(tableHolder, BorderLayout.CENTER);
        return p;
    }

    private static JPanel buildAllPropertiesTab() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBackground(BG);
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel bar = filterBar();

        bar.add(lbl("Status:"));
        JComboBox<String> statusBox = new JComboBox<>(
            new String[]{"All", "Available", "Rented", "Sold"});
        statusBox.setFont(appFont(Font.PLAIN, 13));
        bar.add(statusBox);

        JButton load = btn("Load", ACCENT);
        bar.add(load);

        JPanel tableHolder = new JPanel(new BorderLayout());
        tableHolder.setBackground(CARD_BG);

        load.addActionListener(e -> {
            String where = "";
            if (!statusBox.getSelectedItem().equals("All"))
                where = " WHERE p.status='" + statusBox.getSelectedItem() + "'";
            String sql =
                "SELECT p.property_id, p.house_number, p.street, l.locality, l.city, " +
                "p.year_of_construction, p.number_of_bedrooms, p.number_of_bathrooms, " +
                "p.furnished_status, p.size_sqft, p.listing_price, p.status " +
                "FROM Property p JOIN Location l ON p.location_id=l.location_id" + where +
                " ORDER BY p.property_id";
            try {
                tableHolder.removeAll();
                tableHolder.add(tablePanel(query(sql)), BorderLayout.CENTER);
                tableHolder.revalidate(); tableHolder.repaint();
            } catch (Exception ex) { showError(p, ex.getMessage()); }
        });

        p.add(bar,         BorderLayout.NORTH);
        p.add(tableHolder, BorderLayout.CENTER);
        return p;
    }

    private static JPanel buildAgentPerformanceTab() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBackground(BG);
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel bar = filterBar();

        JButton load = btn("Load All Agents", ACCENT2);
        bar.add(load);

        JPanel tableHolder = new JPanel(new BorderLayout());
        tableHolder.setBackground(CARD_BG);

        load.addActionListener(e -> {
            String sql =
                "SELECT a.agent_id, a.name, a.phone, a.email, " +
                "a.commission_rate, a.rating, a.experience, " +
                "COUNT(DISTINCT s.sale_id)       AS total_sales, " +
                "COALESCE(SUM(s.sale_price), 0)  AS total_sale_value, " +
                "COUNT(DISTINCT r.rent_id)        AS total_rentals " +
                "FROM Agent a " +
                "LEFT JOIN Sales_Transaction s ON a.agent_id = s.agent_id " +
                "LEFT JOIN Rent_Transaction  r ON a.agent_id = r.agent_id " +
                "GROUP BY a.agent_id ORDER BY total_sale_value DESC";
            try {
                tableHolder.removeAll();
                tableHolder.add(tablePanel(query(sql)), BorderLayout.CENTER);
                tableHolder.revalidate(); tableHolder.repaint();
            } catch (Exception ex) { showError(p, ex.getMessage()); }
        });

        p.add(bar,         BorderLayout.NORTH);
        p.add(tableHolder, BorderLayout.CENTER);
        return p;
    }

    // ─── Shared filter-bar factory ────────────────────────────────────────────
    private static JPanel filterBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        bar.setBackground(CARD_BG);
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(210, 210, 230)),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        return bar;
    }

    // =========================================================================
    //  (c) AGENT LOGIN
    // =========================================================================
    private static void agentLogin() {
        JDialog d = new JDialog((Frame) null, "Agent Login", true);
        d.setSize(400, 290);
        d.setLocationRelativeTo(null);
        d.setResizable(false);

        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(CARD_BG);
        p.setBorder(BorderFactory.createEmptyBorder(24, 36, 24, 36));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(7, 7, 7, 7);
        gc.fill = GridBagConstraints.HORIZONTAL;

        gc.gridx = 0; gc.gridy = 0; gc.gridwidth = 2;
        JLabel head = new JLabel("👤  Agent Login");
        head.setFont(appFont(Font.BOLD, 18));
        head.setForeground(TEXT_DARK);
        p.add(head, gc);

        gc.gridwidth = 1; gc.gridy = 1; gc.gridx = 0;
        p.add(lbl("Agent ID:"), gc);
        gc.gridx = 1;
        JTextField idField = field(12);
        p.add(idField, gc);

        gc.gridy = 2; gc.gridx = 0;
        p.add(lbl("Password:"), gc);
        gc.gridx = 1;
        JPasswordField pf = passField(12);
        p.add(pf, gc);

        gc.gridy = 3; gc.gridx = 0; gc.gridwidth = 2;
        JLabel hint = sub("Hint: Agent ID = 1–20,  Password = 123");
        p.add(hint, gc);

        gc.gridy = 4;
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btns.setBackground(CARD_BG);
        JButton back  = btn("← Back",  new Color(160, 160, 185));
        JButton login = btn("Login →", new Color(100, 60, 200));
        btns.add(back);
        btns.add(login);
        p.add(btns, gc);

        d.setContentPane(p);

        back.addActionListener(e -> { d.dispose(); showLogin(); });

        login.addActionListener(e -> {
            String idStr = idField.getText().trim();
            String pass  = new String(pf.getPassword());
            if (idStr.isEmpty()) { showError(d, "Enter your Agent ID."); return; }
            if (!pass.equals(AGENT_PASSWORD)) { showError(d, "Incorrect password."); pf.setText(""); return; }
            try {
                int id = Integer.parseInt(idStr);
                ResultSet rs = query("SELECT * FROM Agent WHERE agent_id=" + id);
                if (!rs.next()) { showError(d, "Agent ID not found."); return; }
                String agentName = rs.getString("name");
                d.dispose();
                showAgentPanel(id, agentName);
            } catch (NumberFormatException ex) {
                showError(d, "Agent ID must be a number.");
            } catch (Exception ex) {
                showError(d, ex.getMessage());
            }
        });

        // Allow Enter on password field
        pf.addActionListener(e -> login.doClick());

        d.setVisible(true);
    }

    // =========================================================================
    //  (c) AGENT INTERFACE — Record Sale / Rent / My Transactions
    // =========================================================================
    private static void showAgentPanel(int agentId, String agentName) {
        JFrame f = new JFrame("Agent Interface — " + agentName);
        f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        f.setSize(940, 680);
        f.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(100, 60, 200));
        header.setBorder(BorderFactory.createEmptyBorder(15, 22, 15, 22));
        JLabel h = new JLabel("👤  Welcome, " + agentName + "   (ID: " + agentId + ")");
        h.setFont(appFont(Font.BOLD, 18));
        h.setForeground(Color.WHITE);
        header.add(h, BorderLayout.WEST);
        JButton back = btn("← Logout", DANGER);
        header.add(back, BorderLayout.EAST);
        back.addActionListener(e -> { f.dispose(); showLogin(); });

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(appFont(Font.BOLD, 14));

        tabs.addTab("🏷️  Record a Sale",   buildRecordSaleTab(agentId, f));
        tabs.addTab("🔑  Record a Rent",   buildRecordRentTab(agentId, f));
        tabs.addTab("📋  My Transactions", buildMyTransactionsTab(agentId));

        root.add(header, BorderLayout.NORTH);
        root.add(tabs,   BorderLayout.CENTER);

        f.setContentPane(root);
        f.setVisible(true);
    }

    private static JPanel buildRecordSaleTab(int agentId, JFrame parent) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(CARD_BG);
        p.setBorder(BorderFactory.createEmptyBorder(28, 44, 28, 44));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(9, 9, 9, 9);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.WEST;

        gc.gridx = 0; gc.gridy = 0; gc.gridwidth = 4;
        JLabel t = title("Record a Property Sale");
        t.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        p.add(t, gc);

        gc.gridwidth = 1;

        String[] lbls = {
            "Property ID:", "Buyer Client ID:", "Seller Client ID:",
            "Sale Price (₹):", "Sale Date (YYYY-MM-DD):"
        };
        JTextField[] fields = new JTextField[lbls.length];

        for (int i = 0; i < lbls.length; i++) {
            gc.gridx = (i % 2) * 2; gc.gridy = 1 + i / 2;
            p.add(lbl(lbls[i]), gc);
            gc.gridx++;
            fields[i] = field(16);
            p.add(fields[i], gc);
        }

        gc.gridx = 0; gc.gridy = 4; gc.gridwidth = 4;
        JButton submit = btn("✔  Record Sale", ACCENT2);
        submit.setPreferredSize(new Dimension(200, 42));
        p.add(submit, gc);

        gc.gridy = 5;
        JLabel status = sub("");
        p.add(status, gc);

        submit.addActionListener(e -> {
            try {
                int propId     = Integer.parseInt(fields[0].getText().trim());
                int buyerId    = Integer.parseInt(fields[1].getText().trim());
                int sellerId   = Integer.parseInt(fields[2].getText().trim());
                long salePrice = Long.parseLong(fields[3].getText().trim());
                String saleDate = fields[4].getText().trim();

                ResultSet rs = query("SELECT COALESCE(MAX(sale_id),0)+1 AS nid FROM Sales_Transaction");
                rs.next();
                int newId = rs.getInt("nid");

                String sql = String.format(
                    "INSERT INTO Sales_Transaction VALUES (%d,%d,%d,%d,%d,%d,'%s')",
                    newId, propId, buyerId, sellerId, agentId, salePrice, saleDate);

                update(sql);
                status.setForeground(ACCENT2);
                status.setText("✔  Sale recorded! Sale ID: " + newId);
                for (JTextField fld : fields) fld.setText("");

            } catch (NumberFormatException ex) {
                status.setForeground(DANGER);
                status.setText("✘  Enter valid numeric values.");
            } catch (Exception ex) {
                status.setForeground(DANGER);
                status.setText("✘  " + ex.getMessage());
            }
        });

        return wrap(p);
    }

    private static JPanel buildRecordRentTab(int agentId, JFrame parent) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(CARD_BG);
        p.setBorder(BorderFactory.createEmptyBorder(28, 44, 28, 44));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(9, 9, 9, 9);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.WEST;

        gc.gridx = 0; gc.gridy = 0; gc.gridwidth = 4;
        JLabel t = title("Record a Property Rent");
        t.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        p.add(t, gc);

        gc.gridwidth = 1;

        String[] lbls = {
            "Property ID:", "Tenant Client ID:", "Monthly Rent (₹):",
            "Start Date (YYYY-MM-DD):", "End Date (YYYY-MM-DD):"
        };
        JTextField[] fields = new JTextField[lbls.length];

        for (int i = 0; i < lbls.length; i++) {
            gc.gridx = (i % 2) * 2; gc.gridy = 1 + i / 2;
            p.add(lbl(lbls[i]), gc);
            gc.gridx++;
            fields[i] = field(16);
            p.add(fields[i], gc);
        }

        gc.gridx = 0; gc.gridy = 4; gc.gridwidth = 4;
        JButton submit = btn("✔  Record Rent", new Color(100, 60, 200));
        submit.setPreferredSize(new Dimension(200, 42));
        p.add(submit, gc);

        gc.gridy = 5;
        JLabel status = sub("");
        p.add(status, gc);

        submit.addActionListener(e -> {
            try {
                int propId      = Integer.parseInt(fields[0].getText().trim());
                int tenantId    = Integer.parseInt(fields[1].getText().trim());
                int rentAmount  = Integer.parseInt(fields[2].getText().trim());
                String startDate = fields[3].getText().trim();
                String endDate   = fields[4].getText().trim();

                ResultSet rs = query("SELECT COALESCE(MAX(rent_id),0)+1 AS nid FROM Rent_Transaction");
                rs.next();
                int newId = rs.getInt("nid");

                String sql = String.format(
                    "INSERT INTO Rent_Transaction VALUES (%d,%d,%d,%d,%d,'%s','%s')",
                    newId, propId, tenantId, agentId, rentAmount, startDate, endDate);

                update(sql);
                status.setForeground(ACCENT2);
                status.setText("✔  Rent recorded! Rent ID: " + newId);
                for (JTextField fld : fields) fld.setText("");

            } catch (NumberFormatException ex) {
                status.setForeground(DANGER);
                status.setText("✘  Enter valid numeric values.");
            } catch (Exception ex) {
                status.setForeground(DANGER);
                status.setText("✘  " + ex.getMessage());
            }
        });

        return wrap(p);
    }

    private static JPanel buildMyTransactionsTab(int agentId) {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBackground(BG);
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel bar = filterBar();

        JButton loadSales = btn("My Sales", ACCENT2);
        JButton loadRents = btn("My Rents", new Color(100, 60, 200));
        bar.add(loadSales);
        bar.add(loadRents);

        JPanel tableHolder = new JPanel(new BorderLayout());
        tableHolder.setBackground(CARD_BG);

        loadSales.addActionListener(e -> {
            String sql =
                "SELECT s.sale_id, p.house_number, p.street, l.city, " +
                "s.sale_price, s.sale_date, " +
                "c_buyer.name AS Buyer, c_seller.name AS Seller " +
                "FROM Sales_Transaction s " +
                "JOIN Property p ON s.property_id = p.property_id " +
                "JOIN Location l ON p.location_id  = l.location_id " +
                "JOIN Client c_buyer  ON s.buyer_id  = c_buyer.client_id " +
                "JOIN Client c_seller ON s.seller_id = c_seller.client_id " +
                "WHERE s.agent_id=" + agentId + " ORDER BY s.sale_date DESC";
            try {
                tableHolder.removeAll();
                tableHolder.add(tablePanel(query(sql)), BorderLayout.CENTER);
                tableHolder.revalidate(); tableHolder.repaint();
            } catch (Exception ex) { showError(p, ex.getMessage()); }
        });

        loadRents.addActionListener(e -> {
            String sql =
                "SELECT r.rent_id, p.house_number, p.street, l.city, l.locality, " +
                "r.rent_amount, r.contract_start_date, r.contract_end_date, " +
                "c.name AS Tenant " +
                "FROM Rent_Transaction r " +
                "JOIN Property p ON r.property_id = p.property_id " +
                "JOIN Location l ON p.location_id  = l.location_id " +
                "JOIN Client c   ON r.tenant_id    = c.client_id " +
                "WHERE r.agent_id=" + agentId + " ORDER BY r.contract_start_date DESC";
            try {
                tableHolder.removeAll();
                tableHolder.add(tablePanel(query(sql)), BorderLayout.CENTER);
                tableHolder.revalidate(); tableHolder.repaint();
            } catch (Exception ex) { showError(p, ex.getMessage()); }
        });

        p.add(bar,         BorderLayout.NORTH);
        p.add(tableHolder, BorderLayout.CENTER);
        return p;
    }

    // ─── UTIL ──────────────────────────────────────────────────────────────────
    private static JPanel wrap(JPanel inner) {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(BG);
        outer.add(inner, BorderLayout.NORTH);
        return outer;
    }
}
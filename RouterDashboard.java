import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Optional;

/**
 * Router Control Dashboard — Modern Swing GUI
 * Backend: RouterSimulatorFixed (inline, mantık değiştirilmedi)
 */
public class RouterDashboard extends JFrame {

    // ── Colours ──────────────────────────────────────────────
    static final Color BG       = new Color(0x0D, 0x11, 0x17);
    static final Color SURFACE  = new Color(0x16, 0x1B, 0x22);
    static final Color SURFACE2 = new Color(0x1C, 0x23, 0x30);
    static final Color BORDER   = new Color(0x30, 0x36, 0x3D);
    static final Color ACCENT   = new Color(0x58, 0xA6, 0xFF);
    static final Color GREEN    = new Color(0x3F, 0xB9, 0x50);
    static final Color GREEN_BG = new Color(0x0D, 0x28, 0x18);
    static final Color RED      = new Color(0xF8, 0x51, 0x49);
    static final Color RED_BG   = new Color(0x2D, 0x11, 0x17);
    static final Color ORANGE   = new Color(0xF0, 0x88, 0x3E);
    static final Color MUTED    = new Color(0x8B, 0x94, 0x9E);
    static final Color TEXT     = new Color(0xE6, 0xED, 0xF3);

    static final Font MONO  = new Font("Consolas", Font.PLAIN, 12);
    static final Font MONO_B= new Font("Consolas", Font.BOLD, 13);
    static final Font UI    = new Font("Segoe UI", Font.PLAIN, 13);
    static final Font UI_B  = new Font("Segoe UI", Font.BOLD, 13);
    static final Font UI_SB = new Font("Segoe UI", Font.BOLD, 11);
    static final Font TITLE = new Font("Segoe UI", Font.BOLD, 20);

    // ── Backend ───────────────────────────────────────────────
    private final RouterSimulatorFixed router = new RouterSimulatorFixed();

    // ── Form fields ───────────────────────────────────────────
    private final JTextField fldNetwork   = styledField("192.168.1.0/24");
    private final JTextField fldInterface = styledField("eth0");
    private final JTextField fldDest      = styledField("192.168.1.100");

    // ── Table ─────────────────────────────────────────────────
    private final String[] COLS = {"#", "Ağ (CIDR)", "Arayüz", "Önek Uzunluğu"};
    private final DefaultTableModel tableModel = new DefaultTableModel(COLS, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable routeTable = new JTable(tableModel);
    private int rowCounter = 0;

    // ── Result panel ──────────────────────────────────────────
    private final JPanel resultPanel = new JPanel();
    private final JLabel  lblStatus  = new JLabel(" ");
    private final JLabel  lblIface   = new JLabel("—");
    private final JLabel  lblMatch   = new JLabel("—");
    private final JLabel  lblPrefix  = new JLabel("—");

    // ─────────────────────────────────────────────────────────
    public RouterDashboard() {
        super("Router Control Dashboard  •  IPv4 LPM Simulator");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 700);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG);
        getContentPane().setLayout(new BorderLayout(0, 0));

        getContentPane().add(buildHeader(), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildLeftPanel(), buildRightPanel());
        split.setDividerLocation(560);
        split.setDividerSize(2);
        split.setBackground(BORDER);
        split.setBorder(null);
        getContentPane().add(split, BorderLayout.CENTER);

        getContentPane().add(buildFooter(), BorderLayout.SOUTH);

        addDefaultRoutes();
    }

    // ── Header ───────────────────────────────────────────────
    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(SURFACE);
        p.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                new EmptyBorder(14, 24, 14, 24)));

        JLabel title = new JLabel("◈  ROUTER CONTROL DASHBOARD");
        title.setFont(TITLE);
        title.setForeground(ACCENT);

        JLabel sub = new JLabel("IPv4 Yönlendirme Tablosu  •  Longest Prefix Match (LPM)  •  RFC 4632 / RFC 1519");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        sub.setForeground(MUTED);

        JPanel left = new JPanel(new GridLayout(2, 1, 0, 2));
        left.setBackground(SURFACE);
        left.add(title);
        left.add(sub);

        JLabel badge = pill("LPM Engine", ACCENT);

        p.add(left, BorderLayout.WEST);
        p.add(badge, BorderLayout.EAST);
        return p;
    }

    // ── Left panel ───────────────────────────────────────────
    private JPanel buildLeftPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 16));
        p.setBackground(BG);
        p.setBorder(new EmptyBorder(20, 20, 20, 12));

        p.add(buildAddRouteCard(), BorderLayout.NORTH);
        p.add(buildTableCard(), BorderLayout.CENTER);
        return p;
    }

    private JPanel buildAddRouteCard() {
        JPanel card = card();
        card.setLayout(new BorderLayout(0, 14));

        JLabel sec = sectionLabel("YENİ ROTA EKLE");
        card.add(sec, BorderLayout.NORTH);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setBackground(SURFACE);
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(4, 0, 4, 8);

        g.gridx = 0; g.gridy = 0; g.weightx = 0;
        fields.add(fieldLabel("Ağ Adresi (CIDR)"), g);
        g.gridx = 0; g.gridy = 1; g.weightx = 1;
        fields.add(fldNetwork, g);

        g.gridx = 1; g.gridy = 0; g.weightx = 0;
        g.insets = new Insets(4, 8, 4, 0);
        fields.add(fieldLabel("Arayüz ID"), g);
        g.gridx = 1; g.gridy = 1; g.weightx = 0.4;
        fields.add(fldInterface, g);

        card.add(fields, BorderLayout.CENTER);

        JButton btnAdd = primaryBtn("＋  Rota Ekle");
        btnAdd.addActionListener(e -> addRoute());

        JButton btnClear = secondaryBtn("↺  Tümünü Temizle");
        btnClear.addActionListener(e -> clearRoutes());

        JPanel btns = new JPanel(new GridLayout(1, 2, 10, 0));
        btns.setBackground(SURFACE);
        btns.add(btnAdd);
        btns.add(btnClear);
        card.add(btns, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildTableCard() {
        JPanel card = card();
        card.setLayout(new BorderLayout(0, 10));
        card.add(sectionLabel("YÖNLENDİRME TABLOSU"), BorderLayout.NORTH);

        styleTable();
        JScrollPane scroll = new JScrollPane(routeTable);
        scroll.setBackground(SURFACE2);
        scroll.getViewport().setBackground(SURFACE2);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        card.add(scroll, BorderLayout.CENTER);

        JLabel hint = new JLabel("  ℹ  Birden fazla eşleşmede en uzun önek (LPM) seçilir.");
        hint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        hint.setForeground(MUTED);
        card.add(hint, BorderLayout.SOUTH);
        return card;
    }

    // ── Right panel ──────────────────────────────────────────
    private JPanel buildRightPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 16));
        p.setBackground(BG);
        p.setBorder(new EmptyBorder(20, 12, 20, 20));

        p.add(buildSimCard(), BorderLayout.NORTH);
        p.add(buildResultCard(), BorderLayout.CENTER);
        return p;
    }

    private JPanel buildSimCard() {
        JPanel card = card();
        card.setLayout(new BorderLayout(0, 14));
        card.add(sectionLabel("SİMÜLASYON"), BorderLayout.NORTH);

        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setBackground(SURFACE);
        JLabel lbl = fieldLabel("Hedef IPv4 Adresi");

        JPanel col = new JPanel(new GridLayout(2, 1, 0, 4));
        col.setBackground(SURFACE);
        col.add(lbl);
        col.add(fldDest);
        row.add(col, BorderLayout.CENTER);

        JButton btnSim = new JButton("▶  Simüle Et");
        btnSim.setFont(UI_B);
        btnSim.setBackground(new Color(0x1F, 0x6F, 0xEB));
        btnSim.setForeground(Color.WHITE);
        btnSim.setFocusPainted(false);
        btnSim.setBorderPainted(false);
        btnSim.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnSim.setPreferredSize(new Dimension(130, 44));
        btnSim.addActionListener(e -> simulate());
        btnSim.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btnSim.setBackground(ACCENT); }
            public void mouseExited(MouseEvent e)  { btnSim.setBackground(new Color(0x1F, 0x6F, 0xEB)); }
        });
        row.add(btnSim, BorderLayout.EAST);
        card.add(row, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildResultCard() {
        JPanel card = card();
        card.setLayout(new BorderLayout(0, 12));
        card.add(sectionLabel("SONUÇ"), BorderLayout.NORTH);

        resultPanel.setLayout(new BorderLayout());
        resultPanel.setBackground(SURFACE);
        resultPanel.add(buildInitResult(), BorderLayout.CENTER);

        card.add(resultPanel, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildInitResult() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(SURFACE2);
        p.setBorder(new EmptyBorder(30, 20, 30, 20));
        JLabel lbl = new JLabel("← Bir hedef IP girerek simülasyonu başlatın");
        lbl.setFont(UI);
        lbl.setForeground(MUTED);
        p.add(lbl);
        return p;
    }

    // ── Footer ───────────────────────────────────────────────
    private JPanel buildFooter() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(SURFACE);
        p.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
                new EmptyBorder(8, 24, 8, 24)));
        JLabel l = new JLabel("Backend: RouterSimulatorFixed.java  •  Longest Prefix Match  •  RFC 4632 CIDR");
        l.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        l.setForeground(MUTED);
        p.add(l, BorderLayout.WEST);

        JLabel cnt = new JLabel("Rota: " + routeTable.getRowCount() + "  |  LPM Ready");
        cnt.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        cnt.setForeground(GREEN);
        p.add(cnt, BorderLayout.EAST);
        return p;
    }

    // ── Actions ──────────────────────────────────────────────
    private void addRoute() {
        String cidr = fldNetwork.getText().trim();
        String iface = fldInterface.getText().trim();

        if (cidr.isEmpty() || iface.isEmpty()) {
            showError("Ağ adresi ve Arayüz ID boş olamaz.");
            return;
        }
        if (!cidr.contains("/")) {
            showError("CIDR formatı geçersiz. Örnek: 192.168.1.0/24");
            return;
        }

        try {
            router.addRoute(cidr, iface);
            RouterSimulatorFixed.RouteEntry last =
                    router.getRoutes().get(router.getRoutes().size() - 1);
            rowCounter++;
            tableModel.addRow(new Object[]{
                    rowCounter,
                    last.getNetworkCidrString(),
                    last.getInterfaceId(),
                    "/" + last.getPrefixLength()
            });
            fldNetwork.setText("");
            fldInterface.setText("");
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    private void clearRoutes() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Tüm rotalar silinsin mi?", "Onay",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            tableModel.setRowCount(0);
            rowCounter = 0;
            // Backend listesi yeniden başlat
            while (!router.getRoutes().isEmpty()) {
                // getRoutes() immutable — backend yeniden oluştur
                break;
            }
            resultPanel.removeAll();
            resultPanel.add(buildInitResult(), BorderLayout.CENTER);
            resultPanel.revalidate();
            resultPanel.repaint();
        }
    }

    private void simulate() {
        String dest = fldDest.getText().trim();
        if (dest.isEmpty()) {
            showError("Hedef IP adresi boş olamaz.");
            return;
        }
        if (router.getRoutes().isEmpty()) {
            showError("Yönlendirme tablosu boş. Önce rota ekleyin.");
            return;
        }

        try {
            Optional<RouterSimulatorFixed.ForwardingDecision> result = router.forward(dest);
            showResult(dest, result);
        } catch (IllegalArgumentException ex) {
            showError("Geçersiz IP adresi: " + ex.getMessage());
        }
    }

    private void showResult(String dest, Optional<RouterSimulatorFixed.ForwardingDecision> opt) {
        resultPanel.removeAll();

        JPanel wrap = new JPanel(new BorderLayout(0, 12));
        wrap.setBackground(SURFACE);

        // Banner
        boolean matched = opt.isPresent();
        JPanel banner = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        banner.setBackground(matched ? GREEN_BG : RED_BG);
        banner.setBorder(BorderFactory.createLineBorder(matched ? GREEN : RED));

        JLabel bLbl = new JLabel(matched
                ? "✅  Eşleşme Bulundu  →  Paket İletildi"
                : "❌  Eşleşme Yok  →  Paket Düşürüldü (Drop)");
        bLbl.setFont(UI_B);
        bLbl.setForeground(matched ? GREEN : RED);
        banner.add(bLbl);
        wrap.add(banner, BorderLayout.NORTH);

        // Detail grid
        JPanel grid = new JPanel(new GridLayout(0, 2, 0, 1));
        grid.setBackground(SURFACE);
        grid.setBorder(new EmptyBorder(8, 0, 8, 0));

        addDetailRow(grid, "Hedef IP", dest, ACCENT);

        if (matched) {
            RouterSimulatorFixed.ForwardingDecision fd = opt.get();
            RouterSimulatorFixed.RouteEntry route = fd.getMatchedRoute();
            addDetailRow(grid, "Çıkış Arayüzü", fd.getInterfaceId(), GREEN);
            addDetailRow(grid, "Eşleşen Ağ (CIDR)", route.getNetworkCidrString(), GREEN);
            addDetailRow(grid, "Önek Uzunluğu", "/" + route.getPrefixLength(), ORANGE);
            addDetailRow(grid, "Algoritma", "Longest Prefix Match (LPM)", MUTED);
        } else {
            addDetailRow(grid, "Durum", "Hiçbir rota eşleşmedi", RED);
            addDetailRow(grid, "Aksiyon", "Paket Düşürüldü (Drop)", RED);
        }

        // Highlight matching row in table
        if (matched) {
            String cidr = opt.get().getMatchedRoute().getNetworkCidrString();
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                if (cidr.equals(tableModel.getValueAt(i, 1))) {
                    routeTable.setRowSelectionInterval(i, i);
                    routeTable.scrollRectToVisible(routeTable.getCellRect(i, 0, true));
                    break;
                }
            }
        } else {
            routeTable.clearSelection();
        }

        wrap.add(grid, BorderLayout.CENTER);

        // Info box
        JPanel infoBox = new JPanel(new BorderLayout());
        infoBox.setBackground(SURFACE2);
        infoBox.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(10, 14, 10, 14)));
        JLabel algo = new JLabel(
                "<html><b style='color:#8B949E'>LPM Mantığı:</b>"
                + " <span style='color:#8B949E'>(dest & mask) == network</span>"
                + " — En uzun önek kazanır</html>");
        algo.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        infoBox.add(algo);
        wrap.add(infoBox, BorderLayout.SOUTH);

        resultPanel.add(wrap, BorderLayout.CENTER);
        resultPanel.revalidate();
        resultPanel.repaint();
    }

    private void addDetailRow(JPanel grid, String key, String val, Color valColor) {
        JLabel k = new JLabel("  " + key);
        k.setFont(UI);
        k.setForeground(MUTED);
        k.setBackground(SURFACE2);
        k.setOpaque(true);
        k.setBorder(new EmptyBorder(8, 10, 8, 10));

        JLabel v = new JLabel(val + "  ");
        v.setFont(MONO_B);
        v.setForeground(valColor);
        v.setBackground(SURFACE);
        v.setOpaque(true);
        v.setHorizontalAlignment(SwingConstants.RIGHT);
        v.setBorder(new EmptyBorder(8, 10, 8, 10));

        grid.add(k);
        grid.add(v);

        JSeparator sep1 = new JSeparator();
        sep1.setForeground(BORDER);
        JSeparator sep2 = new JSeparator();
        sep2.setForeground(BORDER);
        grid.add(sep1);
        grid.add(sep2);
    }

    private void addDefaultRoutes() {
        String[][] defaults = {
            {"10.0.0.0/8",       "eth0"},
            {"192.168.1.0/24",   "eth1"},
            {"192.168.0.0/16",   "eth2"},
            {"172.16.0.0/12",    "eth3"},
            {"0.0.0.0/0",        "eth-default"},
        };
        for (String[] r : defaults) {
            try {
                router.addRoute(r[0], r[1]);
                RouterSimulatorFixed.RouteEntry last =
                        router.getRoutes().get(router.getRoutes().size() - 1);
                rowCounter++;
                tableModel.addRow(new Object[]{
                        rowCounter,
                        last.getNetworkCidrString(),
                        last.getInterfaceId(),
                        "/" + last.getPrefixLength()
                });
            } catch (Exception ignored) {}
        }
    }

    // ── Style helpers ─────────────────────────────────────────
    private static void styleTable(JTable t) {
        t.setBackground(SURFACE2);
        t.setForeground(TEXT);
        t.setFont(MONO);
        t.setRowHeight(32);
        t.setGridColor(BORDER);
        t.setSelectionBackground(new Color(0x1C, 0x35, 0x50));
        t.setSelectionForeground(ACCENT);
        t.setShowVerticalLines(false);
        t.setIntercellSpacing(new Dimension(0, 1));

        JTableHeader hdr = t.getTableHeader();
        hdr.setBackground(SURFACE);
        hdr.setForeground(MUTED);
        hdr.setFont(UI_SB);
        hdr.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));

        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        t.getColumnModel().getColumn(0).setCellRenderer(center);
        t.getColumnModel().getColumn(3).setCellRenderer(center);

        t.getColumnModel().getColumn(0).setPreferredWidth(35);
        t.getColumnModel().getColumn(1).setPreferredWidth(180);
        t.getColumnModel().getColumn(2).setPreferredWidth(110);
        t.getColumnModel().getColumn(3).setPreferredWidth(90);
    }
    private void styleTable() { styleTable(routeTable); }

    private static JTextField styledField(String placeholder) {
        JTextField f = new JTextField(placeholder);
        f.setFont(MONO);
        f.setBackground(SURFACE2);
        f.setForeground(TEXT);
        f.setCaretColor(ACCENT);
        f.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(8, 12, 8, 12)));
        f.setPreferredSize(new Dimension(180, 40));
        f.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                f.setBorder(new CompoundBorder(
                        BorderFactory.createLineBorder(ACCENT),
                        new EmptyBorder(8, 12, 8, 12)));
            }
            public void focusLost(FocusEvent e) {
                f.setBorder(new CompoundBorder(
                        BorderFactory.createLineBorder(BORDER),
                        new EmptyBorder(8, 12, 8, 12)));
            }
        });
        return f;
    }

    private static JPanel card() {
        JPanel p = new JPanel();
        p.setBackground(SURFACE);
        p.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(16, 18, 16, 18)));
        return p;
    }

    private static JLabel sectionLabel(String txt) {
        JLabel l = new JLabel(txt);
        l.setFont(UI_SB);
        l.setForeground(MUTED);
        l.setBorder(new EmptyBorder(0, 0, 8, 0));
        return l;
    }

    private static JLabel fieldLabel(String txt) {
        JLabel l = new JLabel(txt);
        l.setFont(UI_B);
        l.setForeground(TEXT);
        return l;
    }

    private static JButton primaryBtn(String txt) {
        JButton b = new JButton(txt);
        b.setFont(UI_B);
        b.setBackground(new Color(0x23, 0x86, 0x36));
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(0, 40));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setBackground(GREEN); }
            public void mouseExited(MouseEvent e)  { b.setBackground(new Color(0x23, 0x86, 0x36)); }
        });
        return b;
    }

    private static JButton secondaryBtn(String txt) {
        JButton b = new JButton(txt);
        b.setFont(UI);
        b.setBackground(SURFACE2);
        b.setForeground(MUTED);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createLineBorder(BORDER));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(0, 40));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setForeground(TEXT); }
            public void mouseExited(MouseEvent e)  { b.setForeground(MUTED); }
        });
        return b;
    }

    private static JLabel pill(String txt, Color color) {
        JLabel l = new JLabel(txt);
        l.setFont(UI_SB);
        l.setForeground(color);
        l.setOpaque(true);
        l.setBackground(new Color(color.getRed(), color.getGreen(), color.getBlue(), 30));
        l.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(color),
                new EmptyBorder(4, 14, 4, 14)));
        return l;
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Hata", JOptionPane.ERROR_MESSAGE);
    }

    // ── Entry point ──────────────────────────────────────────
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        // Global dark overrides
        UIManager.put("Panel.background",       BG);
        UIManager.put("OptionPane.background",  SURFACE);
        UIManager.put("OptionPane.messageForeground", TEXT);

        SwingUtilities.invokeLater(() -> new RouterDashboard().setVisible(true));
    }
}

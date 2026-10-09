package com.trading.engine.ui;

import com.trading.engine.engine.MatchingEngine;
import com.trading.engine.model.Order;
import com.trading.engine.model.Trade;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** A dark, terminal-inspired control panel backed by the matching engine. */
public class DashboardFrame extends JFrame {
    private static final Color BACKGROUND = new Color(2, 10, 22);
    private static final Color PANEL = new Color(5, 19, 35);
    private static final Color LINE = new Color(20, 65, 104);
    private static final Color BLUE = new Color(47, 165, 255);
    private static final Color CYAN = new Color(42, 220, 238);
    private static final Color GREEN = new Color(72, 224, 100);
    private static final Color RED = new Color(255, 91, 91);
    private static final Color TEXT = new Color(220, 227, 243);
    private static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 16);
    private MatchingEngine engine = new MatchingEngine("AAPL");
    private final DefaultTableModel asks = tableModel();
    private final DefaultTableModel bids = tableModel();
    private final DefaultTableModel trades = new DefaultTableModel(new String[]{"TRADE ID", "SYMBOL", "QUANTITY", "PRICE", "TIME"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JLabel bestAsk = new JLabel("--");
    private final JLabel bestBid = new JLabel("--");
    private final JLabel totalTrades = new JLabel();
    private final JLabel totalShares = new JLabel();
    private final JLabel ordersSubmitted = new JLabel("0");
    private final JLabel openBuys = new JLabel("0");
    private final JLabel openSells = new JLabel("0");
    private final JLabel sharesTraded = new JLabel("0");
    private final JLabel lastTradedPrice = new JLabel("--");
    private final JLabel marketChange = new JLabel("--");
    private final JLabel marketVolume = new JLabel("0");
    private final JLabel marketBestBid = new JLabel("--");
    private final JLabel marketBestAsk = new JLabel("--");
    private final JLabel marketSpread = new JLabel("--");
    private final JPanel recentTrades = new JPanel();
    private final JTextField quantityInput = inputField("100");
    private final JTextField priceInput = inputField("150.00");
    private final JTextField cancelInput = inputField("");
    private int submitted;

    public DashboardFrame() {
        super("Order Matching Engine");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1180, 780));
        setSize(1536, 1024);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BACKGROUND);
        setLayout(new BorderLayout(10, 10));
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(15, 15, 15, 15));
        add(header(), BorderLayout.NORTH);
        add(body(), BorderLayout.CENTER);
        add(commandBar(), BorderLayout.SOUTH);
        refreshDashboard();
        new Timer(1000, e -> refreshClock()).start();
    }

    private JComponent header() {
        JPanel p = panel(new BorderLayout(20, 0));
        p.setPreferredSize(new Dimension(0, 85));
        JLabel brand = new JLabel("ORDER MATCHING ENGINE", SwingConstants.LEFT);
        brand.setFont(new Font(Font.MONOSPACED, Font.BOLD, 29)); brand.setForeground(BLUE);
        JLabel tag = new JLabel("LOW-LATENCY PRICE-TIME PRIORITY ENGINE  |  NASDAQ SIMULATION  |  AAPL"); tag.setFont(MONO); tag.setForeground(CYAN);
        JPanel left = new JPanel(new GridLayout(2, 1)); left.setOpaque(false); left.add(brand); left.add(tag);
        JPanel status = new JPanel(new GridLayout(2, 3, 22, 4)); status.setOpaque(false);
        status.add(headerValue("EXCHANGE", "NASDAQ SIM")); status.add(headerValue("SESSION", "LIVE")); status.add(headerValue("LATENCY", "< 1 ms"));
        status.add(headerValue("ORDERS / SEC", "READY")); status.add(headerValue("VERSION", "v2.1")); status.add(headerValue("ENGINE STATUS", "RUNNING"));
        p.add(left, BorderLayout.CENTER); p.add(status, BorderLayout.EAST);
        return p;
    }

    private JPanel headerValue(String caption, String value) {
        JPanel p = new JPanel(new GridLayout(2, 1)); p.setOpaque(false);
        JLabel a = new JLabel(caption); a.setFont(MONO); a.setForeground(BLUE);
        JLabel b = new JLabel(value); b.setName(caption.contains("DATE") ? "date" : caption.contains("TIME") ? "time" : "status"); b.setFont(MONO); b.setForeground(caption.contains("STATUS") ? GREEN : TEXT);
        p.add(a); p.add(b); return p;
    }

    private JComponent body() {
        JPanel whole = new JPanel(new BorderLayout(12, 0)); whole.setOpaque(false);
        whole.add(sidebar(), BorderLayout.WEST);
        JPanel right = new JPanel(new BorderLayout(0, 14)); right.setOpaque(false);
        right.add(liveMarketInformation(), BorderLayout.NORTH);
        JPanel grid = new JPanel(new GridLayout(2, 2, 12, 12)); grid.setOpaque(false);
        grid.add(orderBook()); grid.add(tradeHistory()); grid.add(recentTradesPanel()); grid.add(statistics());
        right.add(grid, BorderLayout.CENTER); whole.add(right, BorderLayout.CENTER); return whole;
    }

    private JComponent sidebar() {
        JPanel side = panel(new BorderLayout(0, 12)); side.setPreferredSize(new Dimension(326, 0));
        JPanel top = new JPanel(new GridLayout(2, 1)); top.setOpaque(false);
        JLabel s = new JLabel("▟  CURRENT SYMBOL"); s.setFont(MONO); s.setForeground(BLUE);
        JLabel a = new JLabel("     AAPL     "); a.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 29)); a.setForeground(GREEN);
        top.add(s); top.add(a); side.add(top, BorderLayout.NORTH);
        JPanel menu = new JPanel(new GridLayout(7, 1)); menu.setOpaque(false); menu.setBorder(new TitledBorder(border(), "MAIN MENU", TitledBorder.LEFT, TitledBorder.TOP, MONO, BLUE));
        menu.add(menuButton("▱   1   Add BUY Order", GREEN, () -> addOrder(Order.Side.BUY)));
        menu.add(menuButton("▱   2   Add SELL Order", RED, () -> addOrder(Order.Side.SELL)));
        menu.add(menuButton("▤   3   View Order Book", new Color(255, 203, 0), this::refreshDashboard));
        menu.add(menuButton("▧   4   View Trade History", BLUE, this::refreshDashboard));
        menu.add(menuButton("⊗   5   Cancel Order", new Color(180, 95, 255), this::cancelOrder));
        menu.add(menuButton("▥   6   View Statistics", CYAN, this::refreshDashboard));
        menu.add(menuButton("◉   7   Exit", RED, this::dispose));
        side.add(menu, BorderLayout.CENTER);
        side.add(orderEntry(), BorderLayout.SOUTH);
        return side;
    }

    /** Always-visible inputs make it easy to submit custom orders without a dialog. */
    private JComponent orderEntry() {
        JPanel entry = new JPanel(new GridLayout(0, 2, 7, 7));
        entry.setOpaque(false);
        entry.setBorder(new TitledBorder(border(), "ORDER ENTRY", TitledBorder.LEFT, TitledBorder.TOP, MONO, BLUE));
        entry.add(entryLabel("QUANTITY")); entry.add(quantityInput);
        entry.add(entryLabel("LIMIT PRICE")); entry.add(priceInput);
        entry.add(actionButton("BUY", GREEN, () -> placeOrder(Order.Side.BUY)));
        entry.add(actionButton("SELL", RED, () -> placeOrder(Order.Side.SELL)));
        entry.add(entryLabel("CANCEL ID")); entry.add(cancelInput);
        entry.add(actionButton("CANCEL", new Color(180, 95, 255), this::cancelEnteredOrder));
        entry.add(actionButton("CLEAR ALL", BLUE, this::clearDashboard));
        return entry;
    }

    private JLabel entryLabel(String text) { return label(text, new Color(167, 188, 224)); }
    private JTextField inputField(String value) {
        JTextField field = new JTextField(value);
        field.setFont(MONO); field.setForeground(TEXT); field.setBackground(new Color(8, 29, 50));
        field.setCaretColor(CYAN); field.setBorder(new LineBorder(LINE));
        return field;
    }
    private JButton actionButton(String text, Color color, Runnable action) {
        JButton button = new JButton(text); button.setFont(MONO); button.setForeground(color); button.setBackground(PANEL);
        button.setFocusPainted(false); button.setBorder(new LineBorder(color)); button.addActionListener(e -> action.run());
        return button;
    }

    private JButton menuButton(String label, Color color, Runnable action) {
        JButton b = new JButton(label); b.setHorizontalAlignment(SwingConstants.LEFT); b.setFont(MONO); b.setForeground(TEXT); b.setBackground(PANEL); b.setFocusPainted(false); b.setBorder(new MatteBorder(0, 0, 1, 0, LINE));
        b.addActionListener(e -> action.run()); b.setToolTipText(label); return b;
    }

    private JComponent liveMarketInformation() {
        JPanel p = panel(new BorderLayout(0, 7));
        p.setBorder(new CompoundBorder(new LineBorder(new Color(35, 170, 83), 1, true), new EmptyBorder(8, 12, 8, 12)));
        JLabel title = new JLabel("●  LIVE MARKET INFORMATION  |  TRADING SESSION: LIVE");
        title.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16)); title.setForeground(GREEN);
        JPanel metrics = new JPanel(new GridLayout(1, 6, 10, 0)); metrics.setOpaque(false);
        metrics.add(marketMetric("LAST TRADED PRICE", lastTradedPrice, CYAN));
        metrics.add(marketMetric("CHANGE", marketChange, GREEN));
        metrics.add(marketMetric("VOLUME", marketVolume, TEXT));
        metrics.add(marketMetric("BEST BID", marketBestBid, GREEN));
        metrics.add(marketMetric("BEST ASK", marketBestAsk, RED));
        metrics.add(marketMetric("SPREAD", marketSpread, new Color(255, 203, 0)));
        p.add(title, BorderLayout.NORTH); p.add(metrics, BorderLayout.CENTER);
        return p;
    }
    private JComponent marketMetric(String caption, JLabel value, Color color) {
        JPanel p = new JPanel(new GridLayout(2, 1)); p.setOpaque(false);
        JLabel label = new JLabel(caption); label.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12)); label.setForeground(new Color(167, 188, 224));
        value.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16)); value.setForeground(color);
        p.add(label); p.add(value); return p;
    }

    private JComponent orderBook() {
        JPanel p = titled("▤  ORDER BOOK - AAPL");
        JPanel center = new JPanel(new GridLayout(1, 2, 10, 0)); center.setOpaque(false);
        center.add(depth("SELL ORDERS (ASKS)", asks, RED)); center.add(depth("BUY ORDERS (BIDS)", bids, GREEN)); p.add(center, BorderLayout.CENTER);
        JPanel footer = new JPanel(new GridLayout(1, 4)); footer.setOpaque(false); footer.add(label("BEST ASK", RED)); footer.add(bestAsk); footer.add(label("BEST BID", GREEN)); footer.add(bestBid); styleValues(footer); p.add(footer, BorderLayout.SOUTH); return p;
    }
    private JComponent depth(String heading, DefaultTableModel model, Color color) { JPanel p = new JPanel(new BorderLayout()); p.setOpaque(false); JLabel h = new JLabel(heading, SwingConstants.CENTER); h.setFont(MONO); h.setForeground(color); p.add(h, BorderLayout.NORTH); p.add(table(model), BorderLayout.CENTER); return p; }
    private JComponent tradeHistory() { JPanel p = titled("▧  TRADE HISTORY"); p.add(table(trades), BorderLayout.CENTER); JPanel f = new JPanel(new GridLayout(1, 4)); f.setOpaque(false); f.add(label("TOTAL TRADES :", BLUE)); f.add(totalTrades); f.add(label("TOTAL SHARES :", BLUE)); f.add(totalShares); styleValues(f); p.add(f, BorderLayout.SOUTH); return p; }
    private JComponent recentTradesPanel() { JPanel p = titled("ϟ  RECENT TRADES"); recentTrades.setOpaque(false); recentTrades.setLayout(new BoxLayout(recentTrades, BoxLayout.Y_AXIS)); p.add(recentTrades, BorderLayout.CENTER); return p; }
    private JComponent statistics() { JPanel p = titled("▥  STATISTICS"); JPanel g = new JPanel(new GridLayout(3, 2)); g.setOpaque(false); g.add(stat("▣  ORDERS SUBMITTED", ordersSubmitted, BLUE)); g.add(stat("↗  OPEN BUY ORDERS", openBuys, GREEN)); g.add(stat("♧  TRADES EXECUTED", totalTrades, GREEN)); g.add(stat("↘  OPEN SELL ORDERS", openSells, RED)); g.add(stat("▤  SHARES TRADED", sharesTraded, new Color(255, 198, 0))); g.add(stat("⚙  MATCHING ALGORITHM", fixed("Price-Time Priority", CYAN), new Color(115, 156, 205))); p.add(g, BorderLayout.CENTER); return p; }
    private JComponent stat(String name, JLabel value, Color color) { JPanel p = new JPanel(new GridLayout(2,1)); p.setOpaque(false); p.setBorder(new MatteBorder(0,0,1,1,LINE)); JLabel n = label(name, new Color(167, 188, 224)); value.setFont(MONO); value.setForeground(color); p.add(n); p.add(value); return p; }

    private JComponent commandBar() { JPanel p = panel(new FlowLayout(FlowLayout.LEFT, 22, 12)); p.setPreferredSize(new Dimension(0, 75)); JLabel prompt = new JLabel("▸_   Enter your choice (1-7) :"); prompt.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 19)); prompt.setForeground(TEXT); JTextField field = new JTextField(3); field.setFont(MONO); field.setBackground(PANEL); field.setForeground(CYAN); field.addActionListener(e -> { try { int n = Integer.parseInt(field.getText()); if (n == 1) addOrder(Order.Side.BUY); else if (n == 2) addOrder(Order.Side.SELL); else if (n == 5) cancelOrder(); else if (n == 7) dispose(); else refreshDashboard(); } catch (NumberFormatException ignored) {} field.setText(""); }); p.add(prompt); p.add(field); return p; }
    private JPanel titled(String t) { JPanel p = panel(new BorderLayout(0, 10)); p.setBorder(new CompoundBorder(border(), new EmptyBorder(12,12,12,12))); JLabel h = new JLabel(t); h.setFont(new Font(Font.MONOSPACED, Font.BOLD, 19)); h.setForeground(BLUE); h.setBorder(new MatteBorder(0,0,1,0,LINE)); p.add(h, BorderLayout.NORTH); return p; }
    private JScrollPane table(DefaultTableModel m) { JTable t = new JTable(m); t.setFont(MONO); t.setForeground(TEXT); t.setBackground(PANEL); t.setRowHeight(28); t.setGridColor(LINE); t.setFillsViewportHeight(true); JTableHeader h=t.getTableHeader(); h.setFont(MONO); h.setForeground(TEXT); h.setBackground(PANEL); JScrollPane s = new JScrollPane(t); s.setBorder(BorderFactory.createEmptyBorder()); s.getViewport().setBackground(PANEL); return s; }
    private DefaultTableModel tableModel() { return new DefaultTableModel(new String[]{"PRICE", "QUANTITY"}, 0) { @Override public boolean isCellEditable(int r,int c) { return false; }}; }
    private JPanel panel(LayoutManager l) { JPanel p = new JPanel(l); p.setBackground(PANEL); p.setBorder(border()); return p; }
    private Border border() { return new LineBorder(LINE, 1, true); }
    private JLabel label(String s, Color c) { JLabel l = new JLabel(s); l.setFont(MONO); l.setForeground(c); return l; }
    private JLabel fixed(String s, Color c) { JLabel l = new JLabel(s); l.setFont(MONO); l.setForeground(c); return l; }
    private void styleValues(Container c) { for (Component x : c.getComponents()) if (x instanceof JLabel l && l != totalTrades && l != totalShares) { l.setFont(MONO); l.setForeground(TEXT); } totalTrades.setFont(MONO); totalShares.setFont(MONO); totalTrades.setForeground(TEXT); totalShares.setForeground(TEXT); }

    private void addOrder(Order.Side side) { placeOrder(side); }
    private void placeOrder(Order.Side side) {
        try {
            long quantity = Long.parseLong(quantityInput.getText().trim());
            BigDecimal price = new BigDecimal(priceInput.getText().trim());
            engine.submitOrder(new Order(engine.nextOrderId(), "AAPL", side, price, quantity));
            submitted++;
            refreshDashboard();
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, "Quantity must be a positive whole number and price must be positive.", "Invalid order", JOptionPane.ERROR_MESSAGE);
        }
    }
    private void cancelOrder() { cancelInput.requestFocusInWindow(); }
    private void cancelEnteredOrder() {
        try {
            boolean ok = engine.cancelOrder(Long.parseLong(cancelInput.getText().trim()));
            JOptionPane.showMessageDialog(this, ok ? "Order cancelled." : "Order not found or already filled.");
            cancelInput.setText(""); refreshDashboard();
        } catch (NumberFormatException e) { JOptionPane.showMessageDialog(this, "Enter a numeric resting order ID."); }
    }
    private void clearDashboard() {
        engine = new MatchingEngine("AAPL");
        submitted = 0;
        cancelInput.setText("");
        refreshDashboard();
    }
    private void refreshDashboard() {
        fillDepth(asks, engine.getOrderBook().depthSnapshot(Order.Side.SELL)); fillDepth(bids, engine.getOrderBook().depthSnapshot(Order.Side.BUY));
        bestAsk.setText(engine.getOrderBook().bestAskPrice() == null ? "--" : engine.getOrderBook().bestAskPrice().toPlainString()); bestBid.setText(engine.getOrderBook().bestBidPrice() == null ? "--" : engine.getOrderBook().bestBidPrice().toPlainString());
        trades.setRowCount(0); long volume=0; List<Trade> log = engine.getTradeLog(); for (int i=log.size()-1;i>=0;i--) { Trade t=log.get(i); volume+=t.getQuantity(); trades.addRow(new Object[]{t.getTradeId(),t.getSymbol(),t.getQuantity(),t.getPrice(),"executed"}); }
        totalTrades.setText(String.valueOf(log.size())); totalShares.setText(String.valueOf(volume)); sharesTraded.setText(String.valueOf(volume)); ordersSubmitted.setText(String.valueOf(submitted)); openBuys.setText(String.valueOf(engine.getOrderBook().restingOrderCount(Order.Side.BUY))); openSells.setText(String.valueOf(engine.getOrderBook().restingOrderCount(Order.Side.SELL)));
        BigDecimal bid = engine.getOrderBook().bestBidPrice();
        BigDecimal ask = engine.getOrderBook().bestAskPrice();
        marketBestBid.setText(bid == null ? "--" : "$" + bid.toPlainString());
        marketBestAsk.setText(ask == null ? "--" : "$" + ask.toPlainString());
        marketSpread.setText(bid == null || ask == null ? "--" : ask.subtract(bid).toPlainString());
        marketVolume.setText(String.format("%,d", volume));
        if (log.isEmpty()) {
            lastTradedPrice.setText("--"); marketChange.setText("--");
        } else {
            BigDecimal last = log.get(log.size() - 1).getPrice();
            BigDecimal openingReference = new BigDecimal("147.49");
            BigDecimal percentage = last.subtract(openingReference).multiply(new BigDecimal("100")).divide(openingReference, 2, java.math.RoundingMode.HALF_UP);
            lastTradedPrice.setText("$" + last.toPlainString());
            marketChange.setText((percentage.signum() >= 0 ? "▲ +" : "▼ ") + percentage + "%");
            marketChange.setForeground(percentage.signum() >= 0 ? GREEN : RED);
        }
        recentTrades.removeAll(); for (int i=log.size()-1, shown=0;i>=0 && shown<3;i--,shown++) { Trade t=log.get(i); JLabel row=label("●  TRADE ID : "+t.getTradeId()+"   BUY      "+t.getQuantity()+" @ "+t.getPrice()+"     AAPL", GREEN); row.setBorder(new EmptyBorder(12,8,12,8)); recentTrades.add(row); } recentTrades.revalidate(); recentTrades.repaint(); refreshClock();
    }
    private void fillDepth(DefaultTableModel m, Map<BigDecimal,Long> data) { m.setRowCount(0); for (Map.Entry<BigDecimal,Long> e:data.entrySet()) m.addRow(new Object[]{e.getKey().toPlainString(),e.getValue()}); for(int i=m.getRowCount();i<5;i++) m.addRow(new Object[]{"-","-"}); }
    private void refreshClock() { LocalDateTime n=LocalDateTime.now(); setNamedText(getContentPane(), "date", n.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))); setNamedText(getContentPane(), "time", n.format(DateTimeFormatter.ofPattern("hh:mm:ss a"))); }
    private void setNamedText(Container c, String name, String text) { for (Component x:c.getComponents()) { if (x instanceof JLabel l && name.equals(l.getName())) l.setText(text); if (x instanceof Container nested) setNamedText(nested,name,text); } }
}

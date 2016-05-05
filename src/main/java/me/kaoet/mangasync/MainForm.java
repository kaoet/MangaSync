package me.kaoet.mangasync;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.event.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainForm {
    private JFrame frame;
    private JTextField searchField;
    private JButton searchButton;
    private JTree bookTree;
    private JPanel rootPanel;
    private JList chapterList;
    private JTable networkTable;
    private JTextField pathField;
    private JButton selectPathButton;
    private JButton saveButton;
    private ExecutorService networkExecutor;
    private MangaSource[] mangaSources;
    private HttpClient http;

    private MainForm(JFrame frame) {
        this.frame = frame;
        searchButton.addActionListener(this::search);
        selectPathButton.addActionListener(this::selectPath);
        saveButton.addActionListener(this::save);
        bookTree.addTreeSelectionListener(this::bookSelected);

        networkExecutor = Executors.newFixedThreadPool(10);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                networkExecutor.shutdownNow();
            }
        });

        http = new HttpClient(networkExecutor, new NetworkTableManager());
        mangaSources = new MangaSource[]{
                new ChuiXue(http),
                new Dm5(http),
                new Dmzj(http),
                new TukuCC(http)
        };
        bookTree.setModel(new DefaultTreeModel(null));
    }

    private void search(ActionEvent event) {
        String query = searchField.getText();
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Manga Sources");
        for (int sid = 0; sid < mangaSources.length; sid++) {
            MangaSource mangaSource = mangaSources[sid];
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(mangaSource.getClass().getSimpleName());
            root.add(node);
            new SwingWorker<List<Book>, Void>() {

                @Override
                protected List<Book> doInBackground() throws Exception {
                    return mangaSource.search(query);
                }

                @Override
                protected void done() {
                    List<Book> books;
                    try {
                        books = get();
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                        return;
                    }
                    for (Book book : books) {
                        node.add(new DefaultMutableTreeNode(book));
                    }
                    bookTree.repaint();
                }
            }.execute();
        }
        bookTree.setModel(new DefaultTreeModel(root));
    }

    private void bookSelected(TreeSelectionEvent event) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) bookTree.getLastSelectedPathComponent();
        if (node == null || !(node.getUserObject() instanceof Book)) {
            saveButton.setEnabled(false);
            return;
        }
        saveButton.setEnabled(true);

        Book book = (Book) node.getUserObject();
        new SwingWorker<List<Chapter>, Void>() {

            @Override
            protected List<Chapter> doInBackground() throws Exception {
                return book.mangaSource.chapters(book.id);
            }

            @Override
            protected void done() {
                List<Chapter> chapters;
                try {
                    chapters = get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                    return;
                }

                DefaultListModel<Chapter> listModel = new DefaultListModel<>();
                for (Chapter chapter : chapters) {
                    listModel.addElement(chapter);
                }
                chapterList.setModel(listModel);
                frame.repaint();
            }
        }.execute();
    }

    private void selectPath(ActionEvent event) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setAcceptAllFileFilterUsed(false);

        if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            pathField.setText(fileChooser.getSelectedFile().toString());
        }
    }

    private void save(ActionEvent event) {
        if (pathField.getText().isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Select path first.");
            return;
        }

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) bookTree.getLastSelectedPathComponent();
        Book book = (Book) node.getUserObject();
        new SwingWorker<Void, Void>() {

            @Override
            protected Void doInBackground() throws Exception {
                List<Chapter> chapters = book.mangaSource.chapters(book.id);
                for (Chapter chapter: chapters) {
                   List<Page> pages= book.mangaSource.pages(book.id, chapter.id);
                    for (int i = 0; i < pages.size();i++) {
                        Path path = Paths.get(pathField.getText(), book.name, chapter.name, Integer.toString(i + 1) + ".jpg");
                        if (!Files.exists(path)) {
                            http.get(pages.get(i).url).referer(pages.get(i).referer).toPath(path);
                        }
                    }
                }
                return null;
            }
        }.execute();
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Manga Sync");
        frame.setContentPane(new MainForm(frame).rootPanel);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

    class NetworkTableManager implements NetworkListener {

        private List<Integer> rowIds = new ArrayList<>();
        private DefaultTableModel tableModel;

        public NetworkTableManager() {
            tableModel = new DefaultTableModel();
            tableModel.addColumn("Method");
            tableModel.addColumn("URL");
            tableModel.addColumn("Progress");
            networkTable.setModel(tableModel);
        }

        @Override
        public void requestStart(int id, String method, String url) {
            SwingUtilities.invokeLater(() -> {
                tableModel.addRow(new Object[]{method, url, "0%"});
                rowIds.add(id);
            });
        }

        @Override
        public void progress(int id, String status) {
            SwingUtilities.invokeLater(() -> {
                int index = rowIds.indexOf(id);
                if (index != -1) {
                    tableModel.setValueAt(status, index, 2);
                }
            });
        }

        @Override
        public void requestFinish(int id) {
            SwingUtilities.invokeLater(() -> {
                int index = rowIds.indexOf(id);
                if (index != -1) {
                    tableModel.removeRow(index);
                    rowIds.remove(index);
                }
            });
        }
    }
}

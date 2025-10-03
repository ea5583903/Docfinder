import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {
    private JFrame frame;
    private JTable fileTable;
    private DefaultTableModel tableModel;
    private JLabel statusLabel;
    private Path currentPath;
    private ScheduledExecutorService scheduler;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private JTextField searchField;
    private JCheckBox recursiveSearchBox;
    private JTextField pathField;
    private java.util.List<Object[]> allFiles;
    private JTabbedPane tabbedPane;
    private JTable gamesTable;
    private DefaultTableModel gamesTableModel;
    private java.util.List<GameInfo> allGames;
    private Path appGamesDirectory;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new Main().createAndShowGUI();
        });
    }

    private void createAndShowGUI() {
        frame = new JFrame("File Manager - Activity Monitor");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLocationRelativeTo(null);

        currentPath = Paths.get(System.getProperty("user.home"), "exe files");
        
        // Initialize app games directory
        appGamesDirectory = currentPath.resolve("app").resolve("games");
        try {
            Files.createDirectories(appGamesDirectory);
        } catch (IOException e) {
            System.err.println("Could not create app games directory: " + e.getMessage());
        }
        
        createComponents();
        setupLayout();
        loadFiles();
        startActivityMonitor();
        
        frame.setVisible(true);
    }

    private void createComponents() {
        String[] columnNames = {"Name", "Type", "Size", "Last Modified", "Status"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        fileTable = new JTable(tableModel);
        fileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileTable.getColumnModel().getColumn(0).setPreferredWidth(200);
        fileTable.getColumnModel().getColumn(1).setPreferredWidth(80);
        fileTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        fileTable.getColumnModel().getColumn(3).setPreferredWidth(150);
        fileTable.getColumnModel().getColumn(4).setPreferredWidth(100);
        
        searchField = new JTextField(20);
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filterFiles(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filterFiles(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filterFiles(); }
        });
        
        recursiveSearchBox = new JCheckBox("Search subdirectories");
        recursiveSearchBox.addActionListener(e -> loadFiles());
        
        pathField = new JTextField(30);
        pathField.setText(currentPath.toString());
        pathField.addActionListener(e -> navigateToPath());
        
        // Initialize games table
        String[] gamesColumnNames = {"Game Name", "Type", "Status", "Description", "Path"};
        gamesTableModel = new DefaultTableModel(gamesColumnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        gamesTable = new JTable(gamesTableModel);
        gamesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        gamesTable.getColumnModel().getColumn(0).setPreferredWidth(200);
        gamesTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        gamesTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        gamesTable.getColumnModel().getColumn(3).setPreferredWidth(300);
        gamesTable.getColumnModel().getColumn(4).setPreferredWidth(350);
        
        gamesTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    launchSelectedGame();
                }
            }
        });
        
        allFiles = new ArrayList<>();
        allGames = new ArrayList<>();
        statusLabel = new JLabel("Ready - Monitoring: " + currentPath.toString());
    }

    private void setupLayout() {
        frame.setLayout(new BorderLayout());
        
        // Create tabbed pane
        tabbedPane = new JTabbedPane();
        
        // Files tab
        JPanel filesPanel = createFilesTab();
        tabbedPane.addTab("Files", filesPanel);
        
        // Games tab
        JPanel gamesPanel = createGamesTab();
        tabbedPane.addTab("Games", gamesPanel);
        
        frame.add(tabbedPane, BorderLayout.CENTER);
        frame.add(statusLabel, BorderLayout.SOUTH);
    }
    
    private JPanel createFilesTab() {
        JPanel filesPanel = new JPanel(new BorderLayout());
        
        JPanel topPanel = new JPanel(new BorderLayout());
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refreshBtn = new JButton("Refresh");
        JButton openBtn = new JButton("Open");
        JButton runBtn = new JButton("Run");
        JButton deleteBtn = new JButton("Delete File");
        JButton editBtn = new JButton("Edit File");
        JButton addFileBtn = new JButton("Add File");
        JButton addFolderBtn = new JButton("Add Folder");
        JButton upBtn = new JButton("Up");
        JButton goBtn = new JButton("Go");
        
        refreshBtn.addActionListener(e -> loadFiles());
        openBtn.addActionListener(e -> openSelectedItem());
        runBtn.addActionListener(e -> runProject());
        deleteBtn.addActionListener(e -> deleteSelectedFile());
        editBtn.addActionListener(e -> editSelectedFile());
        addFileBtn.addActionListener(e -> createNewFile());
        addFolderBtn.addActionListener(e -> createNewFolder());
        upBtn.addActionListener(e -> navigateUp());
        goBtn.addActionListener(e -> navigateToPath());
        
        buttonPanel.add(refreshBtn);
        buttonPanel.add(openBtn);
        buttonPanel.add(runBtn);
        buttonPanel.add(deleteBtn);
        buttonPanel.add(editBtn);
        buttonPanel.add(addFileBtn);
        buttonPanel.add(addFolderBtn);
        buttonPanel.add(upBtn);
        
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.add(new JLabel("Search:"));
        searchPanel.add(searchField);
        searchPanel.add(recursiveSearchBox);
        
        JPanel pathPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pathPanel.add(new JLabel("Path:"));
        pathPanel.add(pathField);
        pathPanel.add(goBtn);
        
        JPanel middlePanel = new JPanel(new BorderLayout());
        middlePanel.add(pathPanel, BorderLayout.NORTH);
        middlePanel.add(searchPanel, BorderLayout.SOUTH);
        
        topPanel.add(buttonPanel, BorderLayout.NORTH);
        topPanel.add(middlePanel, BorderLayout.SOUTH);
        
        fileTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    openSelectedItem();
                }
            }
        });
        
        filesPanel.add(topPanel, BorderLayout.NORTH);
        filesPanel.add(new JScrollPane(fileTable), BorderLayout.CENTER);
        
        return filesPanel;
    }
    
    private JPanel createGamesTab() {
        JPanel gamesPanel = new JPanel(new BorderLayout());
        
        JPanel gameButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton scanGamesBtn = new JButton("Scan for Games");
        JButton launchGameBtn = new JButton("Launch Game");
        JButton testGameBtn = new JButton("Test Game");
        JButton gameInfoBtn = new JButton("Game Info");
        
        scanGamesBtn.addActionListener(e -> scanForGames());
        launchGameBtn.addActionListener(e -> launchSelectedGame());
        testGameBtn.addActionListener(e -> testSelectedGame());
        gameInfoBtn.addActionListener(e -> showGameInfo());
        
        gameButtonPanel.add(scanGamesBtn);
        gameButtonPanel.add(launchGameBtn);
        gameButtonPanel.add(testGameBtn);
        gameButtonPanel.add(gameInfoBtn);
        
        gamesPanel.add(gameButtonPanel, BorderLayout.NORTH);
        gamesPanel.add(new JScrollPane(gamesTable), BorderLayout.CENTER);
        
        return gamesPanel;
    }

    private void loadFiles() {
        allFiles.clear();
        
        try {
            if (!Files.exists(currentPath)) {
                Files.createDirectories(currentPath);
            }
            
            if (recursiveSearchBox.isSelected()) {
                Files.walkFileTree(currentPath, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                        addFileToList(file);
                        return FileVisitResult.CONTINUE;
                    }
                    
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                        if (!dir.equals(currentPath)) {
                            addFileToList(dir);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                Files.list(currentPath).forEach(this::addFileToList);
            }
            
            filterFiles();
            
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Error loading files: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void addFileToList(Path path) {
        File file = path.toFile();
        String name = file.getName();
        String type = file.isDirectory() ? "Folder" : getFileExtension(name);
        String size = file.isDirectory() ? "" : formatFileSize(file.length());
        String lastModified = dateFormat.format(new Date(file.lastModified()));
        String status = file.canRead() && file.canWrite() ? "RW" : file.canRead() ? "R" : "?";
        String relativePath = recursiveSearchBox.isSelected() ? 
            currentPath.relativize(path).toString() : name;
        
        allFiles.add(new Object[]{relativePath, type, size, lastModified, status, path.toString()});
    }
    
    private void filterFiles() {
        tableModel.setRowCount(0);
        String searchText = searchField.getText().toLowerCase().trim();
        
        for (Object[] fileData : allFiles) {
            String fileName = (String) fileData[0];
            if (searchText.isEmpty() || fileName.toLowerCase().contains(searchText)) {
                tableModel.addRow(new Object[]{fileData[0], fileData[1], fileData[2], fileData[3], fileData[4]});
            }
        }
        
        statusLabel.setText("Ready - Monitoring: " + currentPath.toString() + 
            " (" + tableModel.getRowCount() + "/" + allFiles.size() + " items)");
    }

    private void startActivityMonitor() {
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            SwingUtilities.invokeLater(() -> {
                updateFileStatuses();
            });
        }, 0, 2, TimeUnit.SECONDS);
    }

    private void updateFileStatuses() {
        try {
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                String fileName = (String) tableModel.getValueAt(i, 0);
                Path filePath = currentPath.resolve(fileName);
                
                if (Files.exists(filePath)) {
                    File file = filePath.toFile();
                    String newLastModified = dateFormat.format(new Date(file.lastModified()));
                    String oldLastModified = (String) tableModel.getValueAt(i, 3);
                    
                    if (!newLastModified.equals(oldLastModified)) {
                        tableModel.setValueAt(newLastModified, i, 3);
                        tableModel.setValueAt("Modified", i, 4);
                        
                        final int rowIndex = i;
                        Timer timer = new Timer(3000, e -> {
                            if (rowIndex < tableModel.getRowCount()) {
                                tableModel.setValueAt("RW", rowIndex, 4);
                            }
                        });
                        timer.setRepeats(false);
                        timer.start();
                    }
                }
            }
        } catch (Exception e) {
            // Silently handle any monitoring errors
        }
    }

    private void deleteSelectedFile() {
        int selectedRow = fileTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(frame, "Please select a file to delete.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String fileName = (String) tableModel.getValueAt(selectedRow, 0);
        Path filePath = getSelectedFilePath(selectedRow);
        
        int result = JOptionPane.showConfirmDialog(frame, 
            "Are you sure you want to delete '" + fileName + "'?", 
            "Confirm Delete", 
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
        
        if (result == JOptionPane.YES_OPTION) {
            try {
                if (Files.isDirectory(filePath)) {
                    Files.walkFileTree(filePath, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                            Files.delete(file);
                            return FileVisitResult.CONTINUE;
                        }
                        
                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                            Files.delete(dir);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } else {
                    Files.delete(filePath);
                }
                
                loadFiles();
                JOptionPane.showMessageDialog(frame, "'" + fileName + "' deleted successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                
            } catch (IOException e) {
                JOptionPane.showMessageDialog(frame, "Error deleting file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void createNewFile() {
        String fileName = JOptionPane.showInputDialog(frame, "Enter new file name:", "Create New File", JOptionPane.PLAIN_MESSAGE);
        
        if (fileName != null && !fileName.trim().isEmpty()) {
            Path newFilePath = currentPath.resolve(fileName.trim());
            
            try {
                if (Files.exists(newFilePath)) {
                    JOptionPane.showMessageDialog(frame, "File already exists!", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                Files.createFile(newFilePath);
                loadFiles();
                JOptionPane.showMessageDialog(frame, "File '" + fileName + "' created successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                
            } catch (IOException e) {
                JOptionPane.showMessageDialog(frame, "Error creating file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void createNewFolder() {
        String folderName = JOptionPane.showInputDialog(frame, "Enter new folder name:", "Create New Folder", JOptionPane.PLAIN_MESSAGE);
        
        if (folderName != null && !folderName.trim().isEmpty()) {
            Path newFolderPath = currentPath.resolve(folderName.trim());
            
            try {
                if (Files.exists(newFolderPath)) {
                    JOptionPane.showMessageDialog(frame, "Folder already exists!", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                Files.createDirectory(newFolderPath);
                loadFiles();
                JOptionPane.showMessageDialog(frame, "Folder '" + folderName + "' created successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                
            } catch (IOException e) {
                JOptionPane.showMessageDialog(frame, "Error creating folder: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void openSelectedItem() {
        int selectedRow = fileTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(frame, "Please select a file or folder to open.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String fileName = (String) tableModel.getValueAt(selectedRow, 0);
        String type = (String) tableModel.getValueAt(selectedRow, 1);
        Path selectedPath = getSelectedFilePath(selectedRow);
        File selectedFile = selectedPath.toFile();
        
        try {
            if ("Folder".equals(type)) {
                handleFolderOpen(selectedPath, fileName);
            } else {
                handleFileOpen(selectedPath, fileName, type);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Error opening item: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void handleFolderOpen(Path folderPath, String folderName) throws IOException {
        String[] options = {"Navigate into folder", "Open in system file manager", "Cancel"};
        int choice = JOptionPane.showOptionDialog(frame,
            "How would you like to open the folder '" + folderName + "'?",
            "Open Folder",
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]);
        
        switch (choice) {
            case 0:
                currentPath = folderPath;
                updatePathField();
                searchField.setText("");
                recursiveSearchBox.setSelected(false);
                loadFiles();
                break;
            case 1:
                openInSystemFileManager(folderPath);
                break;
        }
    }
    
    private void handleFileOpen(Path filePath, String fileName, String fileType) throws IOException {
        String extension = getFileExtension(fileName).toLowerCase();
        File file = filePath.toFile();
        
        if (isTextFile(extension)) {
            handleTextFile(filePath, fileName);
        } else if (isExecutableFile(extension, file)) {
            handleExecutableFile(filePath, fileName);
        } else if (isImageFile(extension)) {
            handleImageFile(filePath, fileName);
        } else if (isDocumentFile(extension)) {
            handleDocumentFile(filePath, fileName);
        } else {
            handleGenericFile(filePath, fileName);
        }
    }
    
    private void handleTextFile(Path filePath, String fileName) {
        String[] options = {"Edit in Nano Editor", "Open with system default", "Cancel"};
        int choice = JOptionPane.showOptionDialog(frame,
            "How would you like to open '" + fileName + "'?",
            "Open Text File",
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]);
        
        switch (choice) {
            case 0:
                new NanoEditor(filePath).setVisible(true);
                break;
            case 1:
                openWithSystemDefault(filePath);
                break;
        }
    }
    
    private void handleExecutableFile(Path filePath, String fileName) throws IOException {
        String[] options = {"Run/Execute", "Edit in Nano", "Open with system default", "Cancel"};
        int choice = JOptionPane.showOptionDialog(frame,
            "'" + fileName + "' appears to be executable. How would you like to open it?",
            "Open Executable",
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]);
        
        switch (choice) {
            case 0:
                executeFile(filePath);
                break;
            case 1:
                new NanoEditor(filePath).setVisible(true);
                break;
            case 2:
                openWithSystemDefault(filePath);
                break;
        }
    }
    
    private void handleImageFile(Path filePath, String fileName) {
        String[] options = {"Open with system default", "Edit in Nano", "Cancel"};
        int choice = JOptionPane.showOptionDialog(frame,
            "How would you like to open the image '" + fileName + "'?",
            "Open Image",
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]);
        
        switch (choice) {
            case 0:
                openWithSystemDefault(filePath);
                break;
            case 1:
                new NanoEditor(filePath).setVisible(true);
                break;
        }
    }
    
    private void handleDocumentFile(Path filePath, String fileName) {
        String[] options = {"Open with system default", "Edit in Nano", "Cancel"};
        int choice = JOptionPane.showOptionDialog(frame,
            "How would you like to open '" + fileName + "'?",
            "Open Document",
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]);
        
        switch (choice) {
            case 0:
                openWithSystemDefault(filePath);
                break;
            case 1:
                new NanoEditor(filePath).setVisible(true);
                break;
        }
    }
    
    private void handleGenericFile(Path filePath, String fileName) {
        String[] options = {"Open with system default", "Edit in Nano", "Cancel"};
        int choice = JOptionPane.showOptionDialog(frame,
            "How would you like to open '" + fileName + "'?",
            "Open File",
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]);
        
        switch (choice) {
            case 0:
                openWithSystemDefault(filePath);
                break;
            case 1:
                new NanoEditor(filePath).setVisible(true);
                break;
        }
    }
    
    private Path getSelectedFilePath(int selectedRow) {
        String displayName = (String) tableModel.getValueAt(selectedRow, 0);
        
        for (Object[] fileData : allFiles) {
            if (displayName.equals(fileData[0])) {
                return Paths.get((String) fileData[5]);
            }
        }
        
        return currentPath.resolve(displayName);
    }

    private void navigateUp() {
        if (currentPath.getParent() != null) {
            currentPath = currentPath.getParent();
            updatePathField();
            loadFiles();
        }
    }
    
    private void navigateToPath() {
        String pathText = pathField.getText().trim();
        if (pathText.isEmpty()) {
            return;
        }
        
        try {
            // Handle ~ expansion
            if (pathText.startsWith("~")) {
                pathText = System.getProperty("user.home") + pathText.substring(1);
            }
            
            // Handle relative paths
            Path newPath;
            if (Paths.get(pathText).isAbsolute()) {
                newPath = Paths.get(pathText);
            } else {
                newPath = currentPath.resolve(pathText);
            }
            
            // Normalize the path
            newPath = newPath.normalize();
            
            // Check if the path exists and is a directory
            if (!Files.exists(newPath)) {
                JOptionPane.showMessageDialog(frame, "Path does not exist: " + newPath, 
                    "Invalid Path", JOptionPane.WARNING_MESSAGE);
                pathField.setText(currentPath.toString());
                return;
            }
            
            if (!Files.isDirectory(newPath)) {
                JOptionPane.showMessageDialog(frame, "Path is not a directory: " + newPath, 
                    "Invalid Path", JOptionPane.WARNING_MESSAGE);
                pathField.setText(currentPath.toString());
                return;
            }
            
            // Navigate to the new path
            currentPath = newPath;
            updatePathField();
            searchField.setText("");
            recursiveSearchBox.setSelected(false);
            loadFiles();
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Error navigating to path: " + e.getMessage(), 
                "Navigation Error", JOptionPane.ERROR_MESSAGE);
            pathField.setText(currentPath.toString());
        }
    }
    
    private void updatePathField() {
        pathField.setText(currentPath.toString());
    }

    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1).toUpperCase() : "File";
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
    
    private void runProject() {
        Path projectPath = currentPath;
        
        try {
            ProjectType projectType = detectProjectType(projectPath);
            
            if (projectType == ProjectType.UNKNOWN) {
                int selectedRow = fileTable.getSelectedRow();
                if (selectedRow != -1) {
                    String type = (String) tableModel.getValueAt(selectedRow, 1);
                    if ("Folder".equals(type)) {
                        Path selectedPath = getSelectedFilePath(selectedRow);
                        projectType = detectProjectType(selectedPath);
                        if (projectType != ProjectType.UNKNOWN) {
                            projectPath = selectedPath;
                        }
                    }
                }
            }
            
            if (projectType == ProjectType.UNKNOWN) {
                JOptionPane.showMessageDialog(frame, 
                    "No runnable project detected in current directory.\n" +
                    "Supported project types:\n" +
                    "- Java (Maven/Gradle/IntelliJ)\n" +
                    "- Node.js (package.json)\n" +
                    "- Python (main.py, requirements.txt)\n" +
                    "- C/C++ (Makefile)\n" +
                    "- Go (go.mod)\n" +
                    "- Rust (Cargo.toml)",
                    "No Project Found", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            runProjectOfType(projectPath, projectType);
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Error running project: " + e.getMessage(), 
                "Run Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private ProjectType detectProjectType(Path path) {
        try {
            if (Files.exists(path.resolve("pom.xml"))) {
                return ProjectType.MAVEN;
            }
            if (Files.exists(path.resolve("build.gradle")) || Files.exists(path.resolve("build.gradle.kts"))) {
                return ProjectType.GRADLE;
            }
            if (Files.exists(path.resolve("package.json"))) {
                return ProjectType.NODEJS;
            }
            if (Files.exists(path.resolve("requirements.txt")) || Files.exists(path.resolve("main.py")) || 
                Files.exists(path.resolve("app.py")) || Files.exists(path.resolve("manage.py"))) {
                return ProjectType.PYTHON;
            }
            if (Files.exists(path.resolve("Cargo.toml"))) {
                return ProjectType.RUST;
            }
            if (Files.exists(path.resolve("go.mod"))) {
                return ProjectType.GO;
            }
            if (Files.exists(path.resolve("Makefile")) || Files.exists(path.resolve("makefile"))) {
                return ProjectType.C_CPP;
            }
            if (hasJavaFiles(path)) {
                return ProjectType.JAVA_PLAIN;
            }
            if (Files.exists(path.resolve("src")) && Files.isDirectory(path.resolve("src"))) {
                ProjectType srcType = detectProjectType(path.resolve("src"));
                if (srcType != ProjectType.UNKNOWN) {
                    return srcType;
                }
            }
        } catch (Exception e) {
            // Ignore and return unknown
        }
        return ProjectType.UNKNOWN;
    }
    
    private boolean hasJavaFiles(Path path) {
        try {
            // Check root directory
            if (Files.walk(path, 1).anyMatch(p -> p.toString().endsWith(".java"))) {
                return true;
            }
            
            // Check src/ directory
            Path srcDir = path.resolve("src");
            if (Files.exists(srcDir) && Files.walk(srcDir, 3).anyMatch(p -> p.toString().endsWith(".java"))) {
                return true;
            }
            
            // Check src/main/java/ directory
            Path mavenSrc = path.resolve("src/main/java");
            if (Files.exists(mavenSrc) && Files.walk(mavenSrc, 5).anyMatch(p -> p.toString().endsWith(".java"))) {
                return true;
            }
            
            return false;
        } catch (IOException e) {
            return false;
        }
    }
    
    private void runProjectOfType(Path projectPath, ProjectType projectType) throws IOException {
        String[] runOptions = getRunOptionsForProject(projectType);
        
        int choice = JOptionPane.showOptionDialog(frame,
            "How would you like to run this " + projectType.getDisplayName() + " project?",
            "Run Project",
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            runOptions,
            runOptions[0]);
        
        if (choice >= 0 && choice < runOptions.length) {
            executeProjectRun(projectPath, projectType, choice);
        }
    }
    
    private String[] getRunOptionsForProject(ProjectType projectType) {
        switch (projectType) {
            case MAVEN:
                return new String[]{"mvn spring-boot:run", "mvn exec:java", "mvn compile exec:java", "Custom command", "Cancel"};
            case GRADLE:
                return new String[]{"./gradlew run", "./gradlew bootRun", "gradle run", "Custom command", "Cancel"};
            case NODEJS:
                return new String[]{"npm start", "npm run dev", "node index.js", "node app.js", "Custom command", "Cancel"};
            case PYTHON:
                return new String[]{"python main.py", "python app.py", "python manage.py runserver", "pip install -r requirements.txt", "Custom command", "Cancel"};
            case RUST:
                return new String[]{"cargo run", "cargo build", "Custom command", "Cancel"};
            case GO:
                return new String[]{"go run .", "go run main.go", "go build", "Custom command", "Cancel"};
            case C_CPP:
                return new String[]{"make", "make run", "Custom command", "Cancel"};
            case JAVA_PLAIN:
                return new String[]{"Compile & Run Main", "Compile All Java Files", "Custom command", "Cancel"};
            default:
                return new String[]{"Custom command", "Cancel"};
        }
    }
    
    private void executeProjectRun(Path projectPath, ProjectType projectType, int choice) throws IOException {
        String command = "";
        String[] runOptions = getRunOptionsForProject(projectType);
        
        if (choice >= runOptions.length - 2) { // Custom command or Cancel
            if (choice == runOptions.length - 2) { // Custom command
                command = JOptionPane.showInputDialog(frame, "Enter custom command:", "Custom Command", JOptionPane.PLAIN_MESSAGE);
                if (command == null || command.trim().isEmpty()) {
                    return;
                }
            } else {
                return; // Cancel
            }
        } else {
            // Handle special Java compilation cases
            if (projectType == ProjectType.JAVA_PLAIN) {
                if (choice == 0) { // Compile & Run Main
                    executeJavaCompileAndRun(projectPath, true);
                    return;
                } else if (choice == 1) { // Compile All Java Files
                    executeJavaCompileAndRun(projectPath, false);
                    return;
                }
            }
            command = runOptions[choice];
        }
        
        final String finalCommand = command;
        
        // Create a new window to show the process output
        JFrame outputFrame = new JFrame("Running: " + finalCommand);
        JTextArea outputArea = new JTextArea(20, 80);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        outputArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(outputArea);
        
        outputFrame.add(scrollPane);
        outputFrame.setSize(800, 400);
        outputFrame.setLocationRelativeTo(frame);
        outputFrame.setVisible(true);
        
        // Execute the command in a separate thread
        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    ProcessBuilder pb = new ProcessBuilder();
                    
                    // Split command properly
                    String[] commandArray;
                    String os = System.getProperty("os.name").toLowerCase();
                    
                    if (os.contains("win")) {
                        commandArray = new String[]{"cmd", "/c", finalCommand};
                    } else {
                        commandArray = new String[]{"bash", "-c", finalCommand};
                    }
                    
                    pb.command(commandArray);
                    pb.directory(projectPath.toFile());
                    pb.redirectErrorStream(true);
                    
                    Process process = pb.start();
                    
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            publish(line);
                        }
                    }
                    
                    int exitCode = process.waitFor();
                    publish("\n--- Process finished with exit code: " + exitCode + " ---");
                    
                } catch (Exception e) {
                    publish("Error: " + e.getMessage());
                }
                return null;
            }
            
            @Override
            protected void process(List<String> chunks) {
                for (String line : chunks) {
                    outputArea.append(line + "\n");
                    outputArea.setCaretPosition(outputArea.getDocument().getLength());
                }
            }
        };
        
        worker.execute();
    }
    
    private void executeJavaCompileAndRun(Path projectPath, boolean runMain) throws IOException {
        // Create a new window to show the process output
        JFrame outputFrame = new JFrame("Java Compile & Run");
        JTextArea outputArea = new JTextArea(20, 80);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        outputArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(outputArea);
        
        outputFrame.add(scrollPane);
        outputFrame.setSize(800, 400);
        outputFrame.setLocationRelativeTo(frame);
        outputFrame.setVisible(true);
        
        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    // Find Java files in various project structures
                    List<Path> javaFiles = new ArrayList<>();
                    List<Path> searchPaths = new ArrayList<>();
                    Path sourcePath = projectPath;
                    
                    // Check common Java project structures
                    publish("Searching for Java files in project...");
                    
                    // 1. Root directory
                    searchPaths.add(projectPath);
                    
                    // 2. src/ directory (common in simple projects)
                    Path srcDir = projectPath.resolve("src");
                    if (Files.exists(srcDir) && Files.isDirectory(srcDir)) {
                        searchPaths.add(srcDir);
                        sourcePath = srcDir; // Use src as the base for compilation
                    }
                    
                    // 3. src/main/java/ (Maven structure)
                    Path mavenSrc = projectPath.resolve("src/main/java");
                    if (Files.exists(mavenSrc) && Files.isDirectory(mavenSrc)) {
                        searchPaths.add(mavenSrc);
                        sourcePath = mavenSrc;
                    }
                    
                    // 4. src/java/ (some Gradle projects)
                    Path gradleSrc = projectPath.resolve("src/java");
                    if (Files.exists(gradleSrc) && Files.isDirectory(gradleSrc)) {
                        searchPaths.add(gradleSrc);
                        sourcePath = gradleSrc;
                    }
                    
                    // Search for Java files recursively in all paths
                    for (Path searchPath : searchPaths) {
                        if (Files.exists(searchPath)) {
                            publish("Searching in: " + projectPath.relativize(searchPath));
                            Files.walk(searchPath)
                                .filter(p -> p.toString().endsWith(".java"))
                                .forEach(javaFiles::add);
                        }
                    }
                    
                    if (javaFiles.isEmpty()) {
                        publish("No Java files found in any of the searched directories.");
                        publish("Searched locations:");
                        publish("  - Root directory");
                        publish("  - src/");
                        publish("  - src/main/java/");
                        publish("  - src/java/");
                        return null;
                    }
                    
                    publish("Found " + javaFiles.size() + " Java file(s):");
                    for (Path javaFile : javaFiles) {
                        publish("  " + projectPath.relativize(javaFile));
                    }
                    publish("");
                    
                    // Compile all Java files
                    publish("Compiling Java files from: " + projectPath.relativize(sourcePath));
                    List<String> compileCommand = new ArrayList<>();
                    compileCommand.add("javac");
                    compileCommand.add("-cp");
                    compileCommand.add(".");
                    compileCommand.add("-d");
                    compileCommand.add(".");
                    
                    // Add all Java files with their relative paths from the source directory
                    for (Path javaFile : javaFiles) {
                        if (javaFile.startsWith(sourcePath)) {
                            Path relativePath = sourcePath.relativize(javaFile);
                            compileCommand.add(relativePath.toString());
                        } else {
                            compileCommand.add(javaFile.toString());
                        }
                    }
                    
                    ProcessBuilder compilePb = new ProcessBuilder(compileCommand);
                    compilePb.directory(sourcePath.toFile());
                    compilePb.redirectErrorStream(true);
                    
                    Process compileProcess = compilePb.start();
                    
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(compileProcess.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            publish(line);
                        }
                    }
                    
                    int compileExitCode = compileProcess.waitFor();
                    
                    if (compileExitCode == 0) {
                        publish("Compilation successful!");
                        
                        if (runMain) {
                            publish("");
                            
                            // Try to find and run Main class
                            String mainClass = findMainClass(sourcePath, javaFiles);
                            if (mainClass != null) {
                                publish("Running " + mainClass + "...");
                                publish("--- Output ---");
                                
                                ProcessBuilder runPb = new ProcessBuilder("java", "-cp", ".", mainClass);
                                runPb.directory(sourcePath.toFile());
                                runPb.redirectErrorStream(true);
                                
                                Process runProcess = runPb.start();
                                
                                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                                        new java.io.InputStreamReader(runProcess.getInputStream()))) {
                                    String line;
                                    while ((line = reader.readLine()) != null) {
                                        publish(line);
                                    }
                                }
                                
                                int runExitCode = runProcess.waitFor();
                                publish("--- Program finished with exit code: " + runExitCode + " ---");
                            } else {
                                publish("No main method found in any Java file.");
                            }
                        }
                    } else {
                        publish("Compilation failed with exit code: " + compileExitCode);
                    }
                    
                } catch (Exception e) {
                    publish("Error: " + e.getMessage());
                }
                return null;
            }
            
            @Override
            protected void process(List<String> chunks) {
                for (String line : chunks) {
                    outputArea.append(line + "\n");
                    outputArea.setCaretPosition(outputArea.getDocument().getLength());
                }
            }
        };
        
        worker.execute();
    }
    
    private void scanForGames() {
        allGames.clear();
        gamesTableModel.setRowCount(0);
        
        SwingWorker<Void, String> scanner = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                publish("Scanning for games...");
                
                // Common game directories to scan
                List<Path> scanPaths = new ArrayList<>();
                Path homeDir = Paths.get(System.getProperty("user.home"));
                
                // Add common game directories
                scanPaths.add(homeDir.resolve("Games"));
                scanPaths.add(homeDir.resolve("Applications"));
                scanPaths.add(homeDir.resolve("Desktop"));
                scanPaths.add(homeDir.resolve("Downloads"));
                scanPaths.add(homeDir.resolve("Documents"));
                scanPaths.add(homeDir.resolve("IdeaProjects")); // IntelliJ projects
                scanPaths.add(homeDir.resolve("Projects")); // General projects
                scanPaths.add(homeDir.resolve("workspace")); // Eclipse workspace
                scanPaths.add(homeDir.resolve("NetBeansProjects")); // NetBeans projects
                scanPaths.add(homeDir.resolve("yooo")); // Your specific projects
                scanPaths.add(currentPath); // Current directory
                
                // Windows specific paths
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    scanPaths.add(Paths.get("C:/Program Files"));
                    scanPaths.add(Paths.get("C:/Program Files (x86)"));
                }
                
                int totalGames = 0;
                for (Path scanPath : scanPaths) {
                    if (Files.exists(scanPath)) {
                        publish("Scanning: " + scanPath);
                        totalGames += scanDirectoryForGames(scanPath);
                    }
                }
                
                publish("Scan complete! Found " + totalGames + " games.");
                return null;
            }
            
            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    statusLabel.setText(message);
                }
            }
            
            @Override
            protected void done() {
                updateGamesTable();
                statusLabel.setText("Games scan complete - Found " + allGames.size() + " games");
            }
        };
        
        scanner.execute();
    }
    
    private int scanDirectoryForGames(Path directory) {
        int gameCount = 0;
        try {
            // First scan for individual game files
            Files.walk(directory, 3)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    GameInfo game = detectGame(file);
                    if (game != null) {
                        allGames.add(game);
                    }
                });
            
            // Then scan for project directories that might be games
            Files.walk(directory, 2)
                .filter(Files::isDirectory)
                .filter(dir -> !dir.equals(directory))
                .forEach(projectDir -> {
                    GameInfo projectGame = detectProjectGame(projectDir);
                    if (projectGame != null) {
                        allGames.add(projectGame);
                    }
                });
                
        } catch (IOException e) {
            // Continue scanning other directories
        }
        return gameCount;
    }
    
    private GameInfo detectGame(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        String extension = getFileExtension(fileName).toLowerCase();
        
        // Java games
        if (extension.equals("jar") && isLikelyGame(fileName)) {
            return new GameInfo(
                getGameNameFromFile(file),
                "Java Game",
                file,
                "JAR executable game",
                true
            );
        }
        
        // Python games
        if (extension.equals("py") && isLikelyGame(fileName)) {
            return new GameInfo(
                getGameNameFromFile(file),
                "Python Game",
                file,
                "Python script game",
                true
            );
        }
        
        // JavaScript games
        if ((extension.equals("js") || extension.equals("html")) && isLikelyGame(fileName)) {
            return new GameInfo(
                getGameNameFromFile(file),
                "Web Game",
                file,
                "HTML/JavaScript game",
                true
            );
        }
        
        // Executable games
        if ((extension.equals("exe") || extension.equals("app") || extension.equals("")) && 
            (isLikelyGame(fileName) || isInGameDirectory(file))) {
            return new GameInfo(
                getGameNameFromFile(file),
                "Native Game",
                file,
                "Native executable game",
                true
            );
        }
        
        // Shell script games
        if ((extension.equals("sh") || extension.equals("bat")) && isLikelyGame(fileName)) {
            return new GameInfo(
                getGameNameFromFile(file),
                "Script Game",
                file,
                "Shell script game",
                true
            );
        }
        
        return null;
    }
    
    private GameInfo detectProjectGame(Path projectDir) {
        try {
            String projectName = projectDir.getFileName().toString().toLowerCase();
            
            // Check if project name suggests it's a game
            if (isLikelyGameProject(projectName)) {
                // Determine project type and how to run it
                if (Files.exists(projectDir.resolve("pom.xml"))) {
                    return new GameInfo(
                        getGameNameFromPath(projectDir),
                        "Maven Game Project",
                        projectDir,
                        "Maven-based Java game project",
                        true
                    );
                } else if (Files.exists(projectDir.resolve("build.gradle")) || 
                          Files.exists(projectDir.resolve("build.gradle.kts"))) {
                    return new GameInfo(
                        getGameNameFromPath(projectDir),
                        "Gradle Game Project", 
                        projectDir,
                        "Gradle-based Java game project",
                        true
                    );
                } else if (Files.exists(projectDir.resolve("package.json"))) {
                    return new GameInfo(
                        getGameNameFromPath(projectDir),
                        "Node.js Game Project",
                        projectDir,
                        "JavaScript/Node.js game project",
                        true
                    );
                } else if (hasJavaFiles(projectDir)) {
                    return new GameInfo(
                        getGameNameFromPath(projectDir),
                        "Java Game Project",
                        projectDir,
                        "Plain Java game project",
                        true
                    );
                } else if (hasPythonFiles(projectDir)) {
                    return new GameInfo(
                        getGameNameFromPath(projectDir),
                        "Python Game Project",
                        projectDir,
                        "Python game project",
                        true
                    );
                }
            }
            
            // Also check if it contains game-related files even if name doesn't suggest it
            if (containsGameFiles(projectDir)) {
                return new GameInfo(
                    getGameNameFromPath(projectDir),
                    "Game Project",
                    projectDir,
                    "Project containing game files",
                    true
                );
            }
            
        } catch (Exception e) {
            // Continue scanning
        }
        
        return null;
    }
    
    private boolean isLikelyGameProject(String projectName) {
        // More inclusive game detection
        String[] gameKeywords = {
            "game", "play", "puzzle", "arcade", "adventure", "action", "rpg", "strategy",
            "simulation", "racing", "sports", "shooter", "platformer", "tetris", "snake",
            "pong", "chess", "checkers", "solitaire", "poker", "blackjack", "mario",
            "zelda", "minecraft", "doom", "quake", "sim", "tycoon", "city", "farm",
            "defense", "tower", "match", "candy", "bird", "run", "jump", "fight",
            "battle", "war", "quest", "dungeon", "castle", "knight", "ninja", "pirate",
            "yooo", "fun", "entertainment", "toy", "mini", "simple", "classic"
        };
        
        for (String keyword : gameKeywords) {
            if (projectName.contains(keyword)) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean containsGameFiles(Path projectDir) {
        try {
            return Files.walk(projectDir, 3)
                .filter(Files::isRegularFile)
                .anyMatch(file -> {
                    String fileName = file.getFileName().toString().toLowerCase();
                    return fileName.contains("game") || fileName.contains("play") ||
                           fileName.contains("main") || fileName.contains("start") ||
                           fileName.contains("run") || fileName.contains("app");
                });
        } catch (IOException e) {
            return false;
        }
    }
    
    private boolean hasPythonFiles(Path projectDir) {
        try {
            return Files.walk(projectDir, 2)
                .anyMatch(p -> p.toString().endsWith(".py"));
        } catch (IOException e) {
            return false;
        }
    }
    
    private String getGameNameFromPath(Path path) {
        return getGameNameFromFile(path);
    }
    
    private boolean isLikelyGame(String fileName) {
        String[] gameKeywords = {
            "game", "play", "puzzle", "arcade", "adventure", "action", "rpg", "strategy",
            "simulation", "racing", "sports", "shooter", "platformer", "tetris", "snake",
            "pong", "chess", "checkers", "solitaire", "poker", "blackjack", "mario",
            "zelda", "minecraft", "doom", "quake", "sim", "tycoon", "city", "farm",
            "defense", "tower", "match", "candy", "bird", "run", "jump", "fight",
            "battle", "war", "quest", "dungeon", "castle", "knight", "ninja", "pirate"
        };
        
        for (String keyword : gameKeywords) {
            if (fileName.contains(keyword)) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean isInGameDirectory(Path file) {
        String parentDir = file.getParent().getFileName().toString().toLowerCase();
        return parentDir.contains("game") || parentDir.contains("play") || 
               parentDir.contains("arcade") || parentDir.contains("entertainment");
    }
    
    private String getGameNameFromFile(Path file) {
        String fileName = file.getFileName().toString();
        int lastDot = fileName.lastIndexOf('.');
        String baseName = lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
        
        // Capitalize first letter of each word
        String[] words = baseName.split("[-_\\s]+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
                result.append(" ");
            }
        }
        return result.toString().trim();
    }
    
    private void updateGamesTable() {
        gamesTableModel.setRowCount(0);
        for (GameInfo game : allGames) {
            gamesTableModel.addRow(new Object[]{
                game.getName(),
                game.getType(),
                game.isWorking() ? "Working" : "Unknown",
                game.getDescription(),
                game.getPath().toString()
            });
        }
    }
    
    private void launchSelectedGame() {
        int selectedRow = gamesTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(frame, "Please select a game to launch.", 
                "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        GameInfo game = allGames.get(selectedRow);
        launchGame(game);
    }
    
    private void launchGame(GameInfo game) {
        SwingWorker<Void, String> launcher = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    publish("Copying game to app directory...");
                    
                    // Create unique directory name for this game
                    String safeName = game.getName().replaceAll("[^a-zA-Z0-9\\s]", "").replaceAll("\\s+", "_");
                    Path gameAppDir = appGamesDirectory.resolve(safeName);
                    
                    // Copy the game to app directory
                    Path copiedGamePath = copyGameToAppDirectory(game, gameAppDir);
                    
                    publish("Starting game: " + game.getName());
                    
                    // Launch the copied game
                    launchCopiedGame(game, copiedGamePath);
                    
                } catch (Exception e) {
                    publish("Error: " + e.getMessage());
                    throw e;
                }
                return null;
            }
            
            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    statusLabel.setText(message);
                }
            }
            
            @Override
            protected void done() {
                try {
                    get(); // Check for exceptions
                    statusLabel.setText("Game launched successfully!");
                    JOptionPane.showMessageDialog(frame, "Game copied to app directory and launched: " + game.getName(), 
                        "Game Started", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    statusLabel.setText("Ready - " + currentPath.toString());
                    JOptionPane.showMessageDialog(frame, "Error launching game: " + e.getCause().getMessage(), 
                        "Launch Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        
        launcher.execute();
    }
    
    private Path copyGameToAppDirectory(GameInfo game, Path targetDir) throws IOException {
        // Remove existing copy if it exists
        if (Files.exists(targetDir)) {
            deleteDirectory(targetDir);
        }
        
        Files.createDirectories(targetDir);
        
        if (Files.isDirectory(game.getPath())) {
            // Copy entire project directory
            copyDirectory(game.getPath(), targetDir);
            return targetDir;
        } else {
            // Copy single file
            Path targetFile = targetDir.resolve(game.getPath().getFileName());
            Files.copy(game.getPath(), targetFile, StandardCopyOption.REPLACE_EXISTING);
            return targetFile;
        }
    }
    
    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source)
            .forEach(sourcePath -> {
                try {
                    Path targetPath = target.resolve(source.relativize(sourcePath));
                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
    }
    
    private void deleteDirectory(Path directory) throws IOException {
        Files.walk(directory)
            .sorted((a, b) -> b.compareTo(a)) // Reverse order to delete files before directories
            .forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
    }
    
    private void launchCopiedGame(GameInfo game, Path copiedPath) throws IOException {
        String gameType = game.getType();
        ProcessBuilder pb;
        
        // Handle project-based games
        if (gameType.contains("Project")) {
            if (gameType.contains("Maven")) {
                pb = new ProcessBuilder("mvn", "exec:java");
                pb.directory(copiedPath.toFile());
            } else if (gameType.contains("Gradle")) {
                pb = new ProcessBuilder("./gradlew", "run");
                pb.directory(copiedPath.toFile());
            } else if (gameType.contains("Node.js")) {
                pb = new ProcessBuilder("npm", "start");
                pb.directory(copiedPath.toFile());
            } else if (gameType.contains("Java")) {
                // Compile and run Java project from copied location
                executeJavaCompileAndRun(copiedPath, true);
                return;
            } else if (gameType.contains("Python")) {
                Path mainPy = findMainPythonFile(copiedPath);
                if (mainPy != null) {
                    pb = new ProcessBuilder("python", mainPy.toString());
                    pb.directory(copiedPath.toFile());
                } else {
                    throw new IOException("No main Python file found in copied game");
                }
            } else {
                throw new IOException("Unknown project type: " + gameType);
            }
        } else {
            // Handle individual files
            String extension = getFileExtension(copiedPath.getFileName().toString()).toLowerCase();
            
            switch (extension) {
                case "jar":
                    pb = new ProcessBuilder("java", "-jar", copiedPath.toString());
                    break;
                case "py":
                    pb = new ProcessBuilder("python", copiedPath.toString());
                    break;
                case "js":
                    pb = new ProcessBuilder("node", copiedPath.toString());
                    break;
                case "html":
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(copiedPath.toFile());
                        return;
                    }
                    pb = new ProcessBuilder("open", copiedPath.toString());
                    break;
                case "sh":
                    pb = new ProcessBuilder("bash", copiedPath.toString());
                    break;
                case "bat":
                    pb = new ProcessBuilder("cmd", "/c", copiedPath.toString());
                    break;
                default:
                    pb = new ProcessBuilder(copiedPath.toString());
                    break;
            }
            
            pb.directory(copiedPath.getParent().toFile());
        }
        
        pb.start();
    }
    
    private void launchJavaProject(Path projectPath) throws IOException {
        // Use the existing Java compilation and run functionality
        ProjectType projectType = detectProjectType(projectPath);
        if (projectType == ProjectType.JAVA_PLAIN) {
            executeJavaCompileAndRun(projectPath, true);
        } else {
            throw new IOException("Java project structure not supported for direct launch");
        }
    }
    
    private Path findMainPythonFile(Path projectPath) {
        try {
            // Look for common main file names
            String[] mainFileNames = {"main.py", "app.py", "game.py", "run.py", "start.py"};
            
            for (String fileName : mainFileNames) {
                Path mainFile = projectPath.resolve(fileName);
                if (Files.exists(mainFile)) {
                    return mainFile;
                }
            }
            
            // If no standard main file, find any Python file
            return Files.walk(projectPath, 2)
                .filter(p -> p.toString().endsWith(".py"))
                .findFirst()
                .orElse(null);
                
        } catch (IOException e) {
            return null;
        }
    }
    
    private void testSelectedGame() {
        int selectedRow = gamesTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(frame, "Please select a game to test.", 
                "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        GameInfo game = allGames.get(selectedRow);
        
        // Simple test - check if file exists and is executable
        boolean working = Files.exists(game.getPath()) && Files.isReadable(game.getPath());
        game.setWorking(working);
        
        gamesTableModel.setValueAt(working ? "Working" : "Broken", selectedRow, 2);
        
        String message = working ? 
            game.getName() + " appears to be working." :
            game.getName() + " may have issues (file not found or not readable).";
            
        JOptionPane.showMessageDialog(frame, message, "Game Test", 
            working ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
    }
    
    private void showGameInfo() {
        int selectedRow = gamesTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(frame, "Please select a game to view info.", 
                "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        GameInfo game = allGames.get(selectedRow);
        
        String info = "Game Name: " + game.getName() + "\n" +
                     "Type: " + game.getType() + "\n" +
                     "Path: " + game.getPath() + "\n" +
                     "Description: " + game.getDescription() + "\n" +
                     "Status: " + (game.isWorking() ? "Working" : "Unknown") + "\n" +
                     "File Size: " + formatFileSize(game.getPath().toFile().length());
                     
        JOptionPane.showMessageDialog(frame, info, "Game Information", 
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    private String findMainClass(Path sourcePath, List<Path> javaFiles) {
        for (Path javaFile : javaFiles) {
            try {
                String content = Files.readString(javaFile);
                if (content.contains("public static void main")) {
                    // Get the class name with package
                    String className = getFullClassName(sourcePath, javaFile, content);
                    return className;
                }
            } catch (IOException e) {
                // Continue searching
            }
        }
        return null;
    }
    
    private String getFullClassName(Path sourcePath, Path javaFile, String content) {
        String fileName = javaFile.getFileName().toString();
        String className = fileName.substring(0, fileName.lastIndexOf('.'));
        
        // Check if there's a package declaration
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("package ") && line.endsWith(";")) {
                String packageName = line.substring(8, line.length() - 1).trim();
                return packageName + "." + className;
            }
        }
        
        return className; // No package, just class name
    }
    
    private enum ProjectType {
        MAVEN("Maven Project"),
        GRADLE("Gradle Project"),
        NODEJS("Node.js Project"),
        PYTHON("Python Project"),
        RUST("Rust Project"),
        GO("Go Project"),
        C_CPP("C/C++ Project"),
        JAVA_PLAIN("Java Project"),
        UNKNOWN("Unknown Project");
        
        private final String displayName;
        
        ProjectType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    private void editSelectedFile() {
        int selectedRow = fileTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(frame, "Please select a file to edit.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String type = (String) tableModel.getValueAt(selectedRow, 1);
        if ("Folder".equals(type)) {
            JOptionPane.showMessageDialog(frame, "Cannot edit a folder.", "Invalid Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        Path filePath = getSelectedFilePath(selectedRow);
        new NanoEditor(filePath).setVisible(true);
    }
    
    private boolean isTextFile(String extension) {
        List<String> textExtensions = List.of("txt", "java", "py", "js", "html", "css", "xml", "json", 
            "md", "yml", "yaml", "ini", "cfg", "conf", "sh", "bat", "log", "csv", "sql", "c", "cpp", 
            "h", "hpp", "php", "rb", "go", "rs", "kt", "swift", "ts", "jsx", "tsx", "vue", "scss", 
            "less", "r", "m", "pl", "lua", "scala", "clj", "hs", "elm", "dart", "toml");
        return textExtensions.contains(extension);
    }
    
    private boolean isExecutableFile(String extension, File file) {
        List<String> executableExtensions = List.of("exe", "app", "dmg", "pkg", "deb", "rpm", "msi", 
            "run", "bin", "command", "jar", "war");
        return executableExtensions.contains(extension) || file.canExecute();
    }
    
    private boolean isImageFile(String extension) {
        List<String> imageExtensions = List.of("jpg", "jpeg", "png", "gif", "bmp", "svg", "webp", 
            "tiff", "tif", "ico", "raw", "heic", "avif");
        return imageExtensions.contains(extension);
    }
    
    private boolean isDocumentFile(String extension) {
        List<String> docExtensions = List.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", 
            "odt", "ods", "odp", "rtf", "pages", "numbers", "key");
        return docExtensions.contains(extension);
    }
    
    private void openWithSystemDefault(Path filePath) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(filePath.toFile());
            } else {
                String os = System.getProperty("os.name").toLowerCase();
                ProcessBuilder pb;
                if (os.contains("win")) {
                    pb = new ProcessBuilder("cmd", "/c", "start", "\"\"", filePath.toString());
                } else if (os.contains("mac")) {
                    pb = new ProcessBuilder("open", filePath.toString());
                } else {
                    pb = new ProcessBuilder("xdg-open", filePath.toString());
                }
                pb.start();
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Cannot open file with system default: " + e.getMessage(), 
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void openInSystemFileManager(Path folderPath) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(folderPath.toFile());
            } else {
                String os = System.getProperty("os.name").toLowerCase();
                ProcessBuilder pb;
                if (os.contains("win")) {
                    pb = new ProcessBuilder("explorer", folderPath.toString());
                } else if (os.contains("mac")) {
                    pb = new ProcessBuilder("open", folderPath.toString());
                } else {
                    pb = new ProcessBuilder("xdg-open", folderPath.toString());
                }
                pb.start();
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Cannot open folder in system file manager: " + e.getMessage(), 
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void executeFile(Path filePath) {
        try {
            String fileName = filePath.getFileName().toString();
            String extension = getFileExtension(fileName).toLowerCase();
            
            ProcessBuilder pb;
            String os = System.getProperty("os.name").toLowerCase();
            
            if (extension.equals("jar")) {
                pb = new ProcessBuilder("java", "-jar", filePath.toString());
            } else if (extension.equals("py")) {
                pb = new ProcessBuilder("python", filePath.toString());
            } else if (extension.equals("js")) {
                pb = new ProcessBuilder("node", filePath.toString());
            } else if (extension.equals("sh") && !os.contains("win")) {
                pb = new ProcessBuilder("bash", filePath.toString());
            } else if (extension.equals("bat") && os.contains("win")) {
                pb = new ProcessBuilder("cmd", "/c", filePath.toString());
            } else if (filePath.toFile().canExecute()) {
                if (os.contains("win")) {
                    pb = new ProcessBuilder(filePath.toString());
                } else {
                    pb = new ProcessBuilder(filePath.toString());
                }
            } else {
                JOptionPane.showMessageDialog(frame, "Don't know how to execute this file type.", 
                    "Execution Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            pb.directory(filePath.getParent().toFile());
            Process process = pb.start();
            
            JOptionPane.showMessageDialog(frame, "Started execution of: " + fileName, 
                "Execution Started", JOptionPane.INFORMATION_MESSAGE);
                
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Error executing file: " + e.getMessage(), 
                "Execution Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}

class GameInfo {
    private String name;
    private String type;
    private Path path;
    private String description;
    private boolean isWorking;
    
    public GameInfo(String name, String type, Path path, String description, boolean isWorking) {
        this.name = name;
        this.type = type;
        this.path = path;
        this.description = description;
        this.isWorking = isWorking;
    }
    
    public String getName() { return name; }
    public String getType() { return type; }
    public Path getPath() { return path; }
    public String getDescription() { return description; }
    public boolean isWorking() { return isWorking; }
    public void setWorking(boolean working) { this.isWorking = working; }
}

class NanoEditor extends JFrame {
    private JTextArea textArea;
    private Path filePath;
    private boolean isModified = false;
    private JLabel statusLabel;
    private JLabel positionLabel;
    
    public NanoEditor(Path filePath) {
        this.filePath = filePath;
        initializeEditor();
        loadFile();
    }
    
    private void initializeEditor() {
        setTitle("Nano Editor - " + filePath.getFileName());
        setSize(800, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        
        textArea = new JTextArea();
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setTabSize(4);
        textArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { setModified(true); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { setModified(true); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { setModified(true); }
        });
        
        textArea.addCaretListener(e -> updatePosition());
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        
        JPanel bottomPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel("File: " + filePath.toString());
        positionLabel = new JLabel("Line 1, Col 1");
        
        JPanel helpPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        helpPanel.add(new JLabel("^S Save  ^X Exit  ^O Save As"));
        
        bottomPanel.add(statusLabel, BorderLayout.WEST);
        bottomPanel.add(positionLabel, BorderLayout.CENTER);
        bottomPanel.add(helpPanel, BorderLayout.SOUTH);
        
        add(scrollPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
        
        setupKeyBindings();
        
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                exitEditor();
            }
        });
    }
    
    private void setupKeyBindings() {
        InputMap inputMap = textArea.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = textArea.getActionMap();
        
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK), "save");
        actionMap.put("save", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveFile();
            }
        });
        
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, KeyEvent.CTRL_DOWN_MASK), "exit");
        actionMap.put("exit", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                exitEditor();
            }
        });
        
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK), "saveas");
        actionMap.put("saveas", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveAsFile();
            }
        });
    }
    
    private void loadFile() {
        try {
            if (Files.exists(filePath)) {
                String content = Files.readString(filePath, StandardCharsets.UTF_8);
                textArea.setText(content);
                textArea.setCaretPosition(0);
                setModified(false);
            } else {
                textArea.setText("");
                setModified(false);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error loading file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void saveFile() {
        try {
            Files.write(filePath, textArea.getText().getBytes(StandardCharsets.UTF_8));
            setModified(false);
            statusLabel.setText("File saved: " + filePath.toString());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error saving file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void saveAsFile() {
        JFileChooser fileChooser = new JFileChooser(filePath.getParent().toFile());
        fileChooser.setSelectedFile(filePath.toFile());
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path newPath = fileChooser.getSelectedFile().toPath();
            try {
                Files.write(newPath, textArea.getText().getBytes(StandardCharsets.UTF_8));
                filePath = newPath;
                setTitle("Nano Editor - " + filePath.getFileName());
                setModified(false);
                statusLabel.setText("File saved as: " + filePath.toString());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Error saving file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void exitEditor() {
        if (isModified) {
            int result = JOptionPane.showConfirmDialog(this,
                "File has been modified. Save changes?",
                "Unsaved Changes",
                JOptionPane.YES_NO_CANCEL_OPTION);
            
            if (result == JOptionPane.YES_OPTION) {
                saveFile();
                dispose();
            } else if (result == JOptionPane.NO_OPTION) {
                dispose();
            }
        } else {
            dispose();
        }
    }
    
    private void setModified(boolean modified) {
        this.isModified = modified;
        String title = "Nano Editor - " + filePath.getFileName();
        if (modified) {
            title += " *";
        }
        setTitle(title);
    }
    
    private void updatePosition() {
        try {
            int caretPosition = textArea.getCaretPosition();
            int line = textArea.getLineOfOffset(caretPosition);
            int column = caretPosition - textArea.getLineStartOffset(line);
            positionLabel.setText("Line " + (line + 1) + ", Col " + (column + 1));
        } catch (Exception e) {
            positionLabel.setText("Line 1, Col 1");
        }
    }
}

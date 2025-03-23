import java.io.IOException;
import java.nio.file.*;

import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;
import java.util.*;

public class HotReload {
    private static final Long TIME_TO_COMPILE = 500L;
    private static final Map<Path, Long> lastTimeChanges = new HashMap<>();
    private String RESET = "\u001B[0m";
    private String RED = "\u001B[31m";
    private String GREEN = "\u001B[32m";
    private String YELLOW = "\u001B[33m";

    public static boolean isTimeToComple(Path file) {
        long currentTime = System.currentTimeMillis();
        long lastTimeChange = lastTimeChanges.getOrDefault(file, 0L);
        
        if (currentTime - lastTimeChange >= TIME_TO_COMPILE) {
            lastTimeChanges.put(file, currentTime);
            return true;
        }
        return false;
    }

    public static void registerAll(WatchService watchService,
            Map<WatchKey, Path> keyMap, Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
           public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
               WatchKey key = dir.register(watchService,
                       StandardWatchEventKinds.ENTRY_CREATE,
                       StandardWatchEventKinds.ENTRY_MODIFY);
               keyMap.put(key, dir);
               System.out.println(YELLOW + "[INFO]:" + RESET + "📂 Watching directory: " + dir);

               return FileVisitResult.CONTINUE;
           } 
        });
    }

    public static void compileProgram() {
        try {
            System.out.println(RED + "[INFO]:" + RESET + "⚡ Running maven compiler...");        
            Process process = new ProcessBuilder("mvn", "compile", "-T", "1C")
                .inheritIO()
                .start();
                process.waitFor();
                System.out.println(GREEN + "[INFO]:" + RESET + "Compilation completed, waiting for new changes!! ");         

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new Exception("Please provide path for scanning");
        }

        Path watchingPath = Paths.get(args[0]);

        try(WatchService watchService = FileSystems.getDefault().newWatchService()) {
            Map<WatchKey, Path> keyMap = new HashMap<>();
            registerAll(watchService, keyMap, watchingPath);

            System.out.println(YELLOW+ "[INFO]:" + RESET + " Scanning " + watchingPath.toString() + " for file changes");

            while (true) {
                WatchKey key = watchService.poll(10, TimeUnit.SECONDS);
                Path dir = keyMap.get(key);
                if (dir != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();
                        Path changedFile = dir.resolve((Path) event.context()); 

                        if (kind == StandardWatchEventKinds.ENTRY_MODIFY && changedFile.toString().endsWith(".java")) {
                            if (isTimeToComple(changedFile)) {
                                System.out.println((YELLOW + "[INFO]:" + RESET + "File changed " + event.context());            
                                compileProgram();
                            }
                        }
                        else if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                            if (Files.isDirectory(changedFile)) {
                                System.out.println(YELLOW + "[INFO]:" + RESET + "📂 Created new directory : " + changedFile);
                                registerAll(watchService, keyMap, changedFile);
                            }
                        }
                    }
                    key.reset();
                }
            }
        } catch(IOException | InterruptedException e) {
            e.printStackTrace();
        }


    }
}

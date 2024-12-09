import java.io.File;
import java.util.concurrent.*;
import java.util.Scanner;

public class FileSearch {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // Введення даних користувачем
        System.out.print("Enter directory path: ");
        String directoryPath = scanner.nextLine();
        System.out.print("Enter word or letter to search in filenames: ");
        String searchTerm = scanner.nextLine();

        // Пошук файлів через Work Stealing
        long startForkJoin = System.currentTimeMillis();
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        FileCountTask task = new FileCountTask(new File(directoryPath), searchTerm);
        int resultForkJoin = forkJoinPool.invoke(task);
        long endForkJoin = System.currentTimeMillis();
        System.out.println("Work Stealing (Fork/Join Framework): Found " + resultForkJoin + " files.");
        System.out.println("Execution time: " + (endForkJoin - startForkJoin) + " ms");

        // Пошук файлів через Work Dealing
        long startExecutorService = System.currentTimeMillis();
        ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        FileCounter fileCounter = new FileCounter(executorService, searchTerm);
        int resultExecutorService = fileCounter.countFiles(new File(directoryPath));
        long endExecutorService = System.currentTimeMillis();
        executorService.shutdown();

        System.out.println("Work Dealing (Executor Service): Found " + resultExecutorService + " files.");
        System.out.println("Execution time: " + (endExecutorService - startExecutorService) + " ms");
    }

    // Пошук файлів через Fork/Join Framework
    static class FileCountTask extends RecursiveTask<Integer> {
        private final File directory;
        private final String searchTerm;

        public FileCountTask(File directory, String searchTerm) {
            this.directory = directory;
            this.searchTerm = searchTerm;
        }

        @Override
        protected Integer compute() {
            File[] files = directory.listFiles();
            if (files == null) return 0;

            int count = 0;
            for (File file : files) {
                if (file.isDirectory()) {
                    FileCountTask subTask = new FileCountTask(file, searchTerm);
                    subTask.fork(); // Розподіл підзадач
                    count += subTask.join(); // Очікування результату
                } else if (file.getName().contains(searchTerm)) {
                    count++;
                }
            }
            return count;
        }
    }

    // Пошук файлів через Executor Service
    static class FileCounter {
        private final ExecutorService executor;
        private final String searchTerm;

        public FileCounter(ExecutorService executor, String searchTerm) {
            this.executor = executor;
            this.searchTerm = searchTerm;
        }

        public int countFiles(File directory) {
            CountDownLatch latch = new CountDownLatch(1);
            ResultWrapper result = new ResultWrapper();

            executor.execute(() -> searchFiles(directory, latch, result));

            try {
                latch.await(); // Очікування завершення завдання
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return result.count;
        }

        private void searchFiles(File directory, CountDownLatch latch, ResultWrapper result) {
            File[] files = directory.listFiles();
            if (files == null) {
                latch.countDown();
                return;
            }

            CountDownLatch subTaskLatch = new CountDownLatch(files.length);

            for (File file : files) {
                executor.execute(() -> {
                    if (file.isDirectory()) {
                        searchFiles(file, subTaskLatch, result);
                    } else if (file.getName().contains(searchTerm)) {
                        synchronized (result) {
                            result.count++;
                        }
                    }
                    subTaskLatch.countDown();
                });
            }

            try {
                subTaskLatch.await(); // Очікування завершення підзадач
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            latch.countDown();
        }

        // Обгортка для синхронізації результату
        static class ResultWrapper {
            int count = 0;
        }
    }
}

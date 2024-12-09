import java.util.Random;
import java.util.concurrent.*;

public class MatrixMultiplication {

    public static void main(String[] args) {
        int rows = 5; // Задайте кількість рядків
        int cols = 5; // Задайте кількість стовпців
        int[][] matrixA = generateMatrix(rows, cols, 1, 10);
        int[][] matrixB = generateMatrix(cols, rows, 1, 10);

        System.out.println("Matrix A:");
        printMatrix(matrixA);
        System.out.println("\nMatrix B:");
        printMatrix(matrixB);

        // Work stealing approach
        long startWorkStealing = System.currentTimeMillis();
        int[][] resultWorkStealing = multiplyMatrixWorkStealing(matrixA, matrixB);
        long endWorkStealing = System.currentTimeMillis();

        System.out.println("\nResult Work Stealing:");
        printMatrix(resultWorkStealing);
        System.out.println("Time Work Stealing: " + (endWorkStealing - startWorkStealing) + " ms");

        // Work dealing approach
        long startWorkDealing = System.currentTimeMillis();
        int[][] resultWorkDealing = multiplyMatrixWorkDealing(matrixA, matrixB);
        long endWorkDealing = System.currentTimeMillis();

        System.out.println("\nResult Work Dealing:");
        printMatrix(resultWorkDealing);
        System.out.println("Time Work Dealing: " + (endWorkDealing - startWorkDealing) + " ms");
    }

    // Генерація матриці
    private static int[][] generateMatrix(int rows, int cols, int min, int max) {
        Random random = new Random();
        int[][] matrix = new int[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                matrix[i][j] = random.nextInt(max - min + 1) + min;
            }
        }
        return matrix;
    }

    // Паралельне множення матриць через Work Stealing
    private static int[][] multiplyMatrixWorkStealing(int[][] matrixA, int[][] matrixB) {
        int rows = matrixA.length;
        int cols = matrixB[0].length;
        int[][] result = new int[rows][cols];

        ForkJoinPool pool = new ForkJoinPool();
        pool.invoke(new MatrixMultiplicationTask(matrixA, matrixB, result, 0, rows));
        return result;
    }

    // Паралельне множення матриць через Work Dealing
    private static int[][] multiplyMatrixWorkDealing(int[][] matrixA, int[][] matrixB) {
        int rows = matrixA.length;
        int cols = matrixB[0].length;
        int[][] result = new int[rows][cols];

        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        CountDownLatch latch = new CountDownLatch(rows);

        for (int i = 0; i < rows; i++) {
            int row = i;
            executor.execute(() -> {
                for (int j = 0; j < cols; j++) {
                    result[row][j] = 0;
                    for (int k = 0; k < matrixA[0].length; k++) {
                        result[row][j] += matrixA[row][k] * matrixB[k][j];
                    }
                }
                latch.countDown();
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        executor.shutdown();
        return result;
    }

    // Виведення матриці
    private static void printMatrix(int[][] matrix) {
        for (int[] row : matrix) {
            for (int element : row) {
                System.out.print(element + "\t");
            }
            System.out.println();
        }
    }

    // Завдання Fork/Join для Work Stealing
    static class MatrixMultiplicationTask extends RecursiveAction {
        private static final int THRESHOLD = 50;
        private final int[][] matrixA;
        private final int[][] matrixB;
        private final int[][] result;
        private final int startRow;
        private final int endRow;

        public MatrixMultiplicationTask(int[][] matrixA, int[][] matrixB, int[][] result, int startRow, int endRow) {
            this.matrixA = matrixA;
            this.matrixB = matrixB;
            this.result = result;
            this.startRow = startRow;
            this.endRow = endRow;
        }

        @Override
        protected void compute() {
            int rowCount = endRow - startRow;
            if (rowCount <= THRESHOLD) {
                for (int i = startRow; i < endRow; i++) {
                    for (int j = 0; j < matrixB[0].length; j++) {
                        result[i][j] = 0;
                        for (int k = 0; k < matrixA[0].length; k++) {
                            result[i][j] += matrixA[i][k] * matrixB[k][j];
                        }
                    }
                }
            } else {
                int mid = (startRow + endRow) / 2;
                MatrixMultiplicationTask task1 = new MatrixMultiplicationTask(matrixA, matrixB, result, startRow, mid);
                MatrixMultiplicationTask task2 = new MatrixMultiplicationTask(matrixA, matrixB, result, mid, endRow);
                invokeAll(task1, task2);
            }
        }
    }
}

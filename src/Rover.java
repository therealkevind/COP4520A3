import java.util.Arrays;
import java.util.concurrent.atomic.AtomicIntegerArray;

public class Rover {
  /**
   * An array treated as 2D, specifically of dimension {@link #numHours} by {@link #numThreads}, in reading order.
   */
  private final AtomicIntegerArray memory;
  private final int numThreads;
  private static final int SENTINEL = Integer.MIN_VALUE;
  private final TemperatureSensor[] sensors;
  private int numHours;
  private long tempInterval, startNanos;

  public Rover(TemperatureReader[] tempReaders) {
    numThreads = tempReaders.length;
    memory = new AtomicIntegerArray(60 * numThreads);
    for (int i = 0; i < memory.length(); i++) memory.set(i, SENTINEL);
    sensors = new TemperatureSensor[numThreads];
    Arrays.setAll(sensors, i -> new TemperatureSensor(i, tempReaders[i]));
  }

  public void simulate(int numHours, long tempInterval) {
    this.tempInterval = tempInterval;
    this.numHours = numHours;
    startNanos = System.nanoTime();
    for (TemperatureSensor sensor : sensors) {
      sensor.start();
    }
    for (TemperatureSensor sensor : sensors) {
      try {
        sensor.join();
      } catch (InterruptedException e) {
        e.printStackTrace();
        System.exit(-1);
      }
    }
  }

  public class TemperatureSensor extends Thread {
    /**
     * Indicates which indices of {@link #memory} this thread writes to.
     * If {@code 0}, this thread's also in charge of summarization.
     */
    final int threadIndex;
    /** Elapsed time in minutes. */
    int time;
    final TemperatureReader reader;

    public TemperatureSensor(int threadIndex, TemperatureReader reader) {
      this.threadIndex = threadIndex;
      this.reader = reader;
    }

    @Override
    public void run() {
      while (time < numHours * 60) {
        recordReading();
        if (threadIndex == 0) {
          switch (time % 60) {
            case 0: initSummary(); break;
            default: updateSummary(); break;
            case 59: completeSummary(); break;
          }
        }
        time++;
        final long sleepNanos = startNanos + tempInterval * time - System.nanoTime();
        try {
          sleep(sleepNanos / 1000000, (int)(sleepNanos % 1000000));
        } catch (InterruptedException e) {
          e.printStackTrace();
          System.exit(-1);
        } catch (IllegalArgumentException e) {
          System.out.println("Thread " + threadIndex + " missed the temperature reading interval by " + -sleepNanos + "ns!");
          System.exit(-1);
        }
      }
    }

    public void recordReading() {
      while (!memory.compareAndSet(threadIndex + (time % 60) * numThreads, SENTINEL, reader.read()))
        Thread.yield();
    }

    private int[] maxs = new int[5], mins = new int[5], max10m = new int[10], min10m = new int[10];
    private int largestDifference, largestDifferenceEnd;
    /**
     * Initializes summary variables. Called at minute 0.
     */
    private void initSummary() {
      // other variables' values are ignored until after they've been written to
      largestDifference = Integer.MIN_VALUE;
    }
    /**
     * Updates the summary with the previous minute's data. Called at minutes 1-58 (inclusive).
     */
    private void updateSummary() {
      updateSummary((time - 1) % 60);
    }
    /**
     * Updates the summary with the given minute's data.
     * @param minute The minute, from 0 to 59 (inclusive).
     */
    private void updateSummary(int minute) {
      max10m[minute % 10] = Integer.MIN_VALUE;
      min10m[minute % 10] = Integer.MAX_VALUE;
      for (int thread = 0; thread < numThreads; thread++) {
        final int i = minute*numThreads + thread,
          reading = getAndReset(i);
        // place in maxs and mins if applicable
        if (i < 5) maxs[i] = mins[i] = reading;
        else for (int j = 0, maxr = reading, minr = reading; j < 5; j++) {
          if (maxs[j] < maxr) {
            final int tmp = maxs[j];
            maxs[j] = maxr;
            maxr = tmp;
          }
          if (mins[j] > minr) {
            final int tmp = mins[j];
            mins[j] = minr;
            minr = tmp;
          }
        }
        // find the extrema for this minute
        if (max10m[minute % 10] < reading) max10m[minute % 10] = reading;
        if (min10m[minute % 10] > reading) min10m[minute % 10] = reading;
      }
      // if ten minutes have elapsed, extrema are available for the past 10m and we can check the extrema for differences
      if (minute >= 10) {
        for (int maxm : max10m) {
          for (int minm : min10m) {
            if (largestDifference < maxm - minm) {
              largestDifference = maxm - minm;
              largestDifferenceEnd = minute;
            }
          }
        }
      }
    }
    /**
     * Completes and prints the summary. Called at minute 59.
     */
    private void completeSummary() {
      updateSummary(58);
      updateSummary(59);
      System.out.printf("Hour %d:\n"
        + "- Highest 5 temperatures: %d, %d, %d, %d, %d\n"
        + "- Lowest 5 temperatures: %d, %d, %d, %d, %d\n"
        + "- Largest temperature difference: %d, within minutes %02d to %02d (inclusive)\n",
        (time / 60), maxs[0], maxs[1], maxs[2], maxs[3], maxs[4], mins[0], mins[1], mins[2], mins[3], mins[4],
        largestDifference, largestDifferenceEnd-9, largestDifferenceEnd);
    }
    /**
     * Waits until the value in {@link #memory} at the given index isn't {@link #SENTINEL}, and returns that value while resetting it to SENTINEL.
     * @param i The index in shared memory.
     * @return The non-SENTINEL value at that index.
     */
    private int getAndReset(int i) {
      int reading;
      while ((reading = memory.getAndSet(i, SENTINEL)) == SENTINEL) Thread.yield();
      return reading;
    }
  }
  @FunctionalInterface
  interface TemperatureReader {
    int read();
  }
}

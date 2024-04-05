import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class App {
  public static void main(String[] args) {
    switch (args[0]) {
      case "1": {
        final Random random = new Random();
        final int numPresents = args.length > 1 ? parseInt(args[1]) : 500000,
          threadCount = args.length > 2 ? parseInt(args[2]) : 4;
        final long askInterval = args.length > 3 ? parseLong(args[3]) : 0;
        if (args.length > 4) random.setSeed(parseLong(args[4]));
        final List<ThankYou.Present> presentsList = IntStream.range(0, numPresents)
          .mapToObj(ThankYou.Present::new).collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(presentsList, random);
        final ConcurrentLinkedQueue<ThankYou.Present> bag = new ConcurrentLinkedQueue<>(presentsList);
        final ThankYou.Servant[] servants = new Servant[threadCount];
        Arrays.setAll(servants, i -> new Servant());
        ThankYou.simulate(bag, servants, () -> random.nextInt(numPresents), askInterval);
      } break;
      case "2": {
        final int numHours = parseInt(args[1]),
          numThreads = args.length > 2 ? parseInt(args[2]) : 8,
          interval = args.length > 3 ? parseInt(args[3]) : 50,
          minTemp = args.length > 5 ? parseInt(args[4]) : -100,
          maxTemp = args.length > 5 ? parseInt(args[5]) : 70,
          tempRange = maxTemp - minTemp + 1;
        int seedIndex = -1;
        if (args.length == 5) seedIndex = 4;
        else if (args.length > 6) seedIndex = 6;
        final SplittableRandom random = seedIndex > -1 ? new SplittableRandom(parseLong(args[seedIndex])) : new SplittableRandom();
        final Rover.TemperatureReader[] tempReaders = new Rover.TemperatureReader[numThreads];
        Arrays.setAll(tempReaders, i -> {
          final SplittableRandom rand = random.split();
          return () -> rand.nextInt(tempRange) + minTemp;
        });
        new Rover(tempReaders).simulate(numHours, interval * 1000000L);
      } break;
      default:
        System.err.println("Expected \"1\" or \"2\", got \"" + args[0] + "\".");
        System.exit(1);
    }
  }
  
  private static int parseInt(String arg) {
    int x = 0;
    try {
      x = Integer.parseInt(arg);
    } catch (NumberFormatException e) {}
    if (x <= 0) {
      System.err.println("Expected a positive integer, got \"" + arg + "\".");
      System.exit(1);
    }
    return x;
  }

  private static long parseLong(String arg) {
    try {
      return Long.parseLong(arg);
    } catch (NumberFormatException e) {
      System.err.println("Expected a long integer, got \"" + arg + "\".");
      System.exit(1);
      return 0;
    }
  }
}

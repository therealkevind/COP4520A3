import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntSupplier;

public final class ThankYou {
  private ThankYou() {}

  public static void simulate(ConcurrentLinkedQueue<ThankYou.Present> bag, Servant[] servants, IntSupplier askSupplier, long askInterval) {
    final Chain chain = new Chain();
    final long startNanos = System.nanoTime(), endNanos;
    if (askInterval > 0) servants[0].asks.add(askSupplier.getAsInt());
    for (Servant servant : servants) {
      servant.bag = bag;
      servant.chain = chain;
      servant.isWorking = true;
      servant.start();
    }
    int totalThankYous = 0;
    for (Servant servant : servants) {
      try {
        while (true) {
          servant.join(askInterval);
          if (servant.isWorking) servant.asks.add(askSupplier.getAsInt());
          else break;
        }
      } catch (InterruptedException e) {
        e.printStackTrace();
        System.exit(-1);
      }
      totalThankYous += servant.numThankYous;
    }
    endNanos = System.nanoTime();
    final int bagSize = bag.size();
    int chainLength = -2;  // account for the two sentinel nodes
    for (Present present = chain.getHead(); present != null; present = present.getNext()) chainLength++;
    if (bagSize > 0) System.out.println("Oops! " + bagSize + " present(s) were left in the bag!");
    if (chainLength != 0) System.out.println("Oops! " + chainLength + " present(s) were left on the chain!");
    System.out.printf("A total of %d thank you note%s written in %.2fms!\n", totalThankYous, totalThankYous == 1 ? " was" : "s were", (endNanos - startNanos)/1e6);
  }

  public static class Chain {
    private Present head;
    public Chain() {
      // sentinel nodes
      head = new Present(Integer.MIN_VALUE);
      head.setNext(new Present(Integer.MAX_VALUE));
    }
    Present getHead() {
      return head;
    }
  }
  public static class Present {
    private final int tag;
    private final Lock lock = new ReentrantLock();
    private Present next;

    public Present(int tag) {
      this.tag = tag;
    }

    int getTag() {
      return tag;
    }
    Present getNext() {
      return next;
    }
    void setNext(Present next) {
      this.next = next;
    }
    void lock() {
      lock.lock();
    }
    void unlock() {
      lock.unlock();
    }
  }
  public static abstract class Servant extends Thread {
    private ConcurrentLinkedQueue<Present> bag;
    private ConcurrentLinkedQueue<Integer> asks = new ConcurrentLinkedQueue<>();
    private Chain chain;
    private int numThankYous;
    private volatile boolean isWorking = false;

    /**
     * Alternates between adding presents from the bag to the chain and removing presents from the chain until the bag's empty.
     */
    @Override
    public void run() {
      while (true) {
        final Present addedPresent = bag.poll();
        if (addedPresent == null) break;
        addPresent(chain, addedPresent);
        final Integer ask = asks.poll();
        if (ask != null) checkTag(ask);
        removeSomePresent(chain);
        numThankYous++;
      }
      isWorking = false;
      while (!asks.isEmpty()) checkTag(asks.poll());
    }

    private void checkTag(int tag) {
      System.out.println("The present with tag " + tag + (hasTag(chain, tag) ? " is" : " is not") + " in the chain.");
    }

    /**
     * Adds a present to the chain, in the correct place to keep the chain in sorted order.
     * <p>Any presents locked in this procedure must be unlocked upon returning.
     * @param chain The chain to add to.
     * @param present The present to add.
     */
    public abstract void addPresent(Chain chain, Present present);
    /**
     * Removes any present from the chain.
     * <p>Any presents locked in this procedure must be unlocked upon returning.
     * @param chain The chain to remove from.
     * @return The present that was removed.
     */
    public abstract Present removeSomePresent(Chain chain);
    /**
     * Checks if the present with the given tag can be found within the chain.
     * <p>Any presents locked in this procedure must be unlocked upon returning.
     * @param chain The chain to find the tag within.
     * @param tag The tag to search for.
     */
    public abstract boolean hasTag(Chain chain, int tag);
  }
}

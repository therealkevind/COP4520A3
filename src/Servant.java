public class Servant extends ThankYou.Servant {
  @Override
  public void addPresent(ThankYou.Chain chain, ThankYou.Present present) {
    final ThankYou.Present prev = findPrev(chain, present.getTag()),
      next = prev.getNext();
    prev.setNext(present);
    present.setNext(next);
    next.unlock();
    prev.unlock();
  }

  @Override
  public ThankYou.Present removeSomePresent(ThankYou.Chain chain) {
    chain.getHead().lock();
    ThankYou.Present prev = chain.getHead(), curr = prev.getNext();
    curr.lock();
    try {
      prev.setNext(curr.getNext());
      curr.setNext(null);  // safety first
      return curr;
    } finally {
      curr.unlock();
      prev.unlock();
    }
  }

  /**
   * Finds the {@link ThankYou.Present Present} that precedes the given tag, by traversing hand-over-hand.
   * <p>Upon return, the returned present and its successor are locked, and must be unlocked by the caller.
   * @param chain The chain of presents to search.
   * @param tag The tag to search for.
   * @return The present preceding it.
   */
  private ThankYou.Present findPrev(ThankYou.Chain chain, int tag) {
    ThankYou.Present prev = chain.getHead(), curr;
    prev.lock();
    curr = prev.getNext();
    curr.lock();
    while (curr.getTag() < tag) {
      prev.unlock();
      prev = curr;
      curr = curr.getNext();
      curr.lock();
    }
    return prev;
  }

  @Override
  public boolean hasTag(ThankYou.Chain chain, int tag) {
    final ThankYou.Present prev = findPrev(chain, tag),
      curr = prev.getNext();
    try {
      return curr.getTag() == tag;
    } finally {
      curr.unlock();
      prev.unlock();
    }
  }
}

# Compiling and running
To compile this project, first ensure JDK 8 or later is installed. Then, open a terminal in this directory, and execute:
```sh
javac -d ./bin/ ./src/*.java
```

This program has two sets of parameters, one per problem.

For problem 1, run:
```sh
java -cp ./bin App 1 [numPresents [threadCount [askInterval [seed]]]]
```
where:
- `numPresents` is the number of presents - default `500000`
- `threadCount` is the number of servants - default `4`
- `askInterval` is the (approximate) interval at which the minotaur will ask whether a tag is present in the chain, in milliseconds, or zero to never ask - default `0`
- `seed` is a random seed to use, a `long` integer

Note that, even when supplying `seed`, a small degree of randomness can result from execution speed.

For problem 2, run:
```sh
java -cp ./bin App 2 numHours [numThreads [interval [minTemp maxTemp] [seed]]]]
```
where:
- `numHours` is the number of hours to simulate
- `numThreads` is the number of sensors to simulate - default `8`
- `interval` is the number of realtime milliseconds for each simulated minute - default `50`
- `minTemp` and `maxTemp` are the (inclusive) range of temperatures to generate - defaults `-100` and `70`, respectively
- `seed` is a random seed to use, a `long` integer

Decreasing `interval` too far may cause the threads to miss temperature readings, at which point they fail. I found that `20` consistently worked, but this may vary with your system's performance.

# Problem 1
## Why the original strategy failed
The servants can only have more presents than "thank you" notes if there's a race condition. (This implies that they somehow fail to notice that they're stepping on each other's toes, so to speak.)

There's a couple possible race conditions when they're adding presents to the chain:

Suppose that the chain of presents contains presents `1` and `4`, and suppose servants A and B grab presents `2` and `3` from the bag, respectively.
- Then, suppose A and B both notice the previous present in the chain, before the link where theirs belongs, is `1`. If A hooks their `2` to the `1` first, B hooking their `3` to the `1` will cause A's `2` to be unhooked from it, since only one thing can be linked on each side of a present (otherwise it's not a chain).
- Suppose instead that A hooks their `2` first, and then B notices the previous present before their `3` is that `2` while A notices the next present after their `2` is the `4`. If B hooks their `3` with the `2` before A hooks their `2` with the `4`, the `3` gets unhooked. (And then, because hooks are two-way connections unlike the one-way links of a digital linked list, if B hooks the `3` with the `4` after A finishes, the *entire chain starting from the `4` is detached from the `1` and `2`*.)

Another presents itself when they're writing the "thank you" notes and taking the presents out. 

Suppose the chain contains presents `1`, `2`, `3`, and `4`, and servants A and B are writing notes for `2` and `3`, respectively. Then suppose A unlinks their `2` from present `1`, and tries to link that `1` to `3` while B unlinks their `3` from present `2`. This works without conflict, but results in two things happening:
- `1` is now connected to `3`, despite `3` already having a "thank you" note written for it.
- Immediately afterwards, B unlinks their `3` from present `4`, so the entire chain starting from the `4` is again detached from the `1`.

Notice that it's *possible* for this to result in a net gain of one extra "thank you note" being written, if `3` is the last present in the chain and there is no `4`. This evidently didn't happen often enough to offset all the lost presents.

## A modification
This only needs a slight modification to work properly. Such a slight modification that, as written, it's actually implied already:

*The servants look at what they're doing.* (And a little around it too.)

Clearly, though, this is a problem for them. We all have our flaws. Instead, they'll just have to use some **locks**, to pretend they're doing that. These locks don't inherently *secure* the links to prevent them from coming apart; instead, they're used to represent "what the servant is looking at". (They probably don't have a pile of locks handy, though. As a substitute, their hands will do just fine, if the links in the chain are short enough that their hands will cover the entire link: to "lock" a link, they can pick it up and hold onto it once no one else has it, and to "unlock" it they can just put it back down. Adding and removing gifts might be tricky with both hands occupied by links, but I'm sure they can manage.)

Specifically, this turns into the *fine-grained lock* strategy:
- To find a particular gift, or the place where a gift belongs, a servant locks the first link, and scans through by locking the next link and *only then* unlocking the link they just had.
- To add a gift, when the servant has two (adjacent) links held, if the gift in between them is the first one they've seen that comes after the gift they're adding, hook up the gift at the first of the two held links, then unlock both. (While unhooking the link, its lock should remain on the gift prior to it.)
  - If the gift turns out to belong at the end, the servant will end up holding the last link with no gift attached on one end, and just needs to hook it up before unlocking the link.
- To remove a gift, when the servant has two (adjacent) links held, if the gift to remove is in between, unlink both and connect the gifts on either end before unlocking the links.

Note that with 4 servants and each alternating between adding and removing gifts, the chain is at most 4 presents long at any given point, so a more sophisticated locking strategy is unimportant; the chain's *going* to have a lot of contention.

## Notes about the output
It's extremely unlikely that, at any point, any particular present will be in the chain; again, it's only at most 4 presents long.

However, I've done a couple things to increase the probability that the Minotaur's question, of whether a gift with a certain tag is in the chain, is answered affirmatively:
- The first question is asked at the very beginning, without waiting `askInterval` milliseconds, if asks are enabled.
- The question is only answered either just after the Servant has added a gift, meaning the chain's at least one present long, or as the thread's completing if some questions got missed.

This means that, for instance, the following output is nearly guaranteed with the shown parameters:
```
> java -cp ./bin App 1 1 1 1000
The present with tag 0 is in the chain.
A total of 1 thank you note was written!
```
(I say "nearly" because there is an extremely unlikely chance that the Minotaur gets a second question in before the servant finishes executing. If this happens, this will produce lines saying `The present with tag 0 is not in the chain.` between the two lines of normal output.)


# Problem 2
## The requirements, and considerations
The eight threads need to
- record the temperatures in shared memory
- somehow cooperate to summarize the readings after every batch of 60.

Note that we're asked to use 8 threads, so the temperature reading module can't have a separate thread specifically for summarization. Also notice that the batch needs to be *complete* before the summary is taken; all eight threads must have finished their readings before those values are used.

For efficiency reasons, only one thread should be finding any given part of the summary; while the summary is deterministic (so multiple threads would get the same values), we don't want them needlessly repeating any work. Otherwise, we don't need to worry much about distribution of work: after a thread's reading is taken, in theory there's nearly a full minute before the next is taken, which should be more than enough time to read and summarize all the shared memory.  
In practice, this means it's best for a single thread to produce the entire summary. Each thread working on the summary needs to read from shared memory, so multiple threads, even if they're working on different aspects of the summary, will be in contention with one another for reading parts of the memory. Not to mention that they'll want to set flags or sentinel values that signal that the memory's been read already and can safely be overwritten, which becomes complicated when there's multiple threads that need to finish reading.

It's possible to spread out a lot of the summarization throughout the hour, though...

## The strategy
Before readings begin, the shared memory is entirely initialized to `SENTINEL`, a temperature value that is never generated (and is practically impossible in real life, being below absolute zero).

Every simulated minute (where `minute` is from `0` to `59`), the following happens:
- Thread `i` writes to `i + numThreads * minute`, so that `numThreads * minute` (inclusive) to `numThreads * (minute + 1)` (exclusive) are written to.  
  They use a `compareAndSet` loop to ensure the memory being overwritten is `SENTINEL`, so that the previous data (if any) was already read.
- Thread `0`, the designated summarizer, updates the summary. For improved efficiency per minute, it does so throughout the hour:
  - At minute `0`, it initializes the summary.
  - At minutes `1` to `58`, it updates the summary with the data from the *previous* minute, which is complete by now. It also resets that data to `SENTINEL`.
  - At minute `59`, it updates the summary with data from minutes `58` and `59`, and prints the summary. It also resets that data to `SENTINEL`.
- After the thread's done, it then checks the time and waits for the next minute.

When thread `0` is reading data, it uses a `getAndSet` loop to ensure the data it's reading isn't `SENTINEL`, so that it's actually a temperature reading. This also makes the index available for the next time it's written to.

Clearly, everything is *safe*: the output is *correct*. Progress is also guaranteed because the only time a thread is waiting on another is at minute `59`, if thread `0` has to wait for the other sensors to record their readings, and they're able to do so because the current value is `SENTINEL` (because that's why thread `0`'s waiting). So, no deadlocks.

That is, the threads are guaranteed to finish their minutely work *eventually*. It's obviously not 100% guaranteed that the threads finish *in time for the next reading*, and there's no way to do so without introducing the possibility of cutting them off in the middle of their individual work, but the distribution of work over time makes finishing on time as likely as reasonably possible.

## Notes
The implementation uses `SplittableRandom`s for improved concurrency (and predictable readings across threads, if seeded). Note that this doesn't affect problem 1 because only the Minotaur (the main thread) uses randomness.

It might not be obvious how `updateSummary` works.
- Maxima:
  - The first five readings (i.e., the readings at minute 0 from threads 0-4 inclusive; this changes if there are fewer than five threads) are used to initialize `maxs` at the top of the hour.
  - Every reading thereafter is compared against each entry in `maxs`. If it's greater, it inserts the reading in that index, and takes the entry it replaced. This entry might be greater than another entry in `maxs`, so it continues looking and inserts that in an index it's greater than, and so on until the end of the array is reached.

  Note that the ordering of the maxima in the final array is not guaranteed: they might be out of order if enough were from the first five readings. This is okay, though; their order doesn't matter as long as they're the maximum five.
- Minima work the same way, but with inequalities reversed.
- Temperature differences:
  - The extrema for each minute are found and placed in `max10m` and `min10m`, at index `minute % 10`.
  - Once `minute >= 10`, both extrema arrays have been fully initialized with data from this hour, so it begins looking for the maximum difference and updating `largestDifference` and `largestDifferenceEnd` accordingly.

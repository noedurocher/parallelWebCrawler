package com.udacity.webcrawler;

import com.udacity.webcrawler.json.CrawlResult;
import com.udacity.webcrawler.parser.PageParser;
import com.udacity.webcrawler.parser.PageParserFactory;
import javax.inject.Inject;
import javax.inject.Provider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import java.util.regex.Pattern;
import java.util.concurrent.*;
import java.util.*;

/**
 * A concrete implementation of {@link WebCrawler} that runs multiple threads on a
 * {@link ForkJoinPool} to fetch and process multiple web pages in parallel.
 */
final class ParallelWebCrawler implements WebCrawler {
  private final Clock clock;
  private final PageParserFactory parserFactory;
  private final Duration timeout;
  private final int popularWordCount;
  private final int maxDepth;
  private final List<Pattern> ignoredUrls;
  private final ForkJoinPool pool;

  @Inject
  ParallelWebCrawler(
          Clock clock,
          PageParserFactory parserFactory,
          @Timeout Duration timeout,
          @PopularWordCount int popularWordCount,
          @MaxDepth int maxDepth,
          @IgnoredUrls List<Pattern> ignoredUrls,
          @TargetParallelism int threadCount) {
    this.clock = clock;
    this.parserFactory = parserFactory;
    this.timeout = timeout;
    this.popularWordCount = popularWordCount;
    this.maxDepth = maxDepth;
    this.ignoredUrls = ignoredUrls;
    this.pool = new ForkJoinPool(Math.min(threadCount, getMaxParallelism()));
  }

  @Override
  public CrawlResult crawl(List<String> startingUrls) {
    Instant deadline = clock.instant().plus(timeout);
    ConcurrentMap<String, Integer> counts = new ConcurrentHashMap<>();
    ConcurrentSkipListSet<String> visitedUrls = new ConcurrentSkipListSet<>();

    for (String url : startingUrls) {
      pool.invoke(new CustomTask(url, deadline, maxDepth, counts, visitedUrls, clock, parserFactory, ignoredUrls));
    }

    if (counts.isEmpty()) {
      return new CrawlResult.Builder()
              .setWordCounts(counts)
              .setUrlsVisited(visitedUrls.size())
              .build();
    }

    return new CrawlResult.Builder()
            .setWordCounts(WordCounts.sort(counts, popularWordCount))
            .setUrlsVisited(visitedUrls.size())
            .build();
  }

  @Override
  public int getMaxParallelism() {
    return Runtime.getRuntime().availableProcessors();
  }

  private class CustomTask extends RecursiveAction {
    private final String url;
    private final Instant deadline;
    private final int maxDepth;
    private final ConcurrentMap<String, Integer> counts;
    private final ConcurrentSkipListSet<String> visitedUrls;
    private final Clock clock;
    private final PageParserFactory parserFactory;
    private final List<Pattern> ignoredUrls;

    CustomTask(String url, Instant deadline, int maxDepth,
                      ConcurrentMap<String, Integer> counts,
                      ConcurrentSkipListSet<String> visitedUrls,
                      Clock clock, PageParserFactory parserFactory,
                      List<Pattern> ignoredUrls) {
      this.url = url;
      this.deadline = deadline;
      this.maxDepth = maxDepth;
      this.counts = counts;
      this.visitedUrls = visitedUrls;
      this.clock = clock;
      this.parserFactory = parserFactory;
      this.ignoredUrls = ignoredUrls;
    }

    @Override
    protected void compute() {
      if (clock.instant().isAfter(deadline) || maxDepth == 0 || visitedUrls.contains(url)) {
        return;
      }

      for (Pattern pattern : ignoredUrls) {
        if (pattern.matcher(url).matches()) {
          return null;
        }
      }

      if (!visitedUrls.add(url)) {
        return null;
      }

      visitedUrls.add(url);

      PageParser.Result result = parserFactory.get(url).parse();

      for (Map.Entry<String, Integer> e : result.getWordCounts().entrySet()) {
        counts.merge(e.getKey(), e.getValue(), Integer::sum);
      }

      List<CustomTask> subtasks = result.getLinks().stream()
              .filter(link -> !visitedUrls.contains(link))
              .map(link -> new CustomTask(link, deadline, maxDepth - 1, counts, visitedUrls, clock, parserFactory, ignoredUrls))
              .collect(Collectors.toList());

      invokeAll(subtasks);
      return null;
    }
  }
}
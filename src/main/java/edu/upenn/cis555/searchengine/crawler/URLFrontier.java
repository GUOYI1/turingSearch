package edu.upenn.cis555.searchengine.crawler;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;

import org.apache.log4j.Logger;

import edu.upenn.cis555.searchengine.crawler.storage.DBWrapper;

public class URLFrontier {
	
	static Logger log = Logger.getLogger(URLFrontier.class);
	
	int numThreads;
	LinkedList<String> frontend;
	Queue<String>[] backends;
	ConcurrentHashMap<String, Integer> hostToQueue;
	PriorityBlockingQueue<TTR> releaseHeap;
	DBWrapper db;
	HashSet<Integer> emptyQueue = new HashSet<>();
	LinkedHashMap<String, Long> lastRelease;
	ConcurrentHashMap<String, Integer> delayCache;
	private long initTime=System.currentTimeMillis();
	
	int upperLimit;
	
	@SuppressWarnings("unchecked")
	public URLFrontier(int numThreads, List<String> seedURLs) {
		this.numThreads = numThreads;
		frontend = new LinkedList<>();
		hostToQueue = new ConcurrentHashMap<String, Integer>();
		releaseHeap = new PriorityBlockingQueue<>(150);
		backends = new Queue[6 * numThreads];
		upperLimit = numThreads * 100;
		lastRelease =  new LinkedHashMap<String, Long>(6 * numThreads * 5, (float) 0.75, true) {
			private static final long serialVersionUID = 2009731084826885027L;

			@Override
			protected boolean removeEldestEntry(java.util.Map.Entry<String, Long> eldest) {
				return size() > 6 * numThreads * 5;
			}
		};
		delayCache =  new ConcurrentHashMap<>();
		db = DBWrapper.getInstance();
		int emptyIdx = 0;
		for (int i = 0; i < 6 * numThreads; i++) {
			backends[i] = new LinkedList<String>();
		}
		
		for (String url : seedURLs) {
			if (emptyIdx < 6 * numThreads) {
				if (addToBackEnd(url, emptyIdx)) emptyIdx++;
			}
			else {
				frontend.add(url);
			}
		}
		
		for (String url : db.getURLs(-1)) {
			if (emptyIdx < 6 * numThreads) {
				if (addToBackEnd(url, emptyIdx)) emptyIdx++;
			}
			else {
				frontend.add(url);
			}
		}
		
		while (emptyIdx < 6 * numThreads) {
			emptyQueue.add(emptyIdx);
			emptyIdx++;
		}
		
		TimerTask fillEmptyTask = new TimerTask() {
			@Override
			public void run() {
				HashSet<Integer> emptyQueue = URLFrontier.this.emptyQueue;
				ConcurrentHashMap<String, Integer> hostToQueue = URLFrontier.this.hostToQueue;
				LinkedList<String> frontend = URLFrontier.this.frontend;
				synchronized (emptyQueue) {
					log.debug("Empty Queue" + emptyQueue.size());
					log.debug("FrontQueue size:" + frontend.size());
//					log.debug("Last release LRU: " + lastRelease.size());
					log.debug("Crawled docs: " + Crawler.num.get());
					log.error("Active thread:" + Thread.activeCount());

					if (frontend.size() > 0.8 * upperLimit) {
						return;
					}
					Iterator<Integer> iter = emptyQueue.iterator();
					ArrayList<String> list = db.getURLs(40);
					
					String uF;
					synchronized (frontend) {
						int limit = 20;
//						if (frontend.size() > 200) {
//							limit = 30;
//						}
						int count = 0;
						while((uF = frontend.poll()) != null) {
							list.add(uF);
							count++;
							if (count >= limit) {
								break;
							}
						}
					}
					
					for (String url : list) {
						try {
							URL u = new URL(url);
							String host = u.getHost();
//							int delay = Crawler.rule.getDelay(host);
//							log.debug("Delay("+ host + "): " + delay);
//							synchronized (hostToQueue) {
								if (hostToQueue.containsKey(host)) {
//									log.debug("Has item put" + host + " to " + hostToQueue.get(host));
//									log.debug("Queue of " + host + ":" + backends[hostToQueue.get(host)].size());
									if (backends[hostToQueue.get(host)].size() > 200) {
										continue;
									}
//									else
									backends[hostToQueue.get(host)].add(url);
									continue;
								} else if (iter.hasNext()) {
									int idx = iter.next();
									iter.remove();
//									log.debug("Empty put" + host + " to " + idx);
									backends[idx].add(url);
									hostToQueue.put(host, idx);
									Long releaseTime = lastRelease.get(host);
									if (releaseTime == null)
										releaseHeap.put(new TTR(host, System.currentTimeMillis()));
									else {
										long time = releaseTime.longValue() + getDelay(host) * 1000;
										releaseHeap.put(new TTR(host, time));
									}
									continue;
								}
//							}
							frontend.add(url);
						} catch (Exception e) {
							continue;
						}
					}
					
				}
			}
		};
		
		Timer timer = new Timer();
		// save urlseen every 2 seconds
		timer.scheduleAtFixedRate(fillEmptyTask, 100, 2 * 1000);
		
	}
	
	private boolean addToBackEnd(String url, int emptyIdx) {
		try {
			URL u = new URL(url);
			String host = u.getHost();
			if (hostToQueue.containsKey(host)) {
				backends[hostToQueue.get(host)].add(url);
				return false;
			} else {
				backends[emptyIdx].add(url);
				hostToQueue.put(host, emptyIdx);
				releaseHeap.put(new TTR(host, System.currentTimeMillis()));
				return true;
			}
		} catch (MalformedURLException e) {
			return false;
		}
	}
	
//	public TTR getNextAvailableHost() {
//		synchronized (releaseHeap) {
//			return releaseHeap.poll();
//		}
//	}
//	
//	public void putAvailableHost(TTR available) {
//		synchronized (releaseHeap) {
//			releaseHeap.add(available);
//		}
//	}
	
	public String getURL() throws InterruptedException {
		TTR release;
		synchronized (releaseHeap) {
			release = releaseHeap.take();
			log.debug("Release heap size: " + releaseHeap.size());
		}
		long wait = release.releaseTime - System.currentTimeMillis();
		if (wait > 0) {
			log.debug("wait");
			Thread.sleep(wait);
		}
		return retrieveBackQuene(release);
	}
	
	public String retrieveBackQuene(TTR release) {
		String host = release.host;
		synchronized (lastRelease) {
			lastRelease.put(host, System.currentTimeMillis());
		}
		int idx = hostToQueue.get(host);
		String url = backends[idx].poll();
		if (backends[idx].isEmpty()) {
//			synchronized (hostToQueue) {
				hostToQueue.remove(host);
//			}
			new Thread(() -> {
				if (!frontToBack(idx)) {
					synchronized (emptyQueue) {
						emptyQueue.add(idx);
					}
				}
			}).start();
		} else {
			// TODO change release time
			release.releaseTime = System.currentTimeMillis() + getDelay(host) * 1000;
			releaseHeap.put(release);
		}
		// log.debug("Get " + url);
		return url;
	}
	
	public synchronized boolean frontToBack(int idx) {
		// get url from front queue
		String s;
		synchronized (frontend) {
			if (frontend.isEmpty()) {
				frontend.addAll(db.getURLs(-1));
			}
			s = frontend.poll();
		}
		if (s == null) {
			return false;
		}
		try {
			URL url = new URL(s);
			String host = url.getHost();
			if (hostToQueue.containsKey(host)) {
				backends[hostToQueue.get(host)].add(s);
				return false;
			} else {
				hostToQueue.put(host, idx);
				backends[idx].add(s);
//				synchronized (lastRelease) {
					Long releaseTime = lastRelease.get(host);
					if (releaseTime == null)
						releaseHeap.put(new TTR(host, System.currentTimeMillis()));
					else {
						long time = releaseTime.longValue() + getDelay(host) * 1000;
						releaseHeap.put(new TTR(host, time));
					}
//				}
				return true;
			}
		} catch (MalformedURLException e) {
			return false;
		}
	}
	
	private int getDelay(String host) {
		if (delayCache.containsKey(host)) {
			return delayCache.get(host);
		} else {
			int delay = 2;
			try {
				delay = Crawler.rule.getDelay(host);
			} catch(Exception e) {
			}
			return delay;
		}
	}
	
	public void addURLToHead(String url) {
		synchronized (frontend) {
			frontend.addFirst(url);
		}
	}
	
	public boolean hasHost(String host) {
		synchronized (hostToQueue) {
			return hostToQueue.containsKey(host);
		}
	}
	
	public int getFrontend() {
		return frontend.size();
	}
	
	public boolean hitUpperBound() {
		synchronized (frontend) {
			return frontend.size() >= upperLimit;
		}
	}

}

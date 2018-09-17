# ![Turing Search](https://github.com/changan1995/turingSearch/raw/master/SearchEngine/conf/title1.jpg?raw=true)

TuringSearch is based on traditional searching & crawler structure. The distributed crawler established on AWS ec2s,with high efficiency and scalability. The Pagerank and Indexer is implemented to support a query style search from the user-interface. 
Essentially, 1.5 million websites are crawled in one day, concerning a wide spread of topics, which has already provided both accuracy and efficiency in searching.
The whole system is capable of, as it is designed for, processing thousands times larger scale in releatively short time.

# author

[Quankang Wang](mailto:changanw@seas.upenn.edu) Crawler, Ranking algorithm, Map Searching.

[Mojiang Jia](mailtomojjia@seas.upenn.edu) PageRank, Ranking.

[Yi Guo](mailto:guoyi1@seas.upenn.edu) Indexer.

[Yitong Long](mailto:yitongl@seas.upenn.edu) User Interface, Yelp support.

#  Architecture

Our Turing Search Engine consists of 4 main components, Crawler, Indexer, PageRank and User Interface. The approaches we used are shown below.

## Crawler: 

The Crawler used Chord-like distributing system, with high efficiency design and great scalability. Select the URLs by domain hashvalue, and implement high performance and polite crawlering derived from the [paper of Allan Heydon and Marc Najork](https://doi.org/10.1023/A:1019213109274) 

There are multiple techniques implemented in preventing hogs and sinks, as well as malicious sites, including content duplicate detection, malicious host detection, trash preventing techniques, etc.

![crawler structure](./figure/figure1.png?raw=true)

## Indexer: 

MapReduce was used to calculate the value of tf and idf. We used EMR for map reduce process and stored the tables in DynamoDB for query. For keyword stemming, we chose to use snowball,a lightweight pure-algo open source stemmer.
![indexer data structure](./figure/figure2.png?raw=true)


## PageRank Engine: 

Given the crawled information, we used Hadoop MapReduce to implement a iterative PageRank algorithm, designed a data encoding to serve the output of previous iteration as input to the next. We used Random Surfer Model by adding a decay factor prevent “sinks” and “hogs”. Web graphs in [Stanford Large Network Dataset Collection](https://snap.stanford.edu/data/) was used  to test correctness.

TODO: implementing perlocating pagerank.

## User interface: 

The user interface is implemented by java servlet. The search interface was written by JavaScript, HTML and CSS. As to the result interface, we ranked the results by combining TF/IDF values and PageRank scores. The search results from Google map and Yelp were integrated by using the APIs.

#  Performance and Analysis

## Crawler
**Scale of System:**  The crawling efficiency grows exponentially with the number of nodes of the distributed system,meanwhile linearly with the computing power of single nodes.The experiment is based on same seedPage, and also same thread number and other parameters.Obviously the efficiency grows proportionally to the nodes number, which is a good evident of our scalability. Since the transfer of the URLs won’t trash the crawling process.

![crawler efficiency1](./figure/figure3.png?raw=true)

**Efficiency of threads number:**  We ran crawlers on c5.2xlarge ec2 model, and we can see drop on the efficiency growth on 50 threads. We didn't test the case with more than 50 threads, but it’s obvious that more threads may leads to trashing. We essentially chose around 30 threads, which balanced efficiency against trashing.

![crawler efficiency2](./figure/figure5.png?raw=true)

## Indexer
The mapreduce job of indexer finishes in 6 hours with a 10 node EMR cluster given 1.5 million crawled page. There are in total 400 million extracted words. The hive script is executed on a 10 nodes EMR cluster and it takes 15 hours to transfer the data into DynamoDB table with writing capacity of 3000. The bottleneck is the writing capacity. We tried to improve the writing capacity to over 10 thousands and overall time cost drops dramatically.

## PageRank
The total number of documents that we crawled is 1504289. To calculate the PageRank, we used 8 EC2 nodes (m3x2large) for MapReduce, and it took 1 hour and 15 minutes to finish the job. We chose 40 as the maximum number of iterations and the threshold of average error is 0.0000001. The following figure shows how total error changes with more iterations. As we can see the total error decrease rapidly, but to make sure PageRank converge (every page changes very little), we use the average error to test convergence.

![PageRank Analysis](./figure/figure4.png?raw=true)

For Spark, we used 3 EC2 nodes (m3xlarge) and finished the job in 2.5 hours. It’s hard to say which is faster since the number of nodes and the type of nodes are different. However, Hadoop used two times better nodes and about 2.7 times more nodes, and used half of time that Spark took, so roughly speaking, Spark is more efficient that Hadoop.

## Search Time
After we received the request from the user, we processed the query, found the relevant values, calculated the total scores and ranked all the websites. By combining the tf values, urls of websites and pagerank scores to one table, the search time was shortened largely. For one word, the search time of ten thousands results is about 0.6s. 

# TuringSearch
![img](https://github.com/changan1995/turingSearch/raw/master/SearchEngine/conf/title1.jpg?raw=true)
TuringSearch is based on traditional searching & crawler structure. The distributed crawler established on AWS ec2s,with high efficiency and scalability. The Pagerank and Indexer is implemented to support a query style search from the user-interface.

## author

Quankang Wang: changanw@seas.upenn.edu	

Mojiang Jia: mojjia@seas.upenn.edu

Yitong Long: yitongl@seas.upenn.edu		

Yi Guo: guoyi1@seas.upenn.edu

# Introduction

##  Architecture

Our Turing Search Engine consists of 4 main components, Crawler, Indexer, PageRank and User Interface. The approaches we used are shown below.

##### Crawler: 

The Crawler used Chord like distributing system, with high efficiency design and great scalability. Select the URLs by domain hashvalue, and implement high performance crawlering derived from the [paper of Allan Heydon and Marc Najork](https://doi.org/10.1023/A:1019213109274) 
![image](https://github.com/changan1995/turingSearch/raw/master/figure/figure1.png?raw=true)


##### Indexer: 

MapReduce was used to calculate the value of tf and idf. We used EMR for map reduce process and stored the tables in DynamoDB for query. For keyword stemming, we chose to use snowball,a lightweight pure-algo open source stemmer.

##### PageRank Engine: 

Given the crawled information, we used Hadoop MapReduce to implement a iterative PageRank algorithm, designed a data encoding to serve the output of previous iteration as input to the next. We used Random Surfer Model by adding a decay factor prevent “sinks” and “hogs”. Web graphs in [Stanford Large Network Dataset Collection](https://snap.stanford.edu/data/) was used  to test correctness.

##### User interface: 

The user interface is implemented by java servlet. The search interface was written by JavaScript, HTML and CSS. As to the result interface, we ranked the results by combining TF/IDF values and PageRank scores. The search results from Google map and Yelp were integrated by using the APIs.

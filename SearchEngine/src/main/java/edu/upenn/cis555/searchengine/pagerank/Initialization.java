package cs3.cs2.cs.searchengine.pagerank;

import java.io.IOException;

import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.Reducer.Context;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;


public class Initialization extends Configured implements Tool {
	
	private static class InitMapper extends Mapper<LongWritable, Text, Text, Text> {
		
		@Override
		public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
			String line = value.toString();
//			System.out.println(line);
			String[] pair = line.split("\\t", 2);
			context.write(new Text(pair[0]), new Text(pair[1]));
		}
	}
	
	private static class InitReducer extends Reducer<Text, Text, Text, Text> {
		
		@Override
		public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
			String rank = "";
			StringBuilder sb = new StringBuilder();
			for (Text v : values) {
				String value = v.toString();
				if (value.startsWith("r:")) {
					rank = value;
				} else {
					sb.append("\001" + value);
				}
			}
			// format is ->  url \t o:old_value \001 r:new_value \001 outlinks(seperate by \001)
			context.write(key, new Text(rank.replace('r', 'o') + "\001" + rank + sb.toString()));
		}
	}

	@Override
	public int run(String[] args) throws Exception {
		String inputDir = args[0];
		String outputDir = args[1];

		Job job = Job.getInstance();
		
		FileInputFormat.setInputPaths(job, new Path(inputDir));
		FileOutputFormat.setOutputPath(job, new Path(outputDir));
		
		job.setJobName("init");
		job.setMapperClass(InitMapper.class);
		job.setReducerClass(InitReducer.class);
		job.setJarByClass(Initialization.class);
		
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);
		
		job.getConfiguration().set("mapreduce.output.basename", "init");
		
		return job.waitForCompletion(true) ? 0 : 1;
	}

}
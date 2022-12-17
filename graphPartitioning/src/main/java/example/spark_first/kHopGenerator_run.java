package example.spark_first;


import org.apache.hadoop.fs.Path;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.Optional;

import org.apache.spark.sql.SparkSession;

import dataset.Node;
import kHop.Edge_Inf;
import kHop.Node_Info;
import kHop.Util.*;


import scala.Tuple2;
import scala.reflect.internal.Trees.This;
import util.FolderManager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;

import java.util.Iterator;
import java.util.List;

import java.util.regex.Pattern;

public final class kHopGenerator_run {
	private static final Pattern SPACE = Pattern.compile(" ");
	
	
	@SuppressWarnings("null")
	public static void main(String[] args) throws Exception {
		// dataset\cora.txt 4
		//System.setProperty("hadoop.home.dir", "c:\\hadoop\\winutil\\");
		//SparkConf sparkConf = new SparkConf().setAppName("JavaWordCount").setMaster("local[*]");

		if (args.length < 3) {
		  System.err.println("Usage: kHopGenerator <inputpath> <numberofhop> <numberOfPart> <outputpath>");
		  System.exit(1);
		}

		SparkSession spark = SparkSession      
				.builder()
			      .appName("kHopGenerator")
	              .master("local[4]")
			      // .master("spark://gpunode1:7077")
			       //.config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
	               .config("spark.driver.bindAddress", "localhost")
	                .config("spark.task.maxDirectResultSize", "4G")
	                //.setJars(JavaSparkContext.jarOfClass(this.getClass())
			      .getOrCreate();

		System.out.println("Start:");
		
		long start = System.currentTimeMillis();
		
		JavaRDD<String> lines = spark.read().textFile(args[0]).javaRDD();
		int K = Integer.valueOf(args[1]);
		
		JavaRDD<Edge_Inf> edgeList = lines.map(line -> getEdge(line));
		
		JavaRDD<Node_Info> node_infos = edgeList.flatMap(s-> createNode_Infor(s));
		//init node_infor_pair
		JavaPairRDD<Integer, Node_Info> node_infor_pair = node_infos.mapToPair(s->new Tuple2<>(s.getNode_id(), s));
		JavaPairRDD<Integer, Node_Info> result = null;
		int k = 0;
		//at k> 1
		String outpath = args[3];

		while(k<K)
		{
			k=k+1;
			System.out.println("---At k: "+ k);

			result = node_infor_pair.reduceByKey((i1, i2) -> inEdge_Merging(i1,i2));
			System.out.println("Print new_node_infor_pair after reducing size: "+result.count());
			
			if((k+1)<=K)
			{
			JavaRDD<Node_Info> node_infos_propa = result.flatMap(s-> outPropagation(s));

			
			// create new node_infor_pair
			node_infor_pair = node_infos_propa.mapToPair(s -> new Tuple2<>(s.getNode_id(),s));
			}
			/*List<Tuple2<Integer, Node_Info>> outslist_2 = node_infor_pair.collect();
			System.out.println("Print new_node_infor_pair size: "+outslist_2.size());
			
			for (Tuple2<Integer,Node_Info> tuple : outslist_2) {
				tuple._2.printInfor();
			}*/
			
			/*
			String fileName = String.valueOf(k)+"hop.txt";
			
			Writer output;
			
			String filePath = outpath +File.separatorChar+ fileName;

			ensureDirExist(filePath);
			
			output = new BufferedWriter(new FileWriter(filePath, false));  //clears file every time			
			List<Tuple2<Integer, Node_Info>> outslist = result.collect();
			System.out.println("Print result size: " + outslist.size());
			
			for (Tuple2<Integer,Node_Info> tuple : outslist) 
			{ 
				//tuple._2.printInfor(); 
				String text = tuple._2.textToPrint()+"\n";
				output.append(text);
			}			
			output.close();
			*/

		}
		long atk = System.currentTimeMillis();
		long runtime = atk - start;
		
        System.out.println("running time : " + runtime  + "ms");
		
		/*
		String fileName = String.valueOf(K)+"hop.txt";
		
		Writer output;
		
		String filePath = outpath +File.separatorChar+ fileName;

		ensureDirExist(filePath);
		
		output = new BufferedWriter(new FileWriter(filePath, false));  //clears file every time			
		List<Tuple2<Integer, Node_Info>> outslist = result.collect();
		System.out.println("Print result size: " + outslist.size());
		
		for (Tuple2<Integer,Node_Info> tuple : outslist) 
		{ 
			//tuple._2.printInfor(); 
			String text = tuple._2.textToPrint()+"\n";
			output.append(text);
		}			
		output.close();
		*/
        
        int numPart = Integer.parseInt(args[2]);
        partitioning(result, outpath, numPart);
        spark.stop();
	}

	private static void partitioning(JavaPairRDD<Integer, Node_Info> result, String outpath, int numPart)
			throws IOException {
		List<String> partOut = new ArrayList<String>();
        List<String> partOutFile = new ArrayList<String>();
        List<Writer> output = new ArrayList<Writer>();
        
        for(int i=0;i<numPart;i++)
        {
        	
        	partOut.add("part"+i+".txt");
        	
        	partOutFile.add(outpath +File.separatorChar+ partOut.get(i));
        	FolderManager.ensureDirExist(partOutFile.get(i));
        	output.add(new BufferedWriter(new FileWriter(partOutFile.get(i), false)));  //clears file every time	
        	System.out.println("out path: " + partOutFile.get(i));
        }	
				
		List<Tuple2<Integer, Node_Info>> outslist = result.collect();
		int size = outslist.size();
		System.out.println("Print result size: " + size);
		
		int count = 0;
		int numInOnePart = size/numPart;
		for (Tuple2<Integer,Node_Info> tuple : outslist) 
		{ 
			String text = tuple._2.textToPrint()+"\n";
			int index = count/numInOnePart;
			if(index == numPart)
				index = numPart -1;
			output.get(index).append(text);
			count ++;
		}
		for(int i=0;i<numPart;i++)
        {
			output.get(i).close();
        }
	}
	


	private static Iterator<Node_Info> outPropagation(Tuple2<Integer, Node_Info> s) {
		
		Node_Info nodeInfo= s._2;
		
		List<Node_Info> newNodeInfoList= new ArrayList<Node_Info>();
		newNodeInfoList.add(nodeInfo);
		if(nodeInfo.getOutEdge()!=null && nodeInfo.isKhopChange())
		{
			for(Edge_Inf edge: nodeInfo.getOutEdge())
			{
				Node_Info newNode = new Node_Info(edge.getNodeB().getId(), nodeInfo.getkhops(), null);
				newNodeInfoList.add(newNode);
			}
			
		}
		return newNodeInfoList.iterator();
	}

	private static Node_Info inEdge_Merging(Node_Info i1, Node_Info i2) {
		/*
		 * if(i1==null && i2!=null) return i2; if(i1!=null && i2==null) return i1;
		 */
		
		List<Edge_Inf> inedge = new ArrayList<Edge_Inf>();
		List<Edge_Inf> outedge = new ArrayList<Edge_Inf>();
		List<Edge_Inf> khop = new ArrayList<Edge_Inf>();

		if(i1.getInEdge()!=null)
			inedge.addAll(i1.getInEdge());
		if(i2.getInEdge()!=null)
		{
			inedge.addAll(i2.getInEdge());
			
		}
		
		if(i1.getOutEdge()!=null)
			outedge.addAll(i1.getOutEdge());
		
		if(i2.getOutEdge()!=null)
			outedge.addAll(i2.getOutEdge());
			
		inedge = uniqueEdgeList(inedge);
		
		//boolean flag = false;
		int tempCount=0;
		if(i1.getkhops()!=null)
		{
			khop.addAll(i1.getkhops());
			tempCount=tempCount +1;
		}
		
		if(i2.getkhops()!=null)
		{
			khop.addAll(i2.getkhops());
			tempCount=tempCount +1;
		}
		//khop = inedge;
		//i1.setInEdge(inedge);
		//i1.setkhops(khop);
		//i1.setOutEdge(outedge);

		
		Node_Info newInfo = new Node_Info(i1.getNode_id(),null, outedge);
		if(inedge.size()>0)
		{
			khop.addAll(inedge);
			newInfo.setKhopChange(true);
			
		}
		else
		{
			if(tempCount==2)
			{
				newInfo.setKhopChange(true);
				//System.out.println("tempCount==2");
			}
			else
			{
				//System.out.println("nochange");
				newInfo.setKhopChange(false);
				
			}
		}
		
		khop = uniqueEdgeList(khop);
		newInfo.setkhops(khop);
		return newInfo;
	}


	private static List<Edge_Inf> uniqueEdgeList(List<Edge_Inf> edgeList)
	{
		List<Edge_Inf> uniq = new ArrayList<Edge_Inf>();
		int size = edgeList.size();
		Integer[] nodeAs = new Integer[size];
		Integer[] nodeBs = new Integer[size];
		int i=0;
		for(Edge_Inf edge: edgeList)
		{
			if(i==0)
			{
				nodeAs[i] = edge.getNodeA().getId();
				nodeBs[i] = edge.getNodeB().getId();
				uniq.add(edge);
				i++;
			}
			else
			{
				Boolean add = true; 
				for(int j=0;j<i;j++)
				{
					if((nodeAs[j]==edge.getNodeA().getId()) && (nodeBs[j]==edge.getNodeB().getId()))
					{
						add = false;
						break;
					}
				}
				if(add ==true)
				{
					nodeAs[i] = edge.getNodeA().getId();
					nodeBs[i] = edge.getNodeB().getId();
					uniq.add(edge);
					i++;
				}
			}
			
		}
		return uniq;
	}


	private static Iterator<Node_Info> createNode_Infor(Edge_Inf s) {
		// TODO Auto-generated method stub
		List<Node_Info> infoList = new ArrayList<Node_Info>();
		List<Edge_Inf> edgeList = new ArrayList<Edge_Inf>();
		edgeList.add(s);
		Node_Info inNode = new Node_Info(s.getNodeB().getId(),edgeList,null );
		Node_Info outNode = new Node_Info(s.getNodeA().getId(),null,edgeList);
		
		infoList.add(inNode);
		infoList.add(outNode);
		return infoList.iterator();
	}


	public static Edge_Inf getEdge(String line)
	{
		//System.out.println("line: "+ line);
		int nodeAid = Integer.parseInt(SPACE.split(line)[0]);
		int nodeBid = Integer.parseInt(SPACE.split(line)[1]);
		
		Node nodeA = new Node(nodeAid);
		Node nodeB = new Node(nodeBid);
		// TODO Auto-generated method stub
		Edge_Inf edge = new Edge_Inf(nodeA, nodeB);
		return edge;
	}
	
	private static void addUniqueEdgeList(List<Edge_Inf> edgeList, List<Edge_Inf> newList)
	{
		if(newList!=null && edgeList!=null)
		{
			for(Edge_Inf newEdge: newList)
			{
				int count =0;
				int total = edgeList.size();
				for(Edge_Inf edge: edgeList)
				{
					if(newEdge.getNodeA().getId()==edge.getNodeA().getId() && newEdge.getNodeB().getId()==edge.getNodeB().getId())
					{
						break;
					}
					else
						count++;
						
				}
				if(count == total)
					edgeList.add(newEdge);
			}
		}
	}
	
	private static JavaPairRDD<Integer, Node_Info> join2Pair_(JavaPairRDD<Integer, Node_Info> list1,
			JavaPairRDD<Integer, Node_Info> list2) {
		
		JavaPairRDD<Integer, Tuple2<Optional<Node_Info>, Optional<Node_Info>>> total = list1.fullOuterJoin(list2);	
		JavaPairRDD<Integer, Node_Info> result = total.mapToPair(tuple ->{
			
			Node_Info node_info = null;
			if(!tuple._2._1.isPresent())
			{
				node_info = tuple._2._2.get();
				
			}
			else if(!tuple._2._2.isPresent())
			{
				node_info = tuple._2._1.get();
			}
			else if(tuple._2._2.isPresent() && tuple._2._1.isPresent())
			{
				node_info = inEdge_Merging(tuple._2._2.get(), tuple._2._1.get());
			}
			
			return new Tuple2<>(tuple._1, node_info); 
			
		});
		
		return result;
	}





	
}
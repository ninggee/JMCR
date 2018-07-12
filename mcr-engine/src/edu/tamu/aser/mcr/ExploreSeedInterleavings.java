package edu.tamu.aser.mcr;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Vector;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.Collections;

import edu.tamu.aser.mcr.config.Configuration;
import edu.tamu.aser.mcr.constraints.ConstraintsBuildEngine;
import edu.tamu.aser.mcr.trace.AbstractNode;
import edu.tamu.aser.mcr.trace.IMemNode;
import edu.tamu.aser.mcr.trace.LockNode;
import edu.tamu.aser.mcr.trace.ReadNode;
import edu.tamu.aser.mcr.trace.Trace;
import edu.tamu.aser.mcr.trace.UnlockNode;
import edu.tamu.aser.mcr.trace.WriteNode;

//import edu.tamu.aser.rvinstrumentation;

/**
 * The MCRTest class implements maximal causal model based systematic
 * testing algorithm based on constraint solving. 
 * The events in the trace are loaded and processed window 
 * by window with a configurable window size. 
 * 
 * @author jeffhuang
 *
 */
public class ExploreSeedInterleavings {

	private static int schedule_id;
	private static Queue<List<String>> schedules = new ConcurrentLinkedQueue<List<String>>();
//	private static HashSet<IViolation> violations= new HashSet<IViolation>();
	
	public static String output = "#Read, #Constraints, SolvingTime(ms)\n";
    public static HashSet<Object> races = new HashSet<Object>();

	private static boolean isfulltrace =false;
	
	//prefix-setOfEquivalentPrefixes_map
	public static HashMap<Vector<String>, Set<Vector<String>>> mapPrefixEquivalent = 
			new HashMap<>();
	public static long memUsed = 0;

	/**
	 * Trim the schedule to show the last 100 only entries
	 * 
	 * @param schedule
	 * @return
	 */
	private static Vector<String> trim(Vector<String> schedule)
	{
		if(schedule.size()>100)
		{
			Vector<String> s = new Vector<String>();
			s.add("...");
			for(int i=schedule.size()-100;i<schedule.size();i++)
				s.add(schedule.get(i));
			return s;
		}
		else
			return schedule;
	}
	
	/**
	 * The method for generating causally different schedules. 
	 * Each schedule enforces a read to be matched with a write that writes
	 * a different value.
	 * @param PPngine
	 * @param trace
	 * @param schedule_prefix
	 */
	private static void genereteCausallyDifferentSchedules( ConstraintsBuildEngine engine, Trace trace, Vector<String> schedule_prefix)
	{
		//OMCR
		Vector<HashMap<String, Set<Vector<String>>>> vReadValuePrefixes =
				new Vector<>();
		/*
		 * for each shared variable, find all the reads and writes to this variable
		 * group the writes based on the value written to this variable
		 * consider each read to check if it can see a different value
		 */
		Iterator<String> 
		addrIt = trace.getIndexedThreadReadWriteNodes().keySet().iterator();
		while(addrIt.hasNext())
		{
			
			//the dynamic memory location
			String addr = addrIt.next();	
			
			//get the initial value on this address
			final String initVal = trace.getInitialWriteValueMap().get(addr);
			
			//get all read nodes on the address
			Vector<ReadNode> readnodes = trace.getIndexedReadNodes().get(addr);
			
			//get all write nodes on the address
			Vector<WriteNode> writenodes = trace.getIndexedWriteNodes().get(addr);
			
			//skip if there is no write events to the address
			if(writenodes==null||writenodes.size()<1)
			continue;

			//check if local variable
			if(trace.isLocalAddress(addr))
				continue;
							
			HashMap<String,ArrayList<WriteNode>> valueMap = new HashMap<String,ArrayList<WriteNode>>();
			//group writes by their value
			for(int i=0;i<writenodes.size();i++)
			{
				WriteNode wnode = writenodes.get(i);//write
				String v = wnode.getValue();
				ArrayList<WriteNode> list = valueMap.get(v);
				if(list==null){
					list = new ArrayList<WriteNode>();
					valueMap.put(v, list);
				}
				list.add(wnode);
			}
				
			//check read-write
   			if(readnodes!=null){
				for(int i=0;i<readnodes.size();i++)
				{
					
					HashMap<String, Set<Vector<String>>> mValuesPrefixes= new HashMap<>();
					ReadNode rnode = readnodes.get(i);
					//if isfulltrace, only consider the read nodes that happen after the prefix
					if(isfulltrace && rnode.getGID()<=schedule_prefix.size())
						continue;
			
					String rValue = rnode.getValue();
					//1. first check if the rnode can read from the initial value which is different from rValue
					boolean success = false;
					if(initVal==null && !rValue.equals("0") 
							||initVal!=null && !initVal.equals(rValue))
					{
						success = checkInitial(engine, trace, schedule_prefix, writenodes,
								rnode, initVal, mValuesPrefixes);			
					}
					
					//2. then check if it can read from a particular write
					for(Iterator<String> valueIter=valueMap.keySet().iterator();valueIter.hasNext();)
					{
						final String wValue = valueIter.next();
						if( !wValue.equals(rValue))  
						{		
							//if it already reads from the initial value, then skip it
							if (wValue.equals(initVal) && success) {
								continue;
							}
							checkReadWrite(engine, trace, schedule_prefix,valueMap, rnode, wValue, mValuesPrefixes);
						}
					}
					//for each read, add the values and the corresponding prefixes to the vector
					if (!mValuesPrefixes.isEmpty()) {
						vReadValuePrefixes.add(mValuesPrefixes);
					}					
					
					
//					for(HashMap<K, V>)
				} //end for check read write
			}
		}  //end while
		
		memUsed += memSize(vReadValuePrefixes);
		
		if (Configuration.OMCR) {
			//local
			HashMap<Vector<String>, Set<Vector<String>>> localMapPrefixEquClass =
					new HashMap<>();
			//compute the equivalent prefixes
			computeEquPrefixes(vReadValuePrefixes,trace,schedule_prefix, localMapPrefixEquClass);
			memUsed += memSize(localMapPrefixEquClass);
			//
			Set<Vector<String>> equPrefixes = null;
			if (mapPrefixEquivalent.containsKey(schedule_prefix)) {
				equPrefixes = mapPrefixEquivalent.get(schedule_prefix);
			}		
			//check each new prefix
			//for each read
			for (int i=0; i<vReadValuePrefixes.size(); ++i){
				HashMap<String, Set<Vector<String>>> valuePrefixes = 
						vReadValuePrefixes.get(i);
				//for each value
				for ( Set<Vector<String>> setPrefixes : valuePrefixes.values()) 
				{
					//choose the prefix with max equivalent prefixes
					int num = 0;
					Iterator<Vector<String>> itPrefix = setPrefixes.iterator();
					Vector<String> prefix = null;
				
					//for each prefix that make the read return the value
					while (itPrefix.hasNext()) {						
						Vector<String> tmp = itPrefix.next();
						Vector<String> prefix1 = new Vector<>();
						for (int j = 0; j < tmp.size(); j++) {
							String xi = tmp.get(j);
							long gid = Long.valueOf(xi.substring(1));
							long tid = trace.getNodeGIDTIdMap().get(gid);
							String name = trace.getThreadIdNameMap().get(tid);	
							prefix1.add(name);
						}
						
						int flag = 0;
						if (equPrefixes != null) {
							//it may not in the same order
							for (Vector<String> p : equPrefixes){
								Vector<String> copy = new Vector<>(p);
								Collections.sort(copy);
								Collections.sort(prefix1);
								if ( copy.equals(prefix1)) {
//									System.err.println("test");
									flag = 1;
									break;
								}
							}					
						}
						
						if (flag ==1) {
							continue;
						}

						if (localMapPrefixEquClass.containsKey(tmp)) {
							if (localMapPrefixEquClass.get(tmp).size() > num) {
								num = localMapPrefixEquClass.get(tmp).size();
								prefix = tmp;
							}
						} 
						else if (prefix == null) {
							prefix = tmp;
						}
//						else if (num == 0){
//							prefix = tmp;
//						}
					}
					////
					//
					if (prefix != null) {
						omcrGenSchedule(trace, prefix, schedule_prefix, localMapPrefixEquClass);
					}
				}
			}
		}
	}
	

	
	private static boolean checkInitial(ConstraintsBuildEngine engine, Trace trace,
			Vector<String> schedule_prefix, Vector<WriteNode> writenodes,
			ReadNode rnode, String initVal,
			HashMap<String, Set<Vector<String>>> mValuesPrefixes) {
		//construct constraints and generate schedule
		HashSet<AbstractNode> depNodes = engine.getDependentNodes(trace,rnode);
		
		HashSet<AbstractNode> readDepNodes = new HashSet<AbstractNode>();
		//OMCR
		HashMap<String, Set<Vector<String>>> mValuePrefix = new HashMap<>();
		Set<Vector<String>> prefix = new HashSet<Vector<String>>();
		
		if(isfulltrace && schedule_prefix.size()>0)
			depNodes.addAll(trace.getFullTrace().subList(0, schedule_prefix.size()));
		
		
		depNodes.add(rnode);
		readDepNodes.add(rnode);

		WriteNode wnode = null;
		StringBuilder sb = engine.constructFeasibilityConstraints(trace,depNodes,readDepNodes, rnode, wnode);
		StringBuilder sb2 = engine.constructReadInitWriteConstraints(rnode,depNodes, writenodes);

		sb.append(sb2);		

		// System.out.println(sb);

		//@alan
		//adding rnode.getGid() as a parameter
		Vector<String> schedule = engine.generateSchedule(sb,rnode.getGID(),rnode.getGID(),isfulltrace?schedule_prefix.size():0);
		
		output = output + Long.toString(Configuration.numReads) + " " +
				Long.toString(Configuration.rwConstraints) + " " +
				Long.toString(Configuration.solveTime) + "\n";
			
		if(schedule!=null){
			if (Configuration.OMCR) {
				prefix.add(schedule);
				mValuesPrefixes.put(initVal, prefix);
//				vReadValuePrefixes.add(mValuePrefix);
				return true;
			}
			else{
				generateSchedule(schedule,trace,schedule_prefix,rnode.getID(),rnode.getValue(),initVal,-1);
				return true;
			}
			
		}
		return false;
	}

	/**
	 * prevent a memory pattern happen in a new trace
	 * so the trace should statisfy happens-before realtionship in a thread
	 * and lock mutual exlusion realtionship
	 * and don't allow the given pattern appear in the new trace
	 */
	private static void stopPattern(
		ConstraintsBuildEngine engine, 
		Trace trace) {
		// StringBuilder sb = engine.constructFeasibilityConstraints(trace, depNodes, readDepNodes, cur_rnode, wnode);
		HashSet<AbstractNode> depNodes = new HashSet<>(trace.getFullTrace());
		engine.constructPOConstraints(trace, depNodes);
		engine.constructSyncConstraints(trace, depNodes);

		System.out.println("call 2");
		engine.generateSchedule2(new StringBuilder());
	}

	/**
	 * check if a read can read from a particular write
	 * @param engine
	 * @param trace
	 * @param schedule_prefix: the prefix that guides this execution
	 * @param valueMap
	 * @param rnode: the target read
	 * @param wValue: the value the read expects to see
	 */
	private static void checkReadWrite(
			ConstraintsBuildEngine engine, 
			Trace trace,
			Vector<String> schedule_prefix,
			HashMap<String, ArrayList<WriteNode>> valueMap, 
			ReadNode rnode,
			String wValue,
			HashMap<String, Set<Vector<String>>> mValuesPrefixes) 
	{	
		Vector<AbstractNode> otherWriteNodes = new Vector<AbstractNode>();
		
		//OMCR
		
		Set<Vector<String>> prefix = new HashSet<Vector<String>>();
		
		Iterator<Entry<String, ArrayList<WriteNode>>> entryIt = valueMap.entrySet().iterator();
		while(entryIt.hasNext())
		{
			Entry<String, ArrayList<WriteNode>> entry= entryIt.next();
			if(!entry.getKey().equals(wValue))
				otherWriteNodes.addAll(entry.getValue());
		}

		ArrayList<WriteNode> wnodes = valueMap.get(wValue);
		Vector<Long> tids = new Vector<>();
		for(int j=0;j<wnodes.size();j++)
		{
			WriteNode wnode = wnodes.get(j);
			if (tids.contains(wnode.getTid())) {
				continue;
			}
			if(rnode.getTid() != wnode.getTid())
			{
				//check whether possible for read to match with write
				//can reach means a happens before relation
				//the first if-condition is a little strange
				if(rnode.getGID() > wnode.getGID() || !engine.canReach(rnode, wnode))
				{
					
					//for all the events that happen before the target read and chosen write
					HashSet<AbstractNode> depNodes = new HashSet<AbstractNode>();
					
					//only for all the events that happen before the target read
					HashSet<AbstractNode> readDepNodes = new HashSet<AbstractNode>();
					
					if(isfulltrace&&schedule_prefix.size()>0)
						depNodes.addAll(trace.getFullTrace().subList(0, schedule_prefix.size()));
					
					//1. first find all the dependent nodes
					depNodes.add(rnode);
					depNodes.add(wnode);
					
					readDepNodes.add(rnode);		
					
					/*
					 * After using static analysis, some reads can be removed
					 * but they cannot be removed, otherwise the order calculated will be wrong
					 * it just needs to ignore the feasibility constraints of these reads
					 * @author Alan
					 */
					HashSet<AbstractNode> nodes1 = engine.getDependentNodes(trace,rnode);
					HashSet<AbstractNode> nodes2 = engine.getDependentNodes(trace,wnode);
										
					depNodes.addAll(nodes1); 
					depNodes.addAll(nodes2);
					
					readDepNodes.addAll(nodes1);
					
					//construct feasibility constraints
					StringBuilder sb = 
							engine.constructFeasibilityConstraints(trace,depNodes,readDepNodes, rnode, wnode);
					
					//construct read write constraints, namely, all other writes either happen before the Write
					//or after the Read.
					StringBuilder sb3 = 
							engine.constructReadWriteConstraints(engine,trace,depNodes, rnode, wnode, otherWriteNodes);
					
					sb.append(sb3);

					Vector<String> schedule = 
							engine.generateSchedule(sb,rnode.getGID(), wnode.getGID(),isfulltrace?schedule_prefix.size():0);
										
					//each time compute a causal schedule, record the information of #read, #constraints, time
					output = output + Long.toString(Configuration.numReads) + " " +
							Long.toString(Configuration.rwConstraints) + " " +
							Long.toString(Configuration.solveTime) + "\n";
					
					if(schedule!=null)
					{						
						if (Configuration.OMCR) {
							//TODO: compute the equivalent class of prefixes
							prefix.add(schedule);
							tids.add(wnode.getTid());
														
						} else {
							//rnode.getID or GID??
							generateSchedule(schedule,trace,schedule_prefix,rnode.getID(),rnode.getValue(),wValue,wnode.getID());
							break;
						}						
					}
				}
			}
		}// end for_writes	
		
		//add the equivalent class to the whole vector
		if (Configuration.OMCR && !prefix.isEmpty()) {
			mValuesPrefixes.put(wValue, prefix);
//			if (mReadValuePrefixes.containsKey(rnode.getGID())) {
//				mReadValuePrefixes.get(rnode).put(wValue, prefix);
//			}
//			else {
//				HashMap<String, Set<Vector<String>>> mValuePrefix = new HashMap<>();
//				mValuePrefix.put(wValue, prefix);
//				mReadValuePrefixes.put(rnode.getGID(), mValuePrefix);
//			}			
		}		
	}
	private static void computeEquPrefixes(Vector<HashMap<String, Set<Vector<String>>>> schedules, 
			Trace trace,
			Vector<String> schedule_prefix,
			HashMap<Vector<String>, Set<Vector<String>>> localMapPrefixEquClass)
	{
		//iterate over reads
		for (int i = 0; i < schedules.size() - 1; i++) {
			HashMap<String, Set<Vector<String>>> mVauePrefix = schedules.get(i);
			//iterate each value that this read can return
			for(Entry<String, Set<Vector<String>>> entryA : mVauePrefix.entrySet()){
				String vA = entryA.getKey();
				Set<Vector<String>> prefixes = entryA.getValue();
				//get prefix A
				for (Vector<String> pA : prefixes){
					//compare with prefix B
					for (int j = i+1; j < schedules.size(); j++){
						HashMap<String, Set<Vector<String>>> mVauePrefix2 = schedules.get(j);
						for(Entry<String, Set<Vector<String>>> entryB : mVauePrefix2.entrySet()){
							String vB = entryB.getKey();
							Set<Vector<String>> prefixes2 = entryB.getValue();
							for (Vector<String> pB : prefixes2){
								Vector<String> pAB = new Vector<>(); 
								if (checkEquivalence(trace, pAB, pA, vA, pB, vB)){
									//add the equivalent prefix to the class
//									addToEquivalentClass(trace, pA, pAB, localMapPrefixEquClass);
									if (localMapPrefixEquClass.containsKey(pA)) {
										localMapPrefixEquClass.get(pA).add(pAB);
									} else {
										Set<Vector<String>> equClass = new HashSet<Vector<String>>();
										equClass.add(pAB);
										localMapPrefixEquClass.put(pA, equClass);
									}
									
								}
							}
						}
					}
				}
			}
		}
		
	}
	
	private static boolean checkEquivalence(Trace trace, Vector<String> pAB, 
			Vector<String> pA, String vA, Vector<String> pB, String vB) {		
		Vector<String> pBA = new Vector<>();
		String rA = pA.lastElement();
		String rB = pB.lastElement();
		if (pA.contains(rB) || pB.contains(rA)) {
			return false;
		}
		
		if (combineTwoPrefixes(trace, pAB, pA, pB, rB, vB) &&
				combineTwoPrefixes(trace, pBA, pB, pA, rA, vA) ) {
			return true;			
		}	
		return false;
	}
	
	private static boolean combineTwoPrefixes(Trace trace, Vector<String> pAB, Vector<String> pA, Vector<String> pB, 
			String rB, String vB){
//		for (int i = 0; i < pA.size(); i++) {
//			pAB.add(pA.get(i));
//		}
		
		//needs to consider about the synchronizations
		//e.g., A: lock-read_x
		//      B: lock-read_y
		//lock-r_x-lock-read_y is infeasible
		long gidrB = Long.valueOf(rB.substring(1));
		long tidrB = trace.getNodeGIDTIdMap().get(gidrB);
		
		Stack<AbstractNode> stLocks = new Stack<AbstractNode>();
		Vector<AbstractNode> aTrace = trace.getFullTrace();
		for (int i=0; i<pA.size(); i++){
			long index = Long.valueOf(pA.get(i).substring(1)) - 1;
			AbstractNode aNode = aTrace.get((int) index);
			if (aNode instanceof LockNode) {
				stLocks.push(aNode);
			} else if (aNode instanceof UnlockNode) {
				if (!stLocks.isEmpty()) {
					stLocks.pop();
				}
			}
		}
		
		//if stLock is not empty, it means that the unlocks do not appear in the prefix
		if (!stLocks.isEmpty()) {
			HashMap<String, LockNode> mAddrTid = new HashMap<>();
			while(!stLocks.isEmpty()){
				LockNode l = (LockNode) stLocks.pop();
				String addr = l.getAddr();
				mAddrTid.put(addr, l);
			}
			
			for (int i = 0; i < pB.size(); i++) {
				long index = Long.valueOf(pB.get(i).substring(1)) - 1;
				AbstractNode aNode = aTrace.get((int) index);
				if (aNode instanceof LockNode) {
					String addr = ((LockNode) aNode).getAddr();
					if (mAddrTid.containsKey(addr)) {
						LockNode l = mAddrTid.get(addr);
						Vector<AbstractNode> tidTrace = trace.getThreadNodesMap().get(l.getTid());
						int index1 = tidTrace.indexOf(l);
						if (index1 != -1) {
							for (int j = index1+1; j < tidTrace.size(); j++) {
								AbstractNode absNode = tidTrace.get(j);
								String choice = "x" + absNode.getGID();
								if (!pA.contains(choice)) {
									pAB.add(choice);
									if (absNode instanceof UnlockNode) {
										break;
									}
								}
							}
						}
					}
				}
			}
		}
		
		for (int i = 0; i < pB.size(); i++) {
			if (!pA.contains(pB.get(i))) {
				pAB.add(pB.get(i));
			}
		}
		long gid = Long.valueOf(rB.substring(1));
//		Vector<AbstractNode> tAbstractNodes = trace.getFullTrace();
//		AbstractNode node1 = tAbstractNodes.get((int) gid);
		ReadNode rNodeB = (ReadNode) trace.getFullTrace().get((int) gid - 1);
		String addr = rNodeB.getAddr();
		for (int j = pAB.size() - 2; j >=0; j--){
			long _gid = Long.valueOf(pAB.get(j).substring(1));
			AbstractNode node = trace.getFullTrace().get((int) _gid - 1);
			if (node instanceof WriteNode) {
				if (((WriteNode) node).getAddr().equals(addr)) {
					//write to the same address
					if (((WriteNode) node).getValue().equals(vB)) {
						return true;
					}
					else
						return false;
				}
			}
		}
		int k;
		for (k = pA.size() - 2; k >=0; k--){
			long _gid = Long.valueOf(pA.get(k).substring(1));
			AbstractNode node = trace.getFullTrace().get((int) _gid -1 );
			if (node instanceof WriteNode) {
				if (((WriteNode) node).getAddr().equals(addr)) {
					//write to the same address
					if (((WriteNode) node).getValue().equals(vB)) {
						return true;
					}
					else
						return false;
				}
			}
		}
		
		if (k < 0) {
			return true;
		}
		
		return false;
	}
	
	private static void omcrGenSchedule(Trace trace, Vector<String> schedule, 
			Vector<String> schedule_prefix, 
			HashMap<Vector<String>, Set<Vector<String>>> localMapPrefixEquClass){
		
		Vector<String> schedule_a = new Vector<String>();	
		int start_index = 0;		
		for (int i=start_index; i<schedule.size(); i++)
		{	
			String xi = schedule.get(i);
			long gid = Long.valueOf(xi.substring(1));
			long tid = trace.getNodeGIDTIdMap().get(gid);
			String name = trace.getThreadIdNameMap().get(tid);
			schedule_a.add(name);
		}
			
		//debugging
//		System.out.println("prefix: " + schedule_a);
		
		if(!isfulltrace) {
		    //add the schedule prefix to the head of the new schedule to make it a complete schedule
			schedule_a.addAll(0, schedule_prefix);
		} 
//		System.out.println("causal schedule: " + schedule_a);
		schedules.add(schedule_a);
		//update the map_prefix_equivalent
		if (localMapPrefixEquClass.containsKey(schedule)) {
			Set<Vector<String>> equPrefixes = localMapPrefixEquClass.get(schedule);
			Set<Vector<String>> valuePrefixes = new HashSet<>();
			for (Vector<String> p : equPrefixes){
				Vector<String> v = new Vector<>();
				for (int i = 0; i < p.size(); i++) {
					String xi = p.get(i);
					long gid = Long.valueOf(xi.substring(1));
					long tid = trace.getNodeGIDTIdMap().get(gid);
					String name = trace.getThreadIdNameMap().get(tid);	
					v.add(name);
				}
				valuePrefixes.add(v);
			}
			mapPrefixEquivalent.put(schedule_a, valuePrefixes);
		}
		
	}

	/**
	 * Generate the causal schedule
	 * @param schedule: constructed from the solution
	 * @param trace:    the given trace
	 * @param schedule_prefix  
	 * @param rGid: the global ID of the read event
	 * @param rValue : old value
	 * @param wValue : new value
	 * @param wID : the global ID of the write event
	 */
	private static void generateSchedule(Vector<String> schedule, Trace trace,
			Vector<String> schedule_prefix, int rGid, String rValue, String wValue, int wID)
	{
		{	
			Vector<String> schedule_a = new Vector<String>();
			
			//get the first start event
			//note that in the first execution, there might be some events before the start event
			//but for the next execution, these events will not be executed
			
			//but for RV example, these events are executed for the next execution
			//for the implementation, just make all the assignments under main
			
			//@Alan
			int start_index = 0;
			for (int i=start_index; i<schedule.size(); i++)
			{
				String xi = schedule.get(i);
				long gid = Long.valueOf(xi.substring(1));
				long tid = trace.getNodeGIDTIdMap().get(gid);
				String name = trace.getThreadIdNameMap().get(tid);	
				
				//add addr info to the schedule 
				//the addr information is needed when replay yhe TSO/PSO schedule
				if (Configuration.mode=="TSO" || Configuration.mode=="PSO") 
				{
					String addr="";
					AbstractNode node = trace.getFullTrace().get((int) (gid-1));
					if(node instanceof ReadNode || node instanceof WriteNode){
						addr = ((IMemNode) node).getAddr();			
						if(addr.split("\\.") [0] != addr )
							addr = addr.split("\\.")[1];					
					}			
					if(addr==""){
						addr=""+node.getType();
					}
					name = name + "_" + addr;
				}
				
				schedule_a.add(name);
			}
			
//			Gson g = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
//			System.out.println(g.toJson(trace));
//			//debugging
//			System.out.println("prefix: " + schedule_a);
			
			if(!isfulltrace) {
			    //add the schedule prefix to the head of the new schedule to make it a complete schedule
				schedule_a.addAll(0, schedule_prefix);
			} else {
				//System.out.println(" USING FULL TRACE************");
			}
			// System.out.println("causal schedule: " + schedule_a);
			schedules.add(schedule_a);
			
			if(!Configuration.DEBUG)
			{
				//report the schedules
				String msg_header = "************* causal schedule "+(++schedule_id)+" **************\n";
				String msg_rwmeta = "Read-Write: "+trace.getStmtSigIdMap().get(rGid)+" -- "+(wID<0?"init":trace.getStmtSigIdMap().get(wID))+"\n";
				String msg_rwvalue = "Values: ("+rValue+"-"+wValue+")     ";
				String msg_schedule = "Schedule: "+trim(schedule_a)+"\n";
				String msg_footer = "**********************************************\n";
				
				report(msg_header+msg_rwmeta+msg_rwvalue+msg_schedule+msg_footer,MSGTYPE.STATISTICS);
			}
		}	
	}
	
	private static PrintWriter out;
	private static ConstraintsBuildEngine iEngine;
	/**
	 * Initialize the file printer. All race detection statistics are stored
	 * into the file result."window_size".
	 * @param appname
	 */
	private static void initPrinter(String appname)
	{
		if(out==null)
		try{
//		String fname = "dataraces."+appname;
//		out = new PrintWriter(new FileWriter(fname,true));
		
		}catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
	private static void report(String msg, MSGTYPE type)
	{
		switch(type)
		{
		case REAL:
		case STATISTICS:
			System.err.println(msg);
//			out.println(msg);
//			out.flush();
			break;
		default: break;
		}
	}
	public enum MSGTYPE
	{
		REAL,POTENTIAL,STATISTICS
	}
	private static ConstraintsBuildEngine getEngine(String name)
	{
		if(iEngine==null){
	        Configuration config = new Configuration();
	        config.tableName = name;
	        config.constraint_outdir = "tmp" + System.getProperty("file.separator") + "smt";

	        iEngine = new ConstraintsBuildEngine(config);//EngineSMTLIB1
		}
		
			return iEngine;
	}
	public static void setQueue(Queue<List<String>> queue) {
		
		schedules = queue;
	}
	
	/**
	 * start exploring the trace
	 * @param trace
	 * @param schedule_prefix
	 */
	public static void execute(Trace trace, Vector<String> schedule_prefix) {
		
//		System.err.println(schedule_prefix);
		
		Configuration.numReads = 0;
		Configuration.rwConstraints = 0;
		Configuration.solveTime = 0;
			
		//OPT: if #sv==0 or #shared rw ==0 continue	
		if(trace.hasSharedVariable())
		{
			System.out.println("call stop pattern");
			initPrinter(trace.getApplicationName());
			
			//engine is used for constructing constraints model
			ConstraintsBuildEngine engine = getEngine(trace.getApplicationName());
			
			//pre-process the trace
			//build the initial happen before relation for some optimization
			engine.preprocess(trace);
		
			
			//generate causal prefixes
			//genereteCausallyDifferentSchedules(engine,trace,schedule_prefix);

			engine.constructPOConstraints(trace, new HashSet<>(trace.getFullTrace()));
			engine.constructSyncConstraints(trace, new HashSet<>(trace.getFullTrace()));
			System.out.println("call stop pattern");
			stopPattern(engine, trace);
			System.out.println("end");
		}		
	}
	
	//compute the memory used
	public static int memSize(Object o){
        try{
//            System.out.println("Index Size: " + ((ByteArrayOutputStream) o).size());
            ByteArrayOutputStream baos=new ByteArrayOutputStream();
            ObjectOutputStream oos=new ObjectOutputStream(baos);
            oos.writeObject(o);
            oos.close();
//            System.out.println("Data Size: " + baos.size() + "bytes.");
            return baos.size();
        }catch(IOException e){
            e.printStackTrace();
            return -1;
        }
        
        
	}
}

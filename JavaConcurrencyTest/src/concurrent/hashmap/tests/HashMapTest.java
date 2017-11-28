package concurrent.hashmap.tests;

import static org.junit.Assert.fail;

import java.util.Iterator;

import org.junit.Test;
import org.junit.runner.RunWith;
import concurrent.hashmap.tests.ConcurrentHashMap;

import edu.tamu.aser.exploration.JUnit4MCRRunner;

@RunWith(JUnit4MCRRunner.class)
public class HashMapTest {
	
	static ConcurrentHashMap<Integer,String> conHashMap = new ConcurrentHashMap<Integer,String>();
	public static void main(String args[]) {
		HashMapTest ins = new HashMapTest();
		Thread t1 = new Thread(ins.new PutThreads());
		Thread t2 = new Thread(ins.new RemoveThreads());
		
		t1.start();
		t2.start();
		
		try {
			t1.join();
			t2.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
//		System.out.print("the con hash map: " + );		
		//print
		for (Iterator<Integer> iterator = conHashMap.keySet().iterator(); iterator.hasNext();) {
			int key = iterator.next();
			System.out.println("key: " + key + "->" + conHashMap.get(key));
			
		}
	}
	
	class PutThreads implements Runnable {
		public void run() {
			for(int i=10; i<10; ++i){
				conHashMap.putIfAbsent(i, Integer.toString(i));
			}
		}
	}
	
	class RemoveThreads implements Runnable {
		public void run() {
			for(int i=10; i<10; ++i){
				if (conHashMap.containsKey(i)) 
					conHashMap.remove(i);
//				conHashMap.putIfAbsent(i, Integer.toString(i));
			}
		}
	}
	
	@Test
	public void test(){
		try {
			HashMapTest.main(null);
		} catch (Exception e) {
			System.out.println("here");
			fail();
		}
	}
}
package edu.tamu.aser.rvtest.jigsaw;

import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;

import edu.tamu.aser.reexcution.JUnit4MCRRunner;

@RunWith(JUnit4MCRRunner.class)
public class JigsawTest 
{
	static org.w3c.jigsaw.daemon.ServerHandlerManager server;

	public static void main(final String[] args)
	{
		try{
		Thread t1 = new Thread()
		{
			public void run()
			{
				server = org.w3c.jigsaw.daemon.ServerHandlerManager.test(new String[0]);
			}
		};
		
		Thread t2 = new Thread()
		{
			public void run()
			{
				JigsawHarnessPretex.main(args);
			}
		};
		
		t1.start();
		t2.start();
		//Thread.sleep(60000);
		
		t1.join();
		t2.join();
		//System.exit(0);
		server.shutdown();
//		Set<Thread> threadSet =Thread.getAllStackTraces().keySet();
//		for(Thread t: threadSet)
//		{
//			if(t.isDaemon())
//			{
//				if(t.getName().contains("http-server"))
//				{
//					t.interrupt();
//				}
//			}
//		}

		System.out.println("Done");

	}catch(Exception e)
	{
		
	}
	}
	
	@Test
	public void test() throws InterruptedException {
		JigsawTest.main(new String[]{});
	}
}

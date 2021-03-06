package edu.tamu.aser.rvtest_simple_tests;

import static org.junit.Assert.*;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.sun.org.apache.bcel.internal.generic.StackInstruction;

import edu.tamu.aser.reexecution.JUnit4MCRRunner;
import sun.net.www.content.audio.x_aiff;

@RunWith(JUnit4MCRRunner.class)
public class Example_SharedVar {

	
	static int x=0, y;
	public static void main(String[] args) {
		Thread t1 = new Thread(new Runnable() {

			@Override
			public void run() {
				int r1 = y;
				int r = x;
			}
		});
		


		t1.start();

		x = 1;

		try {
			t1.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
	}

	@Test
	public void test() throws InterruptedException {
		try {
//			x = 0;
//			y = 0;
		Example_SharedVar.main(null);
		} catch (Exception e) {
			System.out.println("here");
			fail();
		}
	}
}
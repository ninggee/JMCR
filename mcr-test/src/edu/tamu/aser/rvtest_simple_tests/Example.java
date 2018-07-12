package edu.tamu.aser.rvtest_simple_tests;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;

import edu.tamu.aser.reexecution.JUnit4MCRRunner;
import junit.framework.Assert;

@RunWith(JUnit4MCRRunner.class)
public class Example {

//	private static int x, y;
	//private static Object lock = new Object();

	static int x, y;
	public static void main(String[] args) {
		Thread t1 = new Thread(new Runnable() {

			@Override
			public void run() {
				int b = x;
				if (x == 0) {
					y = 2;
				} else {
					y = 1;
				}
//				y = 1;
				
				System.out.println("b:" + b);
			}
		});

		t1.start();

		int a = y;
		x = 1;
		


		try {
			t1.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.out.println("a:" + a); 
	}

	@Test
	public void test() throws InterruptedException {
		try {
			x = 0;
			y = 0;
		Example.main(null);
		} catch (Exception e) {
			System.out.println("here");
			fail();
		}
	}
}
package edu.tamu.aser.rvtest.omcr;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;

import edu.tamu.aser.reexecution.JUnit4MCRRunner;

@RunWith(JUnit4MCRRunner.class)
public class PLDI15 {

	private static int x;
	private static int y;
	private static Object lock; 
	final static int loop = 2;
	
	public static void main(String[] args) {	
		Thread t1 = new Thread(new Runnable() {

			@Override
			public void run() {
				for (int i = 0; i < 2; i++) {
					synchronized (lock) {
						x = 0;
					}
					if (x > 0) {
						y++;
						x = 2;
					}
				}
			}

		});

		Thread t2 = new Thread(new Runnable() {
			@Override
			public void run() {
				for (int i = 0; i < loop; i++) {
					if (x > 1) {
						if (y == 3) {
							System.err.println("Find the error!");
//							fail();
						} else
							y = 2;
					}
				}
			}

		});
		t1.start();
		t2.start();

		for (int i = 0; i < 2; i++) {
			synchronized (lock) {
				x = 1;
				y = 1;
			}
		}
		try {
			t1.join();
			t2.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void test() throws InterruptedException {
		try {
			x = 0;
			y = 0;
			lock = new Object();
			PLDI15.main(null);
		} catch (Exception e) {
			System.out.println("here");
			fail();
		}
	}
}